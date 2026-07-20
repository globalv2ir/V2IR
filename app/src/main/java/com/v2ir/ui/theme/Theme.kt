package com.v2ir.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val V2irDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DeepNavy,
    primaryContainer = CardSurface,
    onPrimaryContainer = TextPrimary,
    secondary = NeonGreen,
    onSecondary = DeepNavy,
    secondaryContainer = CardSurface,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonOrange,
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    error = DisconnectedRed,
    onError = TextPrimary
)

@Composable
fun V2irTheme(content: @Composable () -> Unit) {
    val colorScheme = V2irDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // FIX: Use WindowCompat APIs instead of deprecated window.statusBarColor /
            // window.navigationBarColor. On Android 15+ these properties are no-ops.
            // The correct modern approach is enableEdgeToEdge() (called in MainActivity)
            // combined with WindowInsetsController for bar appearance.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            // Keep color assignments for API 26-34 where they still take effect.
            // Suppress deprecation — intentional for pre-35 compatibility.
            @Suppress("DEPRECATION")
            window.statusBarColor = DeepNavy.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = DeepNavy.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = V2irTypography,
        content = content
    )
}




