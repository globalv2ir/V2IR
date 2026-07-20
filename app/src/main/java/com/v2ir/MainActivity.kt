package com.v2ir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.v2ir.data.local.SeedDataInitializer
import com.v2ir.data.repository.SettingsRepository
import com.v2ir.ui.navigation.AppNavigation
import com.v2ir.ui.theme.V2irTheme
import com.v2ir.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var seedDataInitializer: SeedDataInitializer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle seeding
        lifecycleScope.launch {
            seedDataInitializer.seedIfEmpty()
        }

        // Observe language changes and apply dynamically.
        // FIX (Bug #27): Added isChangingConfigurations guard to prevent infinite recreate() loop.
        // The sequence without the guard:
        //   1. settings emit → lang != currentLocale → applyLocale + recreate()
        //   2. new Activity created → new lifecycleScope.launch → settings emit again
        //   3. If applyLocale() doesn't persist reliably, lang != currentLocale again → loop
        //
        // The guard ensures we only react when the Activity is NOT already in the middle of
        // a configuration change (recreate() sets isChangingConfigurations = true until
        // the new instance is fully created).
        lifecycleScope.launch {
            settingsRepository.settings
                .map { it.language }
                .distinctUntilChanged()
                .collect { lang ->
                    val currentLocale = resources.configuration.locales[0].language
                    if (lang != currentLocale && !isChangingConfigurations) {
                        LocaleHelper.applyLocale(this@MainActivity, lang)
                        recreate()
                    }
                }
        }

        enableEdgeToEdge()
        setContent {
            V2irTheme {
                AppNavigation()
            }
        }
    }
}




