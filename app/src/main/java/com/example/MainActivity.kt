package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notification.KyoshinNotificationManager
import com.example.ui.AppTab
import com.example.ui.KyoshinViewModel
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        KyoshinNotificationManager.initNotificationChannels(this)
        setContent {
            MyApplicationTheme {
                KyoshinMainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KyoshinMainApp(viewModel: KyoshinViewModel = viewModel()) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val isVoiceEnabled by viewModel.voiceAlertManager.isVoiceEnabled.collectAsState()
    val activeEEW by viewModel.repository.activeEEW.collectAsState()

    // Request notification permission on Android 13+ (TIRAMISU)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // BackHandler: return to MONITOR tab if user presses back from another tab
    BackHandler(enabled = currentTab != AppTab.MONITOR) {
        viewModel.selectTab(AppTab.MONITOR)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = KyoshinBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = KyoshinPrimary,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "強震モニター速報",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (activeEEW != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = KyoshinAccentRed
                            ) {
                                Text(
                                    text = "EEW発令中",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Quick Drill trigger button
                    IconButton(
                        onClick = { viewModel.selectTab(AppTab.DRILL) },
                        modifier = Modifier.testTag("action_drill_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "緊急訓練",
                            tint = Color(0xFFF59E0B)
                        )
                    }

                    // Voice Mute / Unmute Toggle
                    IconButton(
                        onClick = { viewModel.voiceAlertManager.setVoiceEnabled(!isVoiceEnabled) },
                        modifier = Modifier.testTag("action_voice_toggle")
                    ) {
                        Icon(
                            imageVector = if (isVoiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "音声設定",
                            tint = if (isVoiceEnabled) KyoshinPrimary else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KyoshinSurface,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = KyoshinSurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .border(1.dp, KyoshinCardBorder)
                    .testTag("bottom_navigation_bar")
            ) {
                listOf(
                    NavigationItem(AppTab.MONITOR, "強震モニタ", Icons.Default.Sensors),
                    NavigationItem(AppTab.EARTHQUAKES, "地震履歴", Icons.Default.List),
                    NavigationItem(AppTab.SEISMOMETER, "マイ地震計", Icons.Default.Speed),
                    NavigationItem(AppTab.DRILL, "訓練", Icons.Default.NotificationsActive),
                    NavigationItem(AppTab.GUIDE, "防災・安否", Icons.Default.Shield)
                ).forEach { item ->
                    val isSelected = currentTab == item.tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(item.tab) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = KyoshinPrimary,
                            indicatorColor = KyoshinPrimary,
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.testTag("nav_item_${item.tab.name}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.MONITOR -> MonitorScreen(viewModel = viewModel)
                AppTab.EARTHQUAKES -> EarthquakeHistoryScreen(viewModel = viewModel)
                AppTab.SEISMOMETER -> MySeismometerScreen(viewModel = viewModel)
                AppTab.DRILL -> DrillScreen(viewModel = viewModel)
                AppTab.GUIDE -> DisasterGuideScreen(viewModel = viewModel)
            }
        }
    }
}

private data class NavigationItem(
    val tab: AppTab,
    val label: String,
    val icon: ImageVector
)
