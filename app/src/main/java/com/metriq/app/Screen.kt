package com.metriq.app

enum class Screen {
    DASHBOARD, SEARCH, CREATE, SCAN, LABEL_SCAN, FOOD_PHOTO,
    WEIGHT, PROFILE, BODY, HABITS, STEPS, MICRONUTRIENTS, RECIPES,
    ANALYTICS, SETTINGS, VOICE_LOG, AI_MODELS, INTEGRATIONS,
    BACKUP, FOOD_PACKS,
}

/**
 * Parent used by both the in-screen back buttons and the system BackHandler, so system
 * back unwinds the same chain instead of jumping straight to DASHBOARD.
 */
val Screen.parent: Screen?
    get() = when (this) {
        Screen.DASHBOARD -> null
        Screen.SEARCH, Screen.CREATE, Screen.SCAN, Screen.LABEL_SCAN,
        Screen.FOOD_PHOTO, Screen.VOICE_LOG -> Screen.DASHBOARD
        Screen.WEIGHT, Screen.PROFILE, Screen.BODY, Screen.HABITS, Screen.STEPS,
        Screen.MICRONUTRIENTS, Screen.RECIPES, Screen.ANALYTICS -> Screen.DASHBOARD
        Screen.SETTINGS -> Screen.PROFILE
        Screen.AI_MODELS, Screen.INTEGRATIONS, Screen.BACKUP, Screen.FOOD_PACKS -> Screen.SETTINGS
    }
