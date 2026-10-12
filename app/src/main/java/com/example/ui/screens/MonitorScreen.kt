package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DepthMode
import com.example.model.DisplayMode
import com.example.model.ObservationStation
import com.example.ui.KyoshinViewModel
import com.example.ui.MapStyle
import com.example.ui.components.EEWAlertBanner
import com.example.ui.components.KyoshinMapCanvas
import com.example.ui.components.SeismographView
import com.example.ui.components.ShindoScaleBar
import com.example.ui.theme.KyoshinPrimary

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
        // Status Bar: Time, Live indicator, Voice toggle, Location refresh
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "全国観測網",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = currentTime,
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

        // Main Hero Map Canvas (Takes full flexible space, maintains perfect aspect ratio)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
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
}
