package com.metriq.app.util

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Validates user-typed dates before they reach the database. */
object DateInput {

    /** Strict ISO local date (YYYY-MM-DD), zero padded, after trimming surrounding whitespace. */
    fun isValidIsoDate(value: String): Boolean = parse(value) != null

    fun parse(value: String): LocalDate? = try {
        LocalDate.parse(value.trim())
    } catch (_: DateTimeParseException) {
        null
    }
}
