package com.metriq.data

import com.metriq.data.db.MetriqDatabase

class HabitRepository(private val db: MetriqDatabase, private val notifier: DataChangeNotifier) {

    fun active() = db.evolutionQueries.selectActiveHabits().executeAsList()

    fun add(name: String, schedule: String = "daily", color: String = "#4DD0E1") {
        db.evolutionQueries.insertHabit(name, schedule, color)
        notifier.notify(DataType.HABIT)
    }

    fun archive(id: Long) {
        db.evolutionQueries.archiveHabit(id)
        notifier.notify(DataType.HABIT)
    }

    fun logs(habitId: Long, from: String, to: String) =
        db.evolutionQueries.selectHabitLogs(habitId, from, to).executeAsList()

    fun toggle(habitId: Long, date: String, done: Boolean) {
        db.evolutionQueries.toggleHabitLog(habitId, date, if (done) 1L else 0L)
        notifier.notify(DataType.HABIT)
    }

    fun streak(habitId: Long, today: String, maxDays: Int = 3650): Int {
        val end = java.time.LocalDate.parse(today)
        val start = end.minusDays((maxDays - 1).toLong())
        // One bounded query instead of one query per day, then walk in memory.
        val doneDates = db.evolutionQueries.selectHabitLogs(habitId, start.toString(), today)
            .executeAsList()
            .asSequence()
            .filter { it.done == 1L }
            .map { it.date }
            .toSet()

        var cursor = end
        var count = 0
        while (count < maxDays && cursor.toString() in doneDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }
}
