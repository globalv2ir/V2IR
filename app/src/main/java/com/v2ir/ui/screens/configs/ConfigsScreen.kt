// FIX: Opt-in to ExperimentalMaterial3Api for ExposedDropdownMenuBox APIs.
// menuAnchor(), ExposedDropdownMenuDefaults.TrailingIcon, and ExposedDropdownMenu
// are experimental in Material3 BOM 2024.09.03. The @OptIn suppresses the error
// without changing behavior — these APIs are stable in practice and used as intended.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.v2ir.ui.screens.configs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.v2ir.R
import com.v2ir.data.model.Config
import com.v2ir.data.model.ConfigType
import com.v2ir.data.model.Subscription
import com.v2ir.ui.components.GlassCard
import com.v2ir.ui.components.ShareConfigDialog
import com.v2ir.ui.theme.NeonCyan
import com.v2ir.ui.theme.CloudflareOrange

private val LatencyGood = Color(0xFF4CAF50)
private val LatencyMedium = Color(0xFFFF9800)
private val LatencyBad = Color(0xFFF44336)

private fun latencyColor(ms: Long): Color = when {
    ms <= 0L -> Color.Gray
    ms < 300L -> LatencyGood
    ms < 800L -> LatencyMedium
    else -> LatencyBad
}

private fun latencyText(ms: Long): String = if (ms > 0L) "${ms}ms" else "—"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigsScreen(viewModel: ConfigsViewModel = hiltViewModel()) {
    val publicRepos by viewModel.publicRepos.collectAsState()
    val privateRepos by viewModel.privateRepos.collectAsState()
    val privateConfigs by viewModel.privateConfigs.collectAsState()
    val expandedConfigs by viewModel.expandedConfigs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showAddOptions by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                viewModel.importFromText(stream.bufferedReader().readText())
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra("SCAN_RESULT")?.let { viewModel.importFromText(it) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importFromGallery(context, it) } }

    LaunchedEffect(uiState.updateMessageRes, uiState.updateMessageStr) {
        uiState.updateMessageRes?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            viewModel.clearUpdateMessage()
        }
        uiState.updateMessageStr?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUpdateMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.configs_title),
                    style = MaterialTheme.typography.titleLarge.copy(color = NeonCyan, fontWeight = FontWeight.Bold)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isScanning || uiState.isCfScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonCyan)
                        Spacer(Modifier.width(12.dp))
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1A1F2B))
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configs_scan_all), color = Color.White) },
                            onClick = { showMenu = false; viewModel.scanAllConfigs() },
                            leadingIcon = { Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = NeonCyan) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configs_share_copy), color = Color.White) },
                            onClick = { showMenu = false; viewModel.exportAllConfigs(context) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = NeonCyan) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configs_export_healthy), color = Color.White) },
                            onClick = { showMenu = false; viewModel.exportHealthyConfigs(context) },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = LatencyGood) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Global scan progress
                item {
                    if (uiState.isScanning && uiState.scanProgress.second > 0) {
                        val (done, total) = uiState.scanProgress
                        LinearProgressIndicator(
                            progress = done.toFloat() / total,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = NeonCyan
                        )
                    }
                }

                // Public repos section
                item { SectionHeader(stringResource(R.string.configs_public_repos)) }

                if (publicRepos.isEmpty()) {
                    item { EmptyText(stringResource(R.string.configs_empty_public)) }
                }

                items(publicRepos, key = { "pub_${it.id}" }) { repo ->
                    SubscriptionItem(
                        subscription = repo,
                        progress = uiState.subscriptionScanProgress[repo.id],
                        isScanned = uiState.scannedSubscriptionIds.contains(repo.id),
                        isExpanded = expandedConfigs.containsKey(repo.id),
                        onClick = { viewModel.toggleSubscriptionExpanded(repo.id, !expandedConfigs.containsKey(repo.id)) },
                        onEdit = { viewModel.openEditRepoDialog(repo) },
                        onDelete = { viewModel.requestDeleteRepo(repo.id) },
                        onUpdate = { viewModel.updateSubscription(repo) },
                        onToggle = { viewModel.toggleSubscriptionEnabled(repo.id, it) },
                        onShare = { viewModel.shareSubscription(repo) }
                    )
                    val configs = expandedConfigs[repo.id] ?: emptyList()
                    configs.forEach { config ->
                        ConfigItem(
                            config = config,
                            isScanning = uiState.scanningConfigId == config.id,
                            onClick = { viewModel.selectConfig(config) },
                            onEdit = { viewModel.openEditConfigDialog(config) },
                            onDelete = { viewModel.requestDeleteConfig(config.id) },
                            onShare = { viewModel.openShareDialog(config) },
                            onTcpScan = { viewModel.scanConfigLatency(config) },
                            onRealScan = { viewModel.scanConfigRealLatency(config) }
                        )
                    }
                }

                // Private repos section
                item {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader(stringResource(R.string.configs_private_repos))
                }

                if (privateRepos.isEmpty() && privateConfigs.isEmpty()) {
                    item { EmptyText(stringResource(R.string.configs_empty_private)) }
                }

                items(privateRepos, key = { "priv_repo_${it.id}" }) { repo ->
                    SubscriptionItem(
                        subscription = repo,
                        progress = uiState.subscriptionScanProgress[repo.id],
                        isScanned = uiState.scannedSubscriptionIds.contains(repo.id),
                        isExpanded = expandedConfigs.containsKey(repo.id),
                        onClick = { viewModel.toggleSubscriptionExpanded(repo.id, !expandedConfigs.containsKey(repo.id)) },
                        onEdit = { viewModel.openEditRepoDialog(repo) },
                        onDelete = { viewModel.requestDeleteRepo(repo.id) },
                        onUpdate = { viewModel.updateSubscription(repo) },
                        onToggle = { viewModel.toggleSubscriptionEnabled(repo.id, it) },
                        onShare = { viewModel.shareSubscription(repo) }
                    )
                    val configs = expandedConfigs[repo.id] ?: emptyList()
                    configs.forEach { config ->
                        ConfigItem(
                            config = config,
                            isScanning = uiState.scanningConfigId == config.id,
                            onClick = { viewModel.selectConfig(config) },
                            onEdit = { viewModel.openEditConfigDialog(config) },
                            onDelete = { viewModel.requestDeleteConfig(config.id) },
                            onShare = { viewModel.openShareDialog(config) },
                            onTcpScan = { viewModel.scanConfigLatency(config) },
                            onRealScan = { viewModel.scanConfigRealLatency(config) }
                        )
                    }
                }

                items(privateConfigs, key = { "priv_cfg_${it.id}" }) { config ->
                    ConfigItem(
                        config = config,
                        isScanning = uiState.scanningConfigId == config.id,
                        onClick = { viewModel.selectConfig(config) },
                        onEdit = { viewModel.openEditConfigDialog(config) },
                        onDelete = { viewModel.requestDeleteConfig(config.id) },
                        onShare = { viewModel.openShareDialog(config) },
                        onTcpScan = { viewModel.scanConfigLatency(config) },
                        onRealScan = { viewModel.scanConfigRealLatency(config) }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // FAB cluster
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(showAddOptions, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallFloatingActionButton(
                        onClick = { showAddOptions = false; val i = Intent(context, CameraScanActivity::class.java); cameraLauncher.launch(i) },
                        containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White
                    ) { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
                    SmallFloatingActionButton(
                        onClick = { showAddOptions = false; galleryLauncher.launch("image/*") },
                        containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White
                    ) { Icon(Icons.Default.PhotoLibrary, contentDescription = null) }
                    SmallFloatingActionButton(
                        onClick = { showAddOptions = false; filePickerLauncher.launch("*/*") },
                        containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White
                    ) { Icon(Icons.Default.FileOpen, contentDescription = null) }
                    SmallFloatingActionButton(
                        onClick = { showAddOptions = false; viewModel.openAddRepoDialog(isPublic = false) },
                        containerColor = NeonCyan, contentColor = Color.Black
                    ) { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                    SmallFloatingActionButton(
                        onClick = { showAddOptions = false; viewModel.openAddConfigDialog() },
                        containerColor = CloudflareOrange, contentColor = Color.White
                    ) { Icon(Icons.Default.Add, contentDescription = null) }
                }
            }
            FloatingActionButton(
                onClick = { showAddOptions = !showAddOptions },
                containerColor = if (showAddOptions) Color(0xFF444444) else NeonCyan,
                contentColor = Color.Black, shape = CircleShape
            ) { Icon(if (showAddOptions) Icons.Default.Close else Icons.Default.Add, contentDescription = null) }
        }
    }

    // Dialogs
    if (uiState.showAddDialog) {
        ConfigDialog(
            uiState = uiState,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { viewModel.saveConfig() },
            onImport = { viewModel.importFromText(it) },
            onNameChange = { viewModel.onDialogNameChange(it) },
            onAddressChange = { viewModel.onDialogAddressChange(it) },
            onPortChange = { viewModel.onDialogPortChange(it) },
            onUserIdChange = { viewModel.onDialogUserIdChange(it) },
            onSniChange = { viewModel.onDialogSniChange(it) },
            onRemarkChange = { viewModel.onDialogRemarkChange(it) },
            onCloudflareChange = { viewModel.onDialogCloudflareChange(it) },
            onTypeChange = { viewModel.onDialogTypeChange(it) },
            onRawUriChange = { viewModel.onDialogRawUriChange(it) }
        )
    }
    if (uiState.showRepoDialog) {
        RepoDialog(
            uiState = uiState,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { viewModel.saveSubscription() },
            onNameChange = { viewModel.onDialogNameChange(it) },
            onUrlChange = { viewModel.onDialogSubscriptionUrlChange(it) },
            onRemarkChange = { viewModel.onDialogRemarkChange(it) },
            onIsPublicChange = { viewModel.onDialogIsPublicChange(it) }
        )
    }
    if (uiState.confirmDeleteId != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.configs_delete_confirm_title),
            message = stringResource(R.string.configs_delete_confirm_message),
            onConfirm = { viewModel.confirmDeleteConfig() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }
    if (uiState.confirmDeleteRepoId != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.configs_delete_repo_confirm_title),
            message = stringResource(R.string.configs_delete_repo_confirm_message),
            onConfirm = { viewModel.confirmDeleteRepo() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }
    if (uiState.showShareDialog && uiState.sharingConfig != null) {
        ShareConfigDialog(
            configName = uiState.sharingConfig!!.name,
            shareText = uiState.shareText,
            qrBitmap = uiState.shareQrBitmap,
            onDismiss = { viewModel.dismissShareDialog() }
        )
    }
}

