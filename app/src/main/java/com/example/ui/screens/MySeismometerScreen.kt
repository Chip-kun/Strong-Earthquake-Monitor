package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.getShindoColorByText
import com.example.ui.KyoshinViewModel
import com.example.ui.theme.KyoshinCardBorder
import com.example.ui.theme.KyoshinPrimary
import com.example.ui.theme.KyoshinSurface

@Composable
fun MySeismometerScreen(
    viewModel: KyoshinViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.deviceSeismometer.state.collectAsState()
    val isEnabled by viewModel.isSeismometerEnabled.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "マイ地震計（端末加速度計）", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = "端末内蔵センサーで揺れをリアルタイム測定", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked -> viewModel.toggleSeismometer(checked) },
                    colors = SwitchDefaults.colors(checkedThumbColor = KyoshinPrimary, checkedTrackColor = KyoshinPrimary.copy(alpha = 0.5f))
                )
            }

            IconButton(
                onClick = { viewModel.deviceSeismometer.resetPeak() },
                enabled = isEnabled,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isEnabled) Color(0xFF1E293B) else Color(0xFF111827))
                    .testTag("reset_peak_button")
            ) {
                Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "最大値リセット", tint = if (isEnabled) KyoshinPrimary else Color(0xFF475569))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val displayShindo = if (isEnabled) state.estimatedShindo else "0"
        val shindoColor = getShindoColorByText(displayShindo)
        
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, KyoshinCardBorder, RoundedCornerShape(12.dp)).testTag("seismometer_gauge_card"),
            colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "推定計測震度", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.size(110.dp).clip(CircleShape).background(shindoColor.copy(alpha = 0.2f)).border(3.dp, shindoColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "震度", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = displayShindo, color = if (isEnabled) Color.White else Color(0xFF475569), fontSize = 42.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
