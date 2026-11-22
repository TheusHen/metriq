package com.metriq.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.Metrics
import com.metriq.app.ui.theme.body
import com.metriq.app.ui.theme.bodyStrong
import com.metriq.app.ui.theme.caption
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
                        .padding(horizontal = Metrics.pageGutter, vertical = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusRow(state.dateLabel)
                    ScreenHeader(
                        title = stringResource(R.string.today),
                        subtitle = state.dateLabel,
                        trailing = { Avatar() },
                    )

                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                BudgetPanel(state)
                                AdaptationPanel(state)
                                WeekStrip(state.week, Modifier.fillMaxWidth())
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                FoodLogPanel(state, onAddFood, onDeleteLog)
                                TrendPanel(state)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) { ExpenditurePanel(state) }
                            Column(Modifier.weight(1f)) { HealthPanel(state) }
                        }
                        CheckinPanel(state, onAcceptCheckin)
                    } else {
                        BudgetPanel(state)
                        AdaptationPanel(state)
                        WeekStrip(state.week, Modifier.fillMaxWidth())
                        FoodLogPanel(state, onAddFood, onDeleteLog)
                        TrendPanel(state)
                        ExpenditurePanel(state)
                        HealthPanel(state)
                        CheckinPanel(state, onAcceptCheckin)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusRow(dateLabel: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "METRIQ",
            style = MaterialTheme.typography.label,
            color = MetriqColors.accent,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = dateLabel.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)),
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink2,
            maxLines = 1,
        )
    }
}

@Composable
private fun Avatar() {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MetriqColors.surface2)
            .border(1.dp, MetriqColors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "M",
            style = MaterialTheme.typography.bodyStrong,
            color = MetriqColors.accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun AdaptationPanel(state: DashboardState) {
    val adaptation = state.macroAdaptation ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MetriqColors.surface1)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MACRO ADAPTATION",
                style = MaterialTheme.typography.label,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = adaptation.intensity.name,
                style = MaterialTheme.typography.label,
                color = if (adaptation.intensity == com.metriq.core.TrainingIntensity.REST) MetriqColors.ink3
                else MetriqColors.accent,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = adaptation.reason,
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink,
            maxLines = 2,
        )
    }
}

@Composable
private fun BudgetPanel(state: DashboardState) {
    EnergyBudget(
        consumed = state.kcalConsumed,
        target = state.kcalTarget,
        protein = state.proteinG to state.proteinTarget,
        carbs = state.carbG to state.carbTarget,
        fat = state.fatG to state.fatTarget,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FoodLogPanel(
    state: DashboardState,
    onAddFood: () -> Unit,
    onDeleteLog: (Long) -> Unit,
) {
    Panel {
        SectionHeader(
            title = "Food log",
            action = if (state.logItems.isEmpty()) "Add" else "View all",
            onAction = onAddFood,
        )
        Spacer(Modifier.height(16.dp))
        if (state.logItems.isEmpty()) {
            Text(
                text = stringResource(R.string.nothing_logged),
                style = MaterialTheme.typography.body,
                color = MetriqColors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            state.logItems.take(3).forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(thickness = 1.dp, color = MetriqColors.border)
                    Spacer(Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoodRow(
                        name = item.name,
                        meta = String.format(
                            Locale.ENGLISH,
                            "%02d:00 · %d kcal · %d g",
                            item.hour,
                            item.calories.toInt(),
                            item.grams.toInt(),
                        ),
                        icon = "🍽",
                        onClick = onAddFood,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteLog(item.id) }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Remove",
                            tint = MetriqColors.ink3,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendPanel(state: DashboardState) {
    val rate = state.trendChangePerWeek
    val rateText = when {
        rate == null -> "Gathering data"
        abs(rate) < 0.02 -> "Holding steady"
        rate < 0 -> "Down ${format2(abs(rate))} kg per week"
        else -> "Up ${format2(rate)} kg per week"
    }
    Panel {
        SectionHeader(title = "Weight trend")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = format1(state.trendKg),
                style = MaterialTheme.typography.number24,
                color = MetriqColors.ink,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "kg",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink2,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            text = rateText,
            style = MaterialTheme.typography.caption,
            color = if (rate != null && rate < -0.02) MetriqColors.success else MetriqColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        TrendChart(
            values = state.trendSeries,
            color = MetriqColors.trend,
            modifier = Modifier.fillMaxWidth(),
            showPoints = true,
        )
    }
}

@Composable
private fun ExpenditurePanel(state: DashboardState) {
    Panel {
        Text(
            text = "EXPENDITURE",
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink2,
            maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.expenditureKcal.toString(),
                        style = MaterialTheme.typography.number24,
                        color = MetriqColors.ink,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "kcal",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink2,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = "Estimated · ${state.expenditureConfidence} confidence",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "GOAL",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 1,
                )
                Text(
                    text = state.goalLabel,
                    style = MaterialTheme.typography.bodyStrong,
                    color = MetriqColors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HealthPanel(state: DashboardState) {
    Panel {
        SectionHeader(title = "Health")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthStat("STEPS", state.stepsToday?.toString() ?: "—", Modifier.weight(1f))
            HealthStat(
                "ACTIVE KCAL",
                state.healthCalories?.let { it.toInt().toString() } ?: "—",
                Modifier.weight(1f),
            )
            HealthStat(
                "SESSIONS",
                state.healthExerciseSessions?.toString() ?: "—",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HealthStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.number24,
            color = MetriqColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CheckinPanel(state: DashboardState, onAccept: () -> Unit) {
    Panel {
        Text(
            text = "WEEKLY CHECK-IN",
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink2,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.checkinAccepted) stringResource(R.string.checkin_complete)
            else "Based on your updated expenditure, your target is ${state.kcalTarget} kcal per day.",
            style = MaterialTheme.typography.body,
            color = MetriqColors.ink,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (!state.checkinAccepted) {
            Spacer(Modifier.height(12.dp))
            MetriqButton(text = stringResource(R.string.accept), onClick = onAccept)
        }
    }
}

private fun format1(value: Double): String = String.format(Locale.ENGLISH, "%.1f", value)

private fun format2(value: Double): String = String.format(Locale.ENGLISH, "%.2f", value)
