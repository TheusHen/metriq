package com.metriq.app.hevy

/** Pure check used before an in-flight sync is allowed to persist its result. */
internal object HevySyncGuard {
    fun isStale(capturedGeneration: Int, currentGeneration: Int, keyPresent: Boolean): Boolean =
        capturedGeneration != currentGeneration || !keyPresent

    /**
     * A 401/403 means the key just saved by the connect wizard is rejected. Without
     * this, a previously connected account keeps HEVY_CONNECTED=true and the UI
     * reports a working connection with a bad key. Transient failures never clear it.
     */
    fun shouldClearConnected(sawInvalidKey: Boolean, sawSuccess: Boolean): Boolean =
        sawInvalidKey && !sawSuccess
}
