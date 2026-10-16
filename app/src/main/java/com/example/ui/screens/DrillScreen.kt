package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.VoiceDetailLevel
import com.example.model.getShindoColorByText
import com.example.service.KyoshinBackgroundService
import com.example.ui.DrillScenario
import com.example.ui.KyoshinViewModel
import com.example.ui.components.EEWAlertBanner
import com.example.ui.theme.KyoshinAccentRed
import com.example.ui.theme.KyoshinCardBorder
import com.example.ui.theme.KyoshinPrimary
import com.example.ui.theme.KyoshinSurface

enum class DrillSubTab(val title: String, val icon: ImageVector) {
    SCENARIOS("訓練シナリオ", Icons.Default.PlayCircle),
    SETTINGS("音声・常駐設定", Icons.Default.Settings)
}

enum class ScenarioIntensityFilter(val label: String, val match: (DrillScenario) -> Boolean) {
    ALL("すべて", { true }),
    SHINDO7("震度7（激甚）", { it.maxShindo == "7" }),
    SHINDO6_HIGH("震度6強", { it.maxShindo == "6強" }),
    SHINDO6_LOW("震度6弱", { it.maxShindo == "6弱" }),
    TSUNAMI("津波警報あり", { it.tsunamiWarning != "津波の心配なし" })
}

