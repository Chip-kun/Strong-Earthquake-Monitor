package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(14.dp)
    ) {
        // Header
        Text(
            text = "緊急地震速報 訓練シミュレーター",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "実際の警報音・音声カウントダウン・波形伝播を疑似体験",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Active EEW status during drill
        if (activeEEW != null) {
            EEWAlertBanner(
                alert = activeEEW!!,
                onRepeatVoice = { viewModel.repeatEEWAnnouncement() },
                onDismiss = { viewModel.dismissEEW() }
            )
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { viewModel.dismissEEW() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("stop_drill_button")
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("訓練を強制停止する", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Voice & Audio Configuration Card
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
                    Text(
                        text = "音声・読み上げ設定",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Switch(
                        checked = isVoiceEnabled,
                        onCheckedChange = { viewModel.voiceAlertManager.setVoiceEnabled(it) },
                        modifier = Modifier.testTag("drill_voice_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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

                // Quick speed presets
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

        Spacer(modifier = Modifier.height(10.dp))

        // 24/7 Background Earthquake & EEW Monitoring Card
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
                        .height(36.dp)
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

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "シナリオ選択（タップで発令開始）",
            color = Color(0xFFCBD5E1),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(viewModel.drillScenarios) { scenario ->
                DrillScenarioCard(
                    scenario = scenario,
                    isCurrentActive = activeEEW?.hypocenter == scenario.hypocenter,
                    onStart = { viewModel.triggerDrill(scenario) }
                )
            }
        }
    }
}

@Composable
fun DrillScenarioCard(
    scenario: DrillScenario,
    isCurrentActive: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCurrentActive) KyoshinAccentRed else KyoshinCardBorder,
                RoundedCornerShape(10.dp)
            )
            .testTag("scenario_${scenario.hypocenter}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentActive) Color(0xFF1E1015) else KyoshinSurface
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Intensity Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        getShindoColorByText(scenario.maxShindo),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "震度",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = scenario.maxShindo,
                        color = Color.White,
                        fontSize = 18.sp,
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
                    text = scenario.subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Text(
                    text = "到達猶予: 約${scenario.countdownSec}秒 / 震源: ${scenario.hypocenter}",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                if (scenario.detailedAreas.isNotEmpty()) {
                    Text(
                        text = "詳細地域: ${scenario.detailedAreas.take(3).joinToString("、")}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                if (scenario.tsunamiWarning != "津波の心配なし") {
                    Text(
                        text = "⚠️ ${scenario.tsunamiWarning}",
                        color = if (scenario.tsunamiWarning.contains("大津波")) Color(0xFFFF6B6B) else Color(0xFFFBBF24),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentActive) Color(0xFFEF4444) else KyoshinPrimary,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("start_drill_${scenario.hypocenter}")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCurrentActive) "再発令" else "開始",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
