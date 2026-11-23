package com.metriq.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppGraph
import com.metriq.app.R
import com.metriq.app.Screen
import com.metriq.app.util.parseDoubleLoose
import com.metriq.core.TrainingIntensity
import com.metriq.app.ui.common.warningText
import com.metriq.app.ui.components.EnergyBudget
import com.metriq.app.ui.components.FoodRow
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.MetriqNavBar
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.components.TrendChart
import com.metriq.app.ui.components.WeekStrip
import com.metriq.app.ui.components.formatCount
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.Metrics
import com.metriq.app.ui.theme.Radius
import com.metriq.app.ui.theme.Spacing
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.h2
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number24
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Fallback used when the host does not supply a ViewModel-backed edit callback. */
private fun writeLogGrams(id: Long, grams: Double) {
    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        // The repository notifies FOOD_LOG; DashboardViewModel then rebuilds the state.
        AppGraph.store.logs.updateGrams(id, grams)
    }
}

private fun gramsInput(value: Double): String = if (value % 1.0 == 0.0) {
    value.toLong().toString()
} else {
    String.format(Locale.ENGLISH, "%.2f", value).trimEnd('0').trimEnd('.')
}

@Composable
fun HomeScreen(
    state: DashboardState,
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onAddFood: () -> Unit,
    onDeleteLog: (Long) -> Unit,
    onAcceptCheckin: () -> Unit,
    onEditGoal: () -> Unit = {},
    onEditLog: (Long, Double) -> Unit = ::writeLogGrams,
) {
    Scaffold(
        containerColor = MetriqColors.bg,
        bottomBar = {
            MetriqNavBar(
                current = currentScreen,
                onNavigate = onNavigate,
                onOpenLog = onAddFood,
            )
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(MetriqColors.bg),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Measured before the 560.dp cap so wide screens can switch to two columns.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= Metrics.wideBreakpoint
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = Metrics.contentMaxWidth)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Metrics.pageGutter)
                        .padding(top = Spacing.md, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    StatusRow(state.dateLabel)
                    ScreenHeader(
                        title = stringResource(R.string.today),
                        subtitle = findingFor(state),
                    )

                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                            ) {
                                BudgetSection(state, onEditGoal)
                                AdaptationSection(state)
                                TrainingSection(state)
                                WeekSection(state)
                                FoodLogSection(state, onAddFood, onDeleteLog, onEditLog)
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                            ) {
                                TrendSection(state)
                                ExpenditureSection(state)
                                HealthSection(state)
                                CheckinSection(state, onAcceptCheckin)
                            }
                        }
                    } else {
                        BudgetSection(state, onEditGoal)
                        AdaptationSection(state)
                        TrainingSection(state)
                        WeekSection(state)
                        FoodLogSection(state, onAddFood, onDeleteLog, onEditLog)
                        TrendSection(state)
                        ExpenditureSection(state)
                        HealthSection(state)
                        CheckinSection(state, onAcceptCheckin)
                    }
                }
            }
        }
    }
}

/** Product mark and one freshness stamp. The date appears nowhere else on the screen. */
@Composable
private fun StatusRow(dateLabel: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink2,
            maxLines = 1,
        )
        Spacer(Modifier.width(Spacing.sm))
        Spacer(Modifier.weight(1f))
        Text(
            text = "$dateLabel · " +
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())),
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The line under the title is a finding from today's numbers, not a repeated date. */
@Composable
private fun findingFor(state: DashboardState): String {
    val logged = formatCount(state.kcalConsumed)
    if (state.kcalTarget <= 0) return stringResource(R.string.home_no_target, logged)
    val remaining = state.kcalTarget - state.kcalConsumed
    return if (remaining >= 0) {
        stringResource(R.string.home_logged_remaining, logged, formatCount(remaining))
    } else {
        stringResource(R.string.home_logged_over, logged, formatCount(-remaining))
    }
}

