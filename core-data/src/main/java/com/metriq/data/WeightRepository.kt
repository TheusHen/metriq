package com.metriq.data

import com.metriq.core.WeightEntry
import com.metriq.data.db.MetriqDatabase
import java.time.LocalDate

class WeightRepository(
    private val db: MetriqDatabase,
    private val notifier: DataChangeNotifier? = null,
) {

    fun all(): List<WeightEntry> = db.metriqQueries.selectWeights()
        .executeAsList()
        .mapNotNull { row ->
            // Legacy rows may hold non-ISO text. Drop just that row so a single bad
            // record cannot crash dashboard construction.
            runCatching { WeightEntry(LocalDate.parse(row.date), row.kg) }.getOrNull()
        }

    fun add(date: String, kg: Double, source: String = "manual", note: String? = null) {
        db.metriqQueries.insertWeight(date, kg, source, note)
        notifier?.notify(DataType.WEIGHT)
    }

    fun remove(date: String) {
        db.metriqQueries.deleteWeight(date)
        notifier?.notify(DataType.WEIGHT)
    }
}
