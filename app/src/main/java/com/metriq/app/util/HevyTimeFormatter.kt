package com.metriq.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats the stored Hevy sync timestamp for display; errors are not timestamps. */
object HevyTimeFormatter {
    private val locale = Locale.forLanguageTag("pt-BR")
    private val timeOnly = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateAndTime = DateTimeFormatter.ofPattern("dd MMM, HH:mm", locale)

    fun formatLastSync(
        raw: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        if (raw.isNullOrBlank()) return null
        val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return null
        val local = instant.atZone(zone)
        val time = timeOnly.format(local)
        return when (local.toLocalDate()) {
            now.atZone(zone).toLocalDate() -> "hoje às $time"
            now.atZone(zone).toLocalDate().minusDays(1) -> "ontem às $time"
            else -> dateAndTime.format(local)
        }
    }
}
