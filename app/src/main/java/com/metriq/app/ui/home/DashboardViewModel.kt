package com.metriq.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.metriq.app.DefaultProfile
import com.metriq.app.AppGraph
import com.metriq.app.AppSettings
import com.metriq.app.TrainingHistory
import com.metriq.core.Aggressiveness
import com.metriq.core.Bmr
import com.metriq.core.CaloriePlan
import com.metriq.core.Confidence
import com.metriq.core.Expenditure
import com.metriq.core.ExpenditureResult
import com.metriq.core.MacroEngine
import com.metriq.core.MacroAdaptation
import com.metriq.core.MacroPlanner
import com.metriq.core.TrainingLoad
import com.metriq.core.TrendWeight
import com.metriq.core.WeightEntry
import com.metriq.app.ui.log.FoodDraft
import com.metriq.data.FoodHit
import com.metriq.data.LogItem
import com.metriq.data.RecipeDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeekDay(
    val letter: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val logged: Boolean,
)

data class DashboardState(
    val dateLabel: String,
    val week: List<WeekDay>,
    val kcalConsumed: Int,
    val kcalTarget: Int,
    val proteinG: Int,
    val proteinTarget: Int,
    val fatG: Int,
    val fatTarget: Int,
    val carbG: Int,
    val carbTarget: Int,
    val trendKg: Double,
    val trendChangePerWeek: Double?,
    val trendSeries: List<Double>,
    val trendStartLabel: String,
    val trendEndLabel: String,
    val expenditureKcal: Int,
    val expenditureConfidence: String,
    val goalLabel: String,
    val logItems: List<LogItem>,
    val weights: List<WeightEntry>,
    val checkinAccepted: Boolean,
    val stepsToday: Long?,
    val waterToday: Long?,
    val habits: List<HabitUi>,
    val periodToday: Boolean,
    val photos: List<com.metriq.data.db.ProgressPhoto>,
    val measurements: List<com.metriq.data.db.BodyMeasurement>,
    val recipes: List<com.metriq.data.db.Recipe>,
    val micros: Map<String, Double>,
    val macroAdaptation: MacroAdaptation?,
    val loggedDates: Set<LocalDate>,
    val healthCalories: Double?,
    val healthExerciseSessions: Int?,
)

data class HabitUi(val id: Long, val name: String, val streak: Int, val doneToday: Boolean)

/** Builds the dashboard from local data using the pure algorithms in core-domain. */
class DashboardViewModel : ViewModel() {

