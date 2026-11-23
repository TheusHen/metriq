package com.metriq.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class CheckinRepositoryTest {

    private val repo get() = newTestStore().checkin

    @Test
    fun `accepting the same week twice does not crash`() {
        val checkin = repo
        val week = LocalDate.of(2026, 2, 23)
        checkin.save(week, 2400.0, "{}")
        checkin.save(week, 2500.0, "{}")
        assertTrue(checkin.acceptedFor(week))
    }

    @Test
    fun `ask about an older week after a newer week was accepted`() {
        val store = newTestStore()
        val older = LocalDate.of(2026, 2, 16)
        val newer = LocalDate.of(2026, 2, 23)

        // Older week accepted, then a different (newer) week accepted.
        store.db.metriqQueries.insertCheckin(older.toString(), 2200.0, "{}", 1L, null)
        store.checkin.save(newer, 2400.0, "{}")

        assertTrue(store.checkin.acceptedFor(older))
        assertTrue(store.checkin.acceptedFor(newer))
    }
}
