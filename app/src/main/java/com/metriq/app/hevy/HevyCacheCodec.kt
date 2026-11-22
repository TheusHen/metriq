package com.metriq.app.hevy

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure codec and merge helpers for the local Hevy workout cache.
 *
 * Keeping this free of Android APIs lets the cache format be tested on the JVM.
 * [encode] returns null instead of a blank string on failure so a bad encode
 * never wipes a previously valid cache.
 */
internal object HevyCacheCodec {
    data class MergeResult(val workouts: List<HevyWorkout>, val imported: Int)

    fun encode(workouts: List<HevyWorkout>): String? = runCatching {
        val array = JSONArray()
        workouts.forEach { w ->
            val o = JSONObject()
            o.put("id", w.id)
            o.put("title", w.title)
            o.put("startTime", w.startTime.toString())
            w.endTime?.let { o.put("endTime", it.toString()) }
            val exArray = JSONArray()
            w.exercises.forEach { ex ->
                val eo = JSONObject()
                eo.put("title", ex.title)
                ex.restSeconds?.let { eo.put("restSeconds", it) }
                val setArray = JSONArray()
                ex.sets.forEach { s ->
                    val so = JSONObject()
                    s.type?.let { so.put("type", it) }
                    s.weightKg?.let { so.put("weightKg", it) }
                    s.reps?.let { so.put("reps", it) }
                    s.distanceKm?.let { so.put("distanceKm", it) }
                    s.durationSeconds?.let { so.put("durationSeconds", it) }
                    setArray.put(so)
                }
                eo.put("sets", setArray)
                exArray.put(eo)
            }
            o.put("exercises", exArray)
            array.put(o)
        }
        array.toString()
    }.getOrNull()

    fun decode(raw: String): List<HevyWorkout> = runCatching {
        val array = JSONArray(raw)
        val result = mutableListOf<HevyWorkout>()
        for (i in 0 until array.length()) {
            val w = array.optJSONObject(i) ?: continue
            val id = w.optString("id", "")
            if (id.isBlank()) continue
            val start = parseHevyInstant(w.optString("startTime", "")) ?: continue
            val title = w.optString("title", "")
            val end = parseHevyInstant(w.optString("endTime", ""))
            result += HevyWorkout(id, title, start, end, decodeExercises(w.optJSONArray("exercises")))
        }
        result
    }.getOrDefault(emptyList())

    /** Replaces workouts by id, counting only ids that were not already cached. */
    fun merge(existing: List<HevyWorkout>, fetched: List<HevyWorkout>): MergeResult {
        val byId = existing.associateBy { it.id }.toMutableMap()
        var imported = 0
        fetched.forEach { workout ->
            if (!byId.containsKey(workout.id)) imported++
            byId[workout.id] = workout
        }
        return MergeResult(byId.values.toList(), imported)
    }

    /** Keeps the newest [limit] workouts by start time. */
    fun cap(workouts: List<HevyWorkout>, limit: Int): List<HevyWorkout> =
        if (limit <= 0) emptyList() else workouts.sortedByDescending { it.startTime }.take(limit)

    private fun decodeExercises(array: JSONArray?): List<HevyExercise> {
        if (array == null) return emptyList()
        val result = mutableListOf<HevyExercise>()
        for (j in 0 until array.length()) {
            val ex = array.optJSONObject(j) ?: continue
            val title = ex.optString("title", "")
            if (title.isBlank()) continue
            val rest = ex.optLong("restSeconds", -1).takeIf { it >= 0 }
            result += HevyExercise(title, decodeSets(ex.optJSONArray("sets")), rest)
        }
        return result
    }

    private fun decodeSets(array: JSONArray?): List<HevySet> {
        if (array == null) return emptyList()
        val result = mutableListOf<HevySet>()
        for (k in 0 until array.length()) {
            val s = array.optJSONObject(k) ?: continue
            result += HevySet(
                type = s.optString("type", "").ifBlank { null },
                weightKg = s.optDouble("weightKg", Double.NaN).takeIf { it.isFinite() },
                reps = s.optInt("reps", -1).takeIf { it >= 0 },
                distanceKm = s.optDouble("distanceKm", Double.NaN).takeIf { it.isFinite() },
                durationSeconds = s.optLong("durationSeconds", -1).takeIf { it >= 0 },
            )
        }
        return result
    }
}
