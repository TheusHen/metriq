package com.metriq.app.health

import java.time.Instant
import java.time.LocalDate

/** A weight sample with the timestamp needed to arbitrate duplicates on the same day. */
data class WeightReading(
    val date: LocalDate,
    val kilograms: Double,
    val time: Instant,
)

/** Collapses multiple Health Connect weigh-ins per day to the most recent one. */
internal fun latestWeightByDate(readings: List<WeightReading>): Map<LocalDate, Double> =
    readings.groupBy { it.date }.mapValues { entry ->
        entry.value.maxByOrNull { it.time }!!.kilograms
    }
