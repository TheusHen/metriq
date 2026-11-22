package com.metriq.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.theme.MetriqColors
import java.util.Locale

/**
 * Standard framing for every log/weight screen: centered, capped width, dark canvas,
 * and system-bar insets applied once at the root.
 */
@Composable
internal fun FramedColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .background(MetriqColors.bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            content = content,
        )
    }
}

/** Explicit dark field colors so text fields never fall back to a light theme surface. */
@Composable
internal fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MetriqColors.ink,
    unfocusedTextColor = MetriqColors.ink,
    disabledTextColor = MetriqColors.ink3,
    focusedContainerColor = MetriqColors.surface2,
    unfocusedContainerColor = MetriqColors.surface1,
    disabledContainerColor = MetriqColors.surface1,
    cursorColor = MetriqColors.accent,
    focusedBorderColor = MetriqColors.accent,
    unfocusedBorderColor = MetriqColors.border,
    focusedLabelColor = MetriqColors.ink2,
    unfocusedLabelColor = MetriqColors.ink3,
    focusedPlaceholderColor = MetriqColors.ink3,
    unfocusedPlaceholderColor = MetriqColors.ink3,
)

internal fun fmt0(value: Double): String = String.format(Locale.ENGLISH, "%.0f", value)

internal fun macroMeta(calories: Double, protein: Double, carbs: Double, fat: Double): String =
    "${String.format(Locale.ENGLISH, "%,d", calories.toInt())} kcal · " +
        "P ${fmt0(protein)} g · C ${fmt0(carbs)} g · F ${fmt0(fat)} g"
