package com.metriq.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integration answers must live in the ViewModel state so rotation cannot lose them. */
class OnboardingIntegrationsTest {

    @Test
    fun `wizard has eight steps with the plan review last`() {
        assertEquals(8, OnboardingViewModel.TOTAL_STEPS)
        assertEquals(7, OnboardingViewModel.LAST_STEP)
    }

    @Test
    fun `health connect statuses are recorded in state`() {
        val vm = OnboardingViewModel()

        vm.refreshHealthStatus(available = true, connected = false)
        assertEquals(OnboardingHealthStatus.AVAILABLE, vm.state.value.healthStatus)

        vm.onHealthPermissionResult(granted = true)
        assertEquals(OnboardingHealthStatus.CONNECTED, vm.state.value.healthStatus)

        vm.onHealthImport(12)
        assertEquals(12, vm.state.value.healthImported)

        vm.onHealthPermissionResult(granted = false)
        assertEquals(OnboardingHealthStatus.DENIED, vm.state.value.healthStatus)
        assertEquals(null, vm.state.value.healthImported)
    }

    @Test
    fun `a typed hevy key is not a connection until a successful sync is reported`() {
        val vm = OnboardingViewModel()

        vm.setHevyKey("secret-key")
        assertEquals("secret-key", vm.state.value.hevyApiKey)
        assertEquals(OnboardingHevyStatus.IDLE, vm.state.value.hevyStatus)

        vm.onHevyConnecting()
        assertEquals(OnboardingHevyStatus.CONNECTING, vm.state.value.hevyStatus)

        vm.onHevyFailed("network error")
        assertEquals(OnboardingHevyStatus.FAILED, vm.state.value.hevyStatus)
        assertEquals("network error", vm.state.value.hevyMessage)

        vm.onHevyConnected("Sincronizados 2 treino(s)")
        assertEquals(OnboardingHevyStatus.CONNECTED, vm.state.value.hevyStatus)

        vm.onHevyKeySaveFailed()
        assertEquals(OnboardingHevyStatus.KEY_SAVE_FAILED, vm.state.value.hevyStatus)
    }

    @Test
    fun `skipping integrations is recorded and advances one step`() {
        val vm = OnboardingViewModel()
        repeat(6) { vm.nextStep() }
        assertEquals(6, vm.state.value.step)
        assertFalse(vm.state.value.integrationsSkipped)

        vm.skipIntegrations()

        assertTrue(vm.state.value.integrationsSkipped)
        assertEquals(7, vm.state.value.step)

        // Skipping never blocks the last step either.
        vm.skipIntegrations()
        assertEquals(OnboardingViewModel.LAST_STEP, vm.state.value.step)
    }
}
