package com.metriq.app.integrations

/**
 * Catalog of integrations shown to the user.
 *
 * `id` is also the suffix of the literal settings key `integration_<id>`.
 * Those keys are read straight from [com.metriq.app.AppGraph.store.settings];
 * adding an integration never requires an AppSettings edit.
 */
data class IntegrationSpec(
    val id: String,
    val name: String,
    val description: String,
    val kind: String,
)

object IntegrationsHub {

    const val KIND_ACTIVE = "active"
    const val KIND_COMING_SOON = "coming-soon"

    const val STATUS_CONNECTED = "connected"
    const val STATUS_NOT_CONFIGURED = "not-configured"
    const val STATUS_COMING_SOON = "coming-soon"

    /** The two sources this build actually reads from, plus honest placeholders. */
    val all: List<IntegrationSpec> = listOf(
        IntegrationSpec(
            id = "health_connect",
            name = "Health Connect",
            description = "Passos, peso e exercício são lidos do Android Health Connect.",
            kind = KIND_ACTIVE,
        ),
        IntegrationSpec(
            id = "hevy",
            name = "Hevy",
            description = "Workout history is read from Hevy with a key stored on this device.",
            kind = KIND_ACTIVE,
        ),
        IntegrationSpec(
            id = "samsung_health",
            name = "Samsung Health",
            description = "O acesso direto ao Samsung Health não está disponível nesta versão.",
            kind = KIND_COMING_SOON,
        ),
        IntegrationSpec(
            id = "garmin",
            name = "Garmin",
            description = "O acesso direto ao Garmin não está disponível nesta versão.",
            kind = KIND_COMING_SOON,
        ),
        IntegrationSpec(
            id = "fitbit",
            name = "Fitbit",
            description = "O acesso direto ao Fitbit não está disponível nesta versão.",
            kind = KIND_COMING_SOON,
        ),
        IntegrationSpec(
            id = "strava",
            name = "Strava",
            description = "O acesso direto ao Strava não está disponível nesta versão.",
            kind = KIND_COMING_SOON,
        ),
        IntegrationSpec(
            id = "sleep",
            name = "Sleep",
            description = "A importação de fases de sono não está disponível nesta versão.",
            kind = KIND_COMING_SOON,
        ),
        IntegrationSpec(
            id = "smart_scale",
            name = "Smart Scale",
            description = "A associação direta de balanças inteligentes não está disponível nesta versão.",
            kind = KIND_COMING_SOON,
        ),
    )

    /**
     * Maps every integration id to `connected` / `not-configured` / `coming-soon`.
     *
     * Only active kinds consult the literal `integration_<id>` setting; a value of
     * "connected", "true" or "1" means connected, anything else is not configured.
     * Placeholder integrations are always `coming-soon` — no setting can turn them on.
     */
    fun status(settings: Map<String, String>): Map<String, String> = all.associate { spec ->
        val value = settings[settingsKey(spec.id)]?.trim()?.lowercase().orEmpty()
        val state = when (spec.kind) {
            KIND_ACTIVE -> when (value) {
                "connected", "true", "1" -> STATUS_CONNECTED
                else -> STATUS_NOT_CONFIGURED
            }
            else -> STATUS_COMING_SOON
        }
        spec.id to state
    }

    fun settingsKey(id: String): String = "integration_$id"
}
