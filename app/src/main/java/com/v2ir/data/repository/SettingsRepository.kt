package com.v2ir.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_xray_settings")

data class AppSettings(
    val autoConnect: Boolean = false,
    val bypassLan: Boolean = true,
    val bypassIran: Boolean = false,
    val cloudflareScannerEnabled: Boolean = true,
    val loadBalancerEnabled: Boolean = true,
    // WorkManager minimum periodic interval is 15 minutes — default reflects this.
    val scanIntervalMinutes: Int = 15,
    val dnsServer: String = "1.1.1.1",
    val mtu: Int = 1500,
    val language: String = "en",
    val scanConcurrency: Int = 16,
    val balancerStrategy: String = "random",
    val muxEnabled: Boolean = true,
    val logLevel: String = "warning"
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val AUTO_CONNECT     = booleanPreferencesKey("auto_connect")
        val BYPASS_LAN       = booleanPreferencesKey("bypass_lan")
        val BYPASS_IRAN      = booleanPreferencesKey("bypass_iran")
        val CF_SCANNER       = booleanPreferencesKey("cf_scanner")
        val LOAD_BALANCE     = booleanPreferencesKey("load_balance")
        val SCAN_INTERVAL    = intPreferencesKey("scan_interval")
        val DNS              = stringPreferencesKey("dns")
        val MTU              = intPreferencesKey("mtu")
        val LANGUAGE         = stringPreferencesKey("language")
        val SCAN_CONCURRENCY = intPreferencesKey("scan_concurrency")
        val BALANCER_STRATEGY = stringPreferencesKey("balancer_strategy")
        val MUX_ENABLED      = booleanPreferencesKey("mux_enabled")
        val LOG_LEVEL        = stringPreferencesKey("log_level")
    }

    // FIX: Added .catch { emit(emptyPreferences()) } to handle DataStore IOException
    // (file corruption, disk full, permission error). Without this, any IO error crashes
    // the entire settings flow and leaves the UI with no settings at all.
    // IOException-specific catch preserves CancellationException propagation.
    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            AppSettings(
                autoConnect           = prefs[Keys.AUTO_CONNECT]       ?: false,
                bypassLan             = prefs[Keys.BYPASS_LAN]         ?: true,
                bypassIran            = prefs[Keys.BYPASS_IRAN]        ?: false,
                cloudflareScannerEnabled = prefs[Keys.CF_SCANNER]      ?: true,
                loadBalancerEnabled   = prefs[Keys.LOAD_BALANCE]       ?: true,
                scanIntervalMinutes   = prefs[Keys.SCAN_INTERVAL]      ?: 15,
                dnsServer             = prefs[Keys.DNS]                ?: "1.1.1.1",
                mtu                   = prefs[Keys.MTU]                ?: 1500,
                language              = prefs[Keys.LANGUAGE]           ?: "en",
                scanConcurrency       = prefs[Keys.SCAN_CONCURRENCY]   ?: 16,
                balancerStrategy      = prefs[Keys.BALANCER_STRATEGY]  ?: "random",
                muxEnabled            = prefs[Keys.MUX_ENABLED]        ?: true,
                logLevel              = prefs[Keys.LOG_LEVEL]          ?: "warning"
            )
        }

    suspend fun setAutoConnect(value: Boolean)       = edit(Keys.AUTO_CONNECT, value)
    suspend fun setBypassLan(value: Boolean)         = edit(Keys.BYPASS_LAN, value)
    suspend fun setBypassIran(value: Boolean)        = edit(Keys.BYPASS_IRAN, value)
    suspend fun setCloudflareScanner(value: Boolean) = edit(Keys.CF_SCANNER, value)
    suspend fun setLoadBalancer(value: Boolean)      = edit(Keys.LOAD_BALANCE, value)
    suspend fun setScanInterval(value: Int)          = editInt(Keys.SCAN_INTERVAL, value)
    suspend fun setDns(value: String)                = editString(Keys.DNS, value)
    suspend fun setMtu(value: Int)                   = editInt(Keys.MTU, value)
    suspend fun setLanguage(value: String)           = editString(Keys.LANGUAGE, value)
    suspend fun setScanConcurrency(value: Int)       = editInt(Keys.SCAN_CONCURRENCY, value)
    suspend fun setBalancerStrategy(value: String)   = editString(Keys.BALANCER_STRATEGY, value)
    suspend fun setMuxEnabled(value: Boolean)        = edit(Keys.MUX_ENABLED, value)
    suspend fun setLogLevel(value: String)           = editString(Keys.LOG_LEVEL, value)

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun editInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun editString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }
}
