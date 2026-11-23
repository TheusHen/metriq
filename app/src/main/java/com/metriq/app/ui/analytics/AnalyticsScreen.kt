package com.metriq.app.ui.analytics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metriq.app.AppSettings
import com.metriq.app.ui.components.MetriqButton
import com.metriq.app.ui.components.Panel
import com.metriq.app.ui.components.ScreenHeader
import com.metriq.app.ui.components.SectionHeader
import com.metriq.app.ui.components.charts.DataChart
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.app.ui.theme.caption
import com.metriq.app.ui.theme.label
import com.metriq.app.ui.theme.number24
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RANGES = listOf(7, 30, 90, 365, 0)
private val RANGE_LABELS = listOf("7D", "30D", "90D", "1Y", "All")
private val GRANULARITIES = listOf("day", "week", "month")
private val GRANULARITY_LABELS = listOf("Day", "Week", "Month")
private val CHART_TYPES = listOf("line", "bar")
private val CHART_TYPE_LABELS = listOf("Line", "Bars")

/**
 * Responsive analytics view: centred 720 dp column, 20 dp gutters, two columns from
 * 600 dp up. All filtering lives in the view model so export and UI see the same data.
 */
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val reducedMotion = AppSettings.bool(AppSettings.REDUCED_MOTION)

    Box(
        Modifier
            .fillMaxSize()
            .background(MetriqColors.bg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            ScreenHeader(
                title = "Analytics",
                subtitle = "From your own logged data",
                onBack = onBack,
                trailing = {
                    MetriqButton(text = "Export", onClick = onExport, primary = false)
                },
            )
            state.etaText?.let { text ->
                Text(
                    text = "Goal ETA · $text",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val scroll = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                if (maxWidth >= 600.dp) {
                    Row(scroll, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Filters(state, viewModel)
                            Spacer(Modifier.height(16.dp))
                            ChartPanel(state, viewModel, reducedMotion)
                            Spacer(Modifier.height(16.dp))
                            ContributorsPanel(state)
                            Spacer(Modifier.height(16.dp))
                            TimingPanel(state)
                        }
                        Column(Modifier.weight(1f)) {
                            StatsPanel(state)
                            Spacer(Modifier.height(16.dp))
                            AveragesPanel(state)
                            Spacer(Modifier.height(16.dp))
                            MacroSplitPanel(state.macroSplit)
                            Spacer(Modifier.height(16.dp))
                            DataTablePanel(state, viewModel::selectPoint)
                        }
                    }
                } else {
                    Column(scroll) {
                        Filters(state, viewModel)
                        Spacer(Modifier.height(16.dp))
                        ChartPanel(state, viewModel, reducedMotion)
                        Spacer(Modifier.height(16.dp))
                        StatsPanel(state)
                        Spacer(Modifier.height(16.dp))
                        AveragesPanel(state)
                        Spacer(Modifier.height(16.dp))
                        MacroSplitPanel(state.macroSplit)
                        Spacer(Modifier.height(16.dp))
                        ContributorsPanel(state)
                        Spacer(Modifier.height(16.dp))
                        TimingPanel(state)
                        Spacer(Modifier.height(16.dp))
                        DataTablePanel(state, viewModel::selectPoint)
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(state: AnalyticsState, viewModel: AnalyticsViewModel) {
    Panel {
        SectionHeader("Metric")
        Spacer(Modifier.height(10.dp))
        ChipRow {
            state.metrics.forEach { (id, label) ->
                FilterPill(label, selected = state.metricId == id, onClick = { viewModel.setMetric(id) })
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionHeader("Range")
        Spacer(Modifier.height(10.dp))
        ChipRow {
            RANGES.forEachIndexed { index, days ->
                FilterPill(
                    label = RANGE_LABELS[index],
                    selected = state.rangeDays == days,
                    onClick = { viewModel.setRange(days) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("GRANULARITY", style = MaterialTheme.typography.caption, color = MetriqColors.ink3)
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    GRANULARITIES.forEachIndexed { index, value ->
                        FilterPill(
                            label = GRANULARITY_LABELS[index],
                            selected = state.granularity == value,
                            onClick = { viewModel.setGranularity(value) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text("CHART", style = MaterialTheme.typography.caption, color = MetriqColors.ink3)
                Spacer(Modifier.height(8.dp))
                ChipRow {
                    CHART_TYPES.forEachIndexed { index, value ->
                        FilterPill(
                            label = CHART_TYPE_LABELS[index],
                            selected = state.chartType == value,
                            onClick = { viewModel.setChartType(value) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionHeader("Compare")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Overlays a second metric on its own scale.",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
        Spacer(Modifier.height(10.dp))
        ChipRow {
            FilterPill("None", selected = state.compareId == null, onClick = { viewModel.setCompare(null) })
            state.metrics.forEach { (id, label) ->
                FilterPill(label, selected = state.compareId == id, onClick = { viewModel.setCompare(id) })
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) MetriqColors.accent else MetriqColors.surface2,
        animationSpec = tween(180),
        label = "filterBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) MetriqColors.accentInk else MetriqColors.ink2,
        animationSpec = tween(180),
        label = "filterForeground",
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.label,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChartPanel(state: AnalyticsState, viewModel: AnalyticsViewModel, reducedMotion: Boolean) {
    Panel {
        SectionHeader(
            title = AnalyticsFormat.label(state.metricId),
            action = AnalyticsFormat.unit(state.metricId),
        )
        Spacer(Modifier.height(12.dp))
        val pattern = remember(state.granularity) {
            DateTimeFormatter.ofPattern(
                if (state.granularity == "month") "MMM ''yy" else "MMM d",
                Locale.ENGLISH,
            )
        }
        DataChart(
            points = state.points,
            primaryColor = AnalyticsFormat.color(state.metricId),
            comparePoints = state.comparePoints,
            compareColor = state.compareId?.let { AnalyticsFormat.color(it) } ?: Color.Unspecified,
            chartType = state.chartType,
            selectedIndex = state.selectedIndex,
            onSelect = viewModel::selectPoint,
            metricId = state.metricId,
            compareMetricId = state.compareId,
            xLabel = { it.format(pattern) },
            reducedMotion = reducedMotion,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendKey(AnalyticsFormat.label(state.metricId), AnalyticsFormat.color(state.metricId))
            state.compareId?.let { id ->
                LegendKey("${AnalyticsFormat.label(id)} · own scale", AnalyticsFormat.color(id))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (state.points.isEmpty()) {
                "No logged data for this range."
            } else {
                "Tap or drag across the chart to inspect a point."
            },
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
    }
}

@Composable
private fun LegendKey(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsPanel(state: AnalyticsState) {
    Panel {
        SectionHeader("Overview")
        Spacer(Modifier.height(12.dp))
        state.stats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { stat -> StatCard(stat, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StatCard(stat: AnalyticsStat, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MetriqColors.surface2)
            .padding(12.dp),
    ) {
        Text(
            text = stat.label,
            style = MaterialTheme.typography.label,
            color = MetriqColors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stat.value,
            style = MaterialTheme.typography.number24,
            color = MetriqColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!stat.note.isNullOrBlank()) {
            Text(
                text = stat.note,
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MacroSplitPanel(split: List<Triple<String, Double, Color>>) {
    Panel {
        SectionHeader("Macro split")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Logged grams across the current range.",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
        Spacer(Modifier.height(12.dp))
        val total = split.sumOf { it.second }
        if (total <= 0.0) {
            Text(text = "—", style = MaterialTheme.typography.number24, color = MetriqColors.ink3)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MetriqColors.surface2),
            ) {
                split.forEach { (_, grams, color) ->
                    if (grams > 0.0) {
                        Box(
                            Modifier
                                .weight(grams.toFloat())
                                .fillMaxHeight()
                                .background(color),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            split.forEach { (name, grams, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.label,
                        color = MetriqColors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${AnalyticsFormat.cell(grams)} g",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${(grams / total * 100).toInt()}%",
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink2,
                        maxLines = 1,
                        modifier = Modifier.width(40.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ContributorsPanel(state: AnalyticsState) {
    Panel {
        SectionHeader("Top contributors", action = AnalyticsFormat.label(state.metricId))
        Spacer(Modifier.height(4.dp))
        if (state.topContributors.isEmpty()) {
            Text(
                text = "No food data for this metric in this range.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
            return@Panel
        }
        val unit = AnalyticsFormat.unit(state.metricId)
        state.topContributors.forEach { (name, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.label,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${AnalyticsFormat.value(state.metricId, value)} $unit",
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AveragesPanel(state: AnalyticsState) {
    Panel {
        SectionHeader("Averages", action = AnalyticsFormat.unit(state.metricId))
        Spacer(Modifier.height(8.dp))
        if (state.averages.isEmpty()) {
            Text(
                text = "No data in this range.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
            return@Panel
        }
        state.averages.forEach { (label, period, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.label,
                    color = MetriqColors.ink2,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = period.uppercase(Locale.ENGLISH),
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink3,
                    maxLines = 1,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    text = AnalyticsFormat.value(state.metricId, value),
                    style = MaterialTheme.typography.caption,
                    color = MetriqColors.ink,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(92.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun TimingPanel(state: AnalyticsState) {
    Panel {
        SectionHeader("Timing", action = "kcal / 4 h")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Calories logged by time of day.",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
        Spacer(Modifier.height(12.dp))
        val timing = state.timing
        if (timing.isEmpty()) {
            Text(
                text = "No meal timing data in this range.",
                style = MaterialTheme.typography.caption,
                color = MetriqColors.ink3,
            )
            return@Panel
        }
        val peak = timing.maxOfOrNull { it.second } ?: 0.0
        val peakHour = timing.maxByOrNull { it.second }?.first
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            timing.forEach { (hour, kcal) ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MetriqColors.surface2),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (kcal > 0.0 && peak > 0.0) {
                            val barHeight = maxOf(3.0, 64.0 * (kcal / peak).coerceIn(0.0, 1.0)).dp
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MetriqColors.accent),
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = hour.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.caption,
                        color = MetriqColors.ink3,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (peak > 0.0 && peakHour != null) {
                "Peak ${peakHour.toString().padStart(2, '0')}:00 · ${AnalyticsFormat.cell(peak)} kcal"
            } else {
                "No calories logged in this range."
            },
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
    }
}

@Composable
private fun DataTablePanel(state: AnalyticsState, onSelect: (Int?) -> Unit) {
    Panel {
        SectionHeader("Data")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${state.rows.size} ${if (state.rows.size == 1) "point" else "points"} · Δ vs previous",
            style = MaterialTheme.typography.caption,
            color = MetriqColors.ink3,
        )
        Spacer(Modifier.height(10.dp))
        if (state.rows.isEmpty()) {
            Text(text = "—", style = MaterialTheme.typography.number24, color = MetriqColors.ink3)
            return@Panel
        }
        val numbers = MaterialTheme.typography.caption.copy(fontFeatureSettings = "tnum")
        val heads = MaterialTheme.typography.label
        val compareId = state.compareId
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text("DATE", style = heads, color = MetriqColors.ink3, modifier = Modifier.weight(1.1f))
            Text(
                "VALUE", style = heads, color = MetriqColors.ink3, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Δ", style = heads, color = MetriqColors.ink3, textAlign = TextAlign.End,
                modifier = Modifier.weight(0.9f),
            )
            if (compareId != null) {
                Text(
                    "CMP", style = heads, color = MetriqColors.ink3, textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.9f),
                )
            }
        }
        val compareByDate = state.comparePoints.associateBy { it.date }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.rows.forEachIndexed { index, row ->
                val selected = state.selectedIndex == index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MetriqColors.surface2 else Color.Transparent)
                        .clickable { onSelect(if (selected) null else index) }
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = AnalyticsFormat.date(row.date),
                        style = numbers,
                        color = MetriqColors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.1f),
                    )
                    Text(
                        text = AnalyticsFormat.value(state.metricId, row.value),
                        style = numbers,
                        color = MetriqColors.ink,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.delta?.let { AnalyticsFormat.signed(state.metricId, it) } ?: "—",
                        style = numbers,
                        color = if (row.delta != null && row.delta > 0) MetriqColors.accent else MetriqColors.ink2,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(0.9f),
                    )
                    if (compareId != null) {
                        val compare = compareByDate[row.date]
                        Text(
                            text = compare?.let { AnalyticsFormat.value(compareId, it.value) } ?: "—",
                            style = numbers,
                            color = MetriqColors.ink2,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                }
            }
        }
    }
}
