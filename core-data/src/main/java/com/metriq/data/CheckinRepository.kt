package com.metriq.data

import com.metriq.data.db.MetriqDatabase
import java.time.LocalDate

class CheckinRepository(private val db: MetriqDatabase) {

    fun acceptedFor(weekStart: LocalDate): Boolean {
        val latest = db.metriqQueries.latestCheckin().executeAsOneOrNull() ?: return false
        return latest.accepted == 1L && latest.weekStart == weekStart.toString()
    }

    fun save(weekStart: LocalDate, kcal: Double, macros: String) {
        db.metriqQueries.insertCheckin(
            weekStart.toString(),
            kcal,
            macros,
            1L,
            null,
        )
    }
}
