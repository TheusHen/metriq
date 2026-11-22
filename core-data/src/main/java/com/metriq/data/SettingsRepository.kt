package com.metriq.data

import com.metriq.data.db.MetriqDatabase

class SettingsRepository(private val db: MetriqDatabase) {

    fun get(key: String): String? = db.metriqQueries.settingValue(key).executeAsOneOrNull()

    fun set(key: String, value: String) = db.metriqQueries.setSetting(key, value)
}

