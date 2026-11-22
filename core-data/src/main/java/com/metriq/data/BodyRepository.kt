package com.metriq.data

import com.metriq.data.db.MetriqDatabase

class BodyRepository(private val db: MetriqDatabase, private val notifier: DataChangeNotifier) {

    fun measurementsForDate(date: String) = db.evolutionQueries.selectMeasurements(date).executeAsList()

    fun history(site: String, limit: Long = 60) =
        db.evolutionQueries.selectMeasurementHistory(site, limit).executeAsList()

    fun add(date: String, site: String, valueCm: Double) {
        db.evolutionQueries.insertMeasurement(date, site, valueCm)
        notifier.notify(DataType.BODY_MEASUREMENT)
    }

    fun delete(id: Long) {
        db.evolutionQueries.deleteMeasurement(id)
        notifier.notify(DataType.BODY_MEASUREMENT)
    }

    fun allSites(): List<String> = db.evolutionQueries.selectAllMeasurementSites().executeAsList()
}
