package com.v2ir.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.v2ir.R
import com.v2ir.data.model.CloudflareScanResult
import com.v2ir.data.model.Config
import com.v2ir.data.model.ScanPhase
import com.v2ir.data.model.ScanStage
import com.v2ir.data.model.ScanStatistics
import com.v2ir.data.scanner.IpVersion
import com.v2ir.data.scanner.ScannerProfile
import com.v2ir.ui.components.GlassCard
import com.v2ir.ui.theme.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudflareScannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: CloudflareScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cloudflareConfigs by viewModel.cloudflareConfigs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = CloudflareOrange, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.configs_cf_scanner_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                actions = {
                    if (uiState.results.isNotEmpty()) {
                        var exportExpanded by remember { mutableStateOf(false) }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Box {
                            IconButton(onClick = { exportExpanded = true }) {
                                Icon(Icons.Default.Download, contentDescription = "Export", tint = NeonCyan)
                            }
                            DropdownMenu(expanded = exportExpanded, onDismissRequest = { exportExpanded = false }) {
                                DropdownMenuItem(text = { Text("Export TXT") }, onClick = { 
                                    shareText(context, viewModel.exportResults("TXT"))
                                    exportExpanded = false 
                                })
                                DropdownMenuItem(text = { Text("Export CSV") }, onClick = { 
                                    shareText(context, viewModel.exportResults("CSV"))
                                    exportExpanded = false 
                                })
                                DropdownMenuItem(text = { Text("Export JSON") }, onClick = { 
                                    shareText(context, viewModel.exportResults("JSON"))
                                    exportExpanded = false 
                                })
                            }
                        }
                        IconButton(onClick = { viewModel.clearResults() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            ProfessionalScannerSettings(
                configs = cloudflareConfigs,
                uiState = uiState,
                onConfigSelected = { viewModel.onConfigSelected(it) },
                onConcurrencyChanged = { viewModel.onConcurrencyChanged(it) },
                onXrayConcurrencyChanged = { viewModel.onXrayConcurrencyChanged(it) },
                onIpVersionChanged = { viewModel.onIpVersionChanged(it) },
                onProfileChanged = { viewModel.onProfileChanged(it) },
                onStart = { viewModel.startScan() },
                onPause = { viewModel.pauseScan() },
                onResume = { viewModel.resumeScan() },
                onNext = { viewModel.proceedToValidation() },
                onStop = { viewModel.stopScan() }
            )

            if (uiState.isScanning || uiState.statistics.elapsedTimeMs > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                ScanStatisticsDashboard(uiState.statistics, uiState.phase)
            }

            if (uiState.results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                BestResultHighlight(uiState.results.first())
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            ResultTableHeader(
                sortColumn = uiState.sortColumn,
                sortAscending = uiState.sortAscending,
                onSort = { viewModel.toggleSort(it) }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Show active scans first if scanning
                if (uiState.isScanning) {
                    val activeList = uiState.activeScans.values
                        .filter { 
                            it.stage != ScanStage.COMPLETED && it.stage != ScanStage.FAILED &&
                            (uiState.phase != ScanPhase.DISCOVERY || it.isAlive)
                        }
                        .sortedByDescending { it.timestamp }
                    
                    items(activeList, key = { "active_${it.ip}" }) { result ->
                        ResultTableRow(
                            result = result,
                            isSelected = false,
                            onClick = { }
                        )
                    }
                }

                items(uiState.results, key = { it.ip }) { result ->
                    ResultTableRow(
                        result = result,
                        isSelected = uiState.selectedIp == result.ip,
                        onClick = { viewModel.selectIp(result.ip) }
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.selectedIp != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                ActionButtons(
                    onApplyToSelected = { viewModel.applyToSelectedConfig() },
                    onApplyToAll = { viewModel.applyToAllCloudflareConfigs() }
                )
            }
        }
    }
}

@Composable
private fun ProfessionalScannerSettings(
    configs: List<Config>,
    uiState: ScannerUiState,
    onConfigSelected: (Config) -> Unit,
    onConcurrencyChanged: (Int) -> Unit,
    onXrayConcurrencyChanged: (Int) -> Unit,
    onIpVersionChanged: (IpVersion) -> Unit,
    onProfileChanged: (ScannerProfile) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var configExpanded by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_active_config), style = MaterialTheme.typography.labelSmall, color = TextHint)
                    Box {
                        Text(
                            text = uiState.selectedConfig?.name ?: "Select Config...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { if (uiState.phase == ScanPhase.IDLE) configExpanded = true }.padding(vertical = 4.dp)
                        )
                        DropdownMenu(expanded = configExpanded, onDismissRequest = { configExpanded = false }) {
                            configs.forEach { config ->
                                DropdownMenuItem(text = { Text(config.name) }, onClick = { onConfigSelected(config); configExpanded = false })
                            }
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.Settings, contentDescription = null, tint = NeonCyan)
                    }

                    if (uiState.isScanning) {
                        IconButton(onClick = { if (uiState.isPaused) onResume() else onPause() }) {
                            Icon(
                                imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color.Yellow
                            )
                        }
                    }

                    val isDiscovery = uiState.phase == ScanPhase.DISCOVERY
                    val isValidation = uiState.phase == ScanPhase.VALIDATION
                    val isPaused = uiState.isPaused

                    Button(
                        onClick = {
                            when {
                                isDiscovery -> onNext()
                                isValidation || isPaused -> onStop()
                                else -> onStart()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isValidation || isPaused -> Color.Red.copy(alpha = 0.7f)
                                isDiscovery -> NeonCyan
                                else -> CloudflareOrange
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val icon = when {
                            isValidation || isPaused -> Icons.Default.Stop
                            isDiscovery -> Icons.AutoMirrored.Filled.ArrowForward
                            else -> Icons.Default.PlayArrow
                        }
                        val text = when {
                            isValidation || isPaused -> stringResource(R.string.configs_cf_stop_scan)
                            isDiscovery -> stringResource(R.string.configs_cf_next_step)
                            else -> stringResource(R.string.configs_cf_start_scan)
                        }
                        val textColor = if (isDiscovery) DeepNavy else Color.White

                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = textColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text, fontSize = 11.sp, color = textColor)
                    }
                }
            }

            if (uiState.isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = if (uiState.phase == ScanPhase.VALIDATION) {
                    if (uiState.statistics.totalTargets > 0) uiState.statistics.processed.toFloat() / uiState.statistics.totalTargets.toFloat() else 0f
                } else {
                    -1f // Indeterminate
                }
                
                if (progress >= 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = CloudflareOrange,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonCyan,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (uiState.phase == ScanPhase.VALIDATION) 
                            stringResource(R.string.configs_cf_status_validation, uiState.statistics.processed, uiState.statistics.totalTargets)
                        else 
                            stringResource(R.string.configs_cf_status_discovery, uiState.statistics.processed, uiState.results.count { it.isAlive }),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHint
                    )
                    if (uiState.isPaused) {
                        Text("PAUSED", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.configs_cf_concurrency_discovery), style = MaterialTheme.typography.labelSmall, color = TextHint)
                            Slider(
                                value = uiState.concurrency.toFloat(),
                                onValueChange = { onConcurrencyChanged(it.toInt()) },
                                valueRange = 1f..200f,
                                enabled = !uiState.isScanning,
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                            )
                            Text("${uiState.concurrency}", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.configs_cf_concurrency_xray), style = MaterialTheme.typography.labelSmall, color = TextHint)
                            Slider(
                                value = uiState.xrayConcurrency.toFloat(),
                                onValueChange = { onXrayConcurrencyChanged(it.toInt()) },
                                valueRange = 1f..50f,
                                enabled = !uiState.isScanning,
                                colors = SliderDefaults.colors(thumbColor = CloudflareOrange, activeTrackColor = CloudflareOrange)
                            )
                            Text("${uiState.xrayConcurrency}", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    
                    if (uiState.xrayConcurrency > 20) {
                        Text(
                            text = stringResource(R.string.configs_cf_warning_high_concurrency),
                            color = Color.Yellow.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelSmall, color = TextHint)
                        IpVersionChip("IPv4", uiState.ipVersion == IpVersion.IPV4) { onIpVersionChanged(IpVersion.IPV4) }
                        IpVersionChip("IPv6", uiState.ipVersion == IpVersion.IPV6) { onIpVersionChanged(IpVersion.IPV6) }
                        IpVersionChip("Both", uiState.ipVersion == IpVersion.BOTH) { onIpVersionChanged(IpVersion.BOTH) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BestResultHighlight(result: CloudflareScanResult) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = NeonCyan.copy(alpha = 0.3f),
        borderWidth = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(NeonCyan.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("BEST ENDPOINT", fontSize = 10.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                Text(result.ip, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(result.edgeLocation.ifBlank { "Optimized Route" }, fontSize = 11.sp, color = TextHint)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${result.latencyMs}ms", fontSize = 18.sp, color = NeonGreen, fontWeight = FontWeight.ExtraBold)
                Text("Score: ${result.finalScore}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ResultTableHeader(
    sortColumn: String,
    sortAscending: Boolean,
    onSort: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.configs_cf_column_ip), modifier = Modifier.weight(3f), fontSize = 10.sp, color = TextHint, fontWeight = FontWeight.Bold)
        TableColumnHeader(stringResource(R.string.configs_cf_column_ping), 1.5f, sortColumn == "PING", sortAscending) { onSort("PING") }
        TableColumnHeader(stringResource(R.string.configs_cf_column_speed), 1.5f, sortColumn == "SPEED", sortAscending) { onSort("SPEED") }
        TableColumnHeader(stringResource(R.string.configs_cf_column_score), 1.2f, sortColumn == "SCORE", sortAscending) { onSort("SCORE") }
    }
}

@Composable
private fun RowScope.TableColumnHeader(
    label: String,
    weight: Float,
    isSorted: Boolean,
    ascending: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.weight(weight).clickable { onClick() }.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(label, fontSize = 10.sp, color = if (isSorted) NeonCyan else TextHint, fontWeight = FontWeight.Bold, maxLines = 1)
        if (isSorted) {
            Icon(
                if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = NeonCyan
            )
        }
    }
}

@Composable
private fun ScanStatisticsDashboard(stats: ScanStatistics, phase: ScanPhase) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatItemCompact("Best", if (stats.bestLatency > 0) "${stats.bestLatency}ms" else "-", NeonGreen)
                StatItemCompact("Success", stats.successCount.toString(), NeonCyan)
                StatItemCompact("Fail", stats.failureCount.toString(), Color.Red)
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (stats.etaSeconds > 0) formatDuration(stats.etaSeconds) else "--:--", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    Text(String.format("%.1f/s", stats.processingRate), fontSize = 8.sp, color = TextHint)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CellTower, contentDescription = null, tint = TextHint, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Scanning: ${stats.currentIp.ifBlank { "..." }}",
                    fontSize = 10.sp,
                    color = TextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Workers: ${stats.activeWorkers}",
                    fontSize = 10.sp,
                    color = TextHint
                )
            }

            if (stats.currentRange.isNotBlank()) {
                Text(
                    text = "Range: ${stats.currentRange}",
                    fontSize = 10.sp,
                    color = TextHint.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatItemCompact(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 7.sp, color = TextHint)
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

@Composable
private fun ResultTableRow(
    result: CloudflareScanResult,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NeonCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(3f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.ip,
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (result.ipVersion == 6) {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text("v6", fontSize = 8.sp, color = TextHint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = result.edgeLocation.ifBlank { result.errorMessage ?: "Cloudflare Edge" },
                        fontSize = 10.sp,
                        color = if (result.errorMessage != null) Color.Red else TextHint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StageIndicator(result.stage)
                }
            }

            Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (result.latencyMs > 0) "${result.latencyMs}ms" else "-",
                    fontSize = 12.sp,
                    color = getLatencyColor(result.latencyMs),
                    fontWeight = FontWeight.Bold
                )
                Text("Latency", fontSize = 8.sp, color = TextHint)
            }

            Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (result.downloadSpeed > 0) String.format("%.1f", result.downloadSpeed / 1024f) else "-",
                    fontSize = 12.sp,
                    color = CloudflareOrange,
                    fontWeight = FontWeight.Bold
                )
                Text("MB/s", fontSize = 8.sp, color = TextHint)
            }

            Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                if (result.stage == ScanStage.COMPLETED) {
                    CircularScoreIndicator(result.finalScore)
                } else if (result.stage == ScanStage.FAILED) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NeonCyan)
                }
            }
        }
    }
}

