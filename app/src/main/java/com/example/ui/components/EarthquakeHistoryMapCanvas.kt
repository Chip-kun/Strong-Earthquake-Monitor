package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.EEWNotificationEntity
import com.example.location.UserLocation
import com.example.location.UserLocationManager
import com.example.model.GeoProjection
import com.example.model.getShindoColorByText
import com.example.ui.MapStyle
import com.example.ui.theme.KyoshinAccentRed
import com.example.ui.theme.KyoshinCardBorder
import com.example.ui.theme.KyoshinPrimary
import com.example.ui.theme.KyoshinSurface
import kotlin.math.hypot

enum class MagFilter(val label: String, val minMag: Float) {
    ALL("全規模", 0f),
    M5("M5.0+", 5.0f),
    M6("M6.0+", 6.0f),
    M7("M7.0+", 7.0f)
}

enum class ShindoFilter(val label: String, val minShindoRank: Int) {
    ALL("全震度", 0),
    SHINDO4("震度4+", 4),
    SHINDO5("震度5弱+", 5),
    SHINDO6("震度6弱+", 6)
}

@Composable
fun EarthquakeHistoryMapCanvas(
    eewLogs: List<EEWNotificationEntity>,
    selectedLog: EEWNotificationEntity?,
    onLogSelected: (EEWNotificationEntity?) -> Unit,
    userLocation: UserLocation?,
    onSpeakLog: (EEWNotificationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var mapStyle by remember { mutableStateOf(MapStyle.SATELLITE) }
    var showPrefectures by remember { mutableStateOf(true) }
    var showLegend by remember { mutableStateOf(false) }

    var selectedMagFilter by remember { mutableStateOf(MagFilter.ALL) }
    var selectedShindoFilter by remember { mutableStateOf(ShindoFilter.ALL) }
    var onlyWarnings by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val mapAspect = GeoProjection.MAP_ASPECT_RATIO

    // Pulsing animation for selected / prominent epicenter
    val infiniteTransition = rememberInfiniteTransition(label = "pulseHistory")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseHistoryScale"
    )

    // Filter logs based on active filters
    val filteredLogs = remember(eewLogs, selectedMagFilter, selectedShindoFilter, onlyWarnings) {
        eewLogs.filter { log ->
            val magOk = log.magnitude >= selectedMagFilter.minMag
            val shindoRank = when (log.maxShindo) {
                "1" -> 1
                "2" -> 2
                "3" -> 3
                "4" -> 4
                "5弱", "5-" -> 5
                "5強", "5+" -> 5
                "6弱", "6-" -> 6
                "6強", "6+" -> 6
                "7" -> 7
                else -> 0
            }
            val shindoOk = shindoRank >= selectedShindoFilter.minShindoRank
            val warningOk = !onlyWarnings || log.isWarning
            magOk && shindoOk && warningOk
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF070D1E))
            .border(1.dp, KyoshinCardBorder, RoundedCornerShape(12.dp))
    ) {
        // Main Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("earthquake_history_map_canvas")
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomLevel = (zoomLevel * zoom).coerceIn(0.7f, 6.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
                .pointerInput(filteredLogs, zoomLevel, panOffsetX, panOffsetY) {
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

                        // Find closest epicenter within touch range
                        val hit = filteredLogs.minByOrNull { log ->
                            val normX = GeoProjection.toNormX(log.longitude)
                            val normY = GeoProjection.toNormY(log.latitude)
                            val sx = finalX + normX * finalW
                            val sy = finalY + normY * finalH
                            hypot(sx - tapOffset.x, sy - tapOffset.y)
                        }

                        if (hit != null) {
                            val normX = GeoProjection.toNormX(hit.longitude)
                            val normY = GeoProjection.toNormY(hit.latitude)
                            val sx = finalX + normX * finalW
                            val sy = finalY + normY * finalH
                            val dist = hypot(sx - tapOffset.x, sy - tapOffset.y)
                            if (dist < 42f * density) {
                                onLogSelected(hit)
                            } else {
                                onLogSelected(null)
                            }
                        } else {
                            onLogSelected(null)
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

            // 1. Ocean Background
            val oceanColor = when (mapStyle) {
                MapStyle.SATELLITE -> Color(0xFF030712)
                MapStyle.RADAR -> Color(0xFF020617)
                MapStyle.TECTONIC -> Color(0xFF050E24)
            }
            drawRect(color = oceanColor)

            // 2. Draw Coastlines & Landmass
            val landFill = when (mapStyle) {
                MapStyle.SATELLITE -> Color(0xFF1E293B)
                MapStyle.RADAR -> Color(0xFF0F172A)
                MapStyle.TECTONIC -> Color(0xFF142038)
            }
            val coastStroke = when (mapStyle) {
                MapStyle.SATELLITE -> Color(0xFF475569)
                MapStyle.RADAR -> Color(0xFF38BDF8).copy(alpha = 0.5f)
                MapStyle.TECTONIC -> Color(0xFF64748B)
            }

            fun drawSmoothIsland(points: List<Pair<Double, Double>>, tension: Float = 0.45f) {
                val path = JapanMapGeometry.createSmoothPath(
                    points = points,
                    toScreenX = { lon -> finalX + GeoProjection.toNormX(lon) * finalW },
                    toScreenY = { lat -> finalY + GeoProjection.toNormY(lat) * finalH },
                    closed = true,
                    tension = tension
                )
                drawPath(path, landFill, style = Fill)
                drawPath(path, coastStroke, style = Stroke(width = 1.3f))
            }

            drawSmoothIsland(JapanMapGeometry.HONSHU_COAST)
            drawSmoothIsland(JapanMapGeometry.HOKKAIDO_COAST)
            drawSmoothIsland(JapanMapGeometry.SHIKOKU_COAST)
            drawSmoothIsland(JapanMapGeometry.KYUSHU_COAST)
            drawSmoothIsland(JapanMapGeometry.SADO_COAST)
            drawSmoothIsland(JapanMapGeometry.AWAJI_COAST)
            drawSmoothIsland(JapanMapGeometry.TSUSHIMA_COAST)
            drawSmoothIsland(JapanMapGeometry.OKINAWA_COAST)
            drawSmoothIsland(JapanMapGeometry.AMAMI_COAST)
            drawSmoothIsland(JapanMapGeometry.TANEGASHIMA_COAST)
            drawSmoothIsland(JapanMapGeometry.YAKUSHIMA_COAST)
            drawSmoothIsland(JapanMapGeometry.IZU_OSHIMA_COAST)
            drawSmoothIsland(JapanMapGeometry.IZU_HACHIJO_COAST)

            // 3. Lakes
            fun drawLake(points: List<Pair<Double, Double>>) {
                val path = JapanMapGeometry.createSmoothPath(
                    points = points,
                    toScreenX = { lon -> finalX + GeoProjection.toNormX(lon) * finalW },
                    toScreenY = { lat -> finalY + GeoProjection.toNormY(lat) * finalH },
                    closed = true
                )
                drawPath(path, oceanColor, style = Fill)
                drawPath(path, coastStroke.copy(alpha = 0.7f), style = Stroke(width = 1.0f))
            }
            drawLake(JapanMapGeometry.LAKE_BIWA_COAST)
            drawLake(JapanMapGeometry.LAKE_KASUMIGAURA_COAST)
            drawLake(JapanMapGeometry.LAKE_INAWASHIRO_COAST)

            // 4. Prefecture boundaries
            JapanMapGeometry.PREFECTURE_DIVIDERS.forEach { linePoints ->
                val path = JapanMapGeometry.createSmoothPath(
                    points = linePoints,
                    toScreenX = { lon -> finalX + GeoProjection.toNormX(lon) * finalW },
                    toScreenY = { lat -> finalY + GeoProjection.toNormY(lat) * finalH },
                    closed = false
                )
                drawPath(
                    path,
                    Color(0xFF334155).copy(alpha = 0.6f),
                    style = Stroke(
                        width = 1.0f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )
                )
            }

            // 5. Tectonic Trenches
            if (mapStyle == MapStyle.TECTONIC) {
                fun drawTrench(points: List<Pair<Double, Double>>, color: Color, label: String) {
                    val path = JapanMapGeometry.createSmoothPath(
                        points = points,
                        toScreenX = { lon -> finalX + GeoProjection.toNormX(lon) * finalW },
                        toScreenY = { lat -> finalY + GeoProjection.toNormY(lat) * finalH },
                        closed = false
                    )
                    drawPath(
                        path,
                        color,
                        style = Stroke(
                            width = 2.4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                        )
                    )
                }
                drawTrench(JapanMapGeometry.JAPAN_TRENCH, Color(0xFFEF4444).copy(alpha = 0.85f), "日本海溝")
                drawTrench(JapanMapGeometry.NANKAI_TROUGH, Color(0xFFF59E0B).copy(alpha = 0.85f), "南海トラフ")
                drawTrench(JapanMapGeometry.RYUKYU_TRENCH, Color(0xFF38BDF8).copy(alpha = 0.75f), "南西諸島海溝")
            }

            // 6. User Location
            if (userLocation != null) {
                val uNormX = GeoProjection.toNormX(userLocation.lon)
                val uNormY = GeoProjection.toNormY(userLocation.lat)
                val ux = finalX + uNormX * finalW
                val uy = finalY + uNormY * finalH

                drawCircle(
                    color = KyoshinPrimary.copy(alpha = 0.25f),
                    radius = 18f * pulseScale,
                    center = Offset(ux, uy)
                )
                drawCircle(
                    color = KyoshinPrimary,
                    radius = 6.0f,
                    center = Offset(ux, uy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = Offset(ux, uy)
                )
            }

            // 7. Plot Past Epicenters from Room
            filteredLogs.forEach { log ->
                val normX = GeoProjection.toNormX(log.longitude)
                val normY = GeoProjection.toNormY(log.latitude)
                val ex = finalX + normX * finalW
                val ey = finalY + normY * finalH

                val isSelected = selectedLog?.id == log.id
                val shindoColor = getShindoColorByText(log.maxShindo)

                // Marker Radius scaled by Magnitude
                val baseRadius = when {
                    log.magnitude >= 7.0f -> 18f
                    log.magnitude >= 6.0f -> 14f
                    log.magnitude >= 5.0f -> 11f
                    log.magnitude >= 4.0f -> 8.5f
                    else -> 7.0f
                }

                // Halo ring for warning or M>=7 or selected
                if (isSelected) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = (baseRadius + 14f) * pulseScale,
                        center = Offset(ex, ey),
                        style = Stroke(width = 2.5f)
                    )
                    drawCircle(
                        color = shindoColor.copy(alpha = 0.35f),
                        radius = (baseRadius + 10f) * pulseScale,
                        center = Offset(ex, ey)
                    )
                } else if (log.isWarning || log.magnitude >= 7.0f) {
                    drawCircle(
                        color = KyoshinAccentRed.copy(alpha = 0.35f),
                        radius = baseRadius + 6f,
                        center = Offset(ex, ey),
                        style = Stroke(width = 1.5f)
                    )
                }

                // Main Beacon Circle
                drawCircle(
                    color = shindoColor,
                    radius = baseRadius,
                    center = Offset(ex, ey)
                )
                drawCircle(
                    color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.8f),
                    radius = baseRadius,
                    center = Offset(ex, ey),
                    style = Stroke(width = if (isSelected) 2.5f else 1.2f)
                )

                // Epicenter 'X' crosshair
                val xSize = baseRadius * 0.45f
                drawLine(
                    color = if (log.maxShindo in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                    start = Offset(ex - xSize, ey - xSize),
                    end = Offset(ex + xSize, ey + xSize),
                    strokeWidth = 2.0f
                )
                drawLine(
                    color = if (log.maxShindo in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                    start = Offset(ex - xSize, ey + xSize),
                    end = Offset(ex + xSize, ey - xSize),
                    strokeWidth = 2.0f
                )

                // Label on zoom >= 1.4 or if selected or major M>=6
                if (isSelected || zoomLevel >= 1.6f || log.magnitude >= 7.0f) {
                    val labelText = "${log.hypocenter} M${log.magnitude}"
                    val textLayout = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = if (isSelected) 11.sp else 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                    val labelX = ex + baseRadius + 4f
                    val labelY = ey - textLayout.size.height / 2f

                    // Background pill for label
                    drawRoundRect(
                        color = Color(0xCC0B1224),
                        topLeft = Offset(labelX - 3f, labelY - 2f),
                        size = androidx.compose.ui.geometry.Size(
                            textLayout.size.width + 6f,
                            textLayout.size.height + 4f
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(labelX, labelY)
                    )
                }
            }
        }

        // Top Filter Bar & Summary Chip
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Stats & Mode Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xDD0F172A),
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
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "震源プロット: ${filteredLogs.size}件 / 保存${eewLogs.size}件",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Map Style Switcher & Legend Toggle
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xDD0F172A),
                        border = BorderStroke(1.dp, KyoshinCardBorder),
                        modifier = Modifier.clickable {
                            mapStyle = when (mapStyle) {
                                MapStyle.SATELLITE -> MapStyle.RADAR
                                MapStyle.RADAR -> MapStyle.TECTONIC
                                MapStyle.TECTONIC -> MapStyle.SATELLITE
                            }
                        }
                    ) {
                        Text(
                            text = mapStyle.label.take(2),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (showLegend) KyoshinPrimary else Color(0xDD0F172A),
                        border = BorderStroke(1.dp, KyoshinCardBorder),
                        modifier = Modifier.clickable { showLegend = !showLegend }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "凡例",
                            tint = if (showLegend) Color.Black else Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Filter Chips Horizontal Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Magnitude filters
                items(MagFilter.values().size) { idx ->
                    val filter = MagFilter.values()[idx]
                    FilterChip(
                        selected = selectedMagFilter == filter,
                        onClick = { selectedMagFilter = filter },
                        label = { Text(filter.label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KyoshinPrimary,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xBB0F172A),
                            labelColor = Color(0xFFCBD5E1)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedMagFilter == filter,
                            borderColor = KyoshinCardBorder,
                            selectedBorderColor = KyoshinPrimary
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }

                // Shindo filters
                items(ShindoFilter.values().size) { idx ->
                    val filter = ShindoFilter.values()[idx]
                    FilterChip(
                        selected = selectedShindoFilter == filter,
                        onClick = { selectedShindoFilter = filter },
                        label = { Text(filter.label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF59E0B),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xBB0F172A),
                            labelColor = Color(0xFFCBD5E1)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedShindoFilter == filter,
                            borderColor = KyoshinCardBorder,
                            selectedBorderColor = Color(0xFFF59E0B)
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }

                // Warning toggle
                item {
                    FilterChip(
                        selected = onlyWarnings,
                        onClick = { onlyWarnings = !onlyWarnings },
                        label = { Text("警報のみ", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KyoshinAccentRed,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xBB0F172A),
                            labelColor = Color(0xFFCBD5E1)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = onlyWarnings,
                            borderColor = KyoshinCardBorder,
                            selectedBorderColor = KyoshinAccentRed
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        // Floating Zoom and Navigation Controls (Right Edge)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { zoomLevel = (zoomLevel * 1.35f).coerceAtMost(6.0f) },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xDD0F172A))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .testTag("history_map_zoom_in")
            ) {
                Icon(Icons.Default.Add, contentDescription = "拡大", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = { zoomLevel = (zoomLevel / 1.35f).coerceAtLeast(0.7f) },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xDD0F172A))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .testTag("history_map_zoom_out")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "縮小", tint = Color.White, modifier = Modifier.size(18.dp))
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
                    .background(Color(0xDD0F172A))
                    .border(1.dp, KyoshinCardBorder, CircleShape)
                    .testTag("history_map_reset_view")
            ) {
                Icon(Icons.Default.CropFree, contentDescription = "全体表示", tint = KyoshinPrimary, modifier = Modifier.size(16.dp))
            }
        }

        // Legend Overlay
        if (showLegend) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = if (selectedLog != null) 160.dp else 12.dp)
                    .widthIn(max = 200.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xEE0B1224),
                border = BorderStroke(1.dp, KyoshinCardBorder)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("震度凡例", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        "震度7" to getShindoColorByText("7"),
                        "震度6強/6弱" to getShindoColorByText("6強"),
                        "震度5強/5弱" to getShindoColorByText("5強"),
                        "震度4" to getShindoColorByText("4"),
                        "震度3以下" to getShindoColorByText("3")
                    ).forEach { (label, col) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(col, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, color = Color(0xFFCBD5E1), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("円の大きさ: マグニチュード", color = Color(0xFF94A3B8), fontSize = 9.sp)
                }
            }
        }

        // Selected Earthquake Inspector Bottom Sheet / Card
        AnimatedVisibility(
            visible = selectedLog != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (selectedLog != null) {
                val distanceKm = remember(userLocation, selectedLog) {
                    if (userLocation != null && selectedLog.latitude != 0.0 && selectedLog.longitude != 0.0) {
                        UserLocationManager.calculateDistanceKm(
                            userLocation.lat,
                            userLocation.lon,
                            selectedLog.latitude,
                            selectedLog.longitude
                        ).toInt()
                    } else null
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("selected_epicenter_detail_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KyoshinSurface.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, if (selectedLog.isWarning) KyoshinAccentRed else KyoshinPrimary)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Title Row + Close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = when {
                                        selectedLog.isDrill -> Color(0xFF2563EB)
                                        selectedLog.isWarning -> KyoshinAccentRed
                                        else -> Color(0xFFF59E0B)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            selectedLog.isDrill -> "緊急地震速報 (訓練)"
                                            selectedLog.isWarning -> "緊急地震速報 (警報)"
                                            else -> "緊急地震速報 (予報)"
                                        },
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedLog.reportNum,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            IconButton(
                                onClick = { onLogSelected(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.LightGray)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Middle Row: Shindo Badge + Hypocenter + Mag/Depth
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(getShindoColorByText(selectedLog.maxShindo)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "最大震度",
                                        color = if (selectedLog.maxShindo in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = selectedLog.maxShindo,
                                        color = if (selectedLog.maxShindo in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedLog.hypocenter,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "発生: ${selectedLog.originTime}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "規模: M${selectedLog.magnitude}",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "深さ: 約${selectedLog.depthKm}km",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp
                                    )
                                    if (distanceKm != null) {
                                        Text(
                                            text = "現在地から約${distanceKm}km",
                                            color = KyoshinPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Speech Audio Playback Button
                            IconButton(
                                onClick = { onSpeakLog(selectedLog) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .testTag("history_map_speak_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "音声読み上げ",
                                    tint = KyoshinPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (selectedLog.affectedAreas.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF162035)
                            ) {
                                Text(
                                    text = "地域: ${selectedLog.affectedAreas}",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