// ─── Reusable UI Components ───────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(color = Color.White.copy(alpha = 0.6f)),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.configs_confirm), color = LatencyBad) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun SubscriptionItem(
    subscription: Subscription,
    progress: Float?,
    isScanned: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onShare: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = subscription.name,
                            style = MaterialTheme.typography.titleSmall.copy(color = Color.White),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (isScanned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LatencyGood, modifier = Modifier.size(14.dp))
                        }
                        if (subscription.healthyCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${subscription.healthyCount}✓",
                                style = MaterialTheme.typography.labelSmall.copy(color = LatencyGood)
                            )
                        }
                    }
                    Text(
                        text = subscription.url,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onUpdate, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
                Switch(
                    checked = subscription.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.3f))
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = NeonCyan
                )
            }
        }
    }
}

@Composable
private fun ConfigItem(
    config: Config,
    isScanning: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onTcpScan: () -> Unit,
    onRealScan: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (config.countryLabel.isNotBlank()) {
                        Text(text = config.countryLabel, fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = config.name,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProtocolTag(config.type.name)
                    Text(
                        text = "${config.address}:${config.port}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Latency row: TCP + Real
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (config.tcpLatency > 0L || config.realLatency > 0L) {
                        if (config.tcpLatency > 0L) {
                            Text(
                                text = "TCP ${latencyText(config.tcpLatency)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = latencyColor(config.tcpLatency)
                            )
                        }
                        if (config.realLatency > 0L) {
                            Text(
                                text = "Real ${latencyText(config.realLatency)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = latencyColor(config.realLatency)
                            )
                        }
                    }
                }
            }

            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NeonCyan)
                Spacer(Modifier.width(4.dp))
            } else {
                // TCP ping
                IconButton(onClick = onTcpScan, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = "TCP ping", tint = NeonCyan, modifier = Modifier.size(16.dp))
                }
                // Real latency
                IconButton(onClick = onRealScan, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = "Real latency", tint = LatencyMedium, modifier = Modifier.size(16.dp))
                }
            }
            // Share
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
            // Edit
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
            // Delete — FIX: was missing before
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LatencyBad.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ProtocolTag(name: String) {
    val color = when (name.uppercase()) {
        "VLESS" -> NeonCyan
        "VMESS" -> Color(0xFF7B68EE)
        "TROJAN" -> Color(0xFFFF6B6B)
        "SHADOWSOCKS" -> Color(0xFFFFD700)
        "HYSTERIA2" -> Color(0xFF00E5FF)
        "TUIC" -> Color(0xFFFF8C00)
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text = name, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
    focusedLabelColor = NeonCyan,
    cursorColor = NeonCyan,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    unfocusedLabelColor = Color.Gray
)

@Composable
private fun ConfigDialog(
    uiState: ConfigsUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onImport: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUserIdChange: (String) -> Unit,
    onSniChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
    onCloudflareChange: (Boolean) -> Unit,
    onTypeChange: (ConfigType) -> Unit,
    onRawUriChange: (String) -> Unit
) {
    val context = LocalContext.current
    val colors = fieldColors
    val isEditing = uiState.editingConfig != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1F2B),
        title = {
            Text(
                if (isEditing) stringResource(R.string.configs_edit_dialog_title)
                else stringResource(R.string.configs_add_dialog_title),
                color = NeonCyan
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clipboard import button (only in add mode)
                if (!isEditing) {
                    val clip = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
                    val isConfigUri = listOf("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://", "tuic://")
                        .any { clip.startsWith(it) } || clip.lines().size > 1
                    if (isConfigUri && clip.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onImport(clip); onDismiss() },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.configs_import_paste), color = NeonCyan)
                        }
                    }
                }

                // Type selector (dropdown)
                if (isEditing) {
                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                        OutlinedTextField(
                            value = uiState.dialogType.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.configs_type_label), color = Color.Gray) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                            colors = colors,
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            modifier = Modifier.background(Color(0xFF1A1F2B))
                        ) {
                            ConfigType.entries.filter { it != ConfigType.SUBSCRIPTION }.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name, color = Color.White) },
                                    onClick = { onTypeChange(type); typeExpanded = false }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(value = uiState.dialogName, onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.configs_name_label)) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(value = uiState.dialogAddress, onValueChange = onAddressChange,
                    label = { Text(stringResource(R.string.configs_address_label)) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uiState.dialogPort, onValueChange = onPortChange,
                        label = { Text(stringResource(R.string.configs_port_label)) },
                        colors = colors, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = uiState.dialogSni, onValueChange = onSniChange,
                        label = { Text(stringResource(R.string.configs_sni_label)) },
                        colors = colors, modifier = Modifier.weight(2f), singleLine = true)
                }

                // UserId / Password field
                OutlinedTextField(
                    value = uiState.dialogUserId, onValueChange = onUserIdChange,
                    label = { Text(stringResource(R.string.configs_userid_label)) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                // Raw URI field (for power users — edit directly or re-import)
                if (isEditing) {
                    OutlinedTextField(
                        value = uiState.dialogRawUri, onValueChange = onRawUriChange,
                        label = { Text(stringResource(R.string.configs_raw_uri_label)) },
                        colors = colors, modifier = Modifier.fillMaxWidth(), maxLines = 2,
                        placeholder = { Text("vless://...", color = Color.Gray) }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = uiState.dialogIsCloudflare, onCheckedChange = onCloudflareChange,
                        colors = CheckboxDefaults.colors(checkedColor = CloudflareOrange)
                    )
                    Text(stringResource(R.string.configs_cloudflare_badge), color = CloudflareOrange,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.configs_save), color = NeonCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.configs_cancel), color = Color.Gray) }
        }
    )
}

@Composable
private fun RepoDialog(
    uiState: ConfigsUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
    onIsPublicChange: (Boolean) -> Unit
) {
    val colors = fieldColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1F2B),
        title = {
            Text(
                if (uiState.editingSubscription != null) stringResource(R.string.configs_edit_repo_dialog_title)
                else stringResource(R.string.configs_add_repo_dialog_title),
                color = NeonCyan
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = uiState.dialogName, onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.configs_name_label)) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = uiState.dialogSubscriptionUrl, onValueChange = onUrlChange,
                    label = { Text(stringResource(R.string.configs_url_label)) },
                    colors = colors, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(value = uiState.dialogRemark, onValueChange = onRemarkChange,
                    label = { Text(stringResource(R.string.configs_remark_label)) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.dialogIsPublic, onCheckedChange = onIsPublicChange,
                        colors = CheckboxDefaults.colors(checkedColor = NeonCyan))
                    Text(stringResource(R.string.configs_is_public), color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.configs_save), color = NeonCyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.configs_cancel), color = Color.Gray) } }
    )
}
