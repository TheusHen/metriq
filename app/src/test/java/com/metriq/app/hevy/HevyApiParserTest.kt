package com.metriq.app.hevy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HevyApiParserTest {

    @Test
    fun `parseHevyInstant accepts common ISO variants`() {
        val expected = Instant.parse("2024-01-02T03:04:05Z")

        assertEquals(expected, parseHevyInstant("2024-01-02T03:04:05Z"))
        assertEquals(expected, parseHevyInstant("2024-01-02T04:04:05+01:00"))
        assertEquals(expected, parseHevyInstant("2024-01-02 03:04:05+00:00"))
        assertNull(parseHevyInstant("nonsense"))
        assertNull(parseHevyInstant(null))
    }

    @Test
    fun `parseWorkouts parses nested exercises and skips invalid entries`() {
        val body = """
            {"workouts":[
              {"id":"a","title":"Push","start_time":"2024-01-02T03:04:05Z",
               "end_time":"2024-01-02T04:04:05Z",
               "exercises":[{"title":"Bench","sets":[
                 {"weight_kg":"60.5","reps":"10","duration_seconds":"30"}]}]},
              {"title":"missing id","start_time":"2024-01-02T03:04:05Z"},
              {"id":"c","start_time":"not-a-date"}
            ]}
        """.trimIndent()

        val parsed = parseWorkouts(body)

        assertEquals(1, parsed.size)
        assertEquals("a", parsed[0].id)
        val set = parsed[0].exercises[0].sets[0]
        assertEquals(60.5, set.weightKg!!, 0.0)
        assertEquals(10, set.reps)
        assertEquals(30L, set.durationSeconds)
    }
}
