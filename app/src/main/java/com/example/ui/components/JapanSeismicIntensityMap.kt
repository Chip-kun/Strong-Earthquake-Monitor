package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.location.UserLocation
import com.example.model.*
import com.example.model.DisplayMode
import com.example.ui.KyoshinViewModel
import com.example.ui.MapStyle
import com.example.ui.theme.*
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A composable function that displays an interactive map of Japan
 * and overlays real-time JMA seismic intensity data using the location permissions
 * already in the dependencies.
 */
@Composable
fun JapanSeismicIntensityMap(
    modifier: Modifier = Modifier,
    viewModel: KyoshinViewModel? = null,
    stations: List<ObservationStation>? = null,
    activeEEW: EEWAlert? = null,
    userLocation: UserLocation? = null,
    onStationClick: ((ObservationStation) -> Unit)? = null
) {
    val context = LocalContext.current

    // Reactive states from viewModel if provided, else fallback to passed parameters
    val liveStations = stations ?: viewModel?.repository?.stations?.collectAsState()?.value ?: emptyList()
    val liveActiveEEW = activeEEW ?: viewModel?.repository?.activeEEW?.collectAsState()?.value
    val liveUserLocation = userLocation ?: viewModel?.userLocation?.collectAsState()?.value

    // Location Permission handling using dependencies
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val granted = fineGranted || coarseGranted
        hasLocationPermission = granted
        if (granted) {
            viewModel?.locationManager?.updatePermissionGranted(true)
        }
    }

    // Auto-request location permission on launch if not granted
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Interactive zoom & pan states
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Map feature toggles
    var showPrefectureNames by remember { mutableStateOf(true) }
    var showStationLabels by remember { mutableStateOf(true) }
    var selectedStation by remember { mutableStateOf<ObservationStation?>(null) }
    var mapStyle by remember { mutableStateOf<MapStyle>(MapStyle.SATELLITE) }
    var displayMode by remember { mutableStateOf<com.example.model.DisplayMode>(com.example.model.DisplayMode.SHINDO) }

    val textMeasurer = rememberTextMeasurer()
    val mapAspect = GeoProjection.MAP_ASPECT_RATIO

    // Real-time wave & beacon animation pulses
    val infiniteTransition = rememberInfiniteTransition(label = "jmaMapPulse")
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconPulse"
    )

    val locationGlowRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "locationGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .onSizeChanged { canvasSize = it }
            .testTag("japan_seismic_intensity_map")
    ) {
        // Main Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newZoom = (zoomLevel * zoom).coerceIn(1.0f, 6.0f)
                        val maxPanX = (size.width * (newZoom - 1.0f) * 0.6f).coerceAtLeast(0f)
                        val maxPanY = (size.height * (newZoom - 1.0f) * 0.6f).coerceAtLeast(0f)

                        panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                        panOffsetY = (panOffsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                        zoomLevel = newZoom
                    }
                }
                .pointerInput(liveStations, zoomLevel, panOffsetX, panOffsetY) {
                    detectTapGestures { tapOffset ->
                        val cW = size.width.toFloat()
                        val cH = size.height.toFloat()

                        val fitW: Float
                        val fitH: Float
                        if (cW / cH > mapAspect) {
                            fitH = cH
                            fitW = cH * mapAspect
                        } else {
                            fitW = cW
                            fitH = cW / mapAspect
                        }
                        val baseLeft = (cW - fitW) / 2f
                        val baseTop = (cH - fitH) / 2f
                        val cX = cW / 2f
                        val cY = cH / 2f

                        // Find closest station within tap touch target (28dp equivalent)
                        val hitThreshold = 36f * density
                        var closest: ObservationStation? = null
                        var minDistance = Float.MAX_VALUE

                        for (st in liveStations) {
                            val normX = GeoProjection.toNormX(st.lon)
                            val normY = GeoProjection.toNormY(st.lat)

                            val unzoomedX = baseLeft + normX * fitW
                            val unzoomedY = baseTop + normY * fitH

                            val screenX = cX + (unzoomedX - cX) * zoomLevel + panOffsetX
                            val screenY = cY + (unzoomedY - cY) * zoomLevel + panOffsetY

                            val dist = hypot(tapOffset.x - screenX, tapOffset.y - screenY)
                            if (dist <= hitThreshold && dist < minDistance) {
                                minDistance = dist
                                closest = st
                            }
                        }

                        selectedStation = closest
                        if (closest != null) {
                            onStationClick?.invoke(closest)
                        }
                    }
                }
        ) {
            val cW = size.width
            val cH = size.height
            if (cW <= 0 || cH <= 0) return@Canvas

            val fitW: Float
            val fitH: Float
            if (cW / cH > mapAspect) {
                fitH = cH
                fitW = cH * mapAspect
            } else {
                fitW = cW
                fitH = cW / mapAspect
            }
            val baseLeft = (cW - fitW) / 2f
            val baseTop = (cH - fitH) / 2f
            val cX = cW / 2f
            val cY = cH / 2f

            fun toScreenX(lon: Double): Float {
                val normX = GeoProjection.toNormX(lon)
                val unzoomedX = baseLeft + normX * fitW
                return cX + (unzoomedX - cX) * zoomLevel + panOffsetX
            }

            fun toScreenY(lat: Double): Float {
                val normY = GeoProjection.toNormY(lat)
                val unzoomedY = baseTop + normY * fitH
                return cY + (unzoomedY - cY) * zoomLevel + panOffsetY
            }

            // 1. Draw Ocean Background and Geographic Grid
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF030712)),
                    center = Offset(cX, cY),
                    radius = hypot(cW, cH) * 0.7f
                )
            )

            // Latitude & Longitude grid lines
            for (lat in 26..46 step 4) {
                val y = toScreenY(lat.toDouble())
                if (y in 0f..cH) {
                    drawLine(
                        color = Color(0xFF1E293B).copy(alpha = 0.45f),
                        start = Offset(0f, y),
                        end = Offset(cW, y),
                        strokeWidth = 1f
                    )
                }
            }
            for (lon in 124..146 step 4) {
                val x = toScreenX(lon.toDouble())
                if (x in 0f..cW) {
                    drawLine(
                        color = Color(0xFF1E293B).copy(alpha = 0.45f),
                        start = Offset(x, 0f),
                        end = Offset(x, cH),
                        strokeWidth = 1f
                    )
                }
            }

            // 2. Draw Japan Landmass Coastlines
            val landColor = when (mapStyle) {
                MapStyle.SATELLITE -> Color(0xFF1E293B)
                MapStyle.RADAR -> Color(0xFF0F261E)
                MapStyle.TECTONIC -> Color(0xFF181528)
            }
            val coastBorderColor = Color(0xFF38BDF8).copy(alpha = 0.35f)

            // Representative Japan Islands
            listOf<List<Pair<Double, Double>>>(
                JapanMapGeometry.HONSHU_COAST,
                JapanMapGeometry.HOKKAIDO_COAST,
                JapanMapGeometry.KYUSHU_COAST,
                JapanMapGeometry.SHIKOKU_COAST,
                JapanMapGeometry.OKINAWA_COAST,
                JapanMapGeometry.SADO_COAST,
                JapanMapGeometry.AMAMI_COAST,
                JapanMapGeometry.ISHIGAKI_COAST,
                JapanMapGeometry.MIYAKO_COAST,
                JapanMapGeometry.TSUSHIMA_COAST,
                JapanMapGeometry.TANEGASHIMA_COAST
            ).forEach { islandCoords ->
                val islandPath = JapanMapGeometry.createSmoothPath(
                    points = islandCoords,
                    toScreenX = { lon -> toScreenX(lon) },
                    toScreenY = { lat -> toScreenY(lat) },
                    closed = true
                )
                drawPath(path = islandPath, color = landColor, style = Fill)
                drawPath(path = islandPath, color = coastBorderColor, style = Stroke(width = 1.4f))
            }

            // 3. Draw Prefectural Border Lines
            for (borderCoords in JapanMapGeometry.PREFECTURE_DIVIDERS) {
                val borderPath = JapanMapGeometry.createSmoothPath(
                    points = borderCoords,
                    toScreenX = { lon -> toScreenX(lon) },
                    toScreenY = { lat -> toScreenY(lat) },
                    closed = false
                )
                drawPath(
                    path = borderPath,
                    color = Color(0xFF475569).copy(alpha = 0.4f),
                    style = Stroke(
                        width = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                )
            }

            // 4. Draw Plate Boundary / Tectonic Trenches (Japan Trench & Nankai Trough)
            val trenchColor = Color(0xFFF43F5E).copy(alpha = 0.45f)
            listOf(JapanMapGeometry.JAPAN_TRENCH, JapanMapGeometry.NANKAI_TROUGH, JapanMapGeometry.RYUKYU_TRENCH).forEach { trenchPts ->
                val trenchPath = JapanMapGeometry.createSmoothPath(
                    points = trenchPts,
                    toScreenX = { lon -> toScreenX(lon) },
                    toScreenY = { lat -> toScreenY(lat) },
                    closed = false
                )
                drawPath(
                    path = trenchPath,
                    color = trenchColor,
                    style = Stroke(
                        width = 1.6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                )
            }

            // 5. Draw Prefecture Name Labels if enabled
            if (showPrefectureNames && zoomLevel >= 1.3f) {
                for (pref in JAPAN_PREFECTURES) {
                    val pX = toScreenX(pref.lon)
                    val pY = toScreenY(pref.lat)
                    if (pX in 0f..cW && pY in 0f..cH) {
                        val textLayout = textMeasurer.measure(
                            text = pref.shortName,
                            style = TextStyle(
                                color = Color(0xFF94A3B8).copy(alpha = 0.75f),
                                fontSize = (10f * zoomLevel.coerceAtMost(1.8f)).sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(pX - textLayout.size.width / 2f, pY - textLayout.size.height / 2f)
                        )
                    }
                }
            }

            // 6. Draw Active EEW Epicenter and Expanding Front Waves
            if (liveActiveEEW != null) {
                val epiX = toScreenX(liveActiveEEW.lon)
                val epiY = toScreenY(liveActiveEEW.lat)

                val kmPerPixel = ((fitH / (GeoProjection.MAX_LAT - GeoProjection.MIN_LAT)) / 111.0f) * zoomLevel
                val pPixelRadius = (liveActiveEEW.pWaveRadiusKm * kmPerPixel).toFloat()
                val sPixelRadius = (liveActiveEEW.sWaveRadiusKm * kmPerPixel).toFloat()

                // P-wave circle (blue dashed)
                if (pPixelRadius > 0f) {
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                        radius = pPixelRadius,
                        center = Offset(epiX, epiY),
                        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    )
                }

                // S-wave circle (red filled transparent + solid perimeter)
                if (sPixelRadius > 0f) {
                    drawCircle(
                        color = KyoshinAccentRed.copy(alpha = 0.12f),
                        radius = sPixelRadius,
                        center = Offset(epiX, epiY),
                        style = Fill
                    )
                    drawCircle(
                        color = KyoshinAccentRed,
                        radius = sPixelRadius,
                        center = Offset(epiX, epiY),
                        style = Stroke(width = 2.5f)
                    )
                }

                // Epicenter Beacon Crosshair
                val epiSize = 18f * pulseRatio
                drawLine(
                    color = Color.Yellow,
                    start = Offset(epiX - epiSize, epiY - epiSize),
                    end = Offset(epiX + epiSize, epiY + epiSize),
                    strokeWidth = 3.5f
                )
                drawLine(
                    color = Color.Yellow,
                    start = Offset(epiX + epiSize, epiY - epiSize),
                    end = Offset(epiX - epiSize, epiY + epiSize),
                    strokeWidth = 3.5f
                )
                drawCircle(
                    color = Color.Red,
                    radius = 5f,
                    center = Offset(epiX, epiY),
                    style = Fill
                )
            }

            // 7. Overlay Real-time JMA Seismic Intensity Data (Stations)
            for (st in liveStations) {
                val sX = toScreenX(st.lon)
                val sY = toScreenY(st.lat)
                if (sX !in -40f..(cW + 40f) || sY !in -40f..(cH + 40f)) continue

                val shindoColor = getShindoColor(st.realtimeShindo)
                val isSelected = selectedStation?.id == st.id

                // Radius scaled by intensity or PGA
                val baseRadius = when {
                    st.realtimeShindo >= 4.5f -> 12f
                    st.realtimeShindo >= 2.5f -> 9f
                    st.realtimeShindo >= 1.0f -> 7f
                    else -> 5.5f
                } * zoomLevel.coerceIn(0.9f, 2.2f)

                // High tremor pulsating outer aura
                if (st.realtimeShindo >= 1.5f || st.pga >= 5f) {
                    drawCircle(
                        color = shindoColor.copy(alpha = 0.35f),
                        radius = baseRadius * pulseRatio * 1.5f,
                        center = Offset(sX, sY),
                        style = Fill
                    )
                }

                // Station Core Bubble
                drawCircle(
                    color = shindoColor,
                    radius = baseRadius,
                    center = Offset(sX, sY),
                    style = Fill
                )
                drawCircle(
                    color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.6f),
                    radius = baseRadius,
                    center = Offset(sX, sY),
                    style = Stroke(width = if (isSelected) 2.5f else 1f)
                )

                // Station Name and Shindo text when zoomed in or selected
                if (showStationLabels && (zoomLevel >= 1.7f || isSelected || st.realtimeShindo >= 2.0f)) {
                    val label = when (displayMode) {
                        DisplayMode.SHINDO -> "${st.name} ${String.format("%.1f", st.realtimeShindo)}"
                        DisplayMode.PGA -> "${st.name} ${st.pga.roundToInt()}gal"
                        DisplayMode.PGV -> "${st.name} ${String.format("%.2f", st.pgv)}cm/s"
                        DisplayMode.PGD -> "${st.name} ${String.format("%.3f", st.pgd)}mm"
                    }
                    val textLayout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            background = Color.Black.copy(alpha = 0.65f)
                        )
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(sX - textLayout.size.width / 2f, sY - baseRadius - textLayout.size.height - 2f)
                    )
                }
            }

            // 8. Overlay User GPS Location (現在地) using location permissions
            if (liveUserLocation != null) {
                val uX = toScreenX(liveUserLocation.lon)
                val uY = toScreenY(liveUserLocation.lat)

                // Animated glowing ripple ring
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = (1f - (locationGlowRadius / 26f)).coerceIn(0f, 0.7f)),
                    radius = locationGlowRadius * zoomLevel.coerceIn(0.9f, 2.0f),
                    center = Offset(uX, uY),
                    style = Stroke(width = 2.5f)
                )

                // Blue core dot
                drawCircle(
                    color = Color(0xFF0284C7),
                    radius = 7.5f * zoomLevel.coerceIn(0.9f, 1.8f),
                    center = Offset(uX, uY),
                    style = Fill
                )
                drawCircle(
                    color = Color.White,
                    radius = 7.5f * zoomLevel.coerceIn(0.9f, 1.8f),
                    center = Offset(uX, uY),
                    style = Stroke(width = 2f)
                )
                // Center white pupil
                drawCircle(
                    color = Color.White,
                    radius = 3f * zoomLevel.coerceIn(0.9f, 1.8f),
                    center = Offset(uX, uY),
                    style = Fill
                )

                // User Location Text Tag
                val userTagLayout = textMeasurer.measure(
                    text = "📍 現在地",
                    style = TextStyle(
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        background = Color(0xFF0F172A).copy(alpha = 0.85f)
                    )
                )
                drawText(
                    textLayoutResult = userTagLayout,
                    topLeft = Offset(uX - userTagLayout.size.width / 2f, uY + 12f)
                )
            }
        }

        // Top Status Header: Location & Seismic summary
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .border(1.dp, KyoshinCardBorder, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = KyoshinSurface.copy(alpha = 0.92f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Location status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hasLocationPermission) Icons.Default.LocationOn else Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = if (hasLocationPermission) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = if (hasLocationPermission) {
                                liveUserLocation?.placeName ?: "現在地測位中..."
                            } else {
                                "位置情報未許可（タップして許可）"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.clickable {
                                if (!hasLocationPermission) {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        )
                        val maxStation = liveStations.maxByOrNull { it.realtimeShindo }
                        val maxShindoStr = maxStation?.let { String.format("%.1f", it.realtimeShindo) } ?: "0.0"
                        Text(
                            text = "全国観測点: ${liveStations.size}点 / 全国最大計測震度: 震度$maxShindoStr",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp
                        )
                    }
                }

                // Mode switch: Shindo / PGA
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = {
                            displayMode = if (displayMode == DisplayMode.SHINDO) DisplayMode.PGA else DisplayMode.SHINDO
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = KyoshinPrimary
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (displayMode == DisplayMode.SHINDO) "表示: 震度" else "表示: PGA",
                            fontSize = 10.sp,
                            color = KyoshinPrimary
                        )
                    }
                }
            }
        }

        // Floating Action Buttons: Zoom & Center on GPS Location
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 70.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Center on Current GPS Location Button
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission && liveUserLocation != null && canvasSize.width > 0) {
                        val cW = canvasSize.width.toFloat()
                        val cH = canvasSize.height.toFloat()
                        val fitW: Float
                        val fitH: Float
                        if (cW / cH > mapAspect) {
                            fitH = cH
                            fitW = cH * mapAspect
                        } else {
                            fitW = cW
                            fitH = cW / mapAspect
                        }
                        val baseLeft = (cW - fitW) / 2f
                        val baseTop = (cH - fitH) / 2f
                        val cX = cW / 2f
                        val cY = cH / 2f

                        val targetZoom = 2.8f
                        val normX = GeoProjection.toNormX(liveUserLocation.lon)
                        val normY = GeoProjection.toNormY(liveUserLocation.lat)
                        val unzoomedX = baseLeft + normX * fitW
                        val unzoomedY = baseTop + normY * fitH

                        zoomLevel = targetZoom
                        panOffsetX = -(unzoomedX - cX) * targetZoom
                        panOffsetY = -(unzoomedY - cY) * targetZoom
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                containerColor = Color(0xFF0284C7),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("btn_center_user_location")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "現在地にセンタリング",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom In (+)
            SmallFloatingActionButton(
                onClick = { zoomLevel = (zoomLevel * 1.35f).coerceAtMost(6.0f) },
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "拡大", modifier = Modifier.size(18.dp))
            }

            // Zoom Out (-)
            SmallFloatingActionButton(
                onClick = {
                    val newZoom = (zoomLevel / 1.35f).coerceAtLeast(1.0f)
                    zoomLevel = newZoom
                    if (newZoom == 1.0f) {
                        panOffsetX = 0f
                        panOffsetY = 0f
                    }
                },
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "縮小", modifier = Modifier.size(18.dp))
            }

            // Reset View (⟲)
            SmallFloatingActionButton(
                onClick = {
                    zoomLevel = 1.0f
                    panOffsetX = 0f
                    panOffsetY = 0f
                    selectedStation = null
                },
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "全体表示リセット", modifier = Modifier.size(18.dp))
            }
        }

        // Station Inspection Card upon tapping
        AnimatedVisibility(
            visible = selectedStation != null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, end = 70.dp, bottom = 44.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            selectedStation?.let { st ->
                val distanceToUser = liveUserLocation?.let { u ->
                    GeoProjection.distanceKm(u.lat, u.lon, st.lat, st.lon).roundToInt()
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = KyoshinSurface.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, getShindoColor(st.realtimeShindo)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shindo Badge
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(getShindoColor(st.realtimeShindo), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("震度", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = String.format("%.1f", st.realtimeShindo),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${st.prefecture} ${st.name}観測点",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "PGA: ${String.format("%.1f", st.pga)} gal | PGV: ${String.format("%.2f", st.pgv)} cm/s",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                            if (distanceToUser != null) {
                                Text(
                                    text = "現在地からの距離: 約${distanceToUser}km",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedStation = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Bottom JMA Seismic Intensity Scale Bar Legend
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "0" to Shindo0Color,
                    "1" to Shindo1Color,
                    "2" to Shindo2Color,
                    "3" to Shindo3Color,
                    "4" to Shindo4Color,
                    "5弱" to Shindo5LowColor,
                    "5強" to Shindo5HighColor,
                    "6弱" to Shindo6LowColor,
                    "6強" to Shindo6HighColor,
                    "7" to Shindo7Color
                ).forEach { (scale, color) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = scale,
                            color = Color(0xFFCBD5E1),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
