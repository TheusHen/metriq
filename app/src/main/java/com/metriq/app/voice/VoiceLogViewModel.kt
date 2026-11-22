package com.metriq.app.voice

import androidx.lifecycle.ViewModel
import com.metriq.app.AppGraph
import com.metriq.data.db.Food

/** Bridges the pure [FoodPhraseParser] to the on-device food catalog. */
class VoiceLogViewModel : ViewModel() {

    /** All locally stored foods — the parser resolves names against this list only. */
    private fun catalog(): List<Food> = AppGraph.store.food.recent(5000L)

    fun parse(phrase: String): ParsedPortion? = FoodPhraseParser.parse(phrase, catalog())

    /**
     * Returns the exact catalog row behind a parsed name so the UI can compute macros.
     * Falls back to full-text search for names that were normalized during parsing.
     */
    fun foodFor(name: String): Food? {
        val target = name.trim()
        return catalog().firstOrNull { it.name.equals(target, ignoreCase = true) }
            ?: AppGraph.store.food.search(target).firstOrNull()
                ?.let { hit -> AppGraph.store.food.byId(hit.id) }
    }
}
