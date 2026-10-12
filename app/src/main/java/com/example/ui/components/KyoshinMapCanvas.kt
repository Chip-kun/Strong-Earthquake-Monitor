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
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var showPrefectureNames by remember { mutableStateOf(true) }
    var localShowStationNames by remember { mutableStateOf(showStationLabels) }

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

            // 1. Draw Ocean Background & Bathymetry
            drawOceanBackground(w, h, mapStyle)

            // 2. Draw Grid Lines
            drawMapGrid(w, h, mapStyle)

            // 3. Draw Accurate Geographic Japan Coastline & Landmass
            drawJapanGeographicLandmass(finalX, finalY, finalW, finalH, mapStyle)

            // 4. Draw Prefecture Boundaries (県境)
            drawPrefectureBoundaries(finalX, finalY, finalW, finalH, mapStyle)

            // 5. Draw Tectonic Fault Lines (日本海溝, 千島海溝, 南海トラフ, 相模トラフ)
            if (mapStyle == MapStyle.TECTONIC || mapStyle == MapStyle.SATELLITE) {
                drawTectonicTrenches(finalX, finalY, finalW, finalH, textMeasurer, zoomLevel)
            }

            // 6. Draw 47 Prefecture Name Labels
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

            // Layer & Label Selectors
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(KyoshinSurface.copy(alpha = 0.92f))
                    .border(1.dp, KyoshinCardBorder, RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Prefecture Names Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showPrefectureNames) Color(0xFF3B82F6).copy(alpha = 0.85f) else Color.Transparent)
                        .clickable { showPrefectureNames = !showPrefectureNames }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "都道府県",
                        color = if (showPrefectureNames) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = if (showPrefectureNames) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Station Names Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (localShowStationNames) Color(0xFF10B981).copy(alpha = 0.85f) else Color.Transparent)
                        .clickable { localShowStationNames = !localShowStationNames }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "観測点",
                        color = if (localShowStationNames) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = if (localShowStationNames) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Depth Mode (地表/地中)
                DepthMode.entries.forEach { mode ->
                    val isSelected = depthMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFFF59E0B) else Color.Transparent)
                            .clickable { onDepthModeChanged(mode) }
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

private fun DrawScope.drawOceanBackground(w: Float, h: Float, style: MapStyle) {
    val oceanBrush = when (style) {
        MapStyle.SATELLITE -> Brush.radialGradient(
            colors = listOf(Color(0xFF0A1528), Color(0xFF040813), Color(0xFF020409)),
            center = Offset(w * 0.5f, h * 0.5f),
            radius = hypot(w, h) * 0.7f
        )
        MapStyle.TECTONIC -> Brush.verticalGradient(
            colors = listOf(Color(0xFF060D1A), Color(0xFF030710))
        )
        MapStyle.RADAR -> Brush.linearGradient(
            colors = listOf(Color(0xFF02050D), Color(0xFF050E1F))
        )
    }
    drawRect(brush = oceanBrush, size = size)
}

