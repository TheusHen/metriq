package com.metriq.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Layout constants shared by all redesigned screens. */
object Metrics {
    val pageGutter = 20.dp
    val panelPadding = 16.dp
    val panelRadius = 20.dp
    val contentMaxWidth = 560.dp
    val wideBreakpoint = 600.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 22.dp
}

object Elevation {
    val card = 0.dp
    val dialog = 8.dp
}

@Immutable
data class SurfaceHierarchy(
    val level0: androidx.compose.ui.graphics.Color,
    val level1: androidx.compose.ui.graphics.Color,
    val level2: androidx.compose.ui.graphics.Color,
    val level3: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color,
)

/** Dark-only surface steps: each level is exactly one tone away from the next. */
val DarkSurfaces = SurfaceHierarchy(
    level0 = MetriqColors.bg,
    level1 = MetriqColors.surface1,
    level2 = MetriqColors.surface2,
    level3 = MetriqColors.surface2,
    border = MetriqColors.border,
)

/** Retained for compatibility; dark identity applies regardless of the requested mode. */
val LightSurfaces = DarkSurfaces

val LocalSurfaceHierarchy = staticCompositionLocalOf { DarkSurfaces }
