package com.v2ir.ui.screens.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.v2ir.R
import com.v2ir.data.model.Config
import com.v2ir.data.model.ConnectionMode
import com.v2ir.data.model.ConnectionState
import com.v2ir.data.model.TrafficStats
import com.v2ir.data.model.Subscription
import com.v2ir.ui.components.GlassCard
import com.v2ir.ui.components.LiveTrafficGraph
import com.v2ir.ui.theme.ConnectedGreen
import com.v2ir.ui.theme.ConnectingYellow
import com.v2ir.ui.theme.DisconnectedGray
import com.v2ir.ui.theme.NeonCyan
import com.v2ir.ui.theme.NeonGreen
import com.v2ir.ui.theme.TextHint
import com.v2ir.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onSelectConfig: () -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(28.dp))

            ConnectionModeTabs(
                selectedMode = uiState.connectionMode,
                onModeSelected = { viewModel.setConnectionMode(it) }
            )

            if (uiState.connectionMode == ConnectionMode.MANUAL) {
                LoadBalancerToggle(
                    enabled = uiState.loadBalancerEnabled,
                    onToggle = { viewModel.toggleLoadBalancer() }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (uiState.isScanning) {
                Text(
                    text = stringResource(R.string.home_scanning),
                    style = MaterialTheme.typography.bodySmall.copy(color = ConnectingYellow)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            ConnectButton(
                state = uiState.connectionState,
                onClick = {
                    viewModel.toggleConnection { intent ->
                        vpnPermissionLauncher.launch(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ConnectionStatusText(uiState)

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.connectionState == ConnectionState.CONNECTED) {
                ConnectionInfoCard(
                    uiState = uiState,
                    onSpeedTest = { viewModel.runSpeedTest() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            LiveTrafficGraph(
                downloadHistory = uiState.downloadHistory,
                uploadHistory = uiState.uploadHistory
            )

            Spacer(modifier = Modifier.height(16.dp))

            TrafficStatsCard(stats = uiState.trafficStats)

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.connectionState != ConnectionState.CONNECTED) {
            if (uiState.connectionMode == ConnectionMode.SMART_AUTO) {
                items(uiState.subscriptions, key = { it.id }) { sub ->
                    SubscriptionGroupCard(
                        subscription = sub,
                        isSelected = sub.id == uiState.selectedSubscriptionId,
                        onClick = { viewModel.selectSubscription(sub.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                uiState.groupedConfigs.forEach { (groupName, configs) ->
                    val isExpanded = uiState.expandedGroups.contains(groupName)
                    item(key = "header_$groupName") {
                        GroupHeader(
                            name = groupName,
                            isExpanded = isExpanded,
                            count = configs.size,
                            scanProgress = uiState.groupScanProgress[groupName],
                            onClick = { viewModel.toggleGroup(groupName) }
                        )
                    }
                    if (isExpanded) {
                        items(configs, key = { it.id }) { config ->
                            ManualServerItem(
                                config = config,
                                isSelected = config.id == uiState.selectedConfig?.id,
                                isPinging = uiState.pingingConfigId == config.id,
                                onSelect = { viewModel.selectManualConfig(config) },
                                onTcpPing = { viewModel.pingConfig(config, false) },
                                onRealPing = { viewModel.pingConfig(config, true) },
                                onEdit = { onSelectConfig() },
                                onShare = { viewModel.shareConfig(config) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        } else {
            item {
                ActiveConfigCard(
                    config = uiState.selectedConfig,
                    onClick = onSelectConfig
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (uiState.showShareDialog && uiState.selectedConfig != null) {
        com.v2ir.ui.components.ShareConfigDialog(
            configName = uiState.selectedConfig!!.name,
            shareText = uiState.shareText,
            qrBitmap = uiState.shareQrBitmap,
            onDismiss = { viewModel.dismissShareDialog() }
        )
    }
}

@Composable
private fun ConnectionStatusText(uiState: HomeUiState) {
    val statusText = when (uiState.connectionState) {
        ConnectionState.CONNECTED -> stringResource(R.string.home_status_connected)
        ConnectionState.CONNECTING -> stringResource(R.string.home_status_connecting)
        ConnectionState.DISCONNECTED -> stringResource(R.string.home_status_disconnected)
    }
    val statusColor by animateColorAsState(
        targetValue = when (uiState.connectionState) {
            ConnectionState.CONNECTED -> ConnectedGreen
            ConnectionState.CONNECTING -> ConnectingYellow
            ConnectionState.DISCONNECTED -> DisconnectedGray
        },
        label = "statusColor"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium.copy(
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        )

        if (uiState.connectionState == ConnectionState.DISCONNECTED && uiState.disconnectReasonRes != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(uiState.disconnectReasonRes!!),
                style = MaterialTheme.typography.bodySmall.copy(color = com.v2ir.ui.theme.LogError),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SubscriptionGroupCard(
    subscription: Subscription,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        borderColor = if (isSelected) NeonCyan else Color.Transparent,
        borderWidth = if (isSelected) 2.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (isSelected) NeonCyan else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (subscription.bestLatency > 0) {
                    Text(
                        text = stringResource(R.string.home_best_ping, subscription.bestLatency),
                        style = MaterialTheme.typography.labelSmall.copy(color = NeonGreen)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.home_healthy_nodes, subscription.healthyCount, subscription.serverCount),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
                if (subscription.lastScanTime > 0) {
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(subscription.lastScanTime))
                    Text(
                        text = stringResource(R.string.home_last_scan, time),
                        style = MaterialTheme.typography.labelSmall.copy(color = TextHint)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionInfoCard(uiState: HomeUiState, onSpeedTest: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_connection_uptime, uiState.connectionDuration),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontFamily = FontFamily.Monospace)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow(Icons.Default.Public, stringResource(R.string.home_public_ip, uiState.ipInfo?.query ?: "---"))
                    InfoRow(Icons.Default.Language, stringResource(R.string.home_location, uiState.ipInfo?.country ?: "---"))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isp = uiState.ipInfo?.isp?.substringBefore(" ") ?: "---"
                    InfoRow(Icons.Default.SignalCellularAlt, stringResource(R.string.home_isp, isp))
                    InfoRow(
                        Icons.Default.NetworkCheck,
                        stringResource(R.string.home_ping_unit) + ": " +
                            (if (uiState.trafficStats.pingMs >= 0) uiState.trafficStats.pingMs.toString() else "---")
                    )
                }
            }

            // Speed Test row — shows result when available, spinner while testing
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (uiState.speedTestResult != null) {
                    Text(
                        text = formatSpeed(uiState.speedTestResult, "KB/s", "MB/s"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = NeonGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (uiState.isSpeedTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.configs_speed_test) + "...",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextHint),
                        fontSize = 12.sp
                    )
                } else {
                    TextButton(
                        onClick = onSpeedTest,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.configs_speed_test), fontSize = 12.sp, color = NeonCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextHint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary), maxLines = 1)
    }
}

@Composable
private fun GroupHeader(
    name: String,
    isExpanded: Boolean,
    count: Int,
    scanProgress: Float? = null,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall.copy(color = Color.White)
                    )
                }
                Text(
                    text = "$count servers",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextHint)
                )
            }
            if (scanProgress != null && scanProgress > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { scanProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = NeonCyan,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@Composable
private fun ManualServerItem(
    config: Config,
    isSelected: Boolean,
    isPinging: Boolean,
    onSelect: (Config) -> Unit,
    onTcpPing: (Config) -> Unit,
    onRealPing: (Config) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isSelected) NeonCyan else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(config) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isSelected) NeonCyan else Color.White
                        )
                    )
                    ProtocolTag(config.type.name)
                    if (config.isCloudflare) {
                        Text(
                            text = " ${stringResource(R.string.configs_cloudflare_icon)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                val latencyText = when {
                    config.realLatency >= 0 -> stringResource(R.string.home_latency_ms, config.realLatency)
                    config.tcpLatency >= 0 -> stringResource(R.string.home_latency_ms, config.tcpLatency)
                    else -> stringResource(R.string.home_latency_unavailable)
                }
                Text(text = latencyText, style = MaterialTheme.typography.labelSmall.copy(color = TextHint))
            }
            if (isPinging) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonCyan)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShare, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { onRealPing(config) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = "Real Delay", tint = NeonCyan, modifier = Modifier.size(14.dp))
                    }
                    TextButton(onClick = { onTcpPing(config) }, contentPadding = PaddingValues(4.dp), modifier = Modifier.height(24.dp)) {
                        Text(stringResource(R.string.home_ping_tcp), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolTag(protocol: String) {
    Surface(
        color = NeonCyan.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Text(
            text = protocol,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                color = NeonCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun ConnectionModeTabs(
    selectedMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit
) {
    val selectedIndex = if (selectedMode == ConnectionMode.SMART_AUTO) 0 else 1

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = Color.Transparent,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                Box(
                    modifier = Modifier
                        .tabIndicatorOffset(tabPositions[selectedIndex])
                        .height(2.dp)
                        .padding(horizontal = 16.dp)
                        .border(0.dp, NeonCyan, RoundedCornerShape(1.dp))
                )
            }
        ) {
            Tab(
                selected = selectedIndex == 0,
                onClick = { onModeSelected(ConnectionMode.SMART_AUTO) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.home_mode_auto))
                    }
                },
                selectedContentColor = NeonCyan,
                unselectedContentColor = TextSecondary
            )
            Tab(
                selected = selectedIndex == 1,
                onClick = { onModeSelected(ConnectionMode.MANUAL) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.home_mode_manual))
                    }
                },
                selectedContentColor = NeonCyan,
                unselectedContentColor = TextSecondary
            )
        }
    }
}

@Composable
private fun ConnectButton(
    state: ConnectionState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == ConnectionState.CONNECTING) 1.08f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> ConnectedGreen
            ConnectionState.CONNECTING -> ConnectingYellow
            ConnectionState.DISCONNECTED -> NeonCyan
        },
        label = "buttonColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(160.dp)
            .scale(pulseScale)
            .border(2.dp, buttonColor.copy(alpha = 0.3f), CircleShape)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .border(1.5f.dp, buttonColor.copy(alpha = 0.6f), CircleShape)
        ) {
            GlassCard(
                modifier = Modifier
                    .size(120.dp)
                    .clickable { onClick() },
                shape = CircleShape,
                borderColor = buttonColor,
                backgroundAlpha = 0.2f
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = stringResource(
                            if (state == ConnectionState.CONNECTED) R.string.home_disconnect else R.string.home_connect
                        ),
                        tint = buttonColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficStatsCard(stats: TrafficStats) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(Icons.Default.Speed, stringResource(R.string.home_download), formatSpeed(stats.downloadSpeed, stringResource(R.string.home_speed_unit_kb), stringResource(R.string.home_speed_unit_mb)), NeonCyan)
                StatItem(Icons.Default.SignalCellularAlt, stringResource(R.string.home_upload), formatSpeed(stats.uploadSpeed, stringResource(R.string.home_speed_unit_kb), stringResource(R.string.home_speed_unit_mb)), NeonGreen)
                StatItem(
                    Icons.Default.NetworkCheck,
                    stringResource(R.string.home_ping),
                    if (stats.pingMs >= 0) stringResource(R.string.home_latency_ms, stats.pingMs)
                    else stringResource(R.string.home_latency_unavailable),
                    NeonCyan
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.home_total_used), style = MaterialTheme.typography.labelSmall.copy(color = TextHint))
                Text(
                    text = formatBytes(stats.downloadBytes + stats.uploadBytes),
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(color = TextHint))
    }
}

@Composable
private fun LoadBalancerToggle(
    enabled: Boolean,
    onToggle: () -> Unit
) {
    ToggleCard(
        title = stringResource(R.string.home_load_balancer),
        description = stringResource(R.string.home_load_balancer_desc),
        icon = Icons.Default.SignalCellularAlt,
        enabled = enabled,
        onToggle = onToggle
    )
}

@Composable
private fun ToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) NeonCyan else TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (enabled) NeonCyan else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextHint)
                    )
                }
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { onToggle() },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = NeonCyan,
                    checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun ActiveConfigCard(config: Config?, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_active_config), style = MaterialTheme.typography.labelSmall.copy(color = TextHint))
                Text(
                    text = config?.name ?: stringResource(R.string.home_no_config_selected),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (config != null) NeonCyan else Color.White
                    )
                )
                if (config != null) {
                    val location = when {
                        config.countryLabel.isNotBlank() -> config.countryLabel
                        config.address.isNotBlank() -> config.address
                        else -> null
                    }
                    if (location != null) {
                        Text(
                            text = "${stringResource(R.string.home_server_location)}: $location",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
            Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.home_select_config), tint = TextSecondary)
        }
    }
}

private fun formatSpeed(kbps: Float, kbUnit: String, mbUnit: String): String {
    return if (kbps >= 1024f) {
        String.format("%.1f %s", kbps / 1024f, mbUnit)
    } else {
        String.format("%.0f %s", kbps, kbUnit)
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824f)
        bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576f)
        else -> String.format("%d KB", bytes / 1024)
    }
}




