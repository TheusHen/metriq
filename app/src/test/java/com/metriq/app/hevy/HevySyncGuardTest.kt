package com.metriq.app.hevy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevySyncGuardTest {

    @Test
    fun `sync is stale when disconnect bumped the generation`() {
        assertTrue(HevySyncGuard.isStale(capturedGeneration = 3, currentGeneration = 4, keyPresent = true))
    }

    @Test
    fun `sync is stale when the key was removed mid-flight`() {
        assertTrue(HevySyncGuard.isStale(capturedGeneration = 3, currentGeneration = 3, keyPresent = false))
    }

    @Test
    fun `sync is current when generation and key are unchanged`() {
        assertFalse(HevySyncGuard.isStale(capturedGeneration = 3, currentGeneration = 3, keyPresent = true))
    }

    @Test
    fun `a rejected key clears a previous connection but an offline sync does not`() {
        assertTrue(HevySyncGuard.shouldClearConnected(sawInvalidKey = true, sawSuccess = false))
        assertFalse(HevySyncGuard.shouldClearConnected(sawInvalidKey = false, sawSuccess = false))
        // One good page means the key worked, even if a later page failed.
        assertFalse(HevySyncGuard.shouldClearConnected(sawInvalidKey = true, sawSuccess = true))
    }
}
