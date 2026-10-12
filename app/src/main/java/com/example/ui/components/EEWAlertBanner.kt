package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EEWAlert
import com.example.model.getShindoColorByText
import com.example.ui.theme.KyoshinAccentRed
import com.example.ui.theme.KyoshinSecondary

@Composable
fun EEWAlertBanner(
    alert: EEWAlert,
    onRepeatVoice: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val bannerBg = if (alert.isWarning) {
        KyoshinAccentRed.copy(alpha = pulseAlpha)
    } else {
        KyoshinSecondary.copy(alpha = pulseAlpha)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("eew_alert_banner")
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = bannerBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header Row: Warning tag, Drill label, Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "警報アイコン",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (alert.isWarning) "緊急地震速報（警報）" else "緊急地震速報（予報）",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    )
                    if (alert.isDrill) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "訓練",
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("eew_dismiss_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "閉じる",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: Hypocenter, Magnitude, Shindo, and Countdown circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alert.hypocenter,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "規模: M${alert.magnitude}",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "深さ: 約${alert.depthKm}km",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Estimated Max Shindo Badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "推定最大",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(getShindoColorByText(alert.maxShindo))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "震度 ${alert.maxShindo}",
                            color = if (alert.maxShindo in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }

                // Countdown Timer Bubble
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (alert.countdownSeconds > 0) {
                            Text(
                                text = "${alert.countdownSeconds}",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "秒",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                        } else {
                            Text(
                                text = "到達",
                                color = Color.Yellow,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Detailed Regions and Tsunami Alert Banners
            if (alert.detailedAreas.isNotEmpty() || (alert.tsunamiWarning.isNotEmpty() && alert.tsunamiWarning != "津波の心配なし")) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (alert.detailedAreas.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "警戒地域: ",
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = alert.detailedAreas.joinToString("、"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (alert.tsunamiWarning.isNotEmpty() && alert.tsunamiWarning != "津波の心配なし") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "津波警報: ",
                                color = Color(0xFFFF6B6B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = alert.tsunamiWarning,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Voice Repeat Button and Action Advisory
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "倒れる家具や落下物から身を守ってください",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onRepeatVoice,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("repeat_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "音声再送",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "音声再生", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
