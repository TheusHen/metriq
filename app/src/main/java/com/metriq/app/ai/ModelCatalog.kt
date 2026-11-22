package com.metriq.app.ai

import java.util.Locale

/**
 * A single catalog entry. These are REFERENCE TIERS describing the shape of an optional,
 * user-supplied model file — not bundled binaries and not guaranteed download links.
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val sizeMb: Int,
    val kind: String,
    val description: String,
)

/**
 * Reference tiers for optional on-device models.
 *
 * No model binaries ship with Metriq and no download URLs are hardcoded here. A user who
 * wants to try a model supplies their own URL in the AI Models screen; until such a file is
 * installed AND selected, the deterministic engine remains the active one.
 */
object ModelCatalog {
    private const val REFERENCE =
        "Reference tier only — no file is bundled and no download is guaranteed. Supply your own URL."

    val all: List<ModelSpec> = listOf(
        ModelSpec(
            id = "text-tiny",
            name = "Bonsai 1-bit (reference tier)",
            sizeMb = 40,
            kind = "TEXT",
            description = "Smallest text tier for very constrained devices. $REFERENCE",
        ),
        ModelSpec(
            id = "stt-tiny",
            name = "Whisper tiny (reference tier)",
            sizeMb = 75,
            kind = "STT",
            description = "Minimal speech-to-text tier for short voice notes. $REFERENCE",
        ),
        ModelSpec(
            id = "text-small",
            name = "Gemma 3n mini (reference tier)",
            sizeMb = 250,
            kind = "TEXT",
            description = "Balanced text tier for everyday phrase parsing. $REFERENCE",
        ),
        ModelSpec(
            id = "text-mid",
            name = "Gemma 3n (reference tier)",
            sizeMb = 600,
            kind = "TEXT",
            description = "Larger text tier with more headroom for long inputs. $REFERENCE",
        ),
        ModelSpec(
            id = "vision-small",
            name = "Gemma 3n vision (reference tier)",
            sizeMb = 900,
            kind = "VISION",
            description = "Multimodal tier for label and photo understanding. $REFERENCE",
        ),
        ModelSpec(
            id = "text-large",
            name = "Bonsai 8B (reference tier)",
            sizeMb = 1500,
            kind = "TEXT",
            description = "Largest tier, for high-memory devices only. $REFERENCE",
        ),
    )

    /**
     * Returns the largest reference tier of [kind] that fits both the storage budget and a
     * conservative RAM headroom rule (a model needs roughly twice its size free in heap).
     * Returns null when nothing fits — callers must not imply a model is available then.
     */
    fun recommend(availableMb: Int, ramMb: Int, kind: String): ModelSpec? {
        if (availableMb <= 0 || ramMb <= 0) return null
        val wanted = kind.trim().uppercase(Locale.ROOT)
        return all
            .filter { wanted.isBlank() || it.kind.equals(wanted, ignoreCase = true) }
            .filter { it.sizeMb <= availableMb }
            .filter { it.sizeMb * 2 <= ramMb }
            .maxByOrNull { it.sizeMb }
    }
}
