package com.metriq.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.metriq.app.AppGraph
import com.metriq.app.AppSettings
import com.metriq.app.DefaultProfile
import com.metriq.core.ActivityLevel
import com.metriq.core.DietStyle
import com.metriq.core.GoalType
import com.metriq.core.OnboardingInput
import com.metriq.core.OnboardingPlanner
import com.metriq.core.PlanResult
import com.metriq.core.Profile
import com.metriq.core.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One screen per step; the last step is the plan review. */
data class OnboardingState(
    val step: Int,
    val sex: Sex,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val bodyFatPercent: Double?,
    val activityLevel: ActivityLevel,
    val goal: GoalType,
    val diet: DietStyle,
    val targetWeightKg: Double?,
    val weeks: Int?,
    val guardianAcknowledged: Boolean,
    val plan: PlanResult?,
    val isMinor: Boolean,
)

/**
 * Owns the onboarding answers and keeps the plan in sync with them. Nothing here
 * talks to a network: the only persistence is the local store on [complete].
 */
class OnboardingViewModel : ViewModel() {

    companion object {
        const val TOTAL_STEPS = 7
        const val LAST_STEP = TOTAL_STEPS - 1
        private const val MINOR_AGE = 18
    }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private fun initialState(): OnboardingState {
        val defaults = DefaultProfile.PROFILE
        val base = OnboardingState(
            step = 0,
            sex = defaults.sex,
            age = defaults.age,
            heightCm = defaults.heightCm,
            weightKg = defaults.weightKg,
            bodyFatPercent = defaults.bodyFatPercent,
            activityLevel = defaults.activityLevel,
            goal = defaults.goal,
            diet = defaults.diet,
            targetWeightKg = null,
            weeks = null,
            guardianAcknowledged = false,
            plan = null,
            isMinor = defaults.age < MINOR_AGE,
        )
        return base.copy(plan = runCatching { buildPlan(base) }.getOrNull())
    }

    private fun buildPlan(current: OnboardingState): PlanResult = OnboardingPlanner.plan(
        OnboardingInput(
            sex = current.sex,
            age = current.age,
            heightCm = current.heightCm,
            weightKg = current.weightKg,
            bodyFatPercent = current.bodyFatPercent,
            activityLevel = current.activityLevel,
            goal = current.goal,
            diet = current.diet,
            targetWeightKg = current.targetWeightKg,
            weeks = current.weeks,
        ),
    )

    /** Every answer change re-derives `isMinor` and the plan in one atomic update. */
    private fun update(transform: (OnboardingState) -> OnboardingState) {
        val next = transform(_state.value)
        val minor = next.age < MINOR_AGE
        val adjusted = when {
            // Keto is not offered to minors; never leave a stale keto selection behind.
            minor && next.diet == DietStyle.KETO -> next.copy(diet = DietStyle.BALANCED)
            !minor && next.guardianAcknowledged -> next.copy(guardianAcknowledged = false)
            else -> next
        }.copy(isMinor = minor)
        val plan = runCatching { buildPlan(adjusted) }.getOrNull() ?: _state.value.plan
        _state.value = adjusted.copy(plan = plan)
    }

    fun setSex(value: Sex) = update { it.copy(sex = value) }

    fun setAge(value: Int) = update { it.copy(age = value) }

    fun setHeightCm(value: Double) = update { it.copy(heightCm = value) }

    fun setWeightKg(value: Double) = update { it.copy(weightKg = value) }

    fun setBodyFat(value: Double?) = update { it.copy(bodyFatPercent = value) }

    fun setActivity(value: ActivityLevel) = update { it.copy(activityLevel = value) }

    fun setGoal(value: GoalType) = update { it.copy(goal = value) }

    fun setDiet(value: DietStyle) = update { it.copy(diet = value) }

    fun setTargetWeightKg(value: Double?) = update { it.copy(targetWeightKg = value) }

    fun setWeeks(value: Int?) = update { it.copy(weeks = value) }

    fun setGuardianAcknowledged(value: Boolean) = update { it.copy(guardianAcknowledged = value) }

    fun nextStep() = update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }

    fun previousStep() = update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    /** Persists the finished profile and marks onboarding done, locally only. */
    fun complete() {
        val current = _state.value
        val profile = Profile(
            sex = current.sex,
            age = current.age,
            heightCm = current.heightCm,
            weightKg = current.weightKg,
            bodyFatPercent = current.bodyFatPercent,
            activityLevel = current.activityLevel,
            goal = current.goal,
            diet = current.diet,
            proteinTier = DefaultProfile.PROFILE.proteinTier,
        )
        AppGraph.store.profile.save(profile, current.targetWeightKg)
        AppGraph.store.settings.set(AppSettings.ONBOARDING_COMPLETE, "true")
    }
}
