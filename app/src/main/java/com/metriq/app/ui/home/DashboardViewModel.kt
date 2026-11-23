package com.metriq.app.ui.home

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.metriq.app.DefaultProfile
import com.metriq.app.AppGraph
import com.metriq.app.AppSettings
import com.metriq.app.TrainingHistory
import com.metriq.app.GoalStore
import com.metriq.core.Aggressiveness
import com.metriq.core.Bmr
import com.metriq.core.CaloriePlan
import com.metriq.core.Confidence
import com.metriq.core.Expenditure
import com.metriq.core.ExpenditureResult
import com.metriq.core.GoalEngine
import com.metriq.core.GoalOverride
import com.metriq.core.MacroEngine
import com.metriq.core.MacroAdaptation
import com.metriq.core.MacroPlanner
import com.metriq.core.PlanResult
import com.metriq.core.Profile
import com.metriq.core.TrainingEngine
import com.metriq.core.TrainingLoad
import com.metriq.core.TrainingSignal
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
    val trainingSignal: com.metriq.core.TrainingSignal,
    val goalRecalculation: com.metriq.core.RecalculatedPlan?,
    val hevyWorkoutCount: Int,
    val hevyLastSync: String?,
    val loggedDates: Set<LocalDate>,
    val healthCalories: Double?,
    val healthExerciseSessions: Int?,
)

data class HabitUi(val id: Long, val name: String, val streak: Int, val doneToday: Boolean)

