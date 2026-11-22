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

    fun streak(habitId: Long, today: String): Int {
        var cursor = java.time.LocalDate.parse(today)
        var count = 0
        while (true) {
            val key = cursor.toString()
            val done = db.evolutionQueries.selectHabitLogs(habitId, key, key)
                .executeAsList().firstOrNull()?.done == 1L
            if (!done) break
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }
}
