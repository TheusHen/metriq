package com.metriq.app.ui.home

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metriq.app.R
import com.metriq.app.Screen
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
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.h2
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number24
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(
    state: DashboardState,
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onAddFood: () -> Unit,
    onDeleteLog: (Long) -> Unit,
    onAcceptCheckin: () -> Unit,
    onEditGoal: () -> Unit = {},
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
                        .padding(horizontal = Metrics.pageGutter, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StatusRow(state.dateLabel)
                    ScreenHeader(
                        title = stringResource(R.string.today),
                        subtitle = findingFor(state),
                    )

                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                BudgetSection(state, onEditGoal)
                                AdaptationSection(state)
                                TrainingSection(state)
                                WeekSection(state)
                                FoodLogSection(state, onAddFood, onDeleteLog)
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        FoodLogSection(state, onAddFood, onDeleteLog)
                        TrendSection(state)
                        ExpenditureSection(state)
                        HealthSection(state)
                        CheckinSection(state, onAcceptCheckin)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/** Product mark and one freshness stamp. The date appears nowhere else on the screen. */
@Composable
private fun StatusRow(dateLabel: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "METRIQ",
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink2,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$dateLabel · " +
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-PT"))),
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
            maxLines = 1,
        )
    }
}

/** The line under the title is a finding from today's numbers, not a repeated date. */
private fun findingFor(state: DashboardState): String {
    val logged = formatCount(state.kcalConsumed)
    if (state.kcalTarget <= 0) return "$logged kcal registadas · meta não definida"
    val remaining = state.kcalTarget - state.kcalConsumed
    return if (remaining >= 0) {
        "$logged kcal registadas · ${formatCount(remaining)} kcal disponíveis"
    } else {
        "$logged kcal registadas · ${formatCount(-remaining)} kcal acima da meta"
    }
}

