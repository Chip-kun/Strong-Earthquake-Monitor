package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DepthMode
import com.example.model.DisplayMode
import com.example.model.ObservationStation
import com.example.network.KmoniFetchStatus
import com.example.network.KmoniFetchStats
import com.example.network.KmoniImageType
import com.example.network.WebSocketStatus
import com.example.network.WebSocketStats
import com.example.ui.KyoshinViewModel
import com.example.ui.MapStyle
import com.example.ui.components.EEWAlertBanner
import com.example.ui.components.JapanSeismicIntensityMap
import com.example.ui.components.KyoshinMapCanvas
import com.example.ui.components.SeismographView
import com.example.ui.components.ShindoScaleBar
import com.example.ui.theme.KyoshinPrimary

enum class MonitorViewMode(val label: String) {
    KYOSHIN_VECTOR("強震網"),
    JMA_SEISMIC("JMA震度"),
    NIED_IMAGE("防災科研")
}

@Composable
fun MonitorScreen(
    viewModel: KyoshinViewModel,
    modifier: Modifier = Modifier
) {
    val stations by viewModel.repository.stations.collectAsState()
    val activeEEW by viewModel.repository.activeEEW.collectAsState()
    val selectedStation by viewModel.repository.selectedStation.collectAsState()
    val displayMode by viewModel.repository.displayMode.collectAsState()
    val depthMode by viewModel.repository.depthMode.collectAsState()
    val waveform by viewModel.repository.liveWaveform.collectAsState()
    val currentTime by viewModel.currentTimeStr.collectAsState()
    val isVoiceEnabled by viewModel.voiceAlertManager.isVoiceEnabled.collectAsState()

    val userLocation by viewModel.userLocation.collectAsState()
    val mapStyle by viewModel.mapStyle.collectAsState()
    val showStationLabels by viewModel.showStationLabels.collectAsState()
    val distanceToEpicenter by viewModel.distanceToEpicenter.collectAsState()
    val userEtaSec by viewModel.userEtaSec.collectAsState()
    val nearestStation by viewModel.nearestStation.collectAsState()

    // Real-time WebSocket and NIED image state
    val wsStats by viewModel.webSocketStats.collectAsState()
    val kmoniBitmap by viewModel.kmoniBitmap.collectAsState()
    val kmoniStats by viewModel.kmoniStats.collectAsState()
    val kmoniTimestamp by viewModel.kmoniTimestamp.collectAsState()

    var monitorViewMode by remember { mutableStateOf(MonitorViewMode.KYOSHIN_VECTOR) }
    var showConnectionDetailsDialog by remember { mutableStateOf(false) }

    // Location Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.locationManager.updatePermissionGranted(isGranted)
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Status Bar: Time, Live indicator, ViewMode Toggles, Location, Voice
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "● LIVE",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = currentTime,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // View Mode Selector Segmented Group: 強震網 / JMA震度 / 防災科研
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                MonitorViewMode.values().forEach { mode ->
                    val isSelected = monitorViewMode == mode
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) KyoshinPrimary else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (isSelected) KyoshinPrimary else Color(0xFF334155)),
                        modifier = Modifier
                            .clickable { monitorViewMode = mode }
                            .testTag("view_mode_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.Black else Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                // Request/refresh GPS Location
                IconButton(
                    onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("request_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "位置情報取得",
                        tint = if (userLocation != null) KyoshinPrimary else Color.Gray,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Voice Speech Toggle Button
                FilledTonalIconButton(
                    onClick = { viewModel.voiceAlertManager.setVoiceEnabled(!isVoiceEnabled) },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("toggle_voice_button"),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isVoiceEnabled) Color(0xFF0284C7) else Color(0xFF334155),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isVoiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = if (isVoiceEnabled) "音声有効" else "音声ミュート",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Real-time Streams Status Bar (WebSocket & NIED Kmoni API)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF111827),
            border = BorderStroke(1.dp, Color(0xFF1F2937)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .clickable { showConnectionDetailsDialog = true }
                .testTag("realtime_stream_status_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WebSocket status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (dotColor, statusText) = when (wsStats.status) {
                        WebSocketStatus.CONNECTED -> Color(0xFF10B981) to "WS配信中"
                        WebSocketStatus.CONNECTING -> Color(0xFFF59E0B) to "WS接続中"
                        WebSocketStatus.RECONNECTING -> Color(0xFFF97316) to "WS再接続中"
                        WebSocketStatus.ERROR -> Color(0xFFEF4444) to "WSエラー"
                        WebSocketStatus.DISCONNECTED -> Color(0xFF94A3B8) to "WS切断"
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        color = dotColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = wsStats.lastEventSummary,
                        color = Color(0xFF9CA3AF),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }

                // NIED Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (niedColor, niedText) = when (kmoniStats.status) {
                        KmoniFetchStatus.SUCCESS -> Color(0xFF06B6D4) to "画像取得"
                        KmoniFetchStatus.FETCHING -> Color(0xFF3B82F6) to "取得中"
                        KmoniFetchStatus.DELAY_ADJUST -> Color(0xFFF59E0B) to "遅延調整"
                        KmoniFetchStatus.ERROR -> Color(0xFFEF4444) to "エラー"
                        KmoniFetchStatus.IDLE -> Color(0xFF64748B) to "待機"
                    }
                    Text(
                        text = "防災科研: $niedText",
                        color = niedColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "配信詳細",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Active EEW Banner with User Location Arrival Info (shown only when alert active)
        activeEEW?.let { eew ->
            Spacer(modifier = Modifier.height(2.dp))
            EEWAlertBanner(
                alert = eew,
                onRepeatVoice = { viewModel.repeatEEWAnnouncement() },
                onDismiss = { viewModel.dismissEEW() }
            )

            if (distanceToEpicenter != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1622),
                    border = BorderStroke(1.dp, Color(0xFFF43F5E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NearMe, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "現在地まで: 約${distanceToEpicenter?.toInt()}km",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = if ((userEtaSec ?: 0) > 0) "主要動到達: あと約${userEtaSec}秒" else "主要動到達済み",
                            color = Color(0xFFFFD600),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Main Hero View Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (monitorViewMode) {
                MonitorViewMode.KYOSHIN_VECTOR -> {
                    KyoshinMapCanvas(
                        stations = stations,
                        activeEEW = activeEEW,
                        selectedStation = selectedStation,
                        userLocation = userLocation,
                        mapStyle = mapStyle,
                        showStationLabels = showStationLabels,
                        displayMode = displayMode,
                        depthMode = depthMode,
                        onMapStyleChanged = { viewModel.setMapStyle(it) },
                        onDisplayModeChanged = { mode: DisplayMode -> viewModel.repository.setDisplayMode(mode) },
                        onDepthModeChanged = { viewModel.repository.setDepthMode(it) },
                        onStationSelected = { viewModel.repository.selectStation(it) }
                    )
                }
                MonitorViewMode.JMA_SEISMIC -> {
                    JapanSeismicIntensityMap(
                        viewModel = viewModel,
                        stations = stations,
                        activeEEW = activeEEW,
                        userLocation = userLocation,
                        onStationClick = { viewModel.repository.selectStation(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MonitorViewMode.NIED_IMAGE -> {
                    NiedLiveImageView(
                        bitmap = kmoniBitmap,
                        stats = kmoniStats,
                        depthMode = depthMode,
                        imageType = when (displayMode) {
                            DisplayMode.SHINDO -> KmoniImageType.SHINDO
                            DisplayMode.PGA -> KmoniImageType.PGA
                            DisplayMode.PGV -> KmoniImageType.PGV
                            DisplayMode.PGD -> KmoniImageType.PGD
                        },
                        onDepthModeChanged = { viewModel.repository.setDepthMode(it) },
                        onImageTypeChanged = { type ->
                            val mode = when (type) {
                                KmoniImageType.SHINDO -> DisplayMode.SHINDO
                                KmoniImageType.PGA -> DisplayMode.PGA
                                KmoniImageType.PGV -> DisplayMode.PGV
                                KmoniImageType.PGD -> DisplayMode.PGD
                            }
                            viewModel.repository.setDisplayMode(mode)
                        },
                        onRefresh = { viewModel.refreshKmoniImage() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Real-time 3-Axis Seismograph Waveform
        SeismographView(
            samples = waveform,
            stationName = selectedStation?.name ?: (nearestStation?.let { "${it.name} (最寄)" } ?: "全国最大観測点")
        )

        Spacer(modifier = Modifier.height(4.dp))

        // JMA Shindo Scale Bar
        ShindoScaleBar()
    }

    // Connection Details Dialog
    if (showConnectionDetailsDialog) {
        ConnectionDetailsDialog(
            wsStats = wsStats,
            kmoniStats = kmoniStats,
            onReconnectWs = { viewModel.reconnectWebSocket() },
            onRefreshKmoni = { viewModel.refreshKmoniImage() },
            onDismiss = { showConnectionDetailsDialog = false }
        )
    }
}

/**
 * 防災科学技術研究所 (NIED) 強震モニタのリアルタイム画像を全画面表示するコンポーネント
 */
@Composable
fun NiedLiveImageView(
    bitmap: Bitmap?,
    stats: KmoniFetchStats,
    depthMode: DepthMode,
    imageType: KmoniImageType,
    onDepthModeChanged: (DepthMode) -> Unit,
    onImageTypeChanged: (KmoniImageType) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF030712))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "防災科研 強震モニタ リアルタイム画像",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = KyoshinPrimary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "防災科学技術研究所 (NIED) から画像を受信中...",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stats.errorMessage ?: "リアルタイム画像エンドポイントに接続しています (${stats.currentDelayOffsetSec}秒遅延同期)",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = KyoshinPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("再試行")
                }
            }
        }

        // Overlay Header: Controls for Depth & ImageType
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xDD0F172A),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Depth Toggle (地表 / 地中)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (depthMode == DepthMode.SURFACE) KyoshinPrimary else Color.Transparent)
                            .clickable { onDepthModeChanged(DepthMode.SURFACE) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "地表",
                            color = if (depthMode == DepthMode.SURFACE) Color.Black else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (depthMode == DepthMode.BOREHOLE) Color(0xFF38BDF8) else Color.Transparent)
                            .clickable { onDepthModeChanged(DepthMode.BOREHOLE) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "地中",
                            color = if (depthMode == DepthMode.BOREHOLE) Color.Black else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Type Toggle (震度 / PGA / PGV / PGD)
                KmoniImageType.values().forEach { type ->
                    val isSelected = imageType == type
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
                        modifier = Modifier.clickable { onImageTypeChanged(type) }
                    ) {
                        Text(
                            text = when (type) {
                                KmoniImageType.SHINDO -> "震度"
                                KmoniImageType.PGA -> "PGA"
                                KmoniImageType.PGV -> "PGV"
                                KmoniImageType.PGD -> "PGD"
                            },
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "更新", tint = KyoshinPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Overlay Footer: Timestamp and delay
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color(0xCC0B1120),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📡 防災科研 強震モニタ",
                    color = KyoshinPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (stats.lastSuccessTimestamp.isNotBlank()) "${stats.lastSuccessTimestamp} (遅延${stats.currentDelayOffsetSec}s)" else "同期待機中",
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * WebSocket配信および防災科研API定期取得の詳細状態を表示するダイアログ
 */
@Composable
fun ConnectionDetailsDialog(
    wsStats: WebSocketStats,
    kmoniStats: KmoniFetchStats,
    onReconnectWs: () -> Unit,
    onRefreshKmoni: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sensors, contentDescription = null, tint = KyoshinPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("リアルタイム配信＆API接続状態", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // WebSocket Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡ WebSocket リアルタイム配信", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 13.sp)
                            Text(wsStats.status.label, color = if (wsStats.status == WebSocketStatus.CONNECTED) Color(0xFF10B981) else Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("接続先: ${wsStats.url}", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("受信メッセージ数: ${wsStats.messagesReceivedCount} 件", color = Color.White, fontSize = 11.sp)
                        Text("最新ステータス: ${wsStats.lastEventSummary}", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onReconnectWs,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("WebSocket 再接続", fontSize = 11.sp)
                        }
                    }
                }

                // NIED Kmoni Image Fetcher Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📡 防災科研 強震モニタ画像API", fontWeight = FontWeight.Bold, color = Color(0xFF34D399), fontSize = 13.sp)
                            Text(kmoniStats.status.label, color = if (kmoniStats.status == KmoniFetchStatus.SUCCESS) Color(0xFF10B981) else Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("取得周期: 1秒間隔 (サーバー遅延: ${kmoniStats.currentDelayOffsetSec}秒自動調整)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text("成功回数: ${kmoniStats.successCount} 回 / 失敗: ${kmoniStats.failureCount} 回", color = Color.White, fontSize = 11.sp)
                        Text("通信レイテンシ: ${kmoniStats.lastLatencyMs} ms", color = Color.White, fontSize = 11.sp)
                        if (kmoniStats.lastSuccessTimestamp.isNotBlank()) {
                            Text("最新取得時刻: ${kmoniStats.lastSuccessTimestamp}", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onRefreshKmoni,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("今すぐ画像再取得", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる", color = KyoshinPrimary)
            }
        },
        containerColor = Color(0xFF0F172A)
    )
}
