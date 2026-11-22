package com.metriq.app.hevy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime

sealed class HevyResult<out T> {
    data class Success<T>(val value: T) : HevyResult<T>()
    data object InvalidKey : HevyResult<Nothing>()
    data object RateLimited : HevyResult<Nothing>()
    data object Offline : HevyResult<Nothing>()
    data class Failure(val message: String) : HevyResult<Nothing>()
}

data class HevySet(
    val type: String?,
    val weightKg: Double?,
    val reps: Int?,
    val distanceKm: Double?,
    val durationSeconds: Long?,
)

data class HevyExercise(
    val title: String,
    val sets: List<HevySet>,
    val restSeconds: Long?,
)

data class HevyWorkout(
    val id: String,
    val title: String,
    val startTime: Instant,
    val endTime: Instant?,
    val exercises: List<HevyExercise>,
)

/** Tolerant ISO-8601 parser used by both the API layer and the local cache decoder. */
internal fun parseHevyInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    val value = raw.trim()
    runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()?.let { return it }
    runCatching { ZonedDateTime.parse(value).toInstant() }.getOrNull()?.let { return it }
    // Some exports use a space instead of 'T' between date and time.
    runCatching {
        OffsetDateTime.parse(value.replace(' ', 'T')).toInstant()
    }.getOrNull()?.let { return it }
    return null
}

private fun jsonString(obj: JSONObject, vararg keys: String): String? {
    for (key in keys) {
        val v = obj.opt(key)
        if (v == null || v == JSONObject.NULL) continue
        val s = v.toString().trim()
        if (s.isNotEmpty()) return s
    }
    return null
}

private fun jsonDouble(obj: JSONObject, vararg keys: String): Double? {
    for (key in keys) {
        val v = obj.opt(key)
        if (v == null || v == JSONObject.NULL) continue
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
        if (d != null && d.isFinite()) return d
    }
    return null
}

private fun jsonInt(obj: JSONObject, vararg keys: String): Int? {
    for (key in keys) {
        val v = obj.opt(key)
        if (v == null || v == JSONObject.NULL) continue
        val i = when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt()
            else -> null
        }
        if (i != null && i >= 0) return i
    }
    return null
}

private fun jsonLong(obj: JSONObject, vararg keys: String): Long? {
    for (key in keys) {
        val v = obj.opt(key)
        if (v == null || v == JSONObject.NULL) continue
        val l = when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
            else -> null
        }
        if (l != null && l >= 0) return l
    }
    return null
}

private fun parseWorkouts(body: String): List<HevyWorkout> {
    val root = JSONObject(body)
    val workouts = root.optJSONArray("workouts") ?: return emptyList()
    val result = mutableListOf<HevyWorkout>()
    for (i in 0 until workouts.length()) {
        val w = workouts.optJSONObject(i) ?: continue
        val id = jsonString(w, "id") ?: continue
        val start = parseHevyInstant(jsonString(w, "start_time", "startTime")) ?: continue
        val title = jsonString(w, "title").orEmpty()
        val end = parseHevyInstant(jsonString(w, "end_time", "endTime"))

        val exercises = mutableListOf<HevyExercise>()
        w.optJSONArray("exercises")?.let { exArray ->
            for (j in 0 until exArray.length()) {
                val ex = exArray.optJSONObject(j) ?: continue
                val exTitle = jsonString(ex, "title", "name") ?: continue
                val sets = mutableListOf<HevySet>()
                ex.optJSONArray("sets")?.let { setArray ->
                    for (k in 0 until setArray.length()) {
                        val s = setArray.optJSONObject(k) ?: continue
                        sets += HevySet(
                            type = jsonString(s, "type"),
                            weightKg = jsonDouble(s, "weight_kg", "weightKg"),
                            reps = jsonInt(s, "reps"),
                            distanceKm = jsonDouble(s, "distance_km", "distanceKm"),
                            durationSeconds = jsonLong(s, "duration_seconds", "durationSeconds"),
                        )
                    }
                }
                exercises += HevyExercise(
                    title = exTitle,
                    sets = sets,
                    restSeconds = jsonLong(ex, "rest_seconds", "restSeconds"),
                )
            }
        }
        result += HevyWorkout(id, title, start, end, exercises)
    }
    return result
}

object HevyApi {
    private const val ENDPOINT = "https://api.hevyapp.com/v1/workouts"
    private const val RATE_LIMITED = 429

    suspend fun fetchWorkouts(
        apiKey: String,
        page: Int = 1,
        pageSize: Int = 25,
    ): HevyResult<List<HevyWorkout>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$ENDPOINT?page=$page&pageSize=$pageSize")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Api-Key", apiKey)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Metriq/0.1 (Hevy sync)")
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val workouts = runCatching { parseWorkouts(body) }.getOrDefault(emptyList())
                    HevyResult.Success(workouts)
                }
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> HevyResult.InvalidKey
                RATE_LIMITED -> HevyResult.RateLimited
                else -> {
                    val detail = runCatching {
                        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?.lineSequence()?.firstOrNull()?.trim()?.take(80)
                    }.getOrNull()
                    HevyResult.Failure(buildString {
                        append("HTTP ").append(code)
                        if (!detail.isNullOrEmpty()) append(": ").append(detail)
                    })
                }
            }
        } catch (e: IOException) {
            HevyResult.Offline
        } catch (e: Exception) {
            HevyResult.Failure(e.message?.take(80) ?: "Erro desconhecido")
        } finally {
            connection?.disconnect()
        }
    }
}
