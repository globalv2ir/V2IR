package com.v2ir.ui.screens.configs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ir.R
import com.v2ir.data.model.Config
import com.v2ir.data.model.ConfigType
import com.v2ir.data.model.LogMessage
import com.v2ir.data.model.Subscription
import com.v2ir.domain.config.ConfigDomainFacade
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.content.Context

data class ConfigsUiState(
    val showAddDialog: Boolean = false,
    val showRepoDialog: Boolean = false,
    val showShareDialog: Boolean = false,
    val editingConfig: Config? = null,
    val editingSubscription: Subscription? = null,
    val sharingConfig: Config? = null,
    val shareText: String = "",
    val shareQrBitmap: Bitmap? = null,
    val confirmDeleteId: Long? = null,
    val confirmDeleteRepoId: Long? = null,
    // Config dialog fields
    val dialogName: String = "",
    val dialogAddress: String = "",
    val dialogPort: String = "443",
    val dialogUserId: String = "",
    val dialogSni: String = "",
    val dialogRemark: String = "",
    val dialogSubscriptionUrl: String = "",
    val dialogIsCloudflare: Boolean = false,
    val dialogIsPublic: Boolean = false,
    val dialogType: ConfigType = ConfigType.VLESS,
    val dialogRawUri: String = "",
    // Scanning state
    val isScanning: Boolean = false,
    val isCfScanning: Boolean = false,
    val cfScanProgress: Pair<Int, Int> = 0 to 0,
    val cfScanResult: String? = null,
    val scanningConfigId: Long? = null,
    val scanProgress: Pair<Int, Int> = 0 to 0,
    val isSpeedTesting: Boolean = false,
    val speedProgress: Pair<Int, Int> = 0 to 0,
    // UI feedback
    val updateMessageRes: Int? = null,
    val updateMessageStr: String? = null,
    val subscriptionScanProgress: Map<Long, Float> = emptyMap(),
    val scannedSubscriptionIds: Set<Long> = emptySet()
)