/** Builds the dashboard from local data using the pure algorithms in core-domain. */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val hevyRepository get() = AppGraph.hevyRepository

    // Exact inputs used by the last buildState(), for faithful goal warnings preview.
    @Volatile private var previewPlan: PlanResult? = null
    @Volatile private var previewProfile: Profile? = null

    // Only the newest requested snapshot may be published; older IO builds are discarded.
    private var buildSequence = 0

    fun previewGoalWarnings(goal: GoalOverride): List<String> {
        val plan = previewPlan ?: return emptyList()
        val profile = previewProfile ?: return emptyList()
        return GoalEngine.recalculate(plan, goal, profile, profile.age < 18).warnings
    }

    /**
     * Read at the moment of each write, not cached in a field: an app left open across
     * midnight must log to the new day even if no refresh happened in between.
     */
    private val today: LocalDate get() = LocalDate.now()

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            AppGraph.store.notifier.events.collect { refresh() }
        }
    }

    fun addFood(food: FoodHit, grams: Double) {
        val id = AppGraph.store.food.materialize(food)
        AppGraph.store.logs.add(today.toString(), LocalTime.now().hour, id, grams)
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

    fun copyFromDay(date: String) {
        AppGraph.store.logs.forDate(date).forEach { item ->
            AppGraph.store.logs.add(today.toString(), LocalTime.now().hour, item.foodId, item.grams, item.servings)
        }
        refresh()
    }

    fun quickAdd(kcal: Double, proteinG: Double, fatG: Double, carbG: Double) {
        if (kcal <= 0.0) return
        val app = getApplication<Application>()
        val id = AppGraph.store.food.insertCustomFood(
            name = app.getString(com.metriq.app.R.string.quick_add_name),
            brand = null, calories = kcal, protein = proteinG,
            fat = fatG, carbs = carbG, fiber = null, sugar = null, sodium = null,
            servingLabel = app.getString(com.metriq.app.R.string.serving_one),
            servingGrams = 100.0,
        )
        AppGraph.store.logs.add(today.toString(), LocalTime.now().hour, id, 100.0)
        refresh()
    }

    fun logDays(food: com.metriq.data.FoodHit, grams: Double, days: Int) {
        val id = AppGraph.store.food.materialize(food)
        val count = days.coerceIn(1, 7)
        (0 until count).forEach { offset ->
            AppGraph.store.logs.add(
                LocalDate.now().plusDays(offset.toLong()).toString(),
                LocalTime.now().hour,
                id,
                grams,
            )
        }
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

    /** Rebuild off the Main thread; notifier bursts must not block the UI. */
    fun refresh() {
        val sequence = ++buildSequence
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { buildState() }
            if (sequence == buildSequence) _state.value = snapshot
        }
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
        // The historical peak is what makes the below-peak BMR correction fire. Without
        // it forProfile sees only the current weight and never discounts the estimate.
        val peakKg = weightEntries.maxOfOrNull { it.kg } ?: profile.weightKg
        val bmr = Bmr.forProfile(effectiveProfile, peakKg)
        // Measured output only feeds expenditure confidence; it never changes kcal.
        val observedActiveKcal = AppGraph.store.settings.get("health_active_calories")?.toDoubleOrNull()
        // Hold the last estimate when logging pauses, as required by algorithms.md.
        val previousKcal = AppGraph.store.settings.get("last_expenditure_kcal")?.toDoubleOrNull()
        val previous = previousKcal?.let {
            ExpenditureResult(today, it, Confidence.MEDIUM, updated = false, samples = 0)
        }
        val expenditure = Expenditure.estimate(
            date = today,
            intake = intake,
            trend = trend,
            bmr = bmr,
            weights = weightMap,
            previous = previous,
            observedActiveKcal = observedActiveKcal,
        )
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
        val hevyConnected = AppSettings.bool(AppSettings.HEVY_CONNECTED) && hevyRepository.hasKey()
        fun signalFor(date: LocalDate): TrainingSignal {
            val health = TrainingHistory.forDate(date)
            val hevy = if (hevyConnected) hevyRepository.daySignal(date) else null
            val kcal = health?.activeKcal ?: 0.0
            val minutes = health?.minutes ?: 0
            val sessions = health?.sessions ?: 0
            // Steps are stored locally; feeding them lets TrainingEngine classify
            // light-activity days. They only affect the macro split, never total kcal.
            val steps = AppGraph.store.step.forDate(date.toString())
            return if (hevy != null) {
                TrainingEngine.fromHevy(
                    activeKcal = kcal,
                    steps = steps,
                    volumeKg = hevy.volumeKg,
                    // Health Connect cardio still counts on a day with a Hevy lift.
                    durationMinutes = hevy.durationMinutes + minutes,
                    muscles = hevy.muscles,
                    sessions = hevy.sessions + sessions,
                    bodyWeightKg = effectiveProfile.weightKg,
                )
            } else {
                TrainingEngine.fromHealthConnect(
                    activeKcal = kcal,
                    steps = steps,
                    sessions = sessions,
                    durationMinutes = minutes,
                    bodyWeightKg = effectiveProfile.weightKg,
                )
            }
        }
        val trainingSignal = signalFor(today)
        val recentSignals = (1..7).map { today.minusDays(it.toLong()) }.map(::signalFor)

        val isMinor = effectiveProfile.age < 18
        val suggestedPlan = PlanResult(
            bmr = bmr,
            tdee = expenditure.kcal,
            dailyKcal = calorieTargets.dailyKcal,
            proteinG = baseMacros.proteinG,
            fatG = baseMacros.fatG,
            carbG = baseMacros.carbG,
            isMinor = isMinor,
            warnings = emptyList(),
            explanation = "",
        )
        previewPlan = suggestedPlan
        previewProfile = effectiveProfile
        val goalRecalculation = GoalStore.read()?.let { stored ->
            // A "follow expenditure" goal must track improved device data instead of a
            // kcal value frozen at onboarding. resolve() returns a copy, so the
            // persisted JSON stays untouched; manual goals are returned as-is.
            val override = FollowExpenditureGoal.resolve(
                stored = stored,
                currentExpenditureKcal = expenditure.kcal,
                currentWeightKg = effectiveProfile.weightKg,
            )
            GoalEngine.recalculate(suggestedPlan, override, effectiveProfile, isMinor)
        }
        // Adapt on top of the goal-adjusted plan so the override and the training
        // signal compose, and the displayed deltas match the final targets.
        val goalAdjustedMacros = goalRecalculation?.plan?.let { recalculated ->
            com.metriq.core.MacroTargets(
                kcal = Math.round(recalculated.dailyKcal).toInt(),
                proteinG = recalculated.proteinG,
                fatG = recalculated.fatG,
                carbG = recalculated.carbG,
            )
        } ?: baseMacros
        val adaptation = MacroEngine.adaptWithSignal(
            base = goalAdjustedMacros,
            profile = effectiveProfile,
            signal = trainingSignal,
            recentSignals = recentSignals,
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
            dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())),
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
            trendStartLabel = start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
            trendEndLabel = today.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
            expenditureKcal = Math.round(expenditure.kcal).toInt(),
            expenditureConfidence = expenditure.confidence.name.lowercase(Locale.ENGLISH),
            // HomeScreen and TrendSection match "cut"/"bulk" as lowercase keys.
            goalLabel = profile.goal.name.lowercase(Locale.ENGLISH),
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
            trainingSignal = trainingSignal,
            goalRecalculation = goalRecalculation,
            hevyWorkoutCount = if (hevyConnected) hevyRepository.workouts().size else 0,
            hevyLastSync = AppSettings.get(AppSettings.HEVY_LAST_SYNC).ifBlank { null },
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
