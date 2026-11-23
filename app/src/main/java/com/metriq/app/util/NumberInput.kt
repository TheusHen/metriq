package com.metriq.app.util

/**
 * Parses a number typed into a text field regardless of the device locale's decimal
 * separator. pt-BR/es users routinely type "150,5", while the JVM parser only accepts a dot.
 */
fun parseDoubleLoose(raw: String?): Double? =
    raw?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** Integer counterpart of [parseDoubleLoose] for whole-number fields (age, weeks, kcal). */
fun parseIntLoose(raw: String?): Int? {
    val value = parseDoubleLoose(raw) ?: return null
    if (value % 1.0 != 0.0) return null
    if (value < Int.MIN_VALUE.toDouble() || value > Int.MAX_VALUE.toDouble()) return null
    return value.toInt()
}
