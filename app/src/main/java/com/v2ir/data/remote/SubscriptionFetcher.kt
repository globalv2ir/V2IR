package com.v2ir.data.remote

import com.v2ir.data.model.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionFetcher @Inject constructor(
    private val configUriParser: ConfigUriParser,
    private val client: OkHttpClient
) {
    // Dedicated client with short timeouts for subscription fetching.
    // On restricted networks (Iran), connections may hang indefinitely without a timeout.
    // The shared OkHttpClient has 15s timeouts which is too long for a responsive UI.
    private val fetchClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS) // Hard deadline for entire call
            .build()
    }

    suspend fun fetchAndParse(url: String, subscriptionId: Long): List<Config> =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext emptyList()
            // withTimeoutOrNull provides an additional safety net if callTimeout is exceeded
            withTimeoutOrNull(35_000L) {
                fetchWithRetry(url, subscriptionId)
            } ?: emptyList()
        }

    private suspend fun fetchWithRetry(url: String, subscriptionId: Long, maxAttempts: Int = 2): List<Config> {
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "V2RayNG/1.8.0") // Some servers filter non-standard UAs
                    .build()
                val body = fetchClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return emptyList()
                    response.body?.string().orEmpty()
                }
                if (body.isNotBlank()) {
                    return configUriParser.parseSubscriptionBody(body, subscriptionId)
                }
            } catch (e: CancellationException) {
                throw e // Never swallow cancellation
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts - 1) {
                    kotlinx.coroutines.delay(1_500L * (attempt + 1)) // Exponential back-off
                }
            }
        }
        lastError?.printStackTrace()
        return emptyList()
    }

    suspend fun parseSubscriptionBody(body: String, subscriptionId: Long): List<Config> =
        configUriParser.parseSubscriptionBody(body, subscriptionId)

    suspend fun parseLine(line: String): Config? = configUriParser.parseSingle(line)
}