@Composable
private fun BudgetSection(state: DashboardState, onEditGoal: () -> Unit) {
    val recalculation = state.goalRecalculation
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_budget),
            action = if (recalculation == null) stringResource(R.string.home_edit_goal) else null,
            onAction = onEditGoal,
        )
        Spacer(Modifier.height(Spacing.sm))
        EnergyBudget(
            consumed = state.kcalConsumed,
            target = state.kcalTarget,
            protein = state.proteinG to state.proteinTarget,
            carbs = state.carbG to state.carbTarget,
            fat = state.fatG to state.fatTarget,
            modifier = Modifier.fillMaxWidth(),
        )
        if (recalculation != null) {
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.home_adjusted,
                            formatCount(recalculation.appliedKcal),
                            formatCount(recalculation.suggestedKcal),
                        ),
                        style = MaterialTheme.typography.bodyStrong,
                        color = MetriqColors.ink,
                        maxLines = 2,
                    )
                    Text(
                        text = warningText(recalculation.summary),
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                        maxLines = 3,
                    )
                    recalculation.warnings.forEach { warning ->
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = warningText(warning),
                            style = MaterialTheme.typography.caption,
                            color = MetriqColors.ink2,
                            maxLines = 3,
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = stringResource(R.string.home_edit_goal),
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onEditGoal() }.padding(vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun AdaptationSection(state: DashboardState) {
    val adaptation = state.macroAdaptation ?: return
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_adaptation),
                style = MaterialTheme.typography.h2,
                color = MetriqColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = intensityLabel(adaptation.intensity),
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = warningText(adaptation.reason),
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
            maxLines = 2,
        )
    }
}

/** Compact, honest training readout — real numbers only, no tinted icon slots. */
@Composable
private fun TrainingSection(state: DashboardState) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.training))
        Spacer(Modifier.height(Spacing.sm))
        val signal = state.trainingSignal
        val muscles = signal.muscles.sorted().joinToString(", ")
        Text(
            text = muscles.ifEmpty { stringResource(R.string.home_no_muscles) },
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink2,
            maxLines = 2,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = String.format(Locale.ENGLISH, "%.0f", signal.volumeKg.toDouble()) +
                " kg · " + signal.durationMinutes.toString().removeSuffix(".0") +
                " min · " + intensityLabel(signal.intensity),
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeekSection(state: DashboardState) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.home_this_week))
        Spacer(Modifier.height(Spacing.sm))
        WeekStrip(state.week, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FoodLogSection(
    state: DashboardState,
    onAddFood: () -> Unit,
    onDeleteLog: (Long) -> Unit,
    onEditLog: (Long, Double) -> Unit,
) {
    var editingId by remember { mutableStateOf<Long?>(null) }
    var gramsText by remember { mutableStateOf("") }
    var invalidGrams by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_food_log),
            action = if (state.logItems.isEmpty()) stringResource(R.string.add) else stringResource(R.string.home_view_all),
            onAction = onAddFood,
        )
        Spacer(Modifier.height(Spacing.sm))
        if (state.logItems.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MetriqColors.border, RoundedCornerShape(Radius.sm))
                    .padding(horizontal = Spacing.md, vertical = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.nothing_logged),
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink3,
                    maxLines = 2,
                )
            }
        } else {
            val items = state.logItems.take(3)
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodRow(
                        name = item.name,
                        meta = String.format(
                            Locale.ENGLISH,
                            "%02d:00 · %s kcal · %d g",
                            item.hour,
                            formatCount(item.calories.toInt()),
                            item.grams.toInt(),
                        ),
                        icon = null,
                        onClick = onAddFood,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.edit),
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.accent,
                        modifier = Modifier
                            .clickable {
                                editingId = item.id
                                gramsText = gramsInput(item.grams)
                                invalidGrams = false
                            }
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    IconButton(
                        onClick = { onDeleteLog(item.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.remove),
                            tint = MetriqColors.ink3,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                }
            }
        }
        if (editingId != null) {
            AlertDialog(
                onDismissRequest = { editingId = null },
                title = { Text(stringResource(R.string.edit_log_title)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = gramsText,
                            onValueChange = { gramsText = it; invalidGrams = false },
                            label = { Text(stringResource(R.string.grams)) },
                            singleLine = true,
                            isError = invalidGrams,
                            shape = RoundedCornerShape(Radius.sm),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (invalidGrams) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                stringResource(R.string.edit_grams_invalid),
                                style = MaterialTheme.typography.caption,
                                color = MetriqColors.danger,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val grams = parseDoubleLoose(gramsText)
                        if (grams == null || grams <= 0.0) {
                            invalidGrams = true
                        } else {
                            editingId?.let { onEditLog(it, grams) }
                            editingId = null
                        }
                    }) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun TrendSection(state: DashboardState) {
    val rate = state.trendChangePerWeek
    val rateText = when {
        rate == null -> stringResource(R.string.home_collecting)
        abs(rate) < 0.02 -> stringResource(R.string.home_stable)
        rate < 0 -> stringResource(R.string.home_trend_down, format2(abs(rate)))
        else -> stringResource(R.string.home_trend_up, format2(rate))
    }
    val rateColor = when {
        rate == null || abs(rate) < 0.02 -> MetriqColors.ink3
        state.goalLabel.equals("cut", ignoreCase = true) && rate < 0 -> MetriqColors.success
        state.goalLabel.equals("bulk", ignoreCase = true) && rate > 0 -> MetriqColors.success
        else -> MetriqColors.danger
    }
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.home_trend))
        Spacer(Modifier.height(Spacing.sm))
        Panel {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = format1(state.trendKg),
                    style = MaterialTheme.typography.number24.copy(fontFeatureSettings = "tnum"),
                    color = MetriqColors.ink,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "kg",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                text = rateText,
                style = MaterialTheme.typography.caption,
                color = rateColor,
                maxLines = 1,
            )
            Spacer(Modifier.height(Spacing.sm))
            TrendChart(
                values = state.trendSeries,
                color = MetriqColors.ink2,
                modifier = Modifier.fillMaxWidth(),
                showPoints = false,
            )
        }
    }
}

