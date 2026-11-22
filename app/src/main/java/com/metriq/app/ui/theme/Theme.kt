package com.metriq.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalMacroColors = staticCompositionLocalOf { macroPalette(dark = true) }

/**
 * Applies the dark-only Metriq identity. `darkTheme` is kept for source compatibility;
 * the palette, typography and system bars are always dark.
 */
@Composable
fun MetriqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = DarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge with dark glyphs on light-free bars.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            // Removes any white flash behind the transparent system bars.
            window.setBackgroundDrawable(ColorDrawable(MetriqColors.bg.toArgb()))
        }
    }
    CompositionLocalProvider(
        LocalMacroColors provides macroPalette(true),
        LocalSurfaceHierarchy provides DarkSurfaces,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = MetriqTypography,
            content = content,
        )
    }
}
