package com.metriq.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class HevyTimeFormatterTest {
    private val now = Instant.parse("2024-06-15T12:00:00Z")
    private val zone = ZoneOffset.UTC

    @Test
    fun `formats a sync from today as a time only`() {
        assertEquals(
            "hoje às 09:30",
            HevyTimeFormatter.formatLastSync("2024-06-15T09:30:00Z", now, zone),
        )
    }

    @Test
    fun `formats a sync from yesterday as yesterday`() {
        assertEquals(
            "ontem às 18:45",
            HevyTimeFormatter.formatLastSync("2024-06-14T18:45:00Z", now, zone),
        )
    }

    @Test
    fun `formats an older sync with date and time`() {
        assertEquals(
            "02 jun., 08:00",
            HevyTimeFormatter.formatLastSync("2024-06-02T08:00:00Z", now, zone),
        )
    }

    @Test
    fun `returns null for blank or non ISO values`() {
        assertNull(HevyTimeFormatter.formatLastSync(null, now, zone))
        assertNull(HevyTimeFormatter.formatLastSync("", now, zone))
        assertNull(HevyTimeFormatter.formatLastSync("Erro: sem conexão", now, zone))
    }
}
