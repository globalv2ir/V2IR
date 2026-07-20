package com.v2ir.data.scanner

import com.v2ir.data.model.CloudflareScanResult
import com.v2ir.data.model.Config
import com.v2ir.data.remote.IpLocationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX (Bug #23): This class was a @Singleton that was injected by Hilt but never referenced
 * from any ViewModel, UseCase, or other class in the codebase (dead code).
 * It held references to OkHttpClient and IpLocationService, keeping them alive in the DI
 * graph for no benefit, and duplicated functionality already in CloudflareScannerCore.
 *
 * The @Inject constructor is kept so existing Hilt component graphs don't break,
 * but the class body has been replaced with a no-op stub. If this scanner is needed
 * in the future, integrate it properly via ConfigDomainFacade instead of using it directly.
 *
 * TODO: Remove this class entirely once confirmed no graph references remain.
 */
@Singleton
@Deprecated("Dead code — not wired to any UI or domain layer. Use CloudflareScannerCore instead.")
class ProfessionalCloudflareScanner @Inject constructor(
    @Suppress("UNUSED_PARAMETER") httpClient: OkHttpClient,
    @Suppress("UNUSED_PARAMETER") ipLocationService: IpLocationService
) {
    fun scan(
        ips: List<String>,
        config: Config,
        concurrency: Int = 32,
        speedTestSizeKb: Int = 512,
        onProgress: (current: Int, total: Int) -> Unit
    ): Flow<CloudflareScanResult> = emptyFlow()
}