@Composable
private fun BudgetSection(state: DashboardState, onEditGoal: () -> Unit) {
    val recalculation = state.goalRecalculation
    SectionHeader(
        title = "Orçamento energético",
        action = if (recalculation == null) "Editar meta" else null,
        onAction = onEditGoal,
    )
    Spacer(Modifier.height(8.dp))
    EnergyBudget(
        consumed = state.kcalConsumed,
        target = state.kcalTarget,
        protein = state.proteinG to state.proteinTarget,
        carbs = state.carbG to state.carbTarget,
        fat = state.fatG to state.fatTarget,
        modifier = Modifier.fillMaxWidth(),
    )
    if (recalculation != null) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Meta ajustada: ${formatCount(recalculation.appliedKcal)} kcal " +
                        "(sugerido ${formatCount(recalculation.suggestedKcal)})",
                    style = MaterialTheme.typography.bodyStrong,
                    color = MetriqColors.ink,
                    maxLines = 2,
                )
                Text(
                    text = recalculation.summary,
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 3,
                )
                recalculation.warnings.forEach { warning ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink2,
                        maxLines = 3,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Editar meta",
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.clickable { onEditGoal() }.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AdaptationSection(state: DashboardState) {
    val adaptation = state.macroAdaptation ?: return
    val intensity = adaptation.intensity.name
        .lowercase(Locale.ENGLISH)
        .replaceFirstChar { it.uppercase() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Macro adaptation",
            style = MaterialTheme.typography.h2,
            color = MetriqColors.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(text = intensity, style = MaterialTheme.typography.caption, color = MetriqColors.ink3)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = adaptation.reason,
        style = MaterialTheme.typography.body,
        color = MetriqColors.ink2,
        maxLines = 2,
    )
}

/** Compact, honest training readout — real numbers only, no tinted icon slots. */
@Composable
private fun TrainingSection(state: DashboardState) {
    SectionHeader(title = "Treino")
    Spacer(Modifier.height(6.dp))
    val signal = state.trainingSignal
    val muscles = signal.muscles.sorted().joinToString(", ")
    Text(
        text = muscles.ifEmpty { "Sem grupos musculares registrados" },
        style = MaterialTheme.typography.body,
        color = MetriqColors.ink2,
        maxLines = 2,
    )
    Spacer(Modifier.height(4.dp))
    val intensity = signal.intensity.toString()
        .lowercase(Locale.ENGLISH)
        .replaceFirstChar { it.uppercase(Locale.ENGLISH) }
    Text(
        text = String.format(Locale.ENGLISH, "%.0f", signal.volumeKg.toDouble()) +
            " kg · " + signal.durationMinutes.toString().removeSuffix(".0") + " min · " + intensity,
        style = MaterialTheme.typography.caption,
        color = MetriqColors.ink3,
        maxLines = 1,
    )
}

@Composable
private fun WeekSection(state: DashboardState) {
    SectionHeader(title = "Esta semana")
    Spacer(Modifier.height(8.dp))
    WeekStrip(state.week, Modifier.fillMaxWidth())
}

@Composable
private fun FoodLogSection(
    state: DashboardState,
    onAddFood: () -> Unit,
    onDeleteLog: (Long) -> Unit,
) {
    SectionHeader(
        title = "Registo alimentar",
        action = if (state.logItems.isEmpty()) "Adicionar" else "View all",
        onAction = onAddFood,
    )
    Spacer(Modifier.height(8.dp))
    if (state.logItems.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MetriqColors.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
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
                IconButton(
                    onClick = { onDeleteLog(item.id) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove",
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
}

@Composable
private fun TrendSection(state: DashboardState) {
    val rate = state.trendChangePerWeek
    val rateText = when {
        rate == null -> "A recolher dados"
        abs(rate) < 0.02 -> "Holding steady"
        rate < 0 -> "A descer ${format2(abs(rate))} kg por semana"
        else -> "A subir ${format2(rate)} kg por semana"
    }
    val rateColor = when {
        rate == null || abs(rate) < 0.02 -> MetriqColors.ink3
        state.goalLabel.equals("Loss", ignoreCase = true) && rate < 0 -> MetriqColors.success
        state.goalLabel.equals("Gain", ignoreCase = true) && rate > 0 -> MetriqColors.success
        else -> MetriqColors.danger
    }
    SectionHeader(title = "Tendência de peso")
    Spacer(Modifier.height(8.dp))
    Panel {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = format1(state.trendKg),
                style = MaterialTheme.typography.number24.copy(fontFeatureSettings = "tnum"),
                color = MetriqColors.ink,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
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
        Spacer(Modifier.height(8.dp))
        TrendChart(
            values = state.trendSeries,
            color = MetriqColors.ink2,
            modifier = Modifier.fillMaxWidth(),
            showPoints = false,
        )
    }
}

@Composable
private fun ExpenditureSection(state: DashboardState) {
    SectionHeader(title = "Gasto")
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Estimated burn",
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
            Spacer(Modifier.width(3.dp))
            Text(
                text = "kcal",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 1,
            )
        }
        Text(
            text = "${state.expenditureConfidence.replaceFirstChar { it.uppercase() }} confidence",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Objetivo",
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = state.goalLabel,
                style = MaterialTheme.typography.bodyStrong,
                color = MetriqColors.ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HealthSection(state: DashboardState) {
    SectionHeader(title = "Saúde")
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HealthStat(
            label = "Passos",
            value = state.stepsToday?.let { formatCount(it.toInt()) } ?: "—",
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(thickness = 1.dp, color = MetriqColors.border, modifier = Modifier.height(44.dp))
        HealthStat(
            label = "Active kcal",
            value = state.healthCalories?.let { formatCount(it.toInt()) } ?: "—",
            modifier = Modifier.weight(1f),
        )
        VerticalDivider(thickness = 1.dp, color = MetriqColors.border, modifier = Modifier.height(44.dp))
        HealthStat(
            label = "Sessions",
            value = state.healthExerciseSessions?.toString() ?: "—",
            modifier = Modifier.weight(1f),
        )
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
    SectionHeader(title = "Check-in semanal")
    Spacer(Modifier.height(8.dp))
    Panel {
        Text(
            text = if (state.checkinAccepted) stringResource(R.string.checkin_complete)
            else "Com base no teu gasto atualizado, a tua meta é de ${formatCount(state.kcalTarget)} kcal por dia.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink,
            maxLines = 4,
        )
        if (!state.checkinAccepted) {
            Spacer(Modifier.height(12.dp))
            MetriqButton(text = stringResource(R.string.accept), onClick = onAccept)
        }
    }
}

private fun format1(value: Double): String = String.format(Locale.ENGLISH, "%.1f", value)

private fun format2(value: Double): String = String.format(Locale.ENGLISH, "%.2f", value)
