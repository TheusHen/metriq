package com.metriq.data

import com.metriq.data.db.MetriqDatabase
import java.time.LocalDate

data class LogItem(
    val id: Long,
    val date: String,
    val hour: Int,
    val foodId: Long,
    val name: String,
    val brand: String?,
    val iconId: String?,
    val grams: Double,
    val servings: Double,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
)

class LogRepository(private val db: MetriqDatabase, private val notifier: DataChangeNotifier? = null) {

    fun forDate(date: String): List<LogItem> = db.metriqQueries.logsForDate(date)
        .executeAsList()
        .map {
            LogItem(
                id = it.id,
                date = it.date,
                hour = it.hour.toInt(),
                foodId = it.foodId,
                name = it.name,
                brand = it.brand,
                iconId = it.iconId,
                grams = it.grams,
                servings = it.servings,
                calories = it.calories * it.grams / 100.0 * it.servings,
                protein = it.protein * it.grams / 100.0 * it.servings,
                fat = it.fat * it.grams / 100.0 * it.servings,
                carbs = it.carbs * it.grams / 100.0 * it.servings,
            )
        }

    fun add(date: String, hour: Int, foodId: Long, grams: Double, servings: Double = 1.0) {
        db.metriqQueries.insertLogEntry(date, hour.toLong(), foodId, grams, servings, System.currentTimeMillis())
        notifier?.notify(DataType.FOOD_LOG)
    }

    fun delete(id: Long) {
        db.metriqQueries.deleteLogEntry(id)
        notifier?.notify(DataType.FOOD_LOG)
    }

    fun copy(fromDate: String, toDate: String) {
        if (fromDate == toDate) return
        // Clear the target first, in the same transaction, so repeating a copy never
        // duplicates the day's entries.
        db.transaction {
            db.metriqQueries.deleteLogsForDate(toDate)
            db.metriqQueries.copyLogToDate(toDate, System.currentTimeMillis(), fromDate)
        }
        notifier?.notify(DataType.FOOD_LOG)
    }

    /** Logged calories per day; used by the expenditure estimator. */
    fun dailyIntake(): Map<LocalDate, Double?> = db.metriqQueries.dailyIntake()
        .executeAsList()
        .mapNotNull { row -> runCatching { LocalDate.parse(row.date) to row.kcal }.getOrNull() }
        .toMap()
}

