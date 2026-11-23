package com.metriq.app.analytics

import org.json.JSONObject

/**
 * On-device codec for user-edited micronutrient targets.
 *
 * Stored as a flat JSON object of "nutrient key": positive number. Anything
 * malformed, non-finite or non-positive is ignored rather than guessed.
 */
object MicroTargetCodec {

    fun decode(raw: String?): Map<String, Double> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            val out = LinkedHashMap<String, Double>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optDouble(key, Double.NaN)
                if (value.isFinite() && value > 0.0) out[key] = value
            }
            out
        }.getOrElse { emptyMap() }
    }

    fun encode(targets: Map<String, Double>): String = JSONObject().apply {
        targets.forEach { (key, value) ->
            if (value.isFinite() && value > 0.0) put(key, value)
        }
    }.toString()
}
