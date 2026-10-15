package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.KyoshinViewModel
import com.example.ui.theme.KyoshinCardBorder
import com.example.ui.theme.KyoshinPrimary
import com.example.ui.theme.KyoshinSurface

@Composable
fun DisasterGuideScreen(
    viewModel: KyoshinViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val checklist by viewModel.checklist.collectAsState()
    val completedCount = checklist.count { it.isChecked }
    val progress = if (checklist.isNotEmpty()) completedCount.toFloat() / checklist.size else 0f

    var selectedSafetyStatus by remember { mutableStateOf("無事です。怪我はありません。") }
    var locationNote by remember { mutableStateOf("自宅におり安全です。") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(14.dp)
    ) {
        // Header
        Text(
            text = "防災マニュアル & 安否連絡",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "地震発生時の初動対応と非常持出品チェック",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Safety Message Generator Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, KyoshinCardBorder, RoundedCornerShape(10.dp))
                        .testTag("safety_message_card"),
                    colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = KyoshinPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "ワンタップ安否連絡作成", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Status Preset Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "無事です。怪我なし",
                                "軽微な被害あり",
                                "避難所へ移動中"
                            ).forEach { status ->
                                FilterChip(
                                    selected = selectedSafetyStatus.startsWith(status.take(4)),
                                    onClick = { selectedSafetyStatus = "$status。周囲の安全を確認しています。" },
                                    label = { Text(status, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KyoshinPrimary,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Message Preview Box
                        val messageToShare = "【地震安否連絡】\n状態: $selectedSafetyStatus\n居場所: $locationNote\n（強震モニターより送信）"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = messageToShare,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("安否確認", messageToShare))
                                    Toast.makeText(context, "安否メッセージをコピーしました", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("copy_safety_message_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("コピー", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, messageToShare)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "安否情報を共有"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KyoshinPrimary, contentColor = Color.Black),
                                modifier = Modifier.testTag("share_safety_message_button")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("送信・共有", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Emergency Action Cards (初動対応マニュアル)
            item {
                Text(
                    text = "緊急地震速報 受信時の行動指針",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            item {
                ActionGuidelineCard(
                    icon = Icons.Default.Home,
                    title = "屋内にいるとき（家庭・学校・職場）",
                    details = "・まずは頭部を座布団やバッグで保護する\n・頑丈な机やテーブルの下にもぐる\n・揺れが収まるまで無理に火を消そうとしない\n・ドアを開けて避難経路を確保する"
                )
            }

            item {
                ActionGuidelineCard(
                    icon = Icons.Default.DirectionsCar,
                    title = "自動車を運転中のとき",
                    details = "・急ブレーキを避け、ハザードランプを点灯\n・緩やかに減速し、道路の左側に停車する\n・エンジンを停止しキーは付けたままにする\n・ラジオ等で地震・津波情報を確認する"
                )
            }

            item {
                ActionGuidelineCard(
                    icon = Icons.Default.Elevator,
                    title = "エレベーターに乗っているとき",
                    details = "・全ての階のボタンを押し、最初に停止した階ですぐに降りる\n・閉じ込められた場合は非常用インターホンで通報する"
                )
            }

            item {
                ActionGuidelineCard(
                    icon = Icons.Default.Waves,
                    title = "海岸・河口付近にいるとき（津波警戒）",
                    details = "・揺れの大きさに関わらず、直ちに高台や避難ビルへ避難\n・津波警報・注意報が解除されるまで絶対に海に近づかない"
                )
            }

            // Checklist section
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "非常持出袋チェックリスト",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "$completedCount / ${checklist.size} 準備完了",
                            color = KyoshinPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = KyoshinPrimary,
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }

            items(checklist, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, KyoshinCardBorder, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isChecked,
                            onCheckedChange = { viewModel.toggleCheckItem(item.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = KyoshinPrimary,
                                checkmarkColor = Color.Black
                            ),
                            modifier = Modifier.testTag("checkbox_${item.id}")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = if (item.isChecked) Color(0xFF64748B) else Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.Medium
                            )
                            Text(
                                text = item.category,
                                color = Color(0xFF475569),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionGuidelineCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    details: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, KyoshinCardBorder, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = details,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
