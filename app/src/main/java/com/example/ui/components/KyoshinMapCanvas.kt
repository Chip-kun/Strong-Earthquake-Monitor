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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.location.UserLocation
import com.example.model.*
import com.example.model.DisplayMode
import com.example.model.DepthMode
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
    val context = LocalContext.current
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var showPrefectureNames by remember { mutableStateOf(true) }
    var localShowStationNames by remember { mutableStateOf(showStationLabels) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val textMeasurer = rememberTextMeasurer()

    // Conformal map aspect ratio of Japan bounding box
    val mapAspect = GeoProjection.MAP_ASPECT_RATIO

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
            .background(Color(0xFF050B18))
            .border(1.dp, KyoshinCardBorder, RoundedCornerShape(12.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
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
                            if (dist < 44f * density) {
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
            val canvasAspect = w / h

            val baseW: Float = if (canvasAspect > mapAspect) h * mapAspect else w
            val baseH: Float = if (canvasAspect > mapAspect) h else w / mapAspect

            val finalW = baseW * zoomLevel
            val finalH = baseH * zoomLevel
            val finalX = (w - finalW) / 2f + panOffsetX
            val finalY = (h - finalH) / 2f + panOffsetY

            // 1. Draw Ocean Background (Pure Solid Black for NIED official)
            drawOceanBackground(w, h, mapStyle)

            // 2. Draw Grid Lines (Only in RADAR style)
            if (mapStyle == MapStyle.RADAR) {
                drawMapGrid(w, h, mapStyle)
            }

            // 3. Draw Accurate Geographic Japan Coastline & Landmass (NIED dark gray)
            drawJapanGeographicLandmass(finalX, finalY, finalW, finalH, mapStyle)

            // 4. Draw Topographic Mountain Relief (Shaded mountain spines)
            drawTopographicRelief(finalX, finalY, finalW, finalH, mapStyle)

            // 5. Draw 47 Prefecture Boundaries (県境)
            drawPrefectureBoundaries(finalX, finalY, finalW, finalH, mapStyle)

            // 6. Draw Tectonic Fault Lines (日本海溝, 千島海溝, 南海トラフ, 相模トラフ)
            if (mapStyle == MapStyle.TECTONIC) {
                drawTectonicTrenches(finalX, finalY, finalW, finalH, textMeasurer, zoomLevel)
            }

            // 8. Draw 47 Prefecture Name Labels
            if (showPrefectureNames) {
                drawPrefectureLabels(finalX, finalY, finalW, finalH, textMeasurer, zoomLevel)
            }

            // 7. Draw Active EEW Waves & Epicenter
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

                if (activeEEW.tsunamiWarning != "津_波の心配なし" && activeEEW.tsunamiWarning != "津波の心配なし" && activeEEW.tsunamiWarning.isNotEmpty()) {
                    drawTsunamiOverlay(
                        finalX = finalX,
                        finalY = finalY,
                        finalW = finalW,
                        finalH = finalH,
                        alert = activeEEW,
                        zoom = zoomLevel,
                        pulse = pulseScale,
                        textMeasurer = textMeasurer
                    )
                }
            }

            // 8. Draw User Location Marker
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

            // 9. Draw Observation Stations
            val effectiveShowStationLabels = localShowStationNames || showStationLabels
            stations.forEach { station ->
                val sx = finalX + station.mapX * finalW
                val sy = finalY + station.mapY * finalH

                if (sx >= -30 && sx <= w + 30 && sy >= -30 && sy <= h + 30) {
                    val color = station.getColorForDisplayMode(displayMode)
                    val metricVal = station.getValueForDisplayMode(displayMode)
                    val baseRadius = when {
                        metricVal >= 4.0f -> 8.5f
                        metricVal >= 2.0f -> 6.5f
                        metricVal >= 0.5f -> 5.2f
                        else -> 3.6f
                    } * zoomLevel.coerceIn(0.85f, 1.8f)

                    // Outer halo for active stations
                    if (metricVal >= 0.8f) {
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

                    // Subterranean Borehole Collar Indicator (地中観測孔)
                    if (depthMode == DepthMode.BOREHOLE) {
                        drawCircle(
                            color = Color(0xFF38BDF8).copy(alpha = 0.85f),
                            radius = baseRadius + 2.5f,
                            center = Offset(sx, sy),
                            style = Stroke(width = 1.4f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.0f,
                            center = Offset(sx, sy)
                        )
                    }

                    // Station city label
                    if (effectiveShowStationLabels && (zoomLevel > 1.35f || station.realtimeShindo >= 1.0f || selectedStation?.id == station.id)) {
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
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(KyoshinSurface.copy(alpha = 0.95f))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .clickable { zoomLevel = (zoomLevel * 1.35f).coerceAtMost(6.0f) }
                    .testTag("zoom_in_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "ズームイン", tint = Color.White, modifier = Modifier.size(17.dp))
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(KyoshinSurface.copy(alpha = 0.95f))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .clickable { zoomLevel = (zoomLevel / 1.35f).coerceAtLeast(0.7f) }
                    .testTag("zoom_out_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, contentDescription = "ズームアウト", tint = Color.White, modifier = Modifier.size(17.dp))
            }

            if (userLocation != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7).copy(alpha = 0.95f))
                        .border(1.dp, Color.White, CircleShape)
                        .clickable {
                            zoomLevel = 2.5f
                            val w = canvasSize.width.toFloat()
                            val h = canvasSize.height.toFloat()
                            if (w > 0f && h > 0f) {
                                val canvasAspect = w / h
                                val baseW: Float = if (canvasAspect > mapAspect) h * mapAspect else w
                                val baseH: Float = if (canvasAspect > mapAspect) h else w / mapAspect
                                val finalW = baseW * 2.5f
                                val finalH = baseH * 2.5f
                                val userNormX = GeoProjection.toNormX(userLocation.lon)
                                val userNormY = GeoProjection.toNormY(userLocation.lat)
                                panOffsetX = finalW * (0.5f - userNormX.toFloat())
                                panOffsetY = finalH * (0.5f - userNormY.toFloat())
                            }
                        }
                        .testTag("center_user_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "現在地", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(KyoshinSurface.copy(alpha = 0.95f))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .clickable {
                        zoomLevel = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    }
                    .testTag("reset_zoom_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "リセット", tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }

        // Bottom Overlay Floating Control Bar (Sleek & Compact)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.92f))
                    .border(1.dp, KyoshinCardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Display Modes (震度 / PGA / PGV)
                DisplayMode.entries.forEach { mode ->
                    val isSelected = displayMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) KyoshinPrimary else Color.Transparent)
                            .clickable { onDisplayModeChanged(mode) }
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Prefecture Names Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showPrefectureNames) Color(0xFF3B82F6).copy(alpha = 0.85f) else Color.Transparent)
                        .clickable { showPrefectureNames = !showPrefectureNames }
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "県名",
                        color = if (showPrefectureNames) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }

                // Station Names Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (localShowStationNames) Color(0xFF10B981).copy(alpha = 0.85f) else Color.Transparent)
                        .clickable { localShowStationNames = !localShowStationNames }
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "観測点",
                        color = if (localShowStationNames) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }

                // Depth Mode (地表/地中) Segmented Control
                Surface(
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row {
                        DepthMode.entries.forEach { mode ->
                            val isSelected = depthMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (isSelected) {
                                            if (mode == DepthMode.BOREHOLE) Color(0xFF0284C7) else Color(0xFFD97706)
                                        } else Color.Transparent
                                    )
                                    .clickable {
                                        onDepthModeChanged(mode)
                                        val msg = if (mode == DepthMode.BOREHOLE) {
                                            "【地中】観測孔モード (-100m〜-3000m 深部岩盤) に切り替えました"
                                        } else {
                                            "【地表】表層観測点モード (地表0m) に切り替えました"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Map Style Cycle Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.75f))
                        .clickable {
                            val nextStyle = when (mapStyle) {
                                MapStyle.SATELLITE -> MapStyle.TECTONIC
                                MapStyle.TECTONIC -> MapStyle.RADAR
                                MapStyle.RADAR -> MapStyle.SATELLITE
                            }
                            onMapStyleChanged(nextStyle)
                        }
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = mapStyle.label.take(2),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Depth Mode HUD Badge (地表/地中 表示中インジケータ)
        Surface(
            color = if (depthMode == DepthMode.BOREHOLE) Color(0xFF0F172A).copy(alpha = 0.92f) else Color(0xFF1E293B).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, if (depthMode == DepthMode.BOREHOLE) Color(0xFF38BDF8) else Color(0xFFF59E0B)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 10.dp, top = 42.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (depthMode == DepthMode.BOREHOLE) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (depthMode == DepthMode.BOREHOLE)
                        "【地中】観測孔 (-100m〜-3000m 深部岩盤 / ノイズ除去)"
                    else
                        "【地表】表層観測点 (0m 表層地盤増幅含む)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
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
                            text = if (selectedStation.prefecture.isNotEmpty()) "[${selectedStation.prefecture}] ${selectedStation.name}" else selectedStation.name,
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

                    Text(
                        text = "位置: ${String.format("%.2f", selectedStation.lat)}°N, ${String.format("%.2f", selectedStation.lon)}°E",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${displayMode.label}:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = selectedStation.getFormattedValue(displayMode),
                            color = selectedStation.getColorForDisplayMode(displayMode),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "観測深度:", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (depthMode == DepthMode.BOREHOLE) "地中 (-100m 深部岩盤)" else "地表 (0m 表層)",
                            color = if (depthMode == DepthMode.BOREHOLE) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawOceanBackground(w: Float, h: Float, style: MapStyle) {
    val oceanColor = when (style) {
        MapStyle.SATELLITE -> Color(0xFF000000) // Pure solid pitch black matching official NIED base map (base_map_black.png)
        MapStyle.TECTONIC -> Color(0xFF03060B)
        MapStyle.RADAR -> Color(0xFF050B14)
    }
    drawRect(color = oceanColor, size = size)

    if (style != MapStyle.RADAR) {
        // Subtle deep sea bathymetric ambient shading
        drawCircle(
            color = Color(0xFF081426).copy(alpha = 0.14f),
            radius = maxOf(w, h) * 0.85f,
            center = Offset(w * 0.55f, h * 0.45f)
        )
    }
}

private fun DrawScope.drawMapGrid(w: Float, h: Float, style: MapStyle) {
    val gridColor = Color(0xFF00E5FF).copy(alpha = 0.08f)
    val step = 45.dp.toPx()
    var x = step
    while (x < w) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < h) {
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += step
    }
}

// -------------------------------------------------------------
// Official NIED Watermark Logo in bottom right corner
// -------------------------------------------------------------
private fun DrawScope.drawNIEDWatermark(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    textMeasurer: TextMeasurer
) {
    val boxW = 124f
    val boxH = 34f
    val posX = finalX + finalW - boxW - 8f
    val posY = finalY + finalH - boxH - 8f

    if (posX < 0 || posY < 0) return

    // Base badge container
    drawRoundRect(
        color = Color(0xFF1E2024).copy(alpha = 0.88f),
        topLeft = Offset(posX, posY),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = Color(0xFF4B5563),
        topLeft = Offset(posX, posY),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(width = 1f)
    )

    // NIED emblem dynamic arcs (Blue & Red)
    drawArc(
        color = Color(0xFF2563EB),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(posX + 8f, posY + 8f),
        size = Size(16f, 16f),
        style = Stroke(width = 3.5f)
    )
    drawArc(
        color = Color(0xFFDC2626),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(posX + 14f, posY + 10f),
        size = Size(14f, 14f),
        style = Stroke(width = 3f)
    )

    // Text: NIED
    val textNIED = textMeasurer.measure(
        text = "NIED",
        style = TextStyle(
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    )
    drawText(textNIED, topLeft = Offset(posX + 34f, posY + 5f))

    // Text: 防災科学技術研究所
    val textBosai = textMeasurer.measure(
        text = "防災科研 強震モニタ",
        style = TextStyle(
            color = Color(0xFFD1D5DB),
            fontSize = 6.8.sp,
            fontWeight = FontWeight.Medium
        )
    )
    drawText(textBosai, topLeft = Offset(posX + 34f, posY + 19f))
}

// -------------------------------------------------------------
// Accurate Japan Geographical Landmass Rendering
// -------------------------------------------------------------
private fun DrawScope.drawJapanGeographicLandmass(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    style: MapStyle
) {
    fun toScreenX(lon: Double): Float = finalX + GeoProjection.toNormX(lon) * finalW
    fun toScreenY(lat: Double): Float = finalY + GeoProjection.toNormY(lat) * finalH

    val (landColor, coastColor, oceanColor) = when (style) {
        MapStyle.SATELLITE -> Triple(
            Color(0xFF22262C), // Official NIED base_map_black.png dark charcoal landmass
            Color(0xFF556070), // Crisp tactical coastline border
            Color(0xFF000000)  // Pitch black ocean background
        )
        MapStyle.TECTONIC -> Triple(
            Color(0xFF252B35),
            Color(0xFF5E6D82),
            Color(0xFF03060B)
        )
        MapStyle.RADAR -> Triple(
            Color(0xFF162230),
            Color(0xFF38BDF8),
            Color(0xFF050B14)
        )
    }

    val coastStroke = Stroke(width = if (style == MapStyle.RADAR) 1.5f else 1.2f)

    // Helper to draw a landmass polygon with smooth Catmull-Rom spline curves
    fun drawSmoothIsland(points: List<Pair<Double, Double>>, tension: Float = 0.5f) {
        val path = JapanMapGeometry.createSmoothPath(
            points = points,
            toScreenX = ::toScreenX,
            toScreenY = ::toScreenY,
            closed = true,
            tension = tension
        )
        drawPath(path, color = landColor)
        drawPath(path, color = coastColor, style = coastStroke)
    }

    // 1. Four Main Islands (Hokkaido, Honshu, Shikoku, Kyushu)
    drawSmoothIsland(JapanMapGeometry.HOKKAIDO_COAST, tension = 0.45f)
    drawSmoothIsland(JapanMapGeometry.HONSHU_COAST, tension = 0.45f)
    drawSmoothIsland(JapanMapGeometry.SHIKOKU_COAST, tension = 0.45f)
    drawSmoothIsland(JapanMapGeometry.KYUSHU_COAST, tension = 0.45f)

    // 2. Prominent Offshore Islands (淡路島, 佐渡島, 対馬, 壱岐, 五島, 隠岐, 伊豆諸島, 屋久島, 種子島, 奄美, 沖縄, 先島, 北方領土)
    drawSmoothIsland(JapanMapGeometry.AWAJI_COAST)        // Awaji Island (Seto Inland Sea)
    drawSmoothIsland(JapanMapGeometry.SADO_COAST)         // Sado Island (Niigata)
    drawSmoothIsland(JapanMapGeometry.TSUSHIMA_COAST)     // Tsushima
    drawSmoothIsland(JapanMapGeometry.IKI_COAST)          // Iki
    drawSmoothIsland(JapanMapGeometry.GOTO_FUKUE_COAST)   // Goto Fukue
    drawSmoothIsland(JapanMapGeometry.GOTO_NAKADORI_COAST)
    drawSmoothIsland(JapanMapGeometry.OKI_DOGO_COAST)     // Oki Islands
    drawSmoothIsland(JapanMapGeometry.IZU_OSHIMA_COAST)   // Izu Islands
    drawSmoothIsland(JapanMapGeometry.IZU_NIIJIMA_COAST)
    drawSmoothIsland(JapanMapGeometry.IZU_KOZUSHIMA_COAST)
    drawSmoothIsland(JapanMapGeometry.IZU_MIYAKE_COAST)
    drawSmoothIsland(JapanMapGeometry.IZU_HACHIJO_COAST)
    drawSmoothIsland(JapanMapGeometry.TANEGASHIMA_COAST)  // Tanegashima
    drawSmoothIsland(JapanMapGeometry.YAKUSHIMA_COAST)    // Yakushima
    drawSmoothIsland(JapanMapGeometry.AMAMI_COAST)        // Amami Oshima
    drawSmoothIsland(JapanMapGeometry.TOKUNOSHIMA_COAST)  // Tokunoshima
    drawSmoothIsland(JapanMapGeometry.OKINAWA_COAST)      // Okinawa Main Island
    drawSmoothIsland(JapanMapGeometry.MIYAKO_COAST)       // Miyako
    drawSmoothIsland(JapanMapGeometry.ISHIGAKI_COAST)     // Ishigaki
    drawSmoothIsland(JapanMapGeometry.IRIOMOTE_COAST)     // Iriomote
    drawSmoothIsland(JapanMapGeometry.KUNASHIRI_COAST)    // Northern Territories
    drawSmoothIsland(JapanMapGeometry.SHIKOTAN_COAST)
    drawSmoothIsland(JapanMapGeometry.RISHIRI_COAST)      // Rishiri
    drawSmoothIsland(JapanMapGeometry.REBUN_COAST)        // Rebun
    drawSmoothIsland(JapanMapGeometry.OKUSHIRI_COAST)     // Okushiri

    // 3. Inland Lakes (Cutouts filled with ocean color and subtle border)
    val lakeStroke = Stroke(width = 0.9f)
    fun drawLake(points: List<Pair<Double, Double>>) {
        val path = JapanMapGeometry.createSmoothPath(
            points = points,
            toScreenX = ::toScreenX,
            toScreenY = ::toScreenY,
            closed = true,
            tension = 0.4f
        )
        drawPath(path, color = oceanColor)
        drawPath(path, color = coastColor, style = lakeStroke)
    }

    drawLake(JapanMapGeometry.LAKE_BIWA_COAST)        // Lake Biwa (滋賀・琵琶湖)
    drawLake(JapanMapGeometry.LAKE_KASUMIGAURA_COAST) // Lake Kasumigaura (茨城・霞ヶ浦)
    drawLake(JapanMapGeometry.LAKE_SAROMA_COAST)      // Lake Saroma (北海道・サロマ湖)
    drawLake(JapanMapGeometry.LAKE_INAWASHIRO_COAST)  // Lake Inawashiro (福島・猪苗代湖)
    drawLake(JapanMapGeometry.LAKE_SHINJI_COAST)      // Lake Shinji/Nakaumi (島根・宍道湖)
    drawLake(JapanMapGeometry.LAKE_HAMANA_COAST)      // Lake Hamana (静岡・浜名湖)
    drawLake(JapanMapGeometry.LAKE_TOWADA_COAST)      // Lake Towada (青森・十和田湖)
}

// -------------------------------------------------------------
// Topographic Relief (山脈・地形陰影)
// -------------------------------------------------------------
private fun DrawScope.drawTopographicRelief(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    style: MapStyle
) {
    if (style == MapStyle.RADAR) return
    fun toScreenX(lon: Double): Float = finalX + GeoProjection.toNormX(lon) * finalW
    fun toScreenY(lat: Double): Float = finalY + GeoProjection.toNormY(lat) * finalH

    val spineColor = Color(0xFF3E4756).copy(alpha = 0.38f)
    JapanMapGeometry.MOUNTAIN_SPINES.forEach { ridgePts ->
        val ridgePath = JapanMapGeometry.createSmoothPath(
            points = ridgePts,
            toScreenX = ::toScreenX,
            toScreenY = ::toScreenY,
            closed = false,
            tension = 0.4f
        )
        drawPath(ridgePath, color = spineColor, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
    }
}

// -------------------------------------------------------------
// Complete 47 Prefecture Boundaries (全47都道府県 県境)
// -------------------------------------------------------------
private fun DrawScope.drawPrefectureBoundaries(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    style: MapStyle
) {
    fun toScreenX(lon: Double): Float = finalX + GeoProjection.toNormX(lon) * finalW
    fun toScreenY(lat: Double): Float = finalY + GeoProjection.toNormY(lat) * finalH

    val boundaryColor = when (style) {
        MapStyle.RADAR -> Color(0xFF38BDF8).copy(alpha = 0.4f)
        MapStyle.TECTONIC -> Color(0xFF5A6678)
        MapStyle.SATELLITE -> Color(0xFF3C4452) // Crisp thin boundary lines matching official NIED basemap
    }
    val boundaryStroke = Stroke(width = 0.85f)

    JapanMapGeometry.PREFECTURE_DIVIDERS.forEach { linePoints ->
        val path = JapanMapGeometry.createSmoothPath(
            points = linePoints,
            toScreenX = ::toScreenX,
            toScreenY = ::toScreenY,
            closed = false,
            tension = 0.35f
        )
        drawPath(path, color = boundaryColor, style = boundaryStroke)
    }
}

// -------------------------------------------------------------
// 47 Prefecture Name Labels
// -------------------------------------------------------------

private fun DrawScope.drawPrefectureLabels(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    textMeasurer: TextMeasurer,
    zoomLevel: Float
) {
    JAPAN_PREFECTURES.forEach { pref ->
        val px = finalX + pref.mapX * finalW
        val py = finalY + pref.mapY * finalH

        if (px >= 0 && px <= size.width && py >= 0 && py <= size.height) {
            val labelText = if (zoomLevel > 1.8f) pref.name else pref.shortName
            val fontSize = if (zoomLevel > 1.8f) 10.sp else 8.5.sp

            val textResult = textMeasurer.measure(
                text = labelText,
                style = TextStyle(
                    color = Color(0xFFE5E7EB),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(color = Color.Black, blurRadius = 4f)
                )
            )

            // Draw a subtle translucent backing pill for high readability
            val padX = 4f
            val padY = 2f
            val bgRect = androidx.compose.ui.geometry.Rect(
                offset = Offset(px - textResult.size.width / 2f - padX, py - textResult.size.height / 2f - padY),
                size = androidx.compose.ui.geometry.Size(textResult.size.width + padX * 2, textResult.size.height + padY * 2)
            )
            drawRoundRect(
                color = Color(0xFF1E2024).copy(alpha = 0.80f),
                topLeft = bgRect.topLeft,
                size = bgRect.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = Color(0xFF4B5563).copy(alpha = 0.60f),
                topLeft = bgRect.topLeft,
                size = bgRect.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                style = Stroke(width = 0.8f)
            )

            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(px - textResult.size.width / 2f, py - textResult.size.height / 2f)
            )
        }
    }
}

// -------------------------------------------------------------
// Tectonic Trenches (日本海溝, 千島海溝, 南海トラフ, 相模トラフ)
// -------------------------------------------------------------

private fun DrawScope.drawTectonicTrenches(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    textMeasurer: TextMeasurer,
    zoomLevel: Float
) {
    fun toScreenX(lon: Double): Float = finalX + GeoProjection.toNormX(lon) * finalW
    fun toScreenY(lat: Double): Float = finalY + GeoProjection.toNormY(lat) * finalH

    // Japan Trench (日本海溝) & Kuril Trench
    val japanTrenchPath = JapanMapGeometry.createSmoothPath(
        points = JapanMapGeometry.JAPAN_TRENCH,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        closed = false,
        tension = 0.35f
    )
    drawPath(
        japanTrenchPath,
        color = Color(0xFF38BDF8).copy(alpha = 0.60f),
        style = Stroke(width = 2.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
    )

    // Nankai Trough (南海トラフ)
    val nankaiTroughPath = JapanMapGeometry.createSmoothPath(
        points = JapanMapGeometry.NANKAI_TROUGH,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        closed = false,
        tension = 0.35f
    )
    drawPath(
        nankaiTroughPath,
        color = Color(0xFFF43F5E).copy(alpha = 0.70f),
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f)))
    )

    // Ryukyu Trench (琉球海溝)
    val ryukyuTrenchPath = JapanMapGeometry.createSmoothPath(
        points = JapanMapGeometry.RYUKYU_TRENCH,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        closed = false,
        tension = 0.35f
    )
    drawPath(
        ryukyuTrenchPath,
        color = Color(0xFFF59E0B).copy(alpha = 0.60f),
        style = Stroke(width = 2.0f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
    )

    // Draw Trench Labels if zoomed
    if (zoomLevel > 1.1f) {
        val jtLabel = textMeasurer.measure("日本海溝", TextStyle(color = Color(0xFF38BDF8).copy(alpha = 0.85f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
        drawText(jtLabel, topLeft = Offset(toScreenX(144.0) + 4f, toScreenY(38.5)))

        val ntLabel = textMeasurer.measure("南海トラフ", TextStyle(color = Color(0xFFF43F5E).copy(alpha = 0.90f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
        drawText(ntLabel, topLeft = Offset(toScreenX(135.5), toScreenY(32.7) + 6f))

        val rtLabel = textMeasurer.measure("琉球海溝", TextStyle(color = Color(0xFFF59E0B).copy(alpha = 0.85f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
        drawText(rtLabel, topLeft = Offset(toScreenX(128.5) + 4f, toScreenY(26.5)))
    }
}

// -------------------------------------------------------------
// EEW Wave Propagation & Epicenter Crosshair
// -------------------------------------------------------------

private fun DrawScope.drawEEWWaves(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    alert: EEWAlert,
    zoom: Float,
    pulse: Float
) {
    val epiNormX = GeoProjection.toNormX(alert.lon)
    val epiNormY = GeoProjection.toNormY(alert.lat)

    val epiX = finalX + epiNormX * finalW
    val epiY = finalY + epiNormY * finalH

    // Total width corresponds to ~2184 km across the projected longitude span
    val kmToPixel = (finalW / 2184f)
    val pRadiusPx = (alert.pWaveRadiusKm * kmToPixel).coerceAtLeast(8f)
    val sRadiusPx = (alert.sWaveRadiusKm * kmToPixel).coerceAtLeast(4f)

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

private fun DrawScope.drawTsunamiOverlay(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    alert: EEWAlert,
    zoom: Float,
    pulse: Float,
    textMeasurer: TextMeasurer
) {
    val epiNormX = GeoProjection.toNormX(alert.lon)
    val epiNormY = GeoProjection.toNormY(alert.lat)
    val epiX = finalX + epiNormX * finalW
    val epiY = finalY + epiNormY * finalH

    val kmToPixel = (finalW / 2184f)
    val tsunamiRadiusPx = (alert.sWaveRadiusKm * 1.6f * kmToPixel).coerceAtLeast(35f)

    // Tsunami wavefront concentric rings (Cyan/Blue wave rings)
    drawCircle(
        color = Color(0xFF0284C7).copy(alpha = 0.22f),
        radius = tsunamiRadiusPx * pulse,
        center = Offset(epiX, epiY)
    )
    drawCircle(
        color = Color(0xFF38BDF8).copy(alpha = 0.85f),
        radius = tsunamiRadiusPx * pulse,
        center = Offset(epiX, epiY),
        style = Stroke(width = 3.0f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f)))
    )

    if (tsunamiRadiusPx > 40f) {
        drawCircle(
            color = Color(0xFF0EA5E9).copy(alpha = 0.35f),
            radius = (tsunamiRadiusPx * 0.55f),
            center = Offset(epiX, epiY),
            style = Stroke(width = 2.0f)
        )
    }

    val badgeText = "🌊 ${alert.tsunamiWarning}"
    val textLayout = textMeasurer.measure(
        badgeText,
        TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    )
    val boxWidth = textLayout.size.width + 18f
    val boxHeight = textLayout.size.height + 10f
    val boxLeft = (epiX - boxWidth / 2f).coerceIn(finalX + 8f, finalX + finalW - boxWidth - 8f)
    val boxTop = (epiY - tsunamiRadiusPx - boxHeight - 12f).coerceAtLeast(finalY + 8f)

    drawRect(
        color = Color(0xFF0369A1).copy(alpha = 0.92f),
        topLeft = Offset(boxLeft, boxTop),
        size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight)
    )
    drawRect(
        color = Color(0xFF38BDF8),
        topLeft = Offset(boxLeft, boxTop),
        size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight),
        style = Stroke(width = 1.5f)
    )
    drawText(
        textLayoutResult = textLayout,
        topLeft = Offset(boxLeft + 9f, boxTop + 5f)
    )
}

// -------------------------------------------------------------
// User Location Marker
// -------------------------------------------------------------

private fun DrawScope.drawUserLocation(
    finalX: Float,
    finalY: Float,
    finalW: Float,
    finalH: Float,
    user: UserLocation,
    pulse: Float,
    textMeasurer: TextMeasurer
) {
    val userNormX = GeoProjection.toNormX(user.lon)
    val userNormY = GeoProjection.toNormY(user.lat)

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