@Composable
private fun ExpenditureSection(state: DashboardState) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.metric_expenditure))
        Spacer(Modifier.height(Spacing.sm))
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_expenditure),
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatCount(state.expenditureKcal),
                    style = MaterialTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"),
                    color = MetriqColors.ink,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "kcal",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 1,
                )
            }
            Text(
                text = "${confidenceLabel(state.expenditureConfidence)} ${stringResource(R.string.confidence_suffix)}",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 1,
            )
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.profile_goal),
                    style = MaterialTheme.typography.body,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = goalLabel(state.goalLabel),
                    style = MaterialTheme.typography.bodyStrong,
                    color = MetriqColors.ink,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HealthSection(state: DashboardState) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.home_health))
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HealthStat(
                label = stringResource(R.string.steps),
                value = state.stepsToday?.let { formatCount(it.toInt()) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(thickness = 1.dp, color = MetriqColors.border, modifier = Modifier.height(44.dp))
            HealthStat(
                label = stringResource(R.string.home_active_kcal),
                value = state.healthCalories?.let { formatCount(it.toInt()) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(thickness = 1.dp, color = MetriqColors.border, modifier = Modifier.height(44.dp))
            HealthStat(
                label = stringResource(R.string.home_sessions),
                value = state.healthExerciseSessions?.toString() ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HealthStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"),
            color = MetriqColors.ink,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
            maxLines = 1,
        )
    }
}

@Composable
private fun CheckinSection(state: DashboardState, onAccept: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.home_checkin))
        Spacer(Modifier.height(Spacing.sm))
        Panel {
            Text(
                text = if (state.checkinAccepted) stringResource(R.string.checkin_complete)
                else stringResource(R.string.home_target_text, formatCount(state.kcalTarget)),
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink,
                maxLines = 4,
            )
            if (!state.checkinAccepted) {
                Spacer(Modifier.height(Spacing.md))
                MetriqButton(text = stringResource(R.string.accept), onClick = onAccept)
            }
        }
    }
}

@Composable
private fun intensityLabel(intensity: TrainingIntensity): String = when (intensity) {
    TrainingIntensity.REST -> stringResource(R.string.intensity_rest)
    TrainingIntensity.LIGHT -> stringResource(R.string.intensity_light)
    TrainingIntensity.MODERATE -> stringResource(R.string.intensity_moderate)
    TrainingIntensity.HARD -> stringResource(R.string.intensity_hard)
}

@Composable
private fun confidenceLabel(value: String): String = when (value) {
    "high" -> stringResource(R.string.confidence_high)
    "medium" -> stringResource(R.string.confidence_medium)
    "low" -> stringResource(R.string.confidence_low)
    else -> value.replaceFirstChar { it.uppercase(Locale.ENGLISH) }
}

@Composable
private fun goalLabel(value: String): String = when (value) {
    "cut" -> stringResource(R.string.goal_cut)
    "maintain" -> stringResource(R.string.goal_maintain)
    "bulk" -> stringResource(R.string.goal_bulk)
    else -> value.replaceFirstChar { it.uppercase(Locale.ENGLISH) }
}

private fun format1(value: Double): String = String.format(Locale.ENGLISH, "%.1f", value)

private fun format2(value: Double): String = String.format(Locale.ENGLISH, "%.2f", value)