    /** Re-evaluated on every refresh so the app does not get stuck on yesterday after midnight. */
    private var today: LocalDate = LocalDate.now()

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            AppGraph.store.notifier.events.collect { refresh() }
        }
    }

    fun addFood(food: FoodHit, grams: Double) {
        AppGraph.store.logs.add(today.toString(), LocalTime.now().hour, food.id, grams)
        refresh()
    }

    fun deleteLog(id: Long) {
        AppGraph.store.logs.delete(id)
        refresh()
    }

    fun createFood(draft: FoodDraft) {
        val id = AppGraph.store.food.insertCustomFood(
            name = draft.name,
            brand = draft.brand,
            calories = draft.calories,
            protein = draft.protein,
            fat = draft.fat,
            carbs = draft.carbs,
            fiber = null,
            sugar = null,
            sodium = null,
            servingLabel = draft.servingLabel,
            servingGrams = draft.servingGrams,
        )
        AppGraph.store.logs.add(today.toString(), LocalTime.now().hour, id, draft.servingGrams)
        refresh()
    }

    fun addWeight(date: String, kg: Double) {
        AppGraph.store.weights.add(date, kg)
        refresh()
    }

    fun deleteWeight(date: String) {
        AppGraph.store.weights.remove(date)
        refresh()
    }

    fun saveProfile(profile: com.metriq.core.Profile, targetWeightKg: Double?) {
        AppGraph.store.profile.save(profile, targetWeightKg)
        refresh()
    }

    fun acceptCheckin() {
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val kcal = state.value.kcalTarget.toDouble()
        AppGraph.store.checkin.save(
            weekStart,
            kcal,
            "p=${state.value.proteinTarget},f=${state.value.fatTarget},c=${state.value.carbTarget}",
        )
        refresh()
    }

    fun exportCsv(): String {
        val weights = AppGraph.store.weights.all().associate { it.date.toString() to it.kg }
        val intake = AppGraph.store.logs.dailyIntake()
        val dates = (weights.keys + intake.keys.map { it.toString() }).distinct().sorted()
        return buildString {
            appendLine("date,scale_weight_kg,logged_kcal")
            dates.forEach { date ->
                val kg = weights[date]?.toString().orEmpty()
                val kcal = intake.entries.firstOrNull { it.key.toString() == date }?.value?.toString().orEmpty()
                appendLine("$date,$kg,$kcal")
            }
        }
    }

    fun saveSteps(steps: Long) {
        AppGraph.store.step.upsert(today.toString(), steps)
        refresh()
    }

    fun saveWater(ml: Long) {
        AppGraph.store.water.upsert(today.toString(), ml)
        refresh()
    }

    fun addHabit(name: String) {
        AppGraph.store.habit.add(name)
        refresh()
    }

    fun toggleHabit(id: Long) {
        val done = AppGraph.store.habit.logs(id, today.toString(), today.toString())
            .firstOrNull()?.done == 1L
        AppGraph.store.habit.toggle(id, today.toString(), !done)
        refresh()
    }

    fun setPeriod(on: Boolean) {
        val note = AppGraph.store.dayProperty.forDate(today.toString())?.note
        AppGraph.store.dayProperty.upsert(today.toString(), on, false, note)
        refresh()
    }

    fun addMeasurement(site: String, valueCm: Double) {
        AppGraph.store.body.add(today.toString(), site, valueCm)
        refresh()
    }

    fun addRecipe(draft: RecipeDraft) {
        AppGraph.store.recipe.add(draft)
        refresh()
    }

    /** Logs one serving of a recipe by logging each of its ingredients. */
    fun logRecipe(recipeId: Long) {
        val servings = AppGraph.store.recipe.all().firstOrNull { it.id == recipeId }?.servings
            ?.toInt()?.coerceAtLeast(1) ?: 1
        AppGraph.store.recipe.items(recipeId).forEach { item ->
            val food = AppGraph.store.food.byId(item.foodId) ?: return@forEach
            AppGraph.store.logs.add(
                today.toString(),
                LocalTime.now().hour,
                food.id,
                item.grams / servings,
            )
        }
        refresh()
    }

    fun setSetting(key: String, value: String) {
        AppGraph.store.settings.set(key, value)
        refresh()
    }

    fun addPhoto(context: Context, uri: Uri) {
        val target = java.io.File(context.filesDir, "progress_photos_incoming")
        target.mkdirs()
        val copy = java.io.File(target, "${System.currentTimeMillis()}.jpg")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            } ?: return
            AppGraph.store.photo.add(
                today.toString(),
                copy.absolutePath,
                null,
                null,
            )
        } finally {
            copy.delete()
        }
        refresh()
    }

    fun deletePhoto(id: Long) {
        AppGraph.store.photo.delete(id)
        refresh()
    }

    fun refresh() {
        today = LocalDate.now()
        _state.value = buildState()
    }

    private fun buildState(): DashboardState {
        val profile = AppGraph.store.profile.get() ?: DefaultProfile.PROFILE
        val logs = AppGraph.store.logs.forDate(today.toString())
        val consumedKcal = logs.sumOf { it.calories }.toInt()
        val weightEntries = AppGraph.store.weights.all()
        val start = weightEntries.firstOrNull()?.date ?: today.minusDays(55)
        val weightMap = weightEntries.associate { it.date to it.kg }
        val trend = if (weightMap.size >= 2) TrendWeight.compute(weightMap, start, today) else emptyMap()
        val intake: MutableMap<LocalDate, Double?> = AppGraph.store.logs.dailyIntake().toMutableMap()
        val lastKg = weightEntries.lastOrNull()?.kg ?: profile.weightKg
        val effectiveProfile = profile.copy(weightKg = lastKg)
        val bmr = Bmr.forProfile(effectiveProfile)
        // Hold the last estimate when logging pauses, as required by algorithms.md.
        val previousKcal = AppGraph.store.settings.get("last_expenditure_kcal")?.toDoubleOrNull()
        val previous = previousKcal?.let {
            ExpenditureResult(today, it, Confidence.MEDIUM, updated = false, samples = 0)
        }
        val expenditure = Expenditure.estimate(today, intake, trend, bmr, previous)
        if (expenditure.updated) {
            AppGraph.store.settings.set("last_expenditure_kcal", expenditure.kcal.toString())
        }
        val calorieTargets = CaloriePlan.target(expenditure.kcal, effectiveProfile.goal, Aggressiveness.MODERATE)
        val baseMacros = MacroPlanner.plan(
            kcal = calorieTargets.dailyKcal,
            bodyWeightKg = effectiveProfile.weightKg,
            fatFreeMassKg = effectiveProfile.fatFreeMassKg,
            goal = effectiveProfile.goal,
            tier = effectiveProfile.proteinTier,
            diet = effectiveProfile.diet,
        )
        val history = TrainingHistory.all()
        fun loadOf(day: com.metriq.app.DailyTraining) = TrainingLoad(
            activeKcal = day.activeKcal,
            exerciseMinutes = day.minutes,
            sessions = day.sessions,
            intensity = MacroEngine.intensityOf(
                day.activeKcal, day.minutes, day.sessions, effectiveProfile.weightKg,
            ),
        )
        val todayLoad = history.firstOrNull { it.date == today }?.let(::loadOf)
        val recentLoads = history.takeLast(7).map(::loadOf)
        val adaptation = MacroEngine.adapt(
            base = baseMacros,
            profile = effectiveProfile,
            load = todayLoad,
            recentLoads = recentLoads,
            enabled = AppSettings.bool(AppSettings.ADAPTIVE_MACROS),
        )
        val macros = adaptation.targets

        val trendSeries = trend.values.toList()
        val changePerWeek = TrendWeight.changeRatePerWeek(trend, today)

        val habits = AppGraph.store.habit.active().map { habit ->
            HabitUi(
                id = habit.id,
                name = habit.name,
                streak = AppGraph.store.habit.streak(habit.id, today.toString()),
                doneToday = AppGraph.store.habit.logs(habit.id, today.toString(), today.toString())
                    .firstOrNull()?.done == 1L,
            )
        }
        val micros = mutableMapOf<String, Double>()
        logs.forEach { item ->
            val factor = item.grams * item.servings / 100.0
            AppGraph.store.food.byId(item.foodId)?.let { food ->
                food.fiber?.let { micros["fiber"] = (micros["fiber"] ?: 0.0) + it * factor }
                food.sodium?.let { micros["sodium"] = (micros["sodium"] ?: 0.0) + it * factor }
            }
            AppGraph.store.food.micros(item.foodId)?.let { json ->
                Regex("\"([a-zA-Z0-9]+)\"\\s*:\\s*([0-9.]+)").findAll(json).forEach { match ->
                    val key = match.groupValues[1]
                    val value = match.groupValues[2].toDoubleOrNull() ?: return@forEach
                    micros[key] = (micros[key] ?: 0.0) + value * factor
                }
            }
        }

        return DashboardState(
            dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)),
            week = buildWeek(intake, weightEntries),
            kcalConsumed = consumedKcal,
            kcalTarget = macros.kcal,
            proteinG = logs.sumOf { it.protein }.toInt(),
            proteinTarget = macros.proteinG,
            fatG = logs.sumOf { it.fat }.toInt(),
            fatTarget = macros.fatG,
            carbG = logs.sumOf { it.carbs }.toInt(),
            carbTarget = macros.carbG,
            trendKg = trendSeries.lastOrNull() ?: lastKg,
            trendChangePerWeek = changePerWeek,
            trendSeries = trendSeries,
            trendStartLabel = start.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)),
            trendEndLabel = today.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)),
            expenditureKcal = Math.round(expenditure.kcal).toInt(),
            expenditureConfidence = expenditure.confidence.name.lowercase(Locale.ENGLISH),
            goalLabel = profile.goal.name.lowercase(Locale.ENGLISH)
                .replaceFirstChar { it.uppercase() },
            logItems = logs,
            weights = weightEntries,
            checkinAccepted = AppGraph.store.checkin.acceptedFor(
                today.minusDays((today.dayOfWeek.value - 1).toLong()),
            ),
            stepsToday = AppGraph.store.step.forDate(today.toString()),
            waterToday = AppGraph.store.water.forDate(today.toString()),
            habits = habits,
            periodToday = AppGraph.store.dayProperty.forDate(today.toString())?.onPeriod == 1L,
            photos = AppGraph.store.photo.all(),
            measurements = AppGraph.store.body.measurementsForDate(today.toString()),
            recipes = AppGraph.store.recipe.all(),
            micros = micros,
            macroAdaptation = adaptation,
            loggedDates = intake.filter { it.value != null }.keys +
                weightEntries.map { it.date }.toSet(),
            healthCalories = AppGraph.store.settings.get("health_active_calories")?.toDoubleOrNull()
                ?: AppGraph.store.settings.get("health_total_calories")?.toDoubleOrNull(),
            healthExerciseSessions = AppGraph.store.settings.get("health_exercise_sessions")?.toIntOrNull(),
        )
    }

    private fun buildWeek(
        intake: Map<LocalDate, Double?>,
        weights: List<WeightEntry>,
    ): List<WeekDay> {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weightDates = weights.map { it.date }.toSet()
        return (0..6).map { index ->
            val day = monday.plusDays(index.toLong())
            WeekDay(
                letter = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH),
                dayOfMonth = day.dayOfMonth,
                isToday = day == today,
                logged = day.isBefore(today) && (intake[day] != null || weightDates.contains(day)),
            )
        }
    }
}
