package com.metriq.app.ui.analytics

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metriq.app.AppGraph
import com.metriq.app.AppSettings
import com.metriq.app.DefaultProfile
import com.metriq.app.GoalStore
import com.metriq.app.ui.theme.MetriqColors
import com.metriq.core.Bmr
import com.metriq.core.Expenditure
import com.metriq.core.ExpenditureResult
import com.metriq.core.TrendWeight
import com.metriq.data.LogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class DataPoint(val date: LocalDate, val value: Double)

data class AnalyticsStat(val label: String, val value: String, val note: String? = null)

data class AnalyticsRow(val date: LocalDate, val value: Double, val delta: Double?)

data class AnalyticsState(
    val metricId: String,
    val rangeDays: Int,
    val granularity: String,
    val chartType: String,
    val compareId: String?,
    val points: List<DataPoint>,
    val comparePoints: List<DataPoint>,
    val stats: List<AnalyticsStat>,
    val rows: List<AnalyticsRow>,
    val selectedIndex: Int?,
    val metrics: List<Pair<String, String>>,
    val macroSplit: List<Triple<String, Double, Color>>,
    /** Top five named foods contributing to the selected metric in the filtered range. */
    val topContributors: List<Pair<String, Double>>,
    /** (label, period, value) — average weekly and monthly totals across the range. */
    val averages: List<Triple<String, String, Double>>,
    /** (4-hour bucket start, logged calories) — intake timing across the range. */
    val timing: List<Pair<Int, Double>>,
    /** Honest, human-readable goal estimate, or null only when nothing can be said. */
    val etaText: String?,
)

private enum class Aggregation { SUM, AVERAGE }

private data class MetricDef(
    val id: String,
    val label: String,
    val unit: String,
    val color: Color,
    val aggregation: Aggregation,
    val decimals: Int,
)

private val METRICS = listOf(
    MetricDef("intake", "Calories", "kcal", MetriqColors.accent, Aggregation.SUM, 0),
    MetricDef("expenditure", "Expenditure", "kcal", MetriqColors.energy, Aggregation.AVERAGE, 0),
    MetricDef("weight", "Weight", "kg", MetriqColors.trend, Aggregation.AVERAGE, 1),
    MetricDef("steps", "Steps", "steps", MetriqColors.accent, Aggregation.SUM, 0),
    MetricDef("water", "Water", "ml", MetriqColors.fat, Aggregation.SUM, 0),
    MetricDef("protein", "Protein", "g", MetriqColors.protein, Aggregation.SUM, 0),
    MetricDef("carbs", "Carbs", "g", MetriqColors.carbs, Aggregation.SUM, 0),
    MetricDef("fat", "Fat", "g", MetriqColors.fat, Aggregation.SUM, 0),
    MetricDef("fiber", "Fiber", "g", MetriqColors.trend, Aggregation.SUM, 1),
    MetricDef("sodium", "Sodium", "mg", MetriqColors.energy, Aggregation.SUM, 0),
)

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val VALID_RANGES = setOf(0, 7, 30, 90, 365)

/** Shared number/unit formatting so chart, table and stats never disagree. */
object AnalyticsFormat {
    fun label(metricId: String): String = def(metricId).label

    fun unit(metricId: String): String = def(metricId).unit

    fun color(metricId: String): Color = def(metricId).color

    fun value(metricId: String, value: Double): String =
        String.format(Locale.ENGLISH, "%,.${def(metricId).decimals}f", value)

    fun signed(metricId: String, value: Double): String =
        (if (value > 0) "+" else "") + value(metricId, value)

    fun cell(value: Double): String = String.format(Locale.ENGLISH, "%,.0f", value)

    fun delta(value: Double): String = (if (value > 0) "+" else "") + cell(value)

    /** Same precision as the UI, without grouping separators that would break CSV columns. */
    fun raw(metricId: String, value: Double): String =
        String.format(Locale.ENGLISH, "%.${def(metricId).decimals}f", value)

