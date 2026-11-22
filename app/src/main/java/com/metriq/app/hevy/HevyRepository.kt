package com.metriq.app.hevy

import android.content.Context
import com.metriq.app.AppGraph
import com.metriq.app.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class HevyDaySignal(
    val date: LocalDate,
    val volumeKg: Double,
    val durationMinutes: Int,
    val muscles: Set<String>,
    val sessions: Int,
    val workoutCount: Int,
)

data class HevySyncResult(
    val imported: Int,
    val totalStored: Int,
    val message: String,
)

class HevyRepository(private val context: Context) {
    private val appContext = context.applicationContext

    init {
        AppGraph.ensure(appContext)
    }

    private val settings get() = AppGraph.store.settings

    suspend fun sync(): HevySyncResult = withContext(Dispatchers.IO) {
        val apiKey = HevyKeyStore.read(appContext)
        if (apiKey == null) {
            return@withContext HevySyncResult(
                imported = 0,
                totalStored = workouts().size,
                message = "Chave de API não configurada",
            )
        }

        AppGraph.ensure(appContext)
        val byId = loadCache().associateBy { it.id }.toMutableMap()
        var imported = 0
        var anySuccess = false
        var errorMessage: String? = null

        for (page in 1..MAX_PAGES) {
            when (val result = HevyApi.fetchWorkouts(apiKey, page = page, pageSize = PAGE_SIZE)) {
                is HevyResult.Success -> {
                    anySuccess = true
                    val items = result.value
                    items.forEach { workout ->
                        if (!byId.containsKey(workout.id)) imported++
                        byId[workout.id] = workout
                    }
                    if (items.size < PAGE_SIZE) break
                }
                else -> {
                    errorMessage = describe(result)
                    break
                }
            }
        }

        if (!anySuccess) {
            return@withContext HevySyncResult(
                imported = 0,
                totalStored = byId.size,
                message = errorMessage ?: "Nenhum treino importado",
            )
        }

        val newest = byId.values.sortedByDescending { it.startTime }.take(CACHE_LIMIT)
        val persisted = runCatching {
            settings.set(AppSettings.HEVY_WORKOUTS_JSON, encodeCache(newest))
            settings.set(AppSettings.HEVY_LAST_SYNC, Instant.now().toString())
            settings.set(AppSettings.HEVY_CONNECTED, "true")
        }.isSuccess

        HevySyncResult(
            imported = imported,
            totalStored = if (persisted) newest.size else byId.size,
            message = when {
                errorMessage != null -> "Importados $imported treino(s); $errorMessage"
                persisted -> "Sincronizados $imported novo(s) treino(s)"
                else -> "Falha ao salvar o cache local"
            },
        )
    }

    fun workouts(): List<HevyWorkout> = loadCache()

    fun daySignal(date: LocalDate): HevyDaySignal? {
        val zone = ZoneId.systemDefault()
        val dayWorkouts = workouts().filter { it.startTime.atZone(zone).toLocalDate() == date }
        if (dayWorkouts.isEmpty()) return null

        var volumeKg = 0.0
        var seconds = 0L
        val muscles = mutableSetOf<String>()
        dayWorkouts.forEach { workout ->
            workout.endTime?.let { end ->
                val elapsed = Duration.between(workout.startTime, end)
                if (!elapsed.isNegative) seconds += elapsed.seconds
            }
            workout.exercises.forEach { exercise ->
                muscles += musclesFor(exercise.title)
                exercise.sets.forEach { set ->
                    val kg = set.weightKg ?: 0.0
                    val reps = set.reps ?: 0
                    if (kg > 0.0 && reps > 0) volumeKg += kg * reps
                }
            }
        }

        return HevyDaySignal(
            date = date,
            volumeKg = volumeKg,
            durationMinutes = (seconds / 60.0).roundToInt(),
            muscles = muscles,
            // Each Hevy workout is one training session for that day.
            sessions = dayWorkouts.size,
            workoutCount = dayWorkouts.size,
        )
    }

    fun recentSignals(days: Int): List<HevyDaySignal> {
        if (days <= 0) return emptyList()
        val today = LocalDate.now()
        return (0 until days).mapNotNull { offset ->
            runCatching { daySignal(today.minusDays(offset.toLong())) }.getOrNull()
        }
    }

    fun lastSync(): String? = runCatching {
        AppGraph.ensure(appContext)
        AppSettings.get(AppSettings.HEVY_LAST_SYNC).takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear() {
        runCatching {
            AppGraph.ensure(appContext)
            settings.set(AppSettings.HEVY_WORKOUTS_JSON, "")
            settings.set(AppSettings.HEVY_LAST_SYNC, "")
            settings.set(AppSettings.HEVY_CONNECTED, "false")
        }
        HevyKeyStore.clear(appContext)
    }

    private fun describe(result: HevyResult<*>): String = when (result) {
        is HevyResult.InvalidKey -> "chave de API inválida"
        is HevyResult.RateLimited -> "limite de requisições atingido"
        is HevyResult.Offline -> "sem conexão com a internet"
        is HevyResult.Failure -> result.message.take(80)
        is HevyResult.Success -> ""
    }

    private fun loadCache(): List<HevyWorkout> = runCatching {
        val raw = AppSettings.get(AppSettings.HEVY_WORKOUTS_JSON)
        if (raw.isBlank()) emptyList() else decodeCache(raw)
    }.getOrDefault(emptyList())

    private fun decodeCache(raw: String): List<HevyWorkout> {
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
        return result
    }

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

    private fun encodeCache(workouts: List<HevyWorkout>): String = runCatching {
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
    }.getOrDefault("")

    private fun musclesFor(title: String): Set<String> {
        val t = title.lowercase(Locale.ROOT)
        return when {
            "bench" in t -> setOf("peito", "tríceps")
            "squat" in t -> setOf("quadríceps", "glúteos")
            "deadlift" in t -> setOf("costas", "posteriores")
            "press" in t -> setOf("ombros", "tríceps")
            "row" in t -> setOf("costas")
            "curl" in t -> setOf("bíceps")
            "lunge" in t -> setOf("quadríceps", "glúteos")
            "pull" in t -> setOf("costas", "bíceps")
            "dip" in t -> setOf("peito", "tríceps")
            "plank" in t || "core" in t || "ab" in t -> setOf("core")
            else -> setOf("outros")
        }
    }

    private companion object {
        const val MAX_PAGES = 3
        const val PAGE_SIZE = 25
        const val CACHE_LIMIT = 120
    }
}
