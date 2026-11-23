package com.metriq.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The single source of truth for the dark-only Metriq identity.
 * All screens should read from this object (or from MaterialTheme.colorScheme,
 * which is derived from it in [MetriqTheme]).
 */
object MetriqColors {
    val bg = Color(0xFF0B0D0F)
    val surface1 = Color(0xFF15181C)
    val surface2 = Color(0xFF1E2228)
    val border = Color(0xFF2A2F37)
    val ink = Color(0xFFF3F5F6)
    val ink2 = Color(0xFF9AA1AB)
    /** Tertiary text. 6.2:1 on bg and 4.9:1 on surface2, so it is safe at caption sizes. */
    val ink3 = Color(0xFF8A919B)
    /** Decorative only: dividers, disabled glyphs, chart grid ticks. Never body text. */
    val ink4 = Color(0xFF646C77)
    val accent = Color(0xFFC6F26E)
    val accentInk = Color(0xFF0B0D0F)
    val energy = Color(0xFFFF7A5C)
    val trend = Color(0xFF7ED9C2)
    val protein = Color(0xFFC58CFF)
    val carbs = Color(0xFFF5C96B)
    val fat = Color(0xFF7AB8FF)
    val danger = Color(0xFFFF6B6B)
    val success = Color(0xFF7ED9C2)
}

@Immutable
data class MacroPalette(
    val protein: Color,
    val carb: Color,
    val fat: Color,
)

private val LightMacro = MacroPalette(
    protein = MetriqColors.protein,
    carb = MetriqColors.carbs,
    fat = MetriqColors.fat,
)

private val DarkMacro = MacroPalette(
    protein = MetriqColors.protein,
    carb = MetriqColors.carbs,
    fat = MetriqColors.fat,
)

/** Kept for source compatibility; the app is dark-only so both branches share the new palette. */
internal fun macroPalette(dark: Boolean): MacroPalette = if (dark) DarkMacro else LightMacro

/** Legacy aliases retained so existing call sites keep compiling, now mapped to the new accent. */
val Accent: Color = MetriqColors.accent
val AccentDim: Color = MetriqColors.accent.copy(alpha = 0.62f)
val AccentLight: Color = MetriqColors.accent.copy(alpha = 0.82f)

private val MetriqDarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = MetriqColors.accent,
    onPrimary = MetriqColors.accentInk,
    primaryContainer = MetriqColors.surface2,
    onPrimaryContainer = MetriqColors.accent,
    secondary = MetriqColors.trend,
    onSecondary = MetriqColors.accentInk,
    secondaryContainer = MetriqColors.surface2,
    onSecondaryContainer = MetriqColors.trend,
    tertiary = MetriqColors.fat,
    onTertiary = MetriqColors.accentInk,
    background = MetriqColors.bg,
    onBackground = MetriqColors.ink,
    surface = MetriqColors.surface1,
    onSurface = MetriqColors.ink,
    surfaceVariant = MetriqColors.surface2,
    onSurfaceVariant = MetriqColors.ink2,
    surfaceTint = MetriqColors.surface1,
    inverseSurface = MetriqColors.ink,
    inverseOnSurface = MetriqColors.bg,
    outline = MetriqColors.border,
    outlineVariant = MetriqColors.border,
    error = MetriqColors.danger,
    onError = MetriqColors.ink,
    errorContainer = MetriqColors.surface2,
    onErrorContainer = MetriqColors.danger,
    scrim = MetriqColors.bg,
)

/** Dark-only scheme retained under the legacy names for compatibility. */
val DarkColors = MetriqDarkColorScheme
val LightColors = MetriqDarkColorScheme

val TextMuted: Color = MetriqColors.ink2
val TextMutedDark: Color = MetriqColors.ink3
