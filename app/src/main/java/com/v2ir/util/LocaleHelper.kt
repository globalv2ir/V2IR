package com.v2ir.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    fun wrapContext(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Applies locale to the activity.
     * On Android 13+ (API 33+) the recommended approach is AppCompatDelegate.setApplicationLocales(),
     * but since this app manages locale manually via recreate(), we use createConfigurationContext
     * to update the app-level configuration without the deprecated updateConfiguration() call.
     *
     * Note: On API < 26, updateConfiguration is still required as createConfigurationContext
     * alone does not update the Resources object in place.
     */
    fun applyLocale(context: Context, language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+: createConfigurationContext is sufficient; updateConfiguration is deprecated
            context.createConfigurationContext(config)
        }
        // Still call updateConfiguration for API < 26 compatibility and to force resource reload
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}




