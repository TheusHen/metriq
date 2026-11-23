package com.metriq.app.ui.goal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.metriq.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.formatCount
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.h2
import com.metriq.core.GoalOverride

/**
 * Manual calorie-goal override. Values are applied only when the user confirms;
 * "Voltar ao sugerido" clears the override and restores the calculated target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(
    suggestedKcal: Int,
    current: GoalOverride?,
    isMinor: Boolean,
    previewWarnings: (GoalOverride) -> List<String> = { emptyList() },
    onDismiss: () -> Unit,
    onApply: (GoalOverride) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val kcalText = remember { mutableStateOf((current?.dailyKcal ?: suggestedKcal).toString()) }
    val weightText = remember {
        mutableStateOf(current?.targetWeightKg?.let { formatKg(it) }.orEmpty())
    }
    val weeksText = remember { mutableStateOf(current?.weeks?.toString().orEmpty()) }

    val enteredKcal = kcalText.value.toIntOrNull()?.takeIf { it > 0 }
    val enteredWeight = weightText.value.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val enteredWeeks = weeksText.value.toIntOrNull()?.takeIf { it > 0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MetriqColors.surface1,
        contentColor = MetriqColors.ink,
        scrimColor = MetriqColors.bg.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_edit_goal),
                    style = MaterialTheme.typography.h2,
                    color = MetriqColors.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.close),
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                    modifier = Modifier.clickable { onDismiss() }.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.goal_editor_suggested, formatCount(suggestedKcal)),
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
                maxLines = 2,
            )
            Spacer(Modifier.height(12.dp))

            Field(
                value = kcalText.value,
                onValueChange = { kcalText.value = it },
                label = stringResource(R.string.goal_editor_kcal),
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(
                    value = weightText.value,
                    onValueChange = { weightText.value = it },
                    label = stringResource(R.string.goal_editor_weight),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                Field(
                    value = weeksText.value,
                    onValueChange = { weeksText.value = it },
                    label = stringResource(R.string.goal_editor_weeks),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(
                R.string.goal_editor_before,
                formatCount(suggestedKcal),
                enteredKcal?.let { formatCount(it) } ?: "—",
            ),
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.goal_editor_macros),
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink2,
                maxLines = 2,
            )
            if (isMinor) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.goal_editor_minor),
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                )
            }
            val warnings = enteredKcal?.let { kcal ->
                previewWarnings(
                    GoalOverride(kcal, enteredWeight, enteredWeeks, reason = null),
                )
            }.orEmpty()
            warnings.forEach { warning ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = warning,
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink2,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
            Spacer(Modifier.height(14.dp))

            MetriqButton(
                text = stringResource(R.string.goal_editor_apply),
                onClick = {
                    val kcal = enteredKcal ?: return@MetriqButton
                    onApply(
                        GoalOverride(
                            dailyKcal = kcal,
                            targetWeightKg = enteredWeight,
                            weeks = enteredWeeks,
                            reason = null,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
            Spacer(Modifier.height(8.dp))
            MetriqButton(
                text = stringResource(R.string.goal_editor_back),
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                primary = false,
            )
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MetriqColors.ink,
            unfocusedTextColor = MetriqColors.ink,
            cursorColor = MetriqColors.accent,
            focusedBorderColor = MetriqColors.accent,
            unfocusedBorderColor = MetriqColors.border,
            focusedLabelColor = MetriqColors.ink2,
            unfocusedLabelColor = MetriqColors.ink3,
            focusedContainerColor = MetriqColors.surface2,
            unfocusedContainerColor = MetriqColors.surface1,
        ),
    )
}

private fun formatKg(value: Double): String = String.format(java.util.Locale.ENGLISH, "%.1f", value)
