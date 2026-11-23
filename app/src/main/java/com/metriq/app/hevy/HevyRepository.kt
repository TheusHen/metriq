package com.metriq.app.hevy

import android.content.Context
import com.metriq.app.AppGraph
import com.metriq.app.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** One decoded snapshot per refresh; parsing 120 workouts nine times per refresh is wasteful. */
    @Volatile
    private var cachedWorkouts: List<HevyWorkout>? = null

    /** Bumped by [clear] so an in-flight sync cannot resurrect data after a disconnect. */
    @Volatile
    private var generation = 0

    fun hasKey(): Boolean = HevyKeyStore.read(appContext) != null

    suspend fun sync(): HevySyncResult = withContext(Dispatchers.IO) {
        val startedGeneration = generation
        val apiKey = HevyKeyStore.read(appContext)
        if (apiKey == null) {
            runCatching { settings.set(AppSettings.HEVY_LAST_ERROR, "Chave de API não configurada") }
            return@withContext HevySyncResult(
                imported = 0,
                totalStored = workouts().size,
                message = "Chave de API não configurada",
            )
        }

        AppGraph.ensure(appContext)
        var cached = workouts()
        var imported = 0
        var anySuccess = false
        var sawInvalidKey = false
        var errorMessage: String? = null

        for (page in 1..HevyPaging.MAX_PAGES) {
            when (val result = HevyApi.fetchWorkouts(apiKey, page = page, pageSize = HevyPaging.PAGE_SIZE)) {
                is HevyResult.Success -> {
                    anySuccess = true
                    val fetched = result.value
                    val merged = HevyCacheCodec.merge(cached, fetched.workouts)
                    cached = merged.workouts
                    imported += merged.imported
                    if (HevyPaging.shouldStop(page, fetched.workouts.size, cached.size, fetched.pageCount)) break
                }
                else -> {
                    if (result is HevyResult.InvalidKey) sawInvalidKey = true
                    errorMessage = describe(result)
                    break
                }
            }
        }

        if (!anySuccess) {
            val message = errorMessage ?: "Nenhum treino importado"
            runCatching {
                settings.set(AppSettings.HEVY_LAST_ERROR, message)
                if (HevySyncGuard.shouldClearConnected(sawInvalidKey, anySuccess)) {
                    settings.set(AppSettings.HEVY_CONNECTED, "false")
                }
            }
            return@withContext HevySyncResult(
                imported = 0,
                totalStored = cached.size,
                message = message,
            )
        }

        val newest = HevyCacheCodec.cap(cached, HevyPaging.CACHE_LIMIT)
        val encoded = HevyCacheCodec.encode(newest)
        // A disconnect (or key invalidation) during the network calls must win over this write.
        if (HevySyncGuard.isStale(startedGeneration, generation, HevyKeyStore.read(appContext) != null)) {
            return@withContext HevySyncResult(
                imported = 0,
                totalStored = workouts().size,
                message = "Sincronização cancelada",
            )
        }
        val persisted = encoded != null && runCatching {
            settings.set(AppSettings.HEVY_WORKOUTS_JSON, encoded)
            settings.set(AppSettings.HEVY_LAST_SYNC, Instant.now().toString())
            settings.set(AppSettings.HEVY_LAST_ERROR, "")
            settings.set(AppSettings.HEVY_CONNECTED, "true")
        }.isSuccess
        if (persisted) cachedWorkouts = newest
        else runCatching { settings.set(AppSettings.HEVY_LAST_ERROR, "Falha ao salvar o cache local") }

        HevySyncResult(
            imported = imported,
            totalStored = if (persisted) newest.size else cached.size,
            message = when {
                errorMessage != null -> "Importados $imported treino(s); $errorMessage"
                persisted -> "Sincronizados $imported novo(s) treino(s)"
                else -> "Falha ao salvar o cache local"
            },
        )
    }

    fun workouts(): List<HevyWorkout> =
        cachedWorkouts ?: loadCache().also { cachedWorkouts = it }

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

    /** Returns false if any local value could not be cleared, so the UI can say so. */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        // Invalidate any in-flight sync before touching the key or the settings.
        generation++
        val keyCleared = HevyKeyStore.clear(appContext)
        val settingsCleared = runCatching {
            AppGraph.ensure(appContext)
            settings.set(AppSettings.HEVY_WORKOUTS_JSON, "")
            settings.set(AppSettings.HEVY_LAST_SYNC, "")
            settings.set(AppSettings.HEVY_LAST_ERROR, "")
            settings.set(AppSettings.HEVY_CONNECTED, "false")
        }.isSuccess
        // Keep the snapshot only if the on-disk cache could not be cleared.
        if (settingsCleared) cachedWorkouts = emptyList()
        settingsCleared && keyCleared
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
        if (raw.isBlank()) emptyList() else HevyCacheCodec.decode(raw)
    }.getOrDefault(emptyList())

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

}
