package com.v2ir.ui.screens.settings

import androidx.compose.material.icons.filled.Check
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.v2ir.R
import com.v2ir.ui.components.GlassCard
import com.v2ir.ui.screens.configs.CloudflareScannerSection
import com.v2ir.ui.screens.configs.ConfigsViewModel
import com.v2ir.ui.theme.CloudflareOrange
import com.v2ir.ui.theme.NeonCyan
import com.v2ir.ui.theme.NeonGreen
import com.v2ir.ui.theme.TextHint
import com.v2ir.ui.theme.TextSecondary

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@Composable
fun SettingsScreen(
    onNavigateToLogs: () -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val configsViewModel: ConfigsViewModel = hiltViewModel()
    val configsUiState by configsViewModel.uiState.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showMtuDialog by remember { mutableStateOf(false) }
    var showScanConcurrencyDialog by remember { mutableStateOf(false) }
    var showScanIntervalDialog by remember { mutableStateOf(false) }
    var showBalancerStrategyDialog by remember { mutableStateOf(false) }
    var showLogLevelDialog by remember { mutableStateOf(false) }

    if (showLogLevelDialog) {
        LogLevelDialog(
            currentLevel = settings.logLevel,
            onDismiss = { showLogLevelDialog = false },
            onSelect = {
                viewModel.setLogLevel(it)
                showLogLevelDialog = false
            }
        )
    }

    if (showBalancerStrategyDialog) {
        BalancerStrategyDialog(
            currentStrategy = settings.balancerStrategy,
            onDismiss = { showBalancerStrategyDialog = false },
            onSelect = {
                viewModel.setBalancerStrategy(it)
                showBalancerStrategyDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = settings.language,
            onDismiss = { showLanguageDialog = false },
            onSelect = { 
                viewModel.setLanguage(it)
                showLanguageDialog = false
            }
        )
    }

    if (showDnsDialog) {
        InputDialog(
            title = stringResource(R.string.settings_dns_label),
            initialValue = settings.dnsServer,
            onDismiss = { showDnsDialog = false },
            onSave = { 
                viewModel.setDnsServer(it)
                showDnsDialog = false
            }
        )
    }

    if (showMtuDialog) {
        InputDialog(
            title = stringResource(R.string.settings_mtu_label),
            initialValue = settings.mtu.toString(),
            onDismiss = { showMtuDialog = false },
            onSave = { 
                it.toIntOrNull()?.let { mtu -> viewModel.setMtu(mtu) }
                showMtuDialog = false
            },
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
    }

    if (showScanConcurrencyDialog) {
        InputDialog(
            title = stringResource(R.string.settings_scan_concurrency),
            initialValue = settings.scanConcurrency.toString(),
            onDismiss = { showScanConcurrencyDialog = false },
            onSave = { 
                it.toIntOrNull()?.let { count -> viewModel.setScanConcurrency(count) }
                showScanConcurrencyDialog = false
            },
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
    }

    if (showScanIntervalDialog) {
        InputDialog(
            title = stringResource(R.string.settings_scan_interval),
            initialValue = settings.scanIntervalMinutes.toString(),
            onDismiss = { showScanIntervalDialog = false },
            onSave = { 
                it.toIntOrNull()?.let { min -> viewModel.setScanInterval(min) }
                showScanIntervalDialog = false
            },
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium.copy(color = NeonCyan)
        )

        SettingsSection(title = stringResource(R.string.settings_general)) {
            SettingsClickableRow(
                icon = Icons.Default.Terminal,
                label = stringResource(R.string.nav_logs),
                description = stringResource(R.string.logs_subtitle),
                onClick = onNavigateToLogs,
                accentColor = NeonCyan
            )
            SettingsDivider()
            SettingsToggleRow(
                icon = Icons.Default.FlashAuto,
                label = stringResource(R.string.settings_auto_connect),
                checked = settings.autoConnect,
                onCheckedChange = { viewModel.setAutoConnect(it) }
            )
            SettingsDivider()
            SettingsClickableRow(
                icon = Icons.Default.Language,
                label = stringResource(R.string.settings_language),
                description = if (settings.language == "fa") "فارسی" else "English",
                onClick = { showLanguageDialog = true },
                accentColor = Color.White
            )
            SettingsDivider()
            SettingsInfoRow(
                icon = Icons.Default.DarkMode,
                label = stringResource(R.string.settings_theme),
                value = stringResource(R.string.settings_theme_dark)
            )
            SettingsDivider()
            SettingsClickableRow(
                icon = Icons.Default.Terminal,
                label = "Xray Log Level",
                description = settings.logLevel,
                onClick = { showLogLevelDialog = true },
                accentColor = NeonCyan
            )
        }

        SettingsSection(title = stringResource(R.string.settings_routing)) {
            SettingsToggleRow(
                icon = Icons.Default.Router,
                label = stringResource(R.string.settings_bypass_lan),
                checked = settings.bypassLan,
                onCheckedChange = { viewModel.setBypassLan(it) }
            )
            SettingsDivider()
            SettingsToggleRow(
                icon = Icons.Default.Flag,
                label = stringResource(R.string.settings_bypass_iran),
                description = stringResource(R.string.settings_bypass_iran_desc),
                checked = settings.bypassIran,
                onCheckedChange = { viewModel.setBypassIran(it) },
                accentColor = NeonCyan
            )
            SettingsDivider()
            SettingsToggleRow(
                icon = Icons.Default.Balance,
                label = stringResource(R.string.settings_load_balance),
                checked = settings.loadBalancerEnabled,
                onCheckedChange = { viewModel.setLoadBalancer(it) }
            )
            if (settings.loadBalancerEnabled) {
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Default.Settings,
                    label = "Balancer Strategy",
                    description = settings.balancerStrategy,
                    onClick = { showBalancerStrategyDialog = true }
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_dns_label)) {
            SettingsClickableRow(
                icon = Icons.Default.Dns,
                label = stringResource(R.string.settings_dns_label),
                description = settings.dnsServer,
                onClick = { showDnsDialog = true }
            )
            SettingsDivider()
            SettingsClickableRow(
                icon = Icons.Default.Timer,
                label = stringResource(R.string.settings_mtu_label),
                description = settings.mtu.toString(),
                onClick = { showMtuDialog = true }
            )
        }

        SettingsSection(title = stringResource(R.string.settings_scan)) {
            SettingsClickableRow(
                icon = Icons.Default.Timer,
                label = stringResource(R.string.settings_scan_interval),
                description = "${settings.scanIntervalMinutes} min",
                onClick = { showScanIntervalDialog = true }
            )
            SettingsDivider()
            SettingsClickableRow(
                icon = Icons.Default.FlashOn,
                label = stringResource(R.string.settings_scan_concurrency),
                description = settings.scanConcurrency.toString(),
                onClick = { showScanConcurrencyDialog = true }
            )
            SettingsDivider()
            SettingsToggleRow(
                icon = Icons.Default.Cloud,
                label = stringResource(R.string.settings_cloudflare_enabled),
                checked = settings.cloudflareScannerEnabled,
                onCheckedChange = { viewModel.setCloudflareScanner(it) },
                accentColor = CloudflareOrange
            )
            if (settings.cloudflareScannerEnabled) {
                SettingsDivider()
                SettingsClickableRow(
                    icon = Icons.Default.Cloud,
                    label = stringResource(R.string.configs_cf_scanner_title),
                    description = stringResource(R.string.configs_cf_scanner_desc),
                    onClick = { onNavigateToScanner() },
                    accentColor = CloudflareOrange
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_about)) {
            SettingsInfoRow(
                icon = Icons.Default.Info,
                label = stringResource(R.string.settings_version),
                value = stringResource(R.string.settings_version_value)
            )
            SettingsDivider()
            SettingsInfoRow(
                icon = Icons.Default.Code,
                label = stringResource(R.string.settings_core_version),
                value = stringResource(R.string.settings_core_version_value)
            )
            SettingsDivider()
            SettingsInfoRow(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.settings_developer),
                value = stringResource(R.string.settings_developer_name)
            )
            SettingsDivider()
            SettingsClickableRow(
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(R.string.settings_telegram_channel),
                onClick = {
                    val url = context.getString(R.string.settings_telegram_url)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                accentColor = NeonCyan
            )
        }

        Spacer(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun LogLevelDialog(
    currentLevel: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val levels = listOf("none", "error", "warning", "info", "debug")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xray Log Level") },
        text = {
            Column {
                levels.forEach { level ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(level) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = level,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (level == currentLevel) NeonCyan else Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (level == currentLevel) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        containerColor = Color(0xFF1A1F2B),
        titleContentColor = Color.White
    )
}

@Composable
private fun BalancerStrategyDialog(
    currentStrategy: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val strategies = listOf("random", "leastPing", "roundRobin", "leastConn")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Balancer Strategy") },
        text = {
            Column {
                strategies.forEach { strategy ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(strategy) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strategy,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (strategy == currentStrategy) NeonCyan else Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (strategy == currentStrategy) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        containerColor = Color(0xFF1A1F2B),
        titleContentColor = Color.White
    )
}

@Composable
private fun LanguageDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                LanguageOption("en", "English", currentLanguage, onSelect)
                LanguageOption("fa", "فارسی", currentLanguage, onSelect)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
private fun InputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = NeonCyan,
                    cursorColor = NeonCyan,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.common_save), color = NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        containerColor = Color(0xFF1A1F2B),
        titleContentColor = Color.White
    )
}

@Composable
private fun LanguageOption(
    code: String,
    name: String,
    currentCode: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(code) }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (code == currentCode) NeonCyan else Color.White
            ),
            modifier = Modifier.weight(1f)
        )
        if (code == currentCode) {
            Icon(Icons.Default.Language, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan.copy(alpha = 0.7f)),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    accentColor: Color = NeonGreen
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.labelSmall.copy(color = TextHint))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.3f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(14.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White), modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(color = TextHint))
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    description: String? = null,
    accentColor: Color = NeonCyan
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = accentColor))
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.labelSmall.copy(color = TextHint))
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 50.dp),
        color = Color.White.copy(alpha = 0.15f),
        thickness = 0.5f.dp
    )
}