@HiltViewModel
class ConfigsViewModel @Inject constructor(
    private val configDomain: ConfigDomainFacade,
    private val logRepository: com.v2ir.data.repository.LogRepository
) : ViewModel() {

    val publicRepos = configDomain.observePublicRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privateRepos = configDomain.observePrivateRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privateConfigs = configDomain.observePrivateConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allConfigs = configDomain.observeAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // FIX: Use combine to avoid race condition between allConfigs and repos
    private val configsBySub = MutableStateFlow<Map<Long, List<Config>>>(emptyMap())
    val expandedConfigs: StateFlow<Map<Long, List<Config>>> = configsBySub.asStateFlow()

    init {
        // FIX: Combine allConfigs + publicRepos + privateRepos so we always have latest values of both
        viewModelScope.launch {
            combine(allConfigs, publicRepos, privateRepos) { configs, pub, priv ->
                Triple(configs, pub, priv)
            }.collect { (configs, pub, priv) ->
                val expandedIds = (pub + priv).filter { it.isExpanded }.map { it.id }
                val sortedConfigs = configs.sortedWith(
                    compareByDescending<Config> { it.realLatency >= 0 }
                        .thenBy { it.realLatency }
                        .thenByDescending { it.lastSuccess }
                        .thenBy { it.name }
                )
                configsBySub.value = expandedIds.associateWith { id ->
                    sortedConfigs.filter { it.subscriptionId == id }
                }
            }
        }
    }

    private val _uiState = MutableStateFlow(ConfigsUiState())
    val uiState: StateFlow<ConfigsUiState> = _uiState.asStateFlow()

    fun selectConfig(config: Config) {
        viewModelScope.launch { configDomain.selectConfig(config.id) }
    }

    fun openAddConfigDialog() {
        _uiState.update { ConfigsUiState(showAddDialog = true) }
    }

    fun openAddRepoDialog(isPublic: Boolean = false) {
        _uiState.update { ConfigsUiState(showRepoDialog = true, dialogIsPublic = isPublic) }
    }

    fun openEditConfigDialog(config: Config) {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                editingConfig = config,
                dialogName = config.name,
                dialogAddress = config.address,
                dialogPort = config.port.toString(),
                dialogUserId = config.userId,
                dialogSni = config.sni,
                dialogSubscriptionUrl = config.subscriptionUrl,
                dialogRemark = config.remark,
                dialogIsCloudflare = config.isCloudflare,
                dialogType = config.type,
                dialogRawUri = config.rawUri
            )
        }
    }

    fun openEditRepoDialog(subscription: Subscription) {
        _uiState.update {
            it.copy(
                showRepoDialog = true,
                editingSubscription = subscription,
                dialogName = subscription.name,
                dialogSubscriptionUrl = subscription.url,
                dialogRemark = subscription.remark,
                dialogIsPublic = subscription.isPublic
            )
        }
    }

    fun openShareDialog(config: Config) {
        val text = configDomain.shareText(config)
        val qr = configDomain.shareQrBitmap(config)
        _uiState.update {
            it.copy(
                showShareDialog = true,
                sharingConfig = config,
                shareText = text,
                shareQrBitmap = qr
            )
        }
    }

    fun dismissShareDialog() {
        _uiState.update {
            it.copy(showShareDialog = false, sharingConfig = null, shareText = "", shareQrBitmap = null)
        }
    }

    fun dismissDialog() {
        _uiState.update { ConfigsUiState() }
    }

    // Dialog field change handlers
    fun onDialogNameChange(v: String) = _uiState.update { it.copy(dialogName = v) }
    fun onDialogAddressChange(v: String) = _uiState.update { it.copy(dialogAddress = v) }
    fun onDialogPortChange(v: String) = _uiState.update { it.copy(dialogPort = v) }
    fun onDialogUserIdChange(v: String) = _uiState.update { it.copy(dialogUserId = v) }
    fun onDialogSniChange(v: String) = _uiState.update { it.copy(dialogSni = v) }
    fun onDialogSubscriptionUrlChange(v: String) = _uiState.update { it.copy(dialogSubscriptionUrl = v) }
    fun onDialogRemarkChange(v: String) = _uiState.update { it.copy(dialogRemark = v) }
    fun onDialogCloudflareChange(v: Boolean) = _uiState.update { it.copy(dialogIsCloudflare = v) }
    fun onDialogIsPublicChange(v: Boolean) = _uiState.update { it.copy(dialogIsPublic = v) }
    fun onDialogTypeChange(v: ConfigType) = _uiState.update { it.copy(dialogType = v) }
    fun onDialogRawUriChange(v: String) = _uiState.update { it.copy(dialogRawUri = v) }

    fun saveConfig() {
        val state = _uiState.value
        if (state.dialogName.isBlank()) return

        viewModelScope.launch {
            val existing = state.editingConfig

            // If editing and a new rawUri was pasted, try to re-import from URI
            if (existing != null && state.dialogRawUri.isNotBlank() && state.dialogRawUri != existing.rawUri) {
                configDomain.importUnified(state.dialogRawUri)
                    .onSuccess {
                        dismissDialog()
                        _uiState.update { it.copy(updateMessageRes = R.string.configs_import_success) }
                        return@launch
                    }
                // If import fails, fall through to manual save
            }

            // FIX: Preserve ALL existing fields when editing — only override what user changed
            val config = Config(
                id = existing?.id ?: 0,
                name = state.dialogName.trim(),
                address = state.dialogAddress.trim(),
                port = state.dialogPort.toIntOrNull() ?: 443,
                userId = state.dialogUserId.trim().ifBlank { existing?.userId.orEmpty() },
                sni = state.dialogSni.trim(),
                subscriptionUrl = state.dialogSubscriptionUrl.trim(),
                remark = state.dialogRemark.trim(),
                isCloudflare = state.dialogIsCloudflare,
                type = state.dialogType,
                // Preserve fields not editable in simple dialog
                subscriptionId = existing?.subscriptionId,
                fragmentIp = existing?.fragmentIp.orEmpty(),
                isSelected = existing?.isSelected ?: false,
                latency = existing?.latency ?: -1L,
                tcpLatency = existing?.tcpLatency ?: -1L,
                realLatency = existing?.realLatency ?: -1L,
                rawUri = state.dialogRawUri.trim().ifBlank { existing?.rawUri.orEmpty() },
                countryLabel = existing?.countryLabel.orEmpty(),
                addedAt = existing?.addedAt ?: System.currentTimeMillis(),
                isFree = existing?.isFree ?: false,
                bandwidth = existing?.bandwidth ?: 0f,
                lastSuccess = existing?.lastSuccess ?: 0L,
                lastChecked = existing?.lastChecked ?: 0L,
                failCount = existing?.failCount ?: 0,
                extraParams = existing?.extraParams ?: emptyMap()
            )
            if (existing != null) configDomain.updateConfig(config)
            else configDomain.insertConfig(config)
            dismissDialog()
        }
    }

    fun importFromText(line: String) {
        viewModelScope.launch {
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@launch
            configDomain.importUnified(trimmed)
                .onSuccess { count ->
                    dismissDialog()
                    _uiState.update { it.copy(updateMessageRes = R.string.configs_import_success) }
                }
                .onFailure {
                    _uiState.update { it.copy(updateMessageRes = R.string.configs_import_failed) }
                }
        }
    }

    fun importFromGallery(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } ?: run {
                    _uiState.update { it.copy(updateMessageRes = R.string.configs_qr_not_found) }
                    return@launch
                }

                val qrText = withContext(Dispatchers.IO) {
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    val source = RGBLuminanceSource(width, height, pixels)
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                    MultiFormatReader().decode(binaryBitmap).text
                }

                configDomain.importUnified(qrText)
                    .onSuccess {
                        _uiState.update { it.copy(updateMessageRes = R.string.configs_import_success) }
                    }
                    .onFailure {
                        _uiState.update { it.copy(updateMessageRes = R.string.configs_import_failed) }
                    }
            } catch (_: NotFoundException) {
                _uiState.update { it.copy(updateMessageRes = R.string.configs_qr_not_found) }
            } catch (_: Exception) {
                _uiState.update { it.copy(updateMessageRes = R.string.common_error) }
            }
        }
    }

    fun exportAllConfigs(context: Context) {
        val configs = allConfigs.value
        if (configs.isEmpty()) {
            _uiState.update { it.copy(updateMessageRes = R.string.configs_empty_private) }
            return
        }
        val text = configDomain.getAllConfigsAsText(configs)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("V2IR Configs", text)
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(updateMessageRes = R.string.configs_export_success) }
    }

    fun exportHealthyConfigs(context: Context) {
        val healthy = allConfigs.value.filter { it.realLatency >= 0 || it.tcpLatency >= 0 }
        if (healthy.isEmpty()) {
            _uiState.update { it.copy(updateMessageRes = R.string.configs_no_healthy_found) }
            return
        }
        val text = configDomain.getAllConfigsAsText(healthy)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("V2IR Healthy Configs", text)
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(updateMessageStr = "Copied ${healthy.size} healthy configs") }
    }

    fun saveSubscription() {
        val state = _uiState.value
        if (state.dialogName.isBlank() || state.dialogSubscriptionUrl.isBlank()) return
        viewModelScope.launch {
            val subscription = Subscription(
                id = state.editingSubscription?.id ?: 0,
                name = state.dialogName.trim(),
                url = state.dialogSubscriptionUrl.trim(),
                remark = state.dialogRemark.trim(),
                isPublic = state.dialogIsPublic
            )
            if (state.editingSubscription != null) configDomain.updateSubscription(subscription)
            else configDomain.insertSubscription(subscription)
            dismissDialog()
        }
    }

    fun requestDeleteConfig(id: Long) = _uiState.update { it.copy(confirmDeleteId = id) }
    fun requestDeleteRepo(id: Long) = _uiState.update { it.copy(confirmDeleteRepoId = id) }
    fun cancelDelete() = _uiState.update { it.copy(confirmDeleteId = null, confirmDeleteRepoId = null) }

    fun confirmDeleteConfig() {
        val id = _uiState.value.confirmDeleteId ?: return
        viewModelScope.launch {
            configDomain.deleteConfig(id)
            _uiState.update { it.copy(confirmDeleteId = null) }
        }
    }

    fun confirmDeleteRepo() {
        val id = _uiState.value.confirmDeleteRepoId ?: return
        viewModelScope.launch {
            configDomain.deleteSubscription(id)
            _uiState.update { it.copy(confirmDeleteRepoId = null) }
        }
    }

    // FIX: guard against id=0 which would corrupt all latency data
    // FIX: Delegate to ConfigDomainFacade instead of accessing repository directly from ViewModel
    fun scanConfigLatency(config: Config) {
        if (config.id <= 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(scanningConfigId = config.id, isScanning = true) }
            configDomain.scanConfigTcpLatency(config)
            _uiState.update { it.copy(scanningConfigId = null, isScanning = false) }
        }
    }

    // FIX: guard against id=0
    // FIX: Delegate to ConfigDomainFacade instead of accessing repository directly from ViewModel
    fun scanConfigRealLatency(config: Config) {
        if (config.id <= 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(scanningConfigId = config.id, isScanning = true) }
            configDomain.scanConfigRealLatency(config)
            _uiState.update { it.copy(scanningConfigId = null, isScanning = false) }
        }
    }

    fun scanAllConfigs() {
        viewModelScope.launch {
            val subscriptions = (publicRepos.value + privateRepos.value).filter { it.isEnabled }
            if (subscriptions.isEmpty() && privateConfigs.value.isEmpty()) return@launch

            _uiState.update { it.copy(isScanning = true, scannedSubscriptionIds = emptySet()) }

            subscriptions.forEach { sub ->
                val subConfigs = allConfigs.value.filter { it.subscriptionId == sub.id }
                if (subConfigs.isNotEmpty()) {
                    configDomain.scanLatencies(
                        configs = subConfigs,
                        onProgress = { done, total ->
                            _uiState.update { state ->
                                state.copy(
                                    subscriptionScanProgress = state.subscriptionScanProgress +
                                            (sub.id to done.toFloat() / total)
                                )
                            }
                        },
                        onResult = { result -> configDomain.applyScanResult(result) }
                    )
                    _uiState.update { state ->
                        state.copy(
                            scannedSubscriptionIds = state.scannedSubscriptionIds + sub.id,
                            subscriptionScanProgress = state.subscriptionScanProgress - sub.id
                        )
                    }
                }
            }

            // Also scan loose private configs
            val loose = privateConfigs.value.filter { it.subscriptionId == null }
            if (loose.isNotEmpty()) {
                configDomain.scanLatencies(loose) { result -> configDomain.applyScanResult(result) }
            }

            _uiState.update {
                it.copy(isScanning = false, updateMessageRes = R.string.configs_scan_complete)
            }
        }
    }

    fun shareSubscription(subscription: Subscription) {
        viewModelScope.launch {
            val configs = allConfigs.value.filter { it.subscriptionId == subscription.id }
            if (configs.isEmpty()) {
                _uiState.update { it.copy(updateMessageRes = R.string.configs_empty_private) }
                return@launch
            }
            val text = configDomain.getAllConfigsAsText(configs)
            // Only generate QR if text is short enough (QR has ~2KB limit for reliable scanning)
            val qr = if (text.length <= 1800) configDomain.shareQrBitmapFromText(text) else null
            _uiState.update {
                it.copy(
                    showShareDialog = true,
                    sharingConfig = Config(id = -1, name = subscription.name, address = ""),
                    shareText = text,
                    shareQrBitmap = qr
                )
            }
        }
    }

    fun startCloudflareCleanIpScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCfScanning = true, cfScanResult = null) }
            logRepository.add(LogMessage(R.string.log_msg_cf_scan_start, tagRes = R.string.logs_tag_scanner))
            val best = configDomain.findBestCleanIp { current, total ->
                _uiState.update { it.copy(cfScanProgress = current to total) }
            }
            if (best != null) {
                _uiState.update {
                    it.copy(
                        isCfScanning = false,
                        cfScanResult = best.ip,
                        updateMessageRes = R.string.configs_scan_complete
                    )
                }
            } else {
                _uiState.update { it.copy(isCfScanning = false, updateMessageRes = R.string.common_error) }
            }
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val count = configDomain.refreshSubscription(subscription)
            if (count > 0) {
                _uiState.update {
                    it.copy(isScanning = false, updateMessageRes = R.string.configs_update_success)
                }
            } else {
                _uiState.update {
                    it.copy(isScanning = false, updateMessageRes = R.string.configs_update_failed)
                }
            }
        }
    }

    fun toggleSubscriptionExpanded(id: Long, expanded: Boolean) {
        viewModelScope.launch { configDomain.toggleSubscriptionExpanded(id, expanded) }
    }

    fun toggleSubscriptionEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { configDomain.toggleSubscriptionEnabled(id, enabled) }
    }

    fun clearUpdateMessage() = _uiState.update { it.copy(updateMessageRes = null, updateMessageStr = null) }
}