private fun DrawScope.drawMapGrid(w: Float, h: Float, style: MapStyle) {
    val gridColor = when (style) {
        MapStyle.RADAR -> Color(0xFF00E5FF).copy(alpha = 0.08f)
        MapStyle.TECTONIC -> Color(0xFF38BDF8).copy(alpha = 0.05f)
        MapStyle.SATELLITE -> Color(0xFF1E293B).copy(alpha = 0.35f)
    }
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

    fun createPath(points: List<Pair<Double, Double>>): Path {
        val path = Path()
        points.forEachIndexed { index, (lat, lon) ->
            val px = toScreenX(lon)
            val py = toScreenY(lat)
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    val (landColor, coastColor, coastGlowColor) = when (style) {
        MapStyle.SATELLITE -> Triple(Color(0xFF101B2E), Color(0xFF254B7A), Color(0xFF38BDF8).copy(alpha = 0.18f))
        MapStyle.TECTONIC -> Triple(Color(0xFF0D1728), Color(0xFF1E3A60), Color(0xFF0284C7).copy(alpha = 0.15f))
        MapStyle.RADAR -> Triple(Color(0xFF071224), Color(0xFF00E5FF).copy(alpha = 0.8f), Color(0xFF00E5FF).copy(alpha = 0.25f))
    }

    // 1. Hokkaido
    val hokkaidoPath = createPath(HOKKAIDO_COAST)
    drawPath(hokkaidoPath, color = landColor)
    drawPath(hokkaidoPath, color = coastGlowColor, style = Stroke(width = 4f))
    drawPath(hokkaidoPath, color = coastColor, style = Stroke(width = 1.4f))

    // 2. Honshu
    val honshuPath = createPath(HONSHU_COAST)
    drawPath(honshuPath, color = landColor)
    drawPath(honshuPath, color = coastGlowColor, style = Stroke(width = 4f))
    drawPath(honshuPath, color = coastColor, style = Stroke(width = 1.4f))

    // 3. Shikoku
    val shikokuPath = createPath(SHIKOKU_COAST)
    drawPath(shikokuPath, color = landColor)
    drawPath(shikokuPath, color = coastGlowColor, style = Stroke(width = 3.5f))
    drawPath(shikokuPath, color = coastColor, style = Stroke(width = 1.4f))

    // 4. Kyushu
    val kyushuPath = createPath(KYUSHU_COAST)
    drawPath(kyushuPath, color = landColor)
    drawPath(kyushuPath, color = coastGlowColor, style = Stroke(width = 3.5f))
    drawPath(kyushuPath, color = coastColor, style = Stroke(width = 1.4f))

    // 5. Okinawa Main Island
    val okinawaPath = createPath(OKINAWA_COAST)
    drawPath(okinawaPath, color = landColor)
    drawPath(okinawaPath, color = coastColor, style = Stroke(width = 1.4f))

    // 6. Miyakojima & Ishigakijima
    val ishigakiPath = createPath(ISHIGAKI_COAST)
    drawPath(ishigakiPath, color = landColor)
    drawPath(ishigakiPath, color = coastColor, style = Stroke(width = 1.2f))

    val miyakoPath = createPath(MIYAKO_COAST)
    drawPath(miyakoPath, color = landColor)
    drawPath(miyakoPath, color = coastColor, style = Stroke(width = 1.2f))

    // 7. Sado Island & Tsushima
    val sadoPath = createPath(SADO_COAST)
    drawPath(sadoPath, color = landColor)
    drawPath(sadoPath, color = coastColor, style = Stroke(width = 1.2f))

    val tsushimaPath = createPath(TSUSHIMA_COAST)
    drawPath(tsushimaPath, color = landColor)
    drawPath(tsushimaPath, color = coastColor, style = Stroke(width = 1.2f))
}

// -------------------------------------------------------------
// Prefecture Boundaries (県境)
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
        MapStyle.RADAR -> Color(0xFF00E5FF).copy(alpha = 0.25f)
        MapStyle.TECTONIC -> Color(0xFF38BDF8).copy(alpha = 0.22f)
        MapStyle.SATELLITE -> Color(0xFF334E68).copy(alpha = 0.35f)
    }
    val boundaryStroke = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))

    // Regional dividers inside Honshu and across prefectures
    val dividers = listOf(
        // Aomori - Akita/Iwate border
        listOf(Pair(40.25, 140.0), Pair(40.35, 140.75), Pair(40.30, 141.5)),
        // Akita/Yamagata border
        listOf(Pair(39.15, 139.9), Pair(39.10, 140.6)),
        // Iwate/Miyagi border
        listOf(Pair(38.95, 140.6), Pair(38.90, 141.6)),
        // Yamagata/Fukushima border
        listOf(Pair(38.00, 139.8), Pair(37.85, 140.5)),
        // Fukushima/Tochigi/Ibaraki border
        listOf(Pair(37.00, 139.5), Pair(36.90, 140.2), Pair(36.85, 140.8)),
        // Gunma/Saitama border
        listOf(Pair(36.25, 138.8), Pair(36.25, 139.4), Pair(36.15, 139.8)),
        // Tokyo/Kanagawa border
        listOf(Pair(35.65, 139.0), Pair(35.58, 139.4), Pair(35.53, 139.75)),
        // Niigata/Nagano border
        listOf(Pair(37.0, 138.0), Pair(36.8, 138.6)),
        // Shizuoka/Aichi border
        listOf(Pair(35.2, 137.8), Pair(34.8, 137.5)),
        // Aichi/Mie border
        listOf(Pair(35.2, 136.8), Pair(34.9, 136.7)),
        // Kyoto/Osaka/Hyogo borders
        listOf(Pair(35.1, 135.2), Pair(34.85, 135.4), Pair(34.7, 135.4)),
        // Osaka/Wakayama border
        listOf(Pair(34.35, 135.1), Pair(34.35, 135.6)),
        // Okayama/Hiroshima border
        listOf(Pair(35.0, 133.4), Pair(34.4, 133.4)),
        // Fukuoka/Saga/Kumamoto borders
        listOf(Pair(33.4, 130.1), Pair(33.2, 130.4), Pair(33.1, 130.6))
    )

    dividers.forEach { linePoints ->
        val path = Path()
        linePoints.forEachIndexed { i, (lat, lon) ->
            val px = toScreenX(lon)
            val py = toScreenY(lat)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
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
                    color = Color(0xFF93C5FD).copy(alpha = 0.90f),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(color = Color(0xFF030712), blurRadius = 5f)
                )
            )

            // Draw a subtle translucent backing pill for high readability
            val padX = 3f
            val padY = 2f
            val bgRect = androidx.compose.ui.geometry.Rect(
                offset = Offset(px - textResult.size.width / 2f - padX, py - textResult.size.height / 2f - padY),
                size = androidx.compose.ui.geometry.Size(textResult.size.width + padX * 2, textResult.size.height + padY * 2)
            )
            drawRoundRect(
                color = Color(0xFF0B192C).copy(alpha = 0.65f),
                topLeft = bgRect.topLeft,
                size = bgRect.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
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
    val japanTrenchPath = Path().apply {
        val points = listOf(
            Pair(44.0, 148.0),
            Pair(42.5, 146.0),
            Pair(41.0, 144.5),
            Pair(39.5, 144.0),
            Pair(38.0, 143.5),
            Pair(36.5, 142.5),
            Pair(35.5, 142.0)
        )
        points.forEachIndexed { i, (lat, lon) ->
            val px = toScreenX(lon)
            val py = toScreenY(lat)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
    }
    drawPath(
        japanTrenchPath,
        color = Color(0xFF38BDF8).copy(alpha = 0.55f),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
    )

    // Nankai Trough (南海トラフ)
    val nankaiTroughPath = Path().apply {
        val points = listOf(
            Pair(34.8, 139.2), // Sagami / Suruga
            Pair(34.0, 137.5),
            Pair(33.2, 136.0),
            Pair(32.4, 134.5),
            Pair(31.8, 133.0),
            Pair(31.0, 131.5)
        )
        points.forEachIndexed { i, (lat, lon) ->
            val px = toScreenX(lon)
            val py = toScreenY(lat)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
    }
    drawPath(
        nankaiTroughPath,
        color = Color(0xFFF43F5E).copy(alpha = 0.65f),
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f)))
    )

    // Draw Trench Labels if zoomed
    if (zoomLevel > 1.1f) {
        val jtLabel = textMeasurer.measure("日本海溝", TextStyle(color = Color(0xFF38BDF8).copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
        drawText(jtLabel, topLeft = Offset(toScreenX(144.0) + 4f, toScreenY(38.5)))

        val ntLabel = textMeasurer.measure("南海トラフ", TextStyle(color = Color(0xFFF43F5E).copy(alpha = 0.85f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
        drawText(ntLabel, topLeft = Offset(toScreenX(135.5), toScreenY(32.7) + 6f))
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

// -------------------------------------------------------------
// Real Geographical Coastline Coordinates (Lat, Lon)
// -------------------------------------------------------------

private val HOKKAIDO_COAST = listOf(
    Pair(45.52, 141.93), // Cape Soya
    Pair(45.10, 142.37), // Omu
    Pair(44.35, 143.35), // Monbetsu
    Pair(44.02, 144.27), // Abashiri
    Pair(44.07, 145.28), // Shiretoko Cape
    Pair(43.70, 145.13), // Rausu
    Pair(43.33, 145.58), // Cape Nosappu
    Pair(42.98, 144.38), // Kushiro
    Pair(42.16, 143.32), // Cape Erimo
    Pair(42.50, 141.60), // Tomakomai
    Pair(42.31, 140.97), // Muroran
    Pair(42.00, 140.70), // Mori
    Pair(41.77, 140.73), // Hakodate
    Pair(41.42, 140.09), // Cape Shirakami
    Pair(42.15, 139.92), // Esashi
    Pair(42.97, 140.35), // Suttsu
    Pair(43.33, 140.35), // Cape Shakotan
    Pair(43.19, 141.00), // Otaru
    Pair(43.20, 141.35), // Ishikari Bay (Sapporo coast)
    Pair(43.94, 141.63), // Rumoi
    Pair(44.86, 141.64), // Teshio
    Pair(45.41, 141.67)  // Wakkanai
)

private val HONSHU_COAST = listOf(
    Pair(41.55, 140.91), // Oma
    Pair(41.43, 141.47), // Cape Shiriya
    Pair(40.55, 141.53), // Hachinohe
    Pair(40.13, 141.82), // Kuji
    Pair(39.64, 141.95), // Miyako
    Pair(39.27, 141.89), // Kamaishi
    Pair(38.97, 141.64), // Kesennuma
    Pair(38.30, 141.52), // Oshika Peninsula
    Pair(38.26, 140.95), // Sendai Bay
    Pair(37.82, 140.96), // Soma
    Pair(36.95, 140.92), // Iwaki
    Pair(36.36, 140.60), // Oarai
    Pair(35.71, 140.86), // Choshi
    Pair(35.13, 140.31), // Katsuura
    Pair(34.90, 139.88), // Cape Nojima
    Pair(35.25, 139.80), // Futtsu
    Pair(35.60, 140.00), // Chiba
    Pair(35.62, 139.77), // Tokyo Port
    Pair(35.44, 139.65), // Yokohama
    Pair(35.14, 139.61), // Miura
    Pair(35.29, 139.15), // Odawara
    Pair(34.90, 139.10), // Ito
    Pair(34.59, 138.91), // Cape Iro
    Pair(34.80, 138.75), // Matsuzaki
    Pair(35.05, 138.52), // Shimizu
    Pair(34.60, 138.22), // Cape Omaezaki
    Pair(34.68, 137.58), // Lake Hamana
    Pair(34.60, 137.16), // Cape Irago
    Pair(34.80, 137.05), // Mikawa Bay
    Pair(34.83, 136.90), // Chita Peninsula
    Pair(35.05, 136.88), // Nagoya Port
    Pair(34.96, 136.63), // Yokkaichi
    Pair(34.48, 136.85), // Toba
    Pair(34.25, 136.35), // Owase
    Pair(33.72, 135.99), // Shingu
    Pair(33.43, 135.76), // Cape Shionomisaki
    Pair(33.68, 135.34), // Shirahama
    Pair(34.00, 135.12), // Gobo
    Pair(34.28, 135.13), // Wakayama
    Pair(34.45, 135.32), // Kansai Airport
    Pair(34.65, 135.43), // Osaka Port
    Pair(34.68, 135.19), // Kobe
    Pair(34.79, 134.68), // Himeji
    Pair(34.73, 134.18), // Ako
    Pair(34.50, 133.75), // Kurashiki
    Pair(34.37, 133.38), // Fukuyama
    Pair(34.30, 132.80), // Takehara
    Pair(34.35, 132.45), // Hiroshima Bay
    Pair(34.18, 132.22), // Iwakuni
    Pair(33.95, 131.25), // Ube
    Pair(33.95, 130.93), // Shimonoseki
    Pair(34.10, 130.90), // Hibikinada
    Pair(34.42, 131.40), // Hagi
    Pair(34.68, 131.78), // Masuda
    Pair(34.90, 132.07), // Hamada
    Pair(35.40, 132.68), // Izumo
    Pair(35.53, 133.00), // Matsue
    Pair(35.53, 133.25), // Sakaiminato
    Pair(35.50, 134.20), // Tottori Sand Dunes
    Pair(35.65, 134.70), // Kyotango
    Pair(35.50, 135.35), // Maizuru
    Pair(35.75, 136.00), // Tsuruga
    Pair(35.88, 136.00), // Echizen
    Pair(36.25, 136.13), // Awara
    Pair(36.50, 136.60), // Kanazawa
    Pair(36.85, 136.75), // Hakui
    Pair(37.40, 136.90), // Wajima
    Pair(37.52, 137.35), // Suzu
    Pair(37.05, 137.00), // Nanao Bay
    Pair(36.85, 137.10), // Himi
    Pair(36.75, 137.22), // Toyama
    Pair(36.97, 137.45), // Kurobe
    Pair(37.04, 137.86), // Itoigawa
    Pair(37.18, 138.25), // Joetsu
    Pair(37.38, 138.56), // Kashiwazaki
    Pair(37.95, 139.05), // Niigata Port
    Pair(38.23, 139.46), // Murakami
    Pair(38.74, 139.80), // Tsuruoka
    Pair(38.93, 139.85), // Sakata
    Pair(39.20, 139.90), // Kisakata
    Pair(39.73, 140.05), // Akita
    Pair(39.88, 139.71), // Oga Peninsula
    Pair(40.23, 139.99), // Noshiro
    Pair(40.85, 139.97), // Fukaura
    Pair(41.13, 140.30), // Cape Tappi
    Pair(40.83, 140.75), // Aomori Port
    Pair(41.00, 140.80), // Noheji
    Pair(41.28, 141.18)  // Mutsu
)

private val SHIKOKU_COAST = listOf(
    Pair(34.20, 134.60), // Naruto
    Pair(33.85, 134.65), // Anan
    Pair(33.55, 134.30), // Kaiyo
    Pair(33.25, 134.18), // Cape Muroto
    Pair(33.50, 133.55), // Kochi
    Pair(33.25, 133.10), // Tosa-shimizu
    Pair(32.72, 133.02), // Cape Ashizuri
    Pair(32.93, 132.72), // Sukumo
    Pair(33.20, 132.50), // Uwajima
    Pair(33.45, 132.40), // Yawatahama
    Pair(33.35, 132.02), // Sadamisaki
    Pair(33.85, 132.75), // Matsuyama
    Pair(34.08, 133.00), // Imabari
    Pair(33.95, 133.30), // Niihama
    Pair(34.10, 133.65), // Kan-onji
    Pair(34.32, 133.80), // Marugame
    Pair(34.35, 134.05), // Takamatsu
    Pair(34.25, 134.35)  // Sanuki
)

private val KYUSHU_COAST = listOf(
    Pair(33.95, 130.95), // Moji
    Pair(33.65, 131.00), // Yukuhashi
    Pair(33.60, 131.20), // Nakatsu
    Pair(33.70, 131.60), // Kunisaki Peninsula
    Pair(33.30, 131.50), // Beppu Bay
    Pair(33.25, 131.90), // Saganoseki
    Pair(32.96, 131.90), // Saiki
    Pair(32.58, 131.68), // Nobeoka
    Pair(32.40, 131.65), // Hyuga
    Pair(31.92, 131.45), // Miyazaki
    Pair(31.58, 131.40), // Nichinan
    Pair(31.45, 131.35), // Kushima
    Pair(31.35, 131.10), // Shibushi Bay
    Pair(30.99, 130.66), // Cape Sata
    Pair(31.25, 130.65), // Kagoshima Bay east
    Pair(31.60, 130.55), // Kagoshima City
    Pair(31.27, 130.30), // Makurazaki
    Pair(31.45, 130.15), // Minamisatsuma
    Pair(31.82, 130.18), // Satsumasendai
    Pair(32.05, 130.20), // Akune
    Pair(32.20, 130.38), // Izumi
    Pair(32.35, 130.50), // Minamata
    Pair(32.50, 130.60), // Yatsushiro
    Pair(32.70, 130.60), // Uto Peninsula
    Pair(32.80, 130.65), // Kumamoto Port
    Pair(32.95, 130.45), // Tamana
    Pair(33.05, 130.40), // Omuta
    Pair(33.15, 130.25), // Kashima
    Pair(32.75, 130.35), // Shimabara Peninsula
    Pair(32.60, 130.20), // Kuchinotsu
    Pair(32.75, 130.05), // Isahaya
    Pair(32.70, 129.85), // Nagasaki
    Pair(32.95, 129.70), // Saikai
    Pair(33.17, 129.70), // Sasebo
    Pair(33.35, 129.55), // Hirado Strait
    Pair(33.40, 129.70), // Imari Bay
    Pair(33.55, 129.90), // Karatsu
    Pair(33.60, 130.20), // Itoshima
    Pair(33.60, 130.40), // Hakata Bay
    Pair(33.80, 130.50), // Munakata
    Pair(33.90, 130.80)  // Wakamatsu
)

private val OKINAWA_COAST = listOf(
    Pair(26.85, 128.25), // Cape Hedo
    Pair(26.65, 128.10), // Ogimi
    Pair(26.70, 127.90), // Motobu
    Pair(26.58, 127.98), // Nago
    Pair(26.40, 127.80), // Cape Zanpa
    Pair(26.28, 127.75), // Ginowan
    Pair(26.21, 127.68), // Naha
    Pair(26.10, 127.67), // Itoman
    Pair(26.08, 127.72), // Cape Kyan
    Pair(26.15, 127.80), // Nanjo
    Pair(26.30, 127.90), // Katsuren
    Pair(26.45, 127.90), // Uruma
    Pair(26.60, 128.15)  // Higashi
)

private val ISHIGAKI_COAST = listOf(
    Pair(24.55, 124.30),
    Pair(24.40, 124.30),
    Pair(24.32, 124.15),
    Pair(24.45, 124.10)
)

private val MIYAKO_COAST = listOf(
    Pair(24.90, 125.30),
    Pair(24.75, 125.35),
    Pair(24.70, 125.25),
    Pair(24.80, 125.20)
)

private val SADO_COAST = listOf(
    Pair(38.30, 138.45),
    Pair(38.15, 138.55),
    Pair(37.80, 138.25),
    Pair(38.00, 138.25)
)

private val TSUSHIMA_COAST = listOf(
    Pair(34.65, 129.40),
    Pair(34.40, 129.35),
    Pair(34.15, 129.20),
    Pair(34.35, 129.30)
)
