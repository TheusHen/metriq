# Algorithms

All formulas are implemented in `core-domain` with an explicit `algorithm_version` on
derived values. Every change to a formula requires a migration that recomputes history.

## BMR (kcal/day)

```
weight form:      129.6 * kg^0.55 + 0.011 * cm^2 - ageCoef * age - 213.8 * sex
                  ageCoef = 1.96, or 4.9 past 60. sex = 0 male, 1 female.
composition form: 50.2 * FFM^0.7 + 40.5 * (FFM^0.7 * FM^0.066) - ageCoef * age
                  ageCoef = 1.1, or 2.75 past 60.
athlete form:     40.4 * FFM^0.932   (>= 7 h/week intense exercise)
```

Corrections: `x0.95` in deficit, `x0.97` when more than 10% below peak weight,
`x0.92` when both. Initial expenditure = corrected BMR x activity factor.

## Weight trend

Missing weigh-ins are linearly interpolated between anchors, then a weighted exponential
average is applied. `alpha = clamp(0.12 + 0.02 * daysSinceWeighIn, 0.12, 0.35)`.
Days without data hold the previous trend. Change rate is the trend delta over 20 days,
expressed per week.

## Expenditure

```
storedEnergy = (trend[d] - trend[d-1]) * 7716 kcal/kg
expenditure[d] = intake[d] - storedEnergy
```

A weighted median over 14 days smooths the result. The estimate is clamped to
`0.6 * BMR .. 2.5 * BMR`. Four or more missing nutrition days in the last seven
pause updates. Optional modifiers (step trend, predictive goal adjustment) are
off by default.

## Goal and macros

```
maintain: kcal = expenditure
bulk:     kcal = expenditure * 1.05
cut:      kcal = expenditure * {0.90, 0.85, 0.70} by aggressiveness
weekly budget = daily * 7
```

Protein uses g/kg fat-free mass, cut 2.0–3.5 and bulk/maintain 1.75–3.1 by preference.
Fat is allocated from the diet style (balanced 30%, low-carb 45%, high-carb 20%,
keto 70%) with a 0.5 g/kg body-weight floor. Carbohydrates take the remainder;
keto caps them at 30 g and moves the rest to fat.

## Validation

`core-domain` tests cover equations, gap handling, missing-day pauses, clamps and macro
allocation. The simulation harness described in the plan is the next validation step.
