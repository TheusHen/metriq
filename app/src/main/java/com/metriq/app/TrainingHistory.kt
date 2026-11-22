package com.metriq.app

import java.time.LocalDate

/** One day of training signal read from Health Connect. Stored locally in the settings table. */
data class DailyTraining(
    val date: LocalDate,
    val activeKcal: Double,
    val minutes: Int,
    val sessions: Int,
)

/**
 * Compact local time series for the adaptive macro engine.
 * Format: `date|activeKcal|minutes|sessions;...` — no JSON dependency, no schema migration.
 */
object TrainingHistory {
    private const val KEY = "health_training_history"
    private const val MAX_DAYS = 60

    fun all(): List<DailyTraining> {
        val raw = AppGraph.store.settings.get(KEY).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(';').mapNotNull { part ->
            val fields = part.split('|')
            if (fields.size != 4) return@mapNotNull null
            runCatching {
                DailyTraining(
                    date = LocalDate.parse(fields[0]),
                    activeKcal = fields[1].toDoubleOrNull() ?: 0.0,
                    minutes = fields[2].toIntOrNull() ?: 0,
                    sessions = fields[3].toIntOrNull() ?: 0,
                )
            }.getOrNull()
        }.sortedBy { it.date }
    }

    fun record(day: DailyTraining) {
        val updated = (all().filter { it.date != day.date } + day)
            .sortedBy { it.date }
            .takeLast(MAX_DAYS)
        val encoded = updated.joinToString(";") {
            "${it.date}|${it.activeKcal}|${it.minutes}|${it.sessions}"
        }
        AppGraph.store.settings.set(KEY, encoded)
    }

    fun forDate(date: LocalDate): DailyTraining? = all().firstOrNull { it.date == date }
}
