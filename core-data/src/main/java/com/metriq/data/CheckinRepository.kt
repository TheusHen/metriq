package com.metriq.data

import app.cash.sqldelight.db.SqlDriver
import com.metriq.data.db.MetriqDatabase
import java.time.LocalDate

class CheckinRepository(
    private val db: MetriqDatabase,
    private val driver: SqlDriver? = null,
) {

    fun acceptedFor(weekStart: LocalDate): Boolean {
        // Look up the requested week directly; the globally latest checkin may be a
        // different week and must not answer questions about older weeks.
        val row = db.metriqQueries.checkinForWeek(weekStart.toString()).executeAsOneOrNull()
            ?: return false
        return row.accepted == 1L
    }

    fun save(weekStart: LocalDate, kcal: Double, macros: String) {
        val week = weekStart.toString()
        val sqlDriver = driver
        if (sqlDriver != null) {
            // SQLDelight's SQLite 3.18 grammar cannot parse UPSERT, but the on-device
            // SQLite runtime supports it. Executing it directly keeps repeated accepts
            // from hitting the UNIQUE(weekStart) constraint.
            sqlDriver.execute(null, UPSERT_SQL, 5) {
                bindString(0, week)
                bindDouble(1, kcal)
                bindString(2, macros)
                bindLong(3, 1L)
                bindString(4, null)
            }
            return
        }
        // Fallback for callers that only have a database handle.
        db.transaction {
            db.metriqQueries.deleteCheckinForWeek(week)
            db.metriqQueries.insertCheckin(week, kcal, macros, 1L, null)
        }
    }

    private companion object {
        const val UPSERT_SQL = """
            INSERT INTO checkin(weekStart, recommendedKcal, recommendedMacros, accepted, note)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(weekStart) DO UPDATE SET
              recommendedKcal = excluded.recommendedKcal,
              recommendedMacros = excluded.recommendedMacros,
              accepted = excluded.accepted,
              note = excluded.note
        """
    }
}
