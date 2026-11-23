# Algorithms

All formulas are implemented in `core-domain` as pure JVM functions (no Android APIs,
no clock, no I/O). Derived values do **not** carry an `algorithm_version` field — that
field does not exist. If a published equation changes, the change must be handled as an
explicit data migration by the layer that persists derived values.

## BMR (kcal/day)

```
weight form:      Mifflin-St Jeor (1990)
                   10 * kg + 6.25 * cm - 5 * age + (5 male / -161 female)
composition form: Katch-McArdle
                   370 + 21.6 * FFM
```

Age enters Mifflin-St Jeor linearly (-5 kcal/year) and there is **no discontinuity at
age 60**. Katch-McArdle depends only on fat-free mass; fat mass and age are accepted by
the API for source compatibility but do not enter the equation.

Corrections: `x0.95` in deficit, `x0.97` when more than 10% below peak weight,
`x0.92` when both. `Bmr.forProfile(profile, peakKg = null, inDeficit = false)` accepts
an optional historical peak; when null (or invalid) the current weight is used as the
peak, so `isBelowPeak` is false. Initial expenditure = corrected BMR x activity factor.

**When `inDeficit` is applied.** The parameter defaults to `false` and nothing in
`core-domain` computes it on its own: callers that leave it out never get the 5%
deficit correction. `Bmr.isInDeficit(weights, today, days = 14)` is the provided helper —
it keeps the weigh-ins in `[today - (days - 1), today]`, ignores non-finite values, needs
at least two finite readings, and returns true when the newest reading is at least
0.05 kg below the oldest. The dashboard computes it from the weight history and passes
the result into `Bmr.forProfile`; if it returns false (stable or rising weight, or too
few readings) the deficit correction is not applied. A separate `belowPeak` condition
uses the historical peak and is independent of `isInDeficit`.

Minors do not use these corrections in onboarding: `OnboardingPlanner` applies the
Schofield resting-energy equation directly and uses a pediatric default aggressiveness
of `CONSERVATIVE` for a cut goal.

## Weight trend

Only actual weigh-ins move the trend. Each new weigh-in uses
`alpha = clamp(0.12 + 0.02 * (daysSinceLastWeighIn - 1), 0.12, 0.35)`, so a daily
weigh-in uses exactly the 0.12 minimum, longer gaps react more strongly, and the 0.35
cap applies to gaps of about two weeks or more. Days without a weigh-in hold the previous
trend value. Change rate is the trend delta over 20 days, expressed per week; a
non-positive span has no rate.

## Expenditure

Stored energy is derived from **actual weigh-ins**, never from day-to-day differences of
the (intentionally flat-filled) trend:

```
kgPerDay[d]      = (weight[weighIn] - weight[previousWeighIn]) / calendarGap
storedEnergy[d]  = kgPerDay[d] * 7716 kcal/kg
expenditure[d]   = intake[d] - storedEnergy[d]
```

Only days inside the 14-day window become samples, but the per-day rate uses the full
calendar gap between the two weigh-ins, so a 7-day weigh-in gap contributes one seventh
of the measured change per day. A weighted median (recent days weighted more) smooths
the samples. The estimate is clamped to `0.9 * BMR .. 2.0 * BMR` — both tighter than the
old `0.6 .. 2.5` bounds to keep it above basal metabolism and below the highest activity
factor (1.9). Four or more missing nutrition days in the last seven pause updates.

The trend map and `observedActiveKcal` affect **confidence only**: activity energy is
already embedded in the intake-vs-weight balance, so adding it to the returned kcal
would double-count. Sparse trend data or an implausibly large observed activity value
downgrade confidence; the kcal value is unchanged.

## Goal and macros

```
maintain: kcal = expenditure
bulk:     kcal = expenditure * 1.05
cut:      kcal = expenditure * {0.90, 0.85, 0.70} by aggressiveness
weekly budget = daily * 7

with a valid target weight and deadline:
weeklyRateKg = |currentKg - targetKg| / weeks
dailyKcal    = tdee -/+ weeklyRateKg * 7716 / 7   (- for CUT, + for BULK)
```

In the goal editor a valid deadline is authoritative and replaces the manually entered
kcal; the result is clamped to `0 .. 2 * max(tdee, bmr)` and `didClamp` reports whether
that happened. Without a deadline the manual kcal is applied as entered. An unsafe rate
still produces an advisory warning. `GoalOverride.followExpenditure` rebuilds the goal
from current expenditure (keeping the goal's aggressiveness) instead of pinning a fixed
kcal.

Protein uses g/kg fat-free mass, cut 2.0–3.5 and bulk/maintain 1.75–3.1 by preference.
Fat is allocated from the diet style (balanced 30%, low-carb 45%, high-carb 20%,
keto 70%) with a 0.5 g/kg body-weight floor. Carbohydrates take the remainder;
keto caps them at 30 g and moves the rest to fat. When the floors make the macros exceed
the calorie target, fat is reduced first toward a minimal survival floor
(`max(10 g, 0.2 g/kg)`), then protein is reduced, and finally fat may drop below the
survival floor if the target is that small. The result is flagged with `floorsRelaxed`
and a warning in the returned plan; int macros still sum back to the target within ~2 kcal
and are never negative.

## Domain messages (warnings and narrative text)

`core-domain` contains no translated or locale-formatted text. Every user-facing message
is a stable [WarningCode] plus a `Map<String, String>` of plain values:

```kotlin
enum class WarningCode { /* descriptive UPPER_SNAKE names */ }
data class DomainWarning(
    val code: WarningCode,
    val params: Map<String, String> = emptyMap(),
)
```

`DomainWarning.of(code, "rateKgPerWeek" to 1.25, ...)` stringifies numbers with
`toString()` — e.g. `1.25`, `0.5`, `175` — with no `Locale` or `NumberFormat`. The app
layer maps codes to translated strings and formats numeric params with the user's
locale; that is the only place language and separators belong.

Where they appear:

- `PlanResult.warnings` and `RecalculatedPlan.warnings` are `List<DomainWarning>`.
- `PlanResult.explanation`, `RecalculatedPlan.summary` and `MacroAdaptation.reason` are
  single `DomainWarning` values, also identified by a `WarningCode`.
- Codes are grouped as input/advisory warnings (`AGE_OUTSIDE_TYPICAL_RANGE`,
  `RATE_ABOVE_REFERENCE`, `KCAL_BELOW_FLOOR`, ...), narrative codes
  (`PLAN_EXPLANATION`, `SUMMARY_GOAL_UNCHANGED`, `SUMMARY_GOAL_ADJUSTED`) and training
  adaptation codes (`CARB_REALLOCATED`, `NO_FAT_MARGIN`, ...).

The authoritative, exhaustive code-and-param list is the KDoc on `WarningCode` in
`core-domain/src/main/kotlin/com/metriq/core/Warning.kt`. Adding a code is a source-level
API change: the app's code-to-string map must be updated in the same change.

## Nutrition label parsing

`NutritionLabelParser` handles English and Portuguese labels, row-style tables,
thousands separators (`1,000` / `1.000`) and values printed on the line after the label.
Energy parsing prefers an explicit `kcal` number; a bare `Calories N` is treated as kcal
by label convention. If only `kJ` is printed, the value is divided by 4.184 and the
result's `energySource` is `EnergySource.KJ_CONVERTED` instead of the default
`EnergySource.KCAL`, so the app can tell the user the value was converted.

## Validation

`core-domain` tests cover equations, gap handling, missing-day pauses, clamps, macro
allocation, warning-code/param output, deficit detection and label-parser edge cases.
The simulation harness described in the plan is the next validation step.
