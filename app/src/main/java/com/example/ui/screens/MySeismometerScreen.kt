package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.getShindoColorByText
import com.example.ui.KyoshinViewModel
import com.example.ui.theme.KyoshinCardBorder
import com.example.ui.theme.KyoshinPrimary
import com.example.ui.theme.KyoshinSurface
import kotlin.math.max

@Composable
fun MySeismometerScreen(
    viewModel: KyoshinViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.deviceSeismometer.state.collectAsState()

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
            Column {
                Text(
                    text = "マイ地震計（端末加速度計）",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "端末内蔵センサーで揺れをリアルタイム測定",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = { viewModel.deviceSeismometer.resetPeak() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .testTag("reset_peak_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "最大値リセット",
                    tint = KyoshinPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Large Real-time Instrumental Gauge Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KyoshinCardBorder, RoundedCornerShape(12.dp))
                .testTag("seismometer_gauge_card"),
            colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "推定計測震度",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Circular Shindo Gauge
                val shindoColor = getShindoColorByText(state.estimatedShindo)
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(shindoColor.copy(alpha = 0.2f))
                        .border(3.dp, shindoColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "震度",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.estimatedShindo,
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Accelerometer Readouts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "瞬時加速度", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text(
                            text = String.format("%.2f gal", state.currentGal),
                            color = Color(0xFF38BDF8),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "ピーク加速度 (Hold)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text(
                            text = String.format("%.2f gal", state.peakGal),
                            color = Color(0xFFF59E0B),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Device Waveform Graph
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KyoshinCardBorder, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = KyoshinPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "端末振動リアルタイム波形",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.isShaking) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEF4444)
                        ) {
                            Text(
                                text = "揺れ検知中",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Canvas plot of phone acceleration
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF070B14))
                ) {
                    val w = size.width
                    val h = size.height
                    val centerY = h / 2f

                    // Grid lines
                    drawLine(Color(0xFF1E293B), Offset(0f, centerY), Offset(w, centerY), strokeWidth = 1f)
                    drawLine(Color(0xFF0F172A), Offset(0f, centerY - 25f), Offset(w, centerY - 25f), strokeWidth = 1f)
                    drawLine(Color(0xFF0F172A), Offset(0f, centerY + 25f), Offset(w, centerY + 25f), strokeWidth = 1f)

                    val samples = state.samples
                    if (samples.size < 2) return@Canvas

                    val maxAmp = max(state.peakGal, 5f)
                    val scale = (h * 0.4f) / maxAmp
                    val stepX = w / (samples.size - 1)

                    val path = Path()
                    samples.forEachIndexed { i, s ->
                        val x = i * stepX
                        val y = centerY - (s.ew * scale).coerceIn(-h * 0.45f, h * 0.45f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(path, color = Color(0xFF00E5FF), style = Stroke(width = 2f))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "端末を振ると加速度が上昇し、一定以上で音声アラートが作動します。",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Info Explanation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D33)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ガル（gal）は地震の揺れの勢い（加速度 cm/s²）を表す単位です。一般に数gal以上で人体に感じられ、数百galを超えると建物の倒壊や家具の転倒が発生します。",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
