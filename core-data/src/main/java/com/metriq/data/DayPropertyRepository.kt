package com.metriq.data

import com.metriq.data.db.MetriqDatabase

class DayPropertyRepository(private val db: MetriqDatabase, private val notifier: DataChangeNotifier) {

    fun forDate(date: String) = db.metriqQueries.dayPropertyForDate(date).executeAsOneOrNull()

    fun range(from: String, to: String) = db.evolutionQueries.selectDayProperties(from, to).executeAsList()

    fun upsert(date: String, onPeriod: Boolean, fasting: Boolean, note: String?) {
        db.metriqQueries.upsertDayProperty(date, if (onPeriod) 1L else 0L, if (fasting) 1L else 0L, note)
        notifier.notify(DataType.PERIOD)
    }
}

class NutrientTargetRepository(private val db: MetriqDatabase, private val notifier: DataChangeNotifier) {

    fun all() = db.evolutionQueries.selectNutrientTargets().executeAsList()

    fun set(nutrient: String, min: Double?, max: Double?) {
        val mode = when {
            min != null && max != null -> "range"
            min != null -> "min"
            max != null -> "max"
            else -> "none"
        }
        db.evolutionQueries.upsertNutrientTarget(nutrient, mode, min, max, java.time.LocalDate.now().toString())
        notifier.notify(DataType.NUTRIENT_TARGET)
    }
}
