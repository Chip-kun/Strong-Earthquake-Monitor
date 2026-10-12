package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EarthquakeItem
import com.example.model.getShindoColorByText
import com.example.ui.KyoshinViewModel
import com.example.ui.theme.KyoshinCardBorder
import com.example.ui.theme.KyoshinPrimary
import com.example.ui.theme.KyoshinSurface

@Composable
fun EarthquakeHistoryScreen(
    viewModel: KyoshinViewModel,
    modifier: Modifier = Modifier
) {
    val earthquakes by viewModel.repository.earthquakes.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "地震情報・履歴",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "気象庁・P2P地震情報 リアルタイム速報",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = { viewModel.repository.fetchEarthquakes() },
                modifier = Modifier.testTag("refresh_earthquakes_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "再読み込み",
                    tint = KyoshinPrimary
                )
            }
        }

        if (earthquakes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = KyoshinPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(earthquakes, key = { it.id }) { item ->
                    EarthquakeItemCard(
                        item = item,
                        onSpeak = { viewModel.announceEarthquake(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun EarthquakeItemCard(
    item: EarthquakeItem,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, KyoshinCardBorder, RoundedCornerShape(10.dp))
            .testTag("earthquake_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Max Shindo Badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(getShindoColorByText(item.maxScale)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "震度",
                            color = if (item.maxScale in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.maxScale,
                            color = if (item.maxScale in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Hypocenter & Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.hypocenter,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "発生日時: ${item.time}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "規模: M${item.magnitude}",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "深さ: 約${item.depthKm}km",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }
                }

                // Voice Read Aloud Button
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                        .testTag("speak_earthquake_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "音声で読み上げ",
                        tint = KyoshinPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tsunami Status Badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (item.tsunamiInfo.contains("警報") || item.tsunamiInfo.contains("注意報")) {
                    Color(0xFFDC2626).copy(alpha = 0.2f)
                } else {
                    Color(0xFF0284C7).copy(alpha = 0.15f)
                },
                border = BorderStroke(
                    1.dp,
                    if (item.tsunamiInfo.contains("警報")) Color(0xFFDC2626) else Color(0xFF0284C7)
                )
            ) {
                Text(
                    text = item.tsunamiInfo,
                    color = if (item.tsunamiInfo.contains("警報")) Color(0xFFFCA5A5) else Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // Observed Prefecture Breakdown Chips
            if (item.intensityPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.intensityPoints.take(4).forEach { pt ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF162035)
                        ) {
                            Text(
                                text = "${pt.prefecture} 震度${pt.scale}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
