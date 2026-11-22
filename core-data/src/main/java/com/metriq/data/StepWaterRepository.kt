package com.metriq.data

import com.metriq.data.db.MetriqDatabase

class StepRepository(private val db: MetriqDatabase, private val notifier: DataChangeNotifier) {

    fun forDate(date: String): Long? = db.metriqQueries.stepsForDate(date).executeAsOneOrNull()

    fun range(from: String, to: String) = db.evolutionQueries.selectStepsRange(from, to).executeAsList()

    fun upsert(date: String, steps: Long, source: String = "manual") {
        db.metriqQueries.insertSteps(date, steps, source)
        notifier.notify(DataType.STEPS)
    }
}

class WaterRepository(private val db: MetriqDatabase, private val notifier: DataChangeNotifier) {

    fun forDate(date: String): Long? = db.metriqQueries.waterForDate(date).executeAsOneOrNull()

    fun range(from: String, to: String) = db.evolutionQueries.selectWaterRange(from, to).executeAsList()

    fun upsert(date: String, ml: Long) {
        db.metriqQueries.insertWater(date, ml)
        notifier.notify(DataType.WATER)
    }
}
