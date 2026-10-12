package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.getShindoColorByText
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
    val activeEEW by viewModel.repository.activeEEW.collectAsState()
    val isVoiceEnabled by viewModel.voiceAlertManager.isVoiceEnabled.collectAsState()
    val speechRate by viewModel.voiceAlertManager.speechRate.collectAsState()

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
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "音声・読み上げ設定",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "音声読み上げ機能", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    Switch(
                        checked = isVoiceEnabled,
                        onCheckedChange = { viewModel.voiceAlertManager.setVoiceEnabled(it) },
                        modifier = Modifier.testTag("drill_voice_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "読み上げ速度: ${String.format("%.2f", speechRate)}x",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.width(130.dp)
                    )
                    Slider(
                        value = speechRate,
                        onValueChange = { viewModel.voiceAlertManager.setSpeechRate(it) },
                        valueRange = 0.8f..1.6f,
                        steps = 7,
                        modifier = Modifier.weight(1f)
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