    /** Short y-axis ticks; keeps four gridlines legible on a narrow phone. */
    fun axis(value: Double): String = when {
        kotlin.math.abs(value) >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1fm", value / 1_000_000.0)
        kotlin.math.abs(value) >= 10_000 -> String.format(Locale.ENGLISH, "%.0fk", value / 1_000.0)
        kotlin.math.abs(value) >= 1_000 -> String.format(Locale.ENGLISH, "%.1fk", value / 1_000.0)
        kotlin.math.abs(value) < 10 && value % 1.0 != 0.0 -> String.format(Locale.ENGLISH, "%.1f", value)
        else -> String.format(Locale.ENGLISH, "%.0f", value)
    }

    fun date(date: LocalDate): String = date.format(SHORT_DATE)

    private fun def(metricId: String): MetricDef = METRICS.firstOrNull { it.id == metricId } ?: METRICS[0]
}

/**
 * Builds every analytics series from on-device data only. Days with no logged data are
 * omitted rather than padded with zeros, so gaps stay visible in both chart and table.
 */
class AnalyticsViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        AnalyticsState(
            metricId = "intake",
            rangeDays = 30,
            granularity = "day",
            chartType = "line",
            compareId = null,
            points = emptyList(),
            comparePoints = emptyList(),
            stats = emptyList(),
            rows = emptyList(),
            selectedIndex = null,
            metrics = METRICS.map { it.id to it.label },
            macroSplit = emptyList(),
            topContributors = emptyList(),
            averages = emptyList(),
            timing = emptyList(),
            etaText = null,
        ),
    )
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    init {
        val savedRange = AppSettings.get(AppSettings.ANALYTICS_RANGE).toIntOrNull()
        val savedMetric = AppSettings.get(AppSettings.ANALYTICS_METRIC)
        _state.value = buildState(
            metricId = savedMetric.takeIf { id -> METRICS.any { it.id == id } } ?: "intake",
            rangeDays = savedRange?.takeIf { it in VALID_RANGES } ?: 30,
            selectedIndex = null,
        )
        viewModelScope.launch {
            AppGraph.store.notifier.events.collect { refresh() }
        }
    }

    fun setMetric(id: String) {
        val next = id.takeIf { candidate -> METRICS.any { it.id == candidate } } ?: "intake"
        AppGraph.store.settings.set(AppSettings.ANALYTICS_METRIC, next)
        _state.value = buildState(metricId = next)
    }

    fun setRange(days: Int) {
        val next = days.takeIf { it in VALID_RANGES } ?: 30
        AppGraph.store.settings.set(AppSettings.ANALYTICS_RANGE, next.toString())
        _state.value = buildState(rangeDays = next)
    }

    fun setGranularity(value: String) {
        val next = if (value in setOf("day", "week", "month")) value else "day"
        _state.value = buildState(granularity = next)
    }

    fun setChartType(value: String) {
        val next = if (value == "bar") "bar" else "line"
        _state.value = buildState(chartType = next)
    }

    fun setCompare(metricId: String?) {
        val next = metricId?.takeIf { candidate -> METRICS.any { it.id == candidate } }
        _state.value = buildState(compareId = next)
    }

    fun selectPoint(index: Int?) {
        val current = _state.value
        _state.value = current.copy(selectedIndex = index?.takeIf { it in current.points.indices })
    }

    fun refresh() {
        _state.value = buildState(selectedIndex = _state.value.selectedIndex)
    }

    /**
     * CSV of the view as currently filtered: current range, granularity and metric,
     * with the compare series aligned by bucket date. No locale grouping separators and
     * no hidden recomputation, so repeated calls return identical output.
     */
    fun exportCsv(): String {
        val snapshot = _state.value
        val compareByDate = snapshot.comparePoints.associateBy { it.date }
        val lines = ArrayList<String>(snapshot.rows.size + 1)
        lines.add("date,metric,value,compare,delta")
        snapshot.rows.forEach { row ->
            val compare = snapshot.compareId?.let { id ->
                compareByDate[row.date]?.let { AnalyticsFormat.raw(id, it.value) }
            }.orEmpty()
            val delta = row.delta?.let { value ->
                (if (value > 0) "+" else "") + AnalyticsFormat.raw(snapshot.metricId, value)
            }.orEmpty()
            lines.add(
                listOf(
                    row.date.toString(),
                    snapshot.metricId,
                    AnalyticsFormat.raw(snapshot.metricId, row.value),
                    compare,
                    delta,
                ).joinToString(",") { csvCell(it) },
            )
        }
        return lines.joinToString("\n")
    }

    private fun csvCell(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

    private fun buildState(
        metricId: String = _state.value.metricId,
        rangeDays: Int = _state.value.rangeDays,
        granularity: String = _state.value.granularity,
        chartType: String = _state.value.chartType,
        compareId: String? = _state.value.compareId,
        selectedIndex: Int? = null,
    ): AnalyticsState {
        val store = AppGraph.store
        val today = LocalDate.now()
        val intakeAll = store.logs.dailyIntake()
        val weightEntries = store.weights.all().sortedBy { it.date }
        val weightMap = weightEntries.associate { it.date to it.kg }

        val stepRows = store.step.range("1900-01-01", today.toString())
        val waterRows = store.water.range("1900-01-01", today.toString())
        val knownDates = mutableSetOf<LocalDate>()
        knownDates += intakeAll.filterValues { it != null }.keys
        knownDates += weightEntries.map { it.date }
        knownDates += stepRows.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        knownDates += waterRows.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        val earliest = knownDates.minOrNull() ?: today.minusDays(29)
        val from = if (rangeDays == 0) earliest else today.minusDays((rangeDays - 1).toLong())

        val dailyLogs = intakeAll.keys
            .filter { !it.isBefore(from) && !it.isAfter(today) }
            .associateWith { store.logs.forDate(it.toString()) }

        /** One logged food's contribution to a nutrient, scaled by grams and servings. */
        fun microOf(item: LogItem, key: String): Double {
            val factor = item.grams * item.servings / 100.0
            var total = 0.0
            val food = store.food.byId(item.foodId)
            if (key == "fiber") food?.fiber?.let { total += it * factor }
            if (key == "sodium") food?.sodium?.let { total += it * factor }
            store.food.micros(item.foodId)?.let { json ->
                Regex("\"$key\"\\s*:\\s*([0-9.]+)")
                    .find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                    ?.let { total += it * factor }
            }
            return total
        }

        fun microsFor(items: List<LogItem>, key: String): Double = items.sumOf { microOf(it, key) }

        /** Food-derived metrics can be attributed to named foods; the rest cannot. */
        fun contributionOf(item: LogItem, id: String): Double = when (id) {
            "intake" -> item.calories
            "protein" -> item.protein
            "carbs" -> item.carbs
            "fat" -> item.fat
            "fiber", "sodium" -> microOf(item, id)
            else -> 0.0
        }

        // Top contributors: only metrics that come from actual food logs can be attributed.
        val attributable = setOf("intake", "protein", "carbs", "fat", "fiber", "sodium")
        val topContributors = if (metricId in attributable) {
            val totals = LinkedHashMap<String, Double>()
            dailyLogs.values.flatten().forEach { item ->
                val value = contributionOf(item, metricId)
                if (value > 0.0) totals[item.name] = (totals[item.name] ?: 0.0) + value
            }
            totals.toList().sortedByDescending { it.second }.take(5)
        } else {
            emptyList()
        }

        // Timing: logged calories grouped into 4-hour buckets (always intake, not the
        // selected metric). Hours outside 0..23 are clamped so a bad record stays harmless.
        val timing = if (dailyLogs.isEmpty()) {
            emptyList()
        } else {
            val byHour = LinkedHashMap<Int, Double>()
            dailyLogs.values.flatten().forEach { item ->
                val bucket = (item.hour.coerceIn(0, 23) / 4) * 4
                byHour[bucket] = (byHour[bucket] ?: 0.0) + item.calories
            }
            listOf(0, 4, 8, 12, 16, 20).map { it to (byHour[it] ?: 0.0) }
        }

        // Trend is computed once over the widest useful window: expenditure needs 14 days
        // of look-back before the first visible day.
        val trendFrom = minOf(from, weightEntries.firstOrNull()?.date ?: from).minusDays(14)
        val trend = if (weightMap.size >= 2) TrendWeight.compute(weightMap, trendFrom, today) else emptyMap()
        val profile = (store.profile.get() ?: DefaultProfile.PROFILE).let { base ->
            base.copy(weightKg = weightEntries.lastOrNull()?.kg ?: base.weightKg)
        }
        val bmr = Bmr.forProfile(profile)

        val expenditureDaily = LinkedHashMap<LocalDate, Double>()
        if (trend.isNotEmpty()) {
            var previous: ExpenditureResult? = null
            var day = from
            while (!day.isAfter(today)) {
                val eaten = intakeAll[day]
                if (eaten != null && trend.containsKey(day)) {
                    val result = Expenditure.estimate(day, intakeAll, trend, bmr, previous)
                    previous = result
                    // Low-confidence fallbacks are carried estimates, not measurements.
                    if (result.updated) expenditureDaily[day] = result.kcal
                }
                day = day.plusDays(1)
            }
        }

        /** One honest daily series per metric: only dates that actually carry data. */
        fun dailyFor(id: String): Map<LocalDate, Double> = when (id) {
            "intake" -> intakeAll.filterKeys { !it.isBefore(from) && !it.isAfter(today) }
                .filterValues { it != null }.mapValues { it.value!! }

            "expenditure" -> expenditureDaily

            "weight" -> weightMap.filterKeys { !it.isBefore(from) && !it.isAfter(today) }

            "steps" -> stepRows.mapNotNull { row ->
                runCatching { LocalDate.parse(row.date) }.getOrNull()
                    ?.takeIf { !it.isBefore(from) && !it.isAfter(today) }
                    ?.let { it to row.steps.toDouble() }
            }.toMap()

            "water" -> waterRows.mapNotNull { row ->
                runCatching { LocalDate.parse(row.date) }.getOrNull()
                    ?.takeIf { !it.isBefore(from) && !it.isAfter(today) }
                    ?.let { it to row.ml.toDouble() }
            }.toMap()

            "protein" -> dailyLogs.mapValues { (_, items) -> items.sumOf { it.protein } }
            "carbs" -> dailyLogs.mapValues { (_, items) -> items.sumOf { it.carbs } }
            "fat" -> dailyLogs.mapValues { (_, items) -> items.sumOf { it.fat } }
            "fiber" -> dailyLogs.mapValues { (_, items) -> microsFor(items, "fiber") }
            "sodium" -> dailyLogs.mapValues { (_, items) -> microsFor(items, "sodium") }
            else -> emptyMap()
        }

        fun aggregate(values: List<Double>, id: String): Double = when (METRICS.first { it.id == id }.aggregation) {
            Aggregation.SUM -> values.sum()
            Aggregation.AVERAGE -> values.sum() / values.size
        }

        fun bucketedByPeriod(id: String, period: String): Map<LocalDate, Double> {
            val groups = LinkedHashMap<LocalDate, MutableList<Double>>()
            dailyFor(id).toSortedMap().forEach { (date, value) ->
                val bucket = when (period) {
                    "week" -> date.minusDays((date.dayOfWeek.value - 1).toLong())
                    "month" -> date.withDayOfMonth(1)
                    else -> date
                }
                groups.getOrPut(bucket) { mutableListOf() }.add(value)
            }
            return groups.mapValues { (_, values) -> aggregate(values, id) }.toSortedMap()
        }

        fun bucketed(id: String): Map<LocalDate, Double> = bucketedByPeriod(id, granularity)

        fun mean(values: Collection<Double>): Double =
            if (values.isEmpty()) 0.0 else values.sum() / values.size

        val buckets = bucketed(metricId)
        val points = buckets.map { (date, value) -> DataPoint(date, value) }
        val mainDates = points.map { it.date }.toSet()
        val comparePoints = if (compareId == null) {
            emptyList()
        } else {
            bucketed(compareId).filterKeys { it in mainDates }
                .map { (date, value) -> DataPoint(date, value) }
        }
        val safeSelection = selectedIndex?.takeIf { it in points.indices }

        val daily = dailyFor(metricId).toSortedMap().map { (date, value) -> DataPoint(date, value) }
        val total = points.sumOf { it.value }
        val average = points.takeIf { it.isNotEmpty() }?.let { total / it.size }
        val min = points.minOfOrNull { it.value }
        val max = points.maxOfOrNull { it.value }
        val best = daily.maxByOrNull { it.value }
        val latest = daily.lastOrNull()
        val baseline = latest?.let { end -> daily.lastOrNull { it.date <= end.date.minusDays(7) } }
        val change = if (latest != null && baseline != null) latest.value - baseline.value else null

        fun formatted(id: String, value: Double): String = AnalyticsFormat.value(id, value)

        val stats = listOf(
            AnalyticsStat("Total", formatted(metricId, total), AnalyticsFormat.unit(metricId)),
            AnalyticsStat(
                "Average",
                average?.let { formatted(metricId, it) } ?: "—",
                AnalyticsFormat.unit(metricId),
            ),
            AnalyticsStat(
                "Min / Max",
                if (min != null && max != null) {
                    "${formatted(metricId, min)} – ${formatted(metricId, max)}"
                } else {
                    "—"
                },
                AnalyticsFormat.unit(metricId),
            ),
            AnalyticsStat(
                "Best day",
                best?.let { formatted(metricId, it.value) } ?: "—",
                best?.let { AnalyticsFormat.date(it.date) } ?: "no data",
            ),
            AnalyticsStat(
                "7-day change",
                change?.let { AnalyticsFormat.signed(metricId, it) } ?: "—",
                baseline?.let { base ->
                    latest?.let { end ->
                        "from ${formatted(metricId, base.value)} on ${AnalyticsFormat.date(base.date)}" +
                            " → ${AnalyticsFormat.date(end.date)}"
                    }
                } ?: "no 7-day baseline",
            ),
        )

        val rows = points.mapIndexed { index, point ->
            AnalyticsRow(
                date = point.date,
                value = point.value,
                delta = if (index == 0) null else point.value - points[index - 1].value,
            )
        }

        val macroSplit = listOf(
            Triple("Protein", dailyLogs.values.sumOf { items -> items.sumOf { it.protein } }, MetriqColors.protein),
            Triple("Carbs", dailyLogs.values.sumOf { items -> items.sumOf { it.carbs } }, MetriqColors.carbs),
            Triple("Fat", dailyLogs.values.sumOf { items -> items.sumOf { it.fat } }, MetriqColors.fat),
        )

        // Weekly / monthly averages use the same aggregation rule as the chart, then
        // average the resulting buckets. No data -> no invented numbers.
        val averages = if (daily.isEmpty()) {
            emptyList()
        } else {
            listOf(
                Triple(
                    "Weekly average",
                    "week",
                    mean(bucketedByPeriod(metricId, "week").values),
                ),
                Triple(
                    "Monthly average",
                    "month",
                    mean(bucketedByPeriod(metricId, "month").values),
                ),
            )
        }

        // Goal ETA: saved target weight + the same smoothed trend the chart uses.
        val goal = GoalStore.read()
        val targetWeight = goal?.targetWeightKg
        val etaText = when {
            goal == null -> "No weight goal set"
            targetWeight == null -> "Goal has no target weight"
            trend.isEmpty() -> "Not enough weigh-ins for a trend"
            else -> {
                val rate = TrendWeight.changeRatePerWeek(trend, today, 20)
                val current = trend[today] ?: weightEntries.lastOrNull()?.kg
                when {
                    rate == null || current == null -> "Need ~20 days of weigh-ins for an estimate"
                    abs(rate) < 0.01 -> "Weight trend is flat — no estimate"
                    else -> {
                        val target = targetWeight
                        val remaining = current - target
                        when {
                            abs(remaining) < 0.1 -> "Target reached — ${"%.1f".format(Locale.ENGLISH, target)} kg"
                            remaining * rate > 0.0 -> "Moving away from target (${"%.2f".format(Locale.ENGLISH, rate)} kg/wk)"
                            else -> {
                                val weeks = abs(remaining) / abs(rate)
                                "≈ ${"%.1f".format(Locale.ENGLISH, weeks)} wk to " +
                                    "${"%.1f".format(Locale.ENGLISH, target)} kg at " +
                                    "${"%.2f".format(Locale.ENGLISH, rate)} kg/wk"
                            }
                        }
                    }
                }
            }
        }

        return AnalyticsState(
            metricId = metricId,
            rangeDays = rangeDays,
            granularity = granularity,
            chartType = chartType,
            compareId = compareId,
            points = points,
            comparePoints = comparePoints,
            stats = stats,
            rows = rows,
            selectedIndex = safeSelection,
            metrics = METRICS.map { it.id to it.label },
            macroSplit = macroSplit,
            topContributors = topContributors,
            averages = averages,
            timing = timing,
            etaText = etaText,
        )
    }
}
