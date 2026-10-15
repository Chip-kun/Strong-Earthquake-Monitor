package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.EEWNotificationEntity
import com.example.model.EarthquakeItem
import com.example.model.getShindoColorByText
import com.example.ui.KyoshinViewModel
import com.example.ui.theme.KyoshinAccentRed
import com.example.ui.theme.KyoshinCardBorder
import com.example.ui.theme.KyoshinPrimary
import com.example.ui.theme.KyoshinSurface
import com.example.ui.components.EarthquakeHistoryMapCanvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistorySubTab {
    EEW_LOGS,
    HISTORY_MAP,
    P2P_HISTORY
}

@Composable
fun EarthquakeHistoryScreen(
    viewModel: KyoshinViewModel,
    modifier: Modifier = Modifier
) {
    val earthquakes by viewModel.repository.earthquakes.collectAsState()
    val eewLogs by viewModel.eewLogs.collectAsState()
    val selectedHistoryLog by viewModel.selectedHistoryLog.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()

    var selectedSubTab by remember { mutableStateOf(HistorySubTab.EEW_LOGS) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showAddSimulationDialog by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "地震・速報ログ管理",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (selectedSubTab) {
                        HistorySubTab.EEW_LOGS -> "Roomデータベースによる緊急地震速報のローカル永続保存ログ"
                        HistorySubTab.HISTORY_MAP -> "Room保存の過去地震・震源地プロット地図（タップで詳細確認）"
                        HistorySubTab.P2P_HISTORY -> "気象庁・P2P地震情報 リアルタイム震度履歴"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            if (selectedSubTab == HistorySubTab.P2P_HISTORY) {
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
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { viewModel.importP2PEarthquakesToRoom() },
                        modifier = Modifier.testTag("sync_p2p_to_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "P2P履歴を同期保存",
                            tint = KyoshinPrimary
                        )
                    }
                    IconButton(
                        onClick = { showAddSimulationDialog = true },
                        modifier = Modifier.testTag("add_simulation_eew_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "速報模擬追加",
                            tint = KyoshinPrimary
                        )
                    }
                    if (eewLogs.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_all_eew_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "全消去",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
        }

        // Sub-tabs: EEW logs (Room) vs History Map (Room) vs P2P Seismic Reports
        TabRow(
            selectedTabIndex = when (selectedSubTab) {
                HistorySubTab.EEW_LOGS -> 0
                HistorySubTab.HISTORY_MAP -> 1
                HistorySubTab.P2P_HISTORY -> 2
            },
            containerColor = KyoshinSurface,
            contentColor = KyoshinPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, KyoshinCardBorder, RoundedCornerShape(8.dp))
                .testTag("history_sub_tabs")
        ) {
            Tab(
                selected = selectedSubTab == HistorySubTab.EEW_LOGS,
                onClick = { selectedSubTab = HistorySubTab.EEW_LOGS },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "速報ログ (${eewLogs.size})",
                            fontWeight = if (selectedSubTab == HistorySubTab.EEW_LOGS) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                },
                modifier = Modifier.testTag("tab_eew_logs")
            )
            Tab(
                selected = selectedSubTab == HistorySubTab.HISTORY_MAP,
                onClick = { selectedSubTab = HistorySubTab.HISTORY_MAP },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "履歴マップ (${eewLogs.size})",
                            fontWeight = if (selectedSubTab == HistorySubTab.HISTORY_MAP) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                },
                modifier = Modifier.testTag("tab_history_map")
            )
            Tab(
                selected = selectedSubTab == HistorySubTab.P2P_HISTORY,
                onClick = { selectedSubTab = HistorySubTab.P2P_HISTORY },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "震度履歴 (${earthquakes.size})",
                            fontWeight = if (selectedSubTab == HistorySubTab.P2P_HISTORY) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                },
                modifier = Modifier.testTag("tab_p2p_history")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedSubTab) {
            HistorySubTab.EEW_LOGS -> {
                if (eewLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "EEW速報ログはまだありません",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "緊急地震速報を受信すると、Roomローカルデータベースに自動保存されます。",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showAddSimulationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = KyoshinPrimary),
                                    modifier = Modifier.testTag("empty_add_simulation_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("模擬速報を追加", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.importP2PEarthquakesToRoom() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, KyoshinCardBorder),
                                    modifier = Modifier.testTag("empty_sync_p2p_button")
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = KyoshinPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("P2P履歴を同期", color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("eew_logs_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(eewLogs, key = { it.id }) { log ->
                            EEWNotificationCard(
                                log = log,
                                onSpeak = { viewModel.announceEEWLog(log) },
                                onDelete = { viewModel.deleteEEWLog(log) },
                                onViewOnMap = {
                                    viewModel.selectHistoryLog(log)
                                    selectedSubTab = HistorySubTab.HISTORY_MAP
                                }
                            )
                        }
                    }
                }
            }

            HistorySubTab.HISTORY_MAP -> {
                if (eewLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "プロット可能な地震ログがありません",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Roomデータベースに保存された地震情報を元に、過去の震源地を地図上にプロットして振り返ることができます。",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showAddSimulationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = KyoshinPrimary),
                                    modifier = Modifier.testTag("empty_map_add_simulation_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("模擬速報を追加", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.importP2PEarthquakesToRoom() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, KyoshinCardBorder),
                                    modifier = Modifier.testTag("empty_map_sync_p2p_button")
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = KyoshinPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("P2P履歴を同期", color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    EarthquakeHistoryMapCanvas(
                        eewLogs = eewLogs,
                        selectedLog = selectedHistoryLog,
                        onLogSelected = { viewModel.selectHistoryLog(it) },
                        userLocation = userLocation,
                        onSpeakLog = { viewModel.announceEEWLog(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            HistorySubTab.P2P_HISTORY -> {
                if (earthquakes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = KyoshinPrimary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("p2p_earthquakes_list"),
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
    }

    // Confirmation dialog for clearing all Room EEW logs
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("EEW速報ログの全消去") },
            text = { Text("Roomデータベースに保存されている全ての緊急地震速報ログ（${eewLogs.size}件）を消去しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllEEWLogs()
                        showClearDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_eew_dialog_button")
                ) {
                    Text("消去する", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // Dialog for adding simulated/test EEW notification to Room database
    if (showAddSimulationDialog) {
        SimulationEEWDialog(
            onDismiss = { showAddSimulationDialog = false },
            onConfirm = { hypocenter, maxShindo, mag, depth, isWarning, isDrill, lat, lon ->
                viewModel.insertSampleEEWLog(hypocenter, maxShindo, mag, depth, isWarning, isDrill, lat, lon)
                showAddSimulationDialog = false
            }
        )
    }
}

@Composable
fun EEWNotificationCard(
    log: EEWNotificationEntity,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onViewOnMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val receivedDateStr = remember(log.receivedAt) {
        val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.JAPAN)
        sdf.format(Date(log.receivedAt))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (log.isWarning) KyoshinAccentRed.copy(alpha = 0.6f) else KyoshinCardBorder,
                RoundedCornerShape(10.dp)
            )
            .testTag("eew_log_card_${log.id}"),
        colors = CardDefaults.cardColors(containerColor = KyoshinSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Warning badge + Report number + Save timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            log.isDrill -> Color(0xFF2563EB)
                            log.isWarning -> KyoshinAccentRed
                            else -> Color(0xFFF59E0B)
                        }
                    ) {
                        Text(
                            text = when {
                                log.isDrill -> "緊急地震速報 (訓練)"
                                log.isWarning -> "緊急地震速報 (警報)"
                                else -> "緊急地震速報 (予報)"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = log.reportNum,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "記録: $receivedDateStr",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle Row: Shindo Box + Hypocenter + Mag/Depth
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Max Shindo Badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(getShindoColorByText(log.maxShindo)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "最大震度",
                            color = if (log.maxShindo in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = log.maxShindo,
                            color = if (log.maxShindo in listOf("1", "2", "3", "4")) Color.Black else Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.hypocenter,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "発生時刻: ${log.originTime}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "規模: M${log.magnitude}",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "深さ: 約${log.depthKm}km",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }
                }

                // Action Buttons: View on Map, TTS Audio & Delete
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onViewOnMap,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .testTag("view_map_eew_log_${log.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "地図で確認",
                            tint = KyoshinPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .testTag("speak_eew_log_${log.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "音声読み上げ",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .testTag("delete_eew_log_${log.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "ログ削除",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Affected Prefectures / Areas
            if (log.affectedAreas.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF162035)
                ) {
                    Text(
                        text = "警戒地域: ${log.affectedAreas}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SimulationEEWDialog(
    onDismiss: () -> Unit,
    onConfirm: (hypocenter: String, maxShindo: String, mag: Float, depth: Int, isWarning: Boolean, isDrill: Boolean, lat: Double, lon: Double) -> Unit
) {
    var selectedPreset by remember { mutableIntStateOf(0) }

    data class PresetItem(
        val name: String,
        val shindo: String,
        val mag: Float,
        val depth: Int,
        val lat: Double,
        val lon: Double,
        val desc: String
    )

    val presets = listOf(
        PresetItem("能登半島沖（令和6年能登半島地震）", "7", 7.6f, 16, 37.50, 137.20, "日本海側最大級 / 大津波警報"),
        PresetItem("首都直下（東京湾北部）", "6強", 7.3f, 30, 35.65, 139.85, "首都圏直下 / 関東広域警報"),
        PresetItem("日向灘地震", "6弱", 7.1f, 30, 31.80, 131.70, "南海トラフ臨時情報対象"),
        PresetItem("福島県沖地震", "6強", 7.4f, 57, 37.70, 141.60, "東北太平洋沿岸"),
        PresetItem("千葉県東方沖", "5弱", 6.2f, 40, 35.40, 140.50, "関東東部沿岸"),
        PresetItem("熊本地震（本震）", "7", 7.3f, 12, 32.78, 130.74, "九州内陸直下型"),
        PresetItem("北海道胆振東部地震", "7", 6.7f, 37, 42.69, 142.00, "北海道震度7観測")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模擬EEW速報をRoomに記録") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Roomデータベースに保存し、履歴マップにプロットする震源を選択:",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(10.dp))
                presets.forEachIndexed { index, preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedPreset == index) Color(0xFF1E293B) else Color.Transparent)
                            .clickable { selectedPreset = index }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPreset == index,
                            onClick = { selectedPreset = index }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = preset.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "震度${preset.shindo} / M${preset.mag} / 深さ${preset.depth}km (${preset.desc})",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = presets[selectedPreset]
                    onConfirm(
                        p.name,
                        p.shindo,
                        p.mag,
                        p.depth,
                        true,
                        false,
                        p.lat,
                        p.lon
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = KyoshinPrimary),
                modifier = Modifier.testTag("confirm_simulation_eew_button")
            ) {
                Text("Roomに記録保存", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
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

            // Observed Prefecture Breakdown Chips & Detailed Municipalities
            if (item.intensityPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

                    // Detailed Cities observing max scale
                    val maxCities = item.intensityPoints
                        .filter { it.scale == item.maxScale }
                        .flatMap { it.cities }
                        .distinct()

                    if (maxCities.isNotEmpty()) {
                        Text(
                            text = "観測市町村: ${maxCities.take(6).joinToString("、")}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    } else if (item.detailedRegions.isNotEmpty()) {
                        Text(
                            text = "詳しい地域: ${item.detailedRegions.take(5).joinToString("、")}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            } else if (item.detailedRegions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "詳しい観測地域: ${item.detailedRegions.take(6).joinToString("、")}",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                )
            }
        }
    }
}
