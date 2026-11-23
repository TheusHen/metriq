package com.metriq.app.ai

/** Pure validation for optional model downloads; cleartext is deliberately disallowed. */
internal object DownloadPolicy {
    const val BYTES_PER_MB: Long = 1024L * 1024L

    /** Returns a user-facing error, or null when the URL is acceptable. */
    fun urlError(raw: String): String? {
        val address = raw.trim()
        if (address.isEmpty() || !address.startsWith("https://", ignoreCase = true) ||
            address.length <= "https://".length
        ) {
            return "Introduz um URL https válido para o ficheiro do modelo."
        }
        return null
    }

    /** Zero means the storage budget is switched off. */
    fun budgetBytes(mb: Int): Long =
        if (mb <= 0) 0L else mb.toLong().coerceAtMost(Long.MAX_VALUE / BYTES_PER_MB) * BYTES_PER_MB

    fun exceeds(bytes: Long, budgetBytes: Long): Boolean = budgetBytes > 0 && bytes > budgetBytes
}