@Composable
private fun CircularScoreIndicator(score: Int) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.size(28.dp),
            color = getScoreColor(score),
            strokeWidth = 3.dp,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        Text(
            text = score.toString(),
            fontSize = 9.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StageIndicator(stage: ScanStage) {
    val color = when (stage) {
        ScanStage.COMPLETED -> NeonGreen
        ScanStage.FAILED -> Color.Red
        else -> NeonCyan
    }
    
    val text = when (stage) {
        ScanStage.IDLE -> ""
        ScanStage.PENDING -> "Pending"
        ScanStage.TCP_CONNECT -> stringResource(R.string.configs_cf_stage_tcp)
        ScanStage.TLS_HANDSHAKE -> stringResource(R.string.configs_cf_stage_tls)
        ScanStage.HTTP_VALIDATION -> stringResource(R.string.configs_cf_stage_http)
        ScanStage.CLOUDFLARE_VALIDATION -> "CF"
        ScanStage.WS_VALIDATION -> "WS"
        ScanStage.NEIGHBOR_DISCOVERY -> "Neighbor"
        ScanStage.STABILITY_CHECK -> stringResource(R.string.configs_cf_stage_stability)
        ScanStage.XRAY_TRANSPORT -> "Xray"
        ScanStage.LATENCY_MEASURE -> "Latency"
        ScanStage.SPEED_TEST -> "Speed"
        ScanStage.COMPLETED -> ""
        ScanStage.FAILED -> "Fail"
    }

    if (text.isNotEmpty()) {
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text(text, fontSize = 7.sp, color = color, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
        }
    } else if (stage == ScanStage.COMPLETED) {
        Icon(Icons.Default.Check, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun IpVersionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) NeonCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, if (selected) NeonCyan else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = if (selected) NeonCyan else TextSecondary)
    }
}

@Composable
private fun ActionButtons(onApplyToSelected: () -> Unit, onApplyToAll: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onApplyToSelected, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) {
            Text(stringResource(R.string.configs_cf_apply_selected), color = DeepNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        OutlinedButton(onClick = onApplyToAll, modifier = Modifier.weight(1f), border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan), colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)) {
            Text(stringResource(R.string.configs_cf_apply_all), fontSize = 11.sp)
        }
    }
}

private fun getLatencyColor(latency: Long): Color {
    return when {
        latency < 150 -> NeonGreen
        latency < 300 -> Color.Yellow
        else -> Color.Red
    }
}

private fun getScoreColor(score: Int): Color {
    return when {
        score >= 80 -> NeonGreen
        score >= 50 -> Color.Yellow
        else -> Color.Red
    }
}

private fun shareText(context: android.content.Context, text: String) {
    if (text.isBlank()) return
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Export Results"))
}




