package com.metriq.app

/**
 * Central registry for user-configurable keys. Values live in the local settings table,
 * so every option here survives restarts and stays on-device.
 */
object AppSettings {
    const val UNIT_SYSTEM = "unit_system"              // "kg" | "lb"
    const val ENERGY_UNIT = "energy_unit"              // "kcal" | "kj"
    const val WIDGET_REFRESH_MINUTES = "widget_refresh_minutes" // "15" | "30" | "60"
    const val HEALTH_AUTO_SYNC = "health_auto_sync"    // "true" | "false"
    const val HEALTH_IMPORT_STEPS = "health_import_steps"
    const val HEALTH_IMPORT_WEIGHT = "health_import_weight"
    const val ANALYTICS_RANGE = "analytics_range"      // "7" | "30" | "90" | "365" | "0"
    const val ANALYTICS_METRIC = "analytics_metric"    // metric id
    const val REDUCED_MOTION = "reduced_motion"        // "true" | "false"
    const val ADVANCED_METRICS = "advanced_metrics"    // "true" | "false"
    const val ADAPTIVE_MACROS = "adaptive_macros"      // "true" | "false"
    const val AI_STORAGE_BUDGET_MB = "ai_storage_budget_mb"
    const val AI_MODEL_ID = "ai_model_id"
    const val AI_MODEL_URL = "ai_model_url"
    const val ONBOARDING_COMPLETE = "onboarding_complete"

    val defaults = mapOf(
        UNIT_SYSTEM to "kg",
        ENERGY_UNIT to "kcal",
        WIDGET_REFRESH_MINUTES to "30",
        HEALTH_AUTO_SYNC to "true",
        HEALTH_IMPORT_STEPS to "true",
        HEALTH_IMPORT_WEIGHT to "true",
        ANALYTICS_RANGE to "30",
        ANALYTICS_METRIC to "intake",
        REDUCED_MOTION to "false",
        ADVANCED_METRICS to "true",
        ADAPTIVE_MACROS to "true",
        AI_STORAGE_BUDGET_MB to "0",
        AI_MODEL_ID to "",
        AI_MODEL_URL to "",
        ONBOARDING_COMPLETE to "false",
    )

    fun get(key: String): String = AppGraph.store.settings.get(key) ?: defaults[key].orEmpty()

    fun bool(key: String): Boolean = get(key).toBooleanStrictOrNull()
        ?: defaults[key]?.toBooleanStrictOrNull() ?: false

    fun all(): Map<String, String> = defaults.mapValues { (key, fallback) ->
        AppGraph.store.settings.get(key) ?: fallback
    }
}