@Composable
fun DrillScreen(
    viewModel: KyoshinViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeEEW by viewModel.repository.activeEEW.collectAsState()
    val isVoiceEnabled by viewModel.voiceAlertManager.isVoiceEnabled.collectAsState()
    val speechRate by viewModel.voiceAlertManager.speechRate.collectAsState()
    val detailLevel by viewModel.voiceAlertManager.detailLevel.collectAsState()
    var isBgMonitoringEnabled by remember { mutableStateOf(KyoshinBackgroundService.isEnabled(context)) }

    var currentSubTab by remember { mutableStateOf(DrillSubTab.SCENARIOS) }
    var selectedFilter by remember { mutableStateOf(ScenarioIntensityFilter.ALL) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Active EEW Banner & Emergency Abort Button (Displayed during active drill)
        if (activeEEW != null) {
            EEWAlertBanner(
                alert = activeEEW!!,
                onRepeatVoice = { viewModel.repeatEEWAnnouncement() },
                onDismiss = { viewModel.dismissEEW() }
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.dismissEEW() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("stop_drill_button")
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("訓練を強制停止する", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // SubTab Navigation: Scenarios vs Settings
        TabRow(
            selectedTabIndex = currentSubTab.ordinal,
            containerColor = Color(0xFF131B2E),
            contentColor = KyoshinPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[currentSubTab.ordinal]),
                    color = KyoshinPrimary
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, KyoshinCardBorder, RoundedCornerShape(8.dp))
        ) {
            DrillSubTab.values().forEach { tab ->
                val selected = currentSubTab == tab
                Tab(
                    selected = selected,
                    onClick = { currentSubTab = tab },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (selected) KyoshinPrimary else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                color = if (selected) Color.White else Color(0xFF94A3B8),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    },
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("drill_tab_${tab.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (currentSubTab) {
            DrillSubTab.SCENARIOS -> {
                // 1. Scenarios Header - High Visibility & Contrast Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D33)),
                    border = BorderStroke(1.dp, KyoshinPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(KyoshinPrimary.copy(alpha = 0.18f), CircleShape)
                                    .border(1.dp, KyoshinPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = KyoshinPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "シナリオ選択（タップで発令開始）",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "警報音・音声カウントダウン・P/S波の到達を疑似体験",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            color = KyoshinPrimary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${viewModel.drillScenarios.count { selectedFilter.match(it) }}件",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Intensity Filter Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ScenarioIntensityFilter.values()) { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KyoshinPrimary,
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF131B2E),
                                labelColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filtered List of Scenarios
                val filteredScenarios = viewModel.drillScenarios.filter { selectedFilter.match(it) }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredScenarios) { scenario ->
                        EnhancedDrillScenarioCard(
                            scenario = scenario,
                            isCurrentActive = activeEEW?.hypocenter == scenario.hypocenter,
                            onStart = { viewModel.triggerDrill(scenario) }
                        )
                    }

                    // Bottom Quick Config Hint Bar
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentSubTab = DrillSubTab.SETTINGS }
                                .border(1.dp, KyoshinCardBorder, RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = KyoshinPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "読み上げ: ${String.format("%.2f", speechRate)}x | 常駐監視: ${if (isBgMonitoringEnabled) "有効" else "停止"}",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                }

                                Text(
                                    text = "設定を変更 ⚙️",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            DrillSubTab.SETTINGS -> {
                // 2. Settings Section - Full-Height, Clean & Spacious
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Voice Alert Settings Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, KyoshinCardBorder, RoundedCornerShape(10.dp)),
                            colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = KyoshinPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "音声・読み上げ設定",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Switch(
                                        checked = isVoiceEnabled,
                                        onCheckedChange = { viewModel.voiceAlertManager.setVoiceEnabled(it) },
                                        modifier = Modifier.testTag("drill_voice_switch")
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val speedLabel = when {
                                    speechRate <= 0.85f -> "ゆっくり"
                                    speechRate in 0.86f..1.1f -> "標準"
                                    speechRate in 1.11f..1.35f -> "やや速め"
                                    speechRate in 1.36f..1.65f -> "速め"
                                    else -> "高速"
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "読み上げ速度: ${String.format("%.2f", speechRate)}x ($speedLabel)",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )

                                    Button(
                                        onClick = { viewModel.voiceAlertManager.testSpeech() },
                                        enabled = isVoiceEnabled,
                                        colors = ButtonDefaults.buttonColors(containerColor = KyoshinPrimary),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .testTag("btn_test_speech")
                                    ) {
                                        Text(
                                            text = "📢 速度テスト発声",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Slider(
                                    value = speechRate,
                                    onValueChange = { viewModel.voiceAlertManager.setSpeechRate(it) },
                                    valueRange = 0.6f..2.0f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("speech_rate_slider")
                                )

                                // Quick Speed Presets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        0.8f to "0.8x 穏やか",
                                        1.0f to "1.0x 標準",
                                        1.25f to "1.25x 推奨",
                                        1.5f to "1.5x 速報",
                                        1.8f to "1.8x 高速"
                                    ).forEach { (presetRate, label) ->
                                        val isSelected = kotlin.math.abs(speechRate - presetRate) < 0.08f
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.voiceAlertManager.setSpeechRate(presetRate)
                                                viewModel.voiceAlertManager.testSpeech(presetRate)
                                            },
                                            label = { Text(label, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = KyoshinPrimary,
                                                selectedLabelColor = Color.Black,
                                                containerColor = Color(0xFF1E293B),
                                                labelColor = Color(0xFF94A3B8)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(text = "読み上げ詳細レベル", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    VoiceDetailLevel.values().forEach { level ->
                                        val selected = detailLevel == level
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.voiceAlertManager.setDetailLevel(level) },
                                            label = { Text(level.label.substringBefore("（"), fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = KyoshinPrimary,
                                                selectedLabelColor = Color.Black,
                                                containerColor = Color(0xFF1E293B),
                                                labelColor = Color(0xFF94A3B8)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 24/7 Background Earthquake Monitoring Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, KyoshinCardBorder, RoundedCornerShape(10.dp))
                                .testTag("bg_monitoring_card"),
                            colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isBgMonitoringEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                            contentDescription = null,
                                            tint = if (isBgMonitoringEnabled) KyoshinPrimary else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "常駐バックグラウンド監視",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (isBgMonitoringEnabled) "● 常駐稼働中（アプリ終了後も通知）" else "○ 監視停止中",
                                                color = if (isBgMonitoringEnabled) KyoshinPrimary else Color.Gray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = isBgMonitoringEnabled,
                                        onCheckedChange = { enabled ->
                                            isBgMonitoringEnabled = enabled
                                            if (enabled) {
                                                KyoshinBackgroundService.start(context)
                                                Toast.makeText(context, "常駐バックグラウンド監視を開始しました", Toast.LENGTH_SHORT).show()
                                            } else {
                                                KyoshinBackgroundService.stop(context)
                                                Toast.makeText(context, "常駐バックグラウンド監視を停止しました", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.testTag("bg_monitoring_switch")
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "アプリ終了後や画面消灯時も、24時間リアルタイムに地震情報・緊急地震速報を受信し、即座にヘッズアップ通知・音声アナウンスします。",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        KyoshinBackgroundService.triggerTestNotification(context)
                                        Toast.makeText(context, "3秒後にバックグラウンド通知が発令されます。アプリを閉じてお待ちください！", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .testTag("btn_test_bg_notification"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, KyoshinPrimary.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = KyoshinPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "🔔 バックグラウンド通知テスト（3秒後発令）",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Enhanced Drill Scenario Card with large readable intensity badge,
 * countdown badges, hypocenter details, and prominent trigger action button.
 */
@Composable
fun EnhancedDrillScenarioCard(
    scenario: DrillScenario,
    isCurrentActive: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onStart() }
            .border(
                width = if (isCurrentActive) 1.5.dp else 1.dp,
                color = if (isCurrentActive) KyoshinAccentRed else KyoshinCardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("scenario_${scenario.hypocenter}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentActive) Color(0xFF200F15) else KyoshinSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large High-Contrast Intensity Badge
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            getShindoColorByText(scenario.maxShindo),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "最大震度",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = scenario.maxShindo,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scenario.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "震源: ${scenario.hypocenter} (深さ${scenario.depthKm}km) / M${String.format("%.1f", scenario.magnitude)}",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Action Button (Min 48dp touch target)
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentActive) Color(0xFFEF4444) else KyoshinPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("start_drill_${scenario.hypocenter}")
                ) {
                    Icon(
                        imageVector = if (isCurrentActive) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCurrentActive) "再発令" else "訓練発令",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges row: Countdown arrival, affected areas, and tsunami alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF0369A1).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "⏱️ 到達猶予: 約${scenario.countdownSec}秒",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                if (scenario.affectedPrefectures.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "警戒: ${scenario.affectedPrefectures.take(3).joinToString("・")}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (scenario.tsunamiWarning != "津波の心配なし") {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = if (scenario.tsunamiWarning.contains("大津波")) Color(0xFF7F1D1D).copy(alpha = 0.4f) else Color(0xFF78350F).copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (scenario.tsunamiWarning.contains("大津波")) Color(0xFFEF4444) else Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🌊 ${scenario.tsunamiWarning}",
                        color = if (scenario.tsunamiWarning.contains("大津波")) Color(0xFFFF8080) else Color(0xFFFCD34D),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
