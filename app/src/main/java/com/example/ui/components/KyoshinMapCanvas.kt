package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.location.UserLocation
import com.example.model.DepthMode
import com.example.model.DisplayMode
import com.example.model.EEWAlert
import com.example.model.ObservationStation
import com.example.ui.MapStyle
import com.example.ui.theme.*
import kotlin.math.hypot

@Composable
fun KyoshinMapCanvas(
    stations: List<ObservationStation>,
    activeEEW: EEWAlert?,
    selectedStation: ObservationStation?,
    userLocation: UserLocation?,
    mapStyle: MapStyle,
    showStationLabels: Boolean,
    displayMode: DisplayMode,
    depthMode: DepthMode,
    onMapStyleChanged: (MapStyle) -> Unit,
    onDisplayModeChanged: (DisplayMode) -> Unit,
    onDepthModeChanged: (DepthMode) -> Unit,
    onStationSelected: (ObservationStation?) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    val textMeasurer = rememberTextMeasurer()
    val satelliteBitmap = ImageBitmap.imageResource(id = R.drawable.japan_seismic_map_1791766355898)

    // Wave animation pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulseWave")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF060A14))
            .border(1.dp, KyoshinCardBorder, RoundedCornerShape(12.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("kyoshin_map_canvas")
                // Pinch to zoom and pan gestures
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomLevel = (zoomLevel * zoom).coerceIn(0.7f, 6.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
                // Tap gesture for selecting station
                .pointerInput(stations, zoomLevel, panOffsetX, panOffsetY) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val mapAspect = satelliteBitmap.width.toFloat() / satelliteBitmap.height.toFloat()
                        val canvasAspect = w / h

                        val baseW: Float = if (canvasAspect > mapAspect) h * mapAspect else w
                        val baseH: Float = if (canvasAspect > mapAspect) h else w / mapAspect
                        val finalW = baseW * zoomLevel
                        val finalH = baseH * zoomLevel
                        val finalX = (w - finalW) / 2f + panOffsetX
                        val finalY = (h - finalH) / 2f + panOffsetY

                        val hitStation = stations.minByOrNull { station ->
                            val sx = finalX + station.mapX * finalW
                            val sy = finalY + station.mapY * finalH
                            hypot(sx - tapOffset.x, sy - tapOffset.y)
                        }

                        if (hitStation != null) {
                            val sx = finalX + hitStation.mapX * finalW
                            val sy = finalY + hitStation.mapY * finalH
                            val dist = hypot(sx - tapOffset.x, sy - tapOffset.y)
                            if (dist < 48f * density) {
                                onStationSelected(hitStation)
                            } else {
                                onStationSelected(null)
                            }
                        } else {
                            onStationSelected(null)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Calculate true aspect-ratio dimensions to avoid squashing Japan
            val mapAspect = satelliteBitmap.width.toFloat() / satelliteBitmap.height.toFloat()
            val canvasAspect = w / h

            val baseW: Float = if (canvasAspect > mapAspect) h * mapAspect else w
            val baseH: Float = if (canvasAspect > mapAspect) h else w / mapAspect

            val finalW = baseW * zoomLevel
            val finalH = baseH * zoomLevel
            val finalX = (w - finalW) / 2f + panOffsetX
            val finalY = (h - finalH) / 2f + panOffsetY

            // 1. Draw Map Background with exact aspect ratio
            if (mapStyle == MapStyle.SATELLITE) {
                drawImage(
                    image = satelliteBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(satelliteBitmap.width, satelliteBitmap.height),
                    dstOffset = IntOffset(finalX.toInt(), finalY.toInt()),
                    dstSize = IntSize(finalW.toInt(), finalH.toInt()),
                    alpha = 0.95f
                )
            } else {
                drawMapGrid(w, h)
                drawJapanVectorOutline(finalX, finalY, finalW, finalH)
            }

            // 2. Draw Tectonic Fault Lines (日本海溝, 南海トラフ)
            if (mapStyle == MapStyle.TECTONIC || mapStyle == MapStyle.SATELLITE) {
                drawTectonicFaultLines(finalX, finalY, finalW, finalH)
            }

            // 3. Draw Active EEW Waves & Epicenter
            if (activeEEW != null) {
                drawEEWWaves(
                    finalX = finalX,
                    finalY = finalY,
                    finalW = finalW,
                    finalH = finalH,
                    alert = activeEEW,
                    zoom = zoomLevel,
                    pulse = pulseScale
                )
            }

            // 4. Draw User Location Marker
            if (userLocation != null) {
                drawUserLocation(
                    finalX = finalX,
                    finalY = finalY,
                    finalW = finalW,
                    finalH = finalH,
                    user = userLocation,
                    pulse = pulseScale,
                    textMeasurer = textMeasurer
                )
            }

            // 5. Draw Observation Stations
            stations.forEach { station ->
                val sx = finalX + station.mapX * finalW
                val sy = finalY + station.mapY * finalH

                if (sx >= -25 && sx <= w + 25 && sy >= -25 && sy <= h + 25) {
                    val color = station.getIntensityColor()
                    val baseRadius = when {
                        station.realtimeShindo >= 4.0f -> 8.5f
                        station.realtimeShindo >= 2.0f -> 6.5f
                        station.realtimeShindo >= 0.5f -> 5.2f
                        else -> 3.6f
                    } * zoomLevel.coerceIn(0.85f, 1.8f)

                    // Outer halo for shaking stations
                    if (station.realtimeShindo >= 0.8f) {
                        drawCircle(
                            color = color.copy(alpha = 0.4f),
                            radius = baseRadius * 2.5f * pulseScale,
                            center = Offset(sx, sy)
                        )
                    }

                    // Station circle
                    drawCircle(
                        color = color,
                        radius = baseRadius,
                        center = Offset(sx, sy)
                    )

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f),
                        radius = baseRadius,
                        center = Offset(sx, sy),
                        style = Stroke(width = 1.2f)
                    )

                    // Station city label
                    if (showStationLabels && (zoomLevel > 1.3f || station.realtimeShindo >= 1.0f || selectedStation?.id == station.id)) {
                        val textLayout = textMeasurer.measure(
                            text = station.name,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(color = Color.Black, blurRadius = 4f)
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(sx - textLayout.size.width / 2f, sy + baseRadius + 2f)
                        )
                    }

                    // Selected cursor ring
                    if (selectedStation?.id == station.id) {
                        drawCircle(
                            color = Color.White,
                            radius = (baseRadius + 6f) * pulseScale,
                            center = Offset(sx, sy),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }
        }

        // Top-Start Floating Location Badge
        userLocation?.let { loc ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = KyoshinSurface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, KyoshinCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = KyoshinPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = loc.placeName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        // Top-End Control Buttons (Zoom, Center User, Reset)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { zoomLevel = (zoomLevel * 1.35f).coerceAtMost(6.0f) },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(KyoshinSurface.copy(alpha = 0.9f))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .testTag("zoom_in_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "ズームイン", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = { zoomLevel = (zoomLevel / 1.35f).coerceAtLeast(0.7f) },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(KyoshinSurface.copy(alpha = 0.9f))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .testTag("zoom_out_button")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "ズームアウト", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            if (userLocation != null) {
                IconButton(
                    onClick = {
                        zoomLevel = 2.2f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7).copy(alpha = 0.9f))
                        .border(1.dp, Color.White, CircleShape)
                        .testTag("center_user_button")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "現在地", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            IconButton(
                onClick = {
                    zoomLevel = 1.0f
                    panOffsetX = 0f
                    panOffsetY = 0f
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(KyoshinSurface.copy(alpha = 0.9f))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .testTag("reset_zoom_button")
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "リセット", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Bottom Overlay Floating Control Bars: Mode & Map Layer
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Mode Selectors (震度 / PGA / PGV)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(KyoshinSurface.copy(alpha = 0.92f))
                    .border(1.dp, KyoshinCardBorder, RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DisplayMode.entries.forEach { mode ->
                    val isSelected = displayMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) KyoshinPrimary else Color.Transparent)
                            .clickable { onDisplayModeChanged(mode) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Layer Selectors: Depth (地表/地中) + Style (衛星/暗色)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(KyoshinSurface.copy(alpha = 0.92f))
                    .border(1.dp, KyoshinCardBorder, RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DepthMode.entries.forEach { mode ->
                    val isSelected = depthMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFFF59E0B) else Color.Transparent)
                            .clickable { onDepthModeChanged(mode) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Map Style Cycle Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.7f))
                        .clickable {
                            val nextStyle = when (mapStyle) {
                                MapStyle.SATELLITE -> MapStyle.TECTONIC
                                MapStyle.TECTONIC -> MapStyle.RADAR
                                MapStyle.RADAR -> MapStyle.SATELLITE
                            }
                            onMapStyleChanged(nextStyle)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = mapStyle.label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Selected Station Floating Info Card
        if (selectedStation != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 42.dp)
                    .widthIn(max = 240.dp)
                    .testTag("station_info_card"),
                colors = CardDefaults.cardColors(containerColor = KyoshinSurface.copy(alpha = 0.95f)),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "観測点: ${selectedStation.name}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        IconButton(
                            onClick = { onStationSelected(null) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "リアルタイム震度:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = String.format("%.2f", selectedStation.realtimeShindo),
                            color = selectedStation.getIntensityColor(),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "最大加速度 (PGA):", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = String.format("%.2f gal", selectedStation.pga),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawUserLocation(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    user: UserLocation,
    pulse: Float,
    textMeasurer: TextMeasurer
) {
    val baseLon = 130.40
    val dLon = (user.lon - baseLon).toFloat()
    val latTilt = (user.lat.toFloat() - 35.0f) * 0.006f
    val userNormX = (0.24f + dLon * 0.0405f + latTilt).coerceIn(0.02f, 0.98f)
    val userNormY = (0.08f + (45.5f - user.lat.toFloat()) * 0.042f).coerceIn(0.02f, 0.98f)

    val ux = finalX + userNormX * finalW
    val uy = finalY + userNormY * finalH

    // Pulsing halo
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.25f),
        radius = 18f * pulse,
        center = Offset(ux, uy)
    )

    // Inner bright cyan beacon
    drawCircle(
        color = Color(0xFF00E5FF),
        radius = 7f,
        center = Offset(ux, uy)
    )

    drawCircle(
        color = Color.White,
        radius = 3.5f,
        center = Offset(ux, uy)
    )

    val textResult = textMeasurer.measure(
        text = "現在地",
        style = TextStyle(
            color = Color(0xFF00E5FF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(color = Color.Black, blurRadius = 4f)
        )
    )
    drawText(
        textLayoutResult = textResult,
        topLeft = Offset(ux - textResult.size.width / 2f, uy - 18f)
    )
}

private fun DrawScope.drawTectonicFaultLines(finalX: Float, finalY: Float, finalW: Float, finalH: Float) {
    // Japan Trench (日本海溝) & Kuril Trench
    val japanTrenchPath = Path().apply {
        moveTo(finalX + 0.88f * finalW, finalY + 0.08f * finalH)
        lineTo(finalX + 0.85f * finalW, finalY + 0.18f * finalH)
        lineTo(finalX + 0.80f * finalW, finalY + 0.30f * finalH)
        lineTo(finalX + 0.78f * finalW, finalY + 0.42f * finalH)
        lineTo(finalX + 0.74f * finalW, finalY + 0.55f * finalH)
        lineTo(finalX + 0.70f * finalW, finalY + 0.65f * finalH)
    }
    drawPath(japanTrenchPath, color = Color(0xFF38BDF8).copy(alpha = 0.55f), style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))

    // Nankai Trough (南海トラフ)
    val nankaiTroughPath = Path().apply {
        moveTo(finalX + 0.66f * finalW, finalY + 0.62f * finalH)
        lineTo(finalX + 0.56f * finalW, finalY + 0.67f * finalH)
        lineTo(finalX + 0.46f * finalW, finalY + 0.71f * finalH)
        lineTo(finalX + 0.36f * finalW, finalY + 0.76f * finalH)
        lineTo(finalX + 0.24f * finalW, finalY + 0.82f * finalH)
    }
    drawPath(nankaiTroughPath, color = Color(0xFFF43F5E).copy(alpha = 0.65f), style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))))
}

private fun DrawScope.drawMapGrid(w: Float, h: Float) {
    val gridColor = Color(0xFF131D33)
    val step = 60f
    var x = 0f
    while (x < w) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < h) {
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawJapanVectorOutline(finalX: Float, finalY: Float, finalW: Float, finalH: Float) {
    val landColor = Color(0xFF0F1A2E)
    val coastColor = Color(0xFF1E3154)

    fun tfX(normX: Float): Float = finalX + normX * finalW
    fun tfY(normY: Float): Float = finalY + normY * finalH

    // Hokkaido
    val hokkaidoPath = Path().apply {
        moveTo(tfX(0.70f), tfY(0.04f))
        lineTo(tfX(0.75f), tfY(0.06f))
        lineTo(tfX(0.85f), tfY(0.07f))
        lineTo(tfX(0.92f), tfY(0.12f))
        lineTo(tfX(0.84f), tfY(0.18f))
        lineTo(tfX(0.74f), tfY(0.20f))
        lineTo(tfX(0.66f), tfY(0.24f))
        lineTo(tfX(0.68f), tfY(0.18f))
        close()
    }
    drawPath(hokkaidoPath, color = landColor)
    drawPath(hokkaidoPath, color = coastColor, style = Stroke(width = 1.5f))

    // Honshu
    val honshuPath = Path().apply {
        moveTo(tfX(0.68f), tfY(0.28f))
        lineTo(tfX(0.72f), tfY(0.33f))
        lineTo(tfX(0.70f), tfY(0.42f))
        lineTo(tfX(0.69f), tfY(0.48f))
        lineTo(tfX(0.68f), tfY(0.55f))
        lineTo(tfX(0.64f), tfY(0.62f))
        lineTo(tfX(0.59f), tfY(0.63f))
        lineTo(tfX(0.52f), tfY(0.65f))
        lineTo(tfX(0.44f), tfY(0.68f))
        lineTo(tfX(0.37f), tfY(0.63f))
        lineTo(tfX(0.28f), tfY(0.65f))
        lineTo(tfX(0.18f), tfY(0.67f))
        lineTo(tfX(0.22f), tfY(0.61f))
        lineTo(tfX(0.32f), tfY(0.58f))
        lineTo(tfX(0.39f), tfY(0.55f))
        lineTo(tfX(0.45f), tfY(0.48f))
        lineTo(tfX(0.48f), tfY(0.52f))
        lineTo(tfX(0.57f), tfY(0.46f))
        lineTo(tfX(0.62f), tfY(0.38f))
        lineTo(tfX(0.65f), tfY(0.30f))
        close()
    }
    drawPath(honshuPath, color = landColor)
    drawPath(honshuPath, color = coastColor, style = Stroke(width = 1.5f))

    // Shikoku
    val shikokuPath = Path().apply {
        moveTo(tfX(0.32f), tfY(0.67f))
        lineTo(tfX(0.32f), tfY(0.72f))
        lineTo(tfX(0.24f), tfY(0.77f))
        lineTo(tfX(0.21f), tfY(0.71f))
        lineTo(tfX(0.22f), tfY(0.67f))
        lineTo(tfX(0.28f), tfY(0.67f))
        close()
    }
    drawPath(shikokuPath, color = landColor)
    drawPath(shikokuPath, color = coastColor, style = Stroke(width = 1.5f))

    // Kyushu
    val kyushuPath = Path().apply {
        moveTo(tfX(0.16f), tfY(0.69f))
        lineTo(tfX(0.13f), tfY(0.73f))
        lineTo(tfX(0.14f), tfY(0.78f))
        lineTo(tfX(0.13f), tfY(0.85f))
        lineTo(tfX(0.18f), tfY(0.86f))
        lineTo(tfX(0.20f), tfY(0.78f))
        lineTo(tfX(0.21f), tfY(0.72f))
        close()
    }
    drawPath(kyushuPath, color = landColor)
    drawPath(kyushuPath, color = coastColor, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawEEWWaves(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    alert: EEWAlert,
    zoom: Float,
    pulse: Float
) {
    val baseLon = 130.40
    val dLon = (alert.lon - baseLon).toFloat()
    val latTilt = (alert.lat.toFloat() - 35.0f) * 0.006f
    val epiNormX = (0.24f + dLon * 0.0405f + latTilt).coerceIn(0.02f, 0.98f)
    val epiNormY = (0.08f + (45.5f - alert.lat.toFloat()) * 0.042f).coerceIn(0.02f, 0.98f)

    val epiX = finalX + epiNormX * finalW
    val epiY = finalY + epiNormY * finalH

    val pxPerKm = (finalW / 1800f)
    val pRadiusPx = (alert.pWaveRadiusKm * pxPerKm).coerceAtLeast(8f)
    val sRadiusPx = (alert.sWaveRadiusKm * pxPerKm).coerceAtLeast(4f)

    // P-wave (Primary Wave) - Cyan expanding ring
    drawCircle(
        color = PWaveColor.copy(alpha = 0.28f),
        radius = pRadiusPx,
        center = Offset(epiX, epiY),
        style = Stroke(width = 2.8f)
    )

    // S-wave (Secondary Wave) - Red/Amber Hazard circle
    if (sRadiusPx > 5f) {
        drawCircle(
            color = SWaveColor.copy(alpha = 0.15f),
            radius = sRadiusPx,
            center = Offset(epiX, epiY)
        )
        drawCircle(
            color = SWaveColor.copy(alpha = 0.9f),
            radius = sRadiusPx,
            center = Offset(epiX, epiY),
            style = Stroke(width = 3.5f)
        )
    }

    // Epicenter Pulsing Crosshair Mark
    val crossSize = 14f * pulse
    drawLine(
        color = Color.Red,
        start = Offset(epiX - crossSize, epiY - crossSize),
        end = Offset(epiX + crossSize, epiY + crossSize),
        strokeWidth = 3.5f
    )
    drawLine(
        color = Color.Red,
        start = Offset(epiX - crossSize, epiY + crossSize),
        end = Offset(epiX + crossSize, epiY - crossSize),
        strokeWidth = 3.5f
    )
    drawCircle(
        color = Color.White,
        radius = 4.5f,
        center = Offset(epiX, epiY)
    )
}
