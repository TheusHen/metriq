package com.metriq.app.hevy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HevyCacheCodecTest {

    private fun workout(id: String, epochSecond: Long): HevyWorkout = HevyWorkout(
        id = id,
        title = id,
        startTime = Instant.ofEpochSecond(epochSecond),
        endTime = Instant.ofEpochSecond(epochSecond + 3600),
        exercises = listOf(
            HevyExercise(
                title = "Bench Press",
                sets = listOf(HevySet("normal", 60.0, 10, null, null)),
                restSeconds = 90,
            ),
        ),
    )

    @Test
    fun `encode and decode preserve workouts`() {
        val original = listOf(workout("a", 1_700_000_000L), workout("b", 1_700_003_600L))

        val encoded = HevyCacheCodec.encode(original)!!

        assertEquals(original, HevyCacheCodec.decode(encoded))
    }

    @Test
    fun `decode returns empty for malformed cache`() {
        assertTrue(HevyCacheCodec.decode("{not json").isEmpty())
    }

    @Test
    fun `decode skips entries without id or start time`() {
        val raw = """
            [
              {"id":"ok","title":"Ok","startTime":"2024-01-02T03:04:05Z","exercises":[]},
              {"title":"no id","startTime":"2024-01-02T03:04:05Z"},
              {"id":"bad-time","startTime":"not-a-date"}
            ]
        """.trimIndent()

        assertEquals(listOf("ok"), HevyCacheCodec.decode(raw).map { it.id })
    }

    @Test
    fun `encode returns null instead of blank when serialization fails`() {
        val bad = HevyWorkout(
            id = "x",
            title = "x",
            startTime = Instant.EPOCH,
            endTime = null,
            exercises = listOf(
                HevyExercise("Bench", listOf(HevySet("normal", Double.NaN, 10, null, null)), null),
            ),
        )

        assertNull(HevyCacheCodec.encode(listOf(bad)))
    }

    @Test
    fun `merge dedupes by id and counts only new workouts`() {
        val existing = listOf(workout("a", 1L).copy(title = "stale"))
        val fetched = listOf(workout("a", 1L).copy(title = "fresh"), workout("b", 2L))

        val result = HevyCacheCodec.merge(existing, fetched)

        assertEquals(1, result.imported)
        assertEquals(listOf("fresh", "b"), result.workouts.map { it.title })
    }

    @Test
    fun `cap keeps newest workouts by start time`() {
        val all = listOf(workout("old", 100L), workout("mid", 200L), workout("new", 300L))

        assertEquals(listOf("new", "mid"), HevyCacheCodec.cap(all, 2).map { it.id })
    }
}
