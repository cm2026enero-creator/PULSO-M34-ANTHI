package com.example

import com.example.domain.OperatorRole
import com.example.domain.RadioChannel
import com.example.domain.TransmissionState
import com.example.ui.PulsoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PulsoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun unconfirmedDriver_cannotTransmitAnywhere() {
        val vm = PulsoViewModel(startTelemetry = false)
        val state = vm.uiState.value
        assertEquals(OperatorRole.DRIVER_UNCONFIRMED, state.role)

        assertFalse(vm.evaluateChannelAccess(state.role, RadioChannel.EventChannel(1), null).canTransmit)
        assertFalse(vm.evaluateChannelAccess(state.role, RadioChannel.VictimChannel, null).canTransmit)
        assertFalse(vm.evaluateChannelAccess(state.role, RadioChannel.AdminChannel, null).canTransmit)
    }

    @Test
    fun singleEvent_siVoyConfirmsDirectly_andMultiEventOpensDropdown() {
        val vm = PulsoViewModel(startTelemetry = false)
        vm.setSimulatedEventCount(1)
        vm.onSiVoyButtonClicked()

        var state = vm.uiState.value
        assertEquals(OperatorRole.DRIVER_CONFIRMED, state.role)
        assertEquals(1, state.confirmedEventNumber)
        assertFalse(state.isSiVoyDropdownExpanded)

        // Reset to unconfirmed and switch to 3 simultaneous events
        vm.cancelSiVoyConfirmation()
        vm.setSimulatedEventCount(3)
        vm.onSiVoyButtonClicked()

        state = vm.uiState.value
        assertTrue(state.isSiVoyDropdownExpanded)

        // Confirm specifically Event 2
        vm.confirmSiVoyForEvent(2)
        state = vm.uiState.value
        assertEquals(2, state.confirmedEventNumber)
        assertTrue(vm.evaluateChannelAccess(state.role, RadioChannel.EventChannel(2), 2).canTransmit)
        assertFalse(vm.evaluateChannelAccess(state.role, RadioChannel.EventChannel(1), 2).canTransmit)
        assertFalse(vm.evaluateChannelAccess(state.role, RadioChannel.AdminChannel, 2).canTransmit)
        assertEquals(30, vm.evaluateChannelAccess(state.role, RadioChannel.EventChannel(2), 2).maxTotSeconds)
    }

    @Test
    fun adminRole_hasFullAccessWith180SecondsTot() {
        val vm = PulsoViewModel(startTelemetry = false)
        vm.setSimulatedEventCount(3)
        vm.setRole(OperatorRole.ADMIN)

        val state = vm.uiState.value
        listOf(
            RadioChannel.EventChannel(1),
            RadioChannel.EventChannel(2),
            RadioChannel.EventChannel(3),
            RadioChannel.VictimChannel,
            RadioChannel.AdminChannel
        ).forEach { ch ->
            val decision = vm.evaluateChannelAccess(state.role, ch, null)
            assertTrue("Admin should transmit on $ch", decision.canTransmit)
            assertTrue("Admin should listen on $ch", decision.canListen)
            assertEquals(180, decision.maxTotSeconds)
        }
    }

    @Test
    fun alarmEmitter_enforcesTotalAudioSilenceAndBlocksIncomingRx() {
        val vm = PulsoViewModel(startTelemetry = false)
        vm.setRole(OperatorRole.ALARM_EMITTER)

        val state = vm.uiState.value
        assertTrue(state.audioOutputMutedBySafetyRule)
        assertEquals(TransmissionState.SILENT_CAPTURE, state.transmissionState)
        assertFalse(vm.evaluateChannelAccess(state.role, RadioChannel.EventChannel(1), null).canListen)
        assertFalse(vm.evaluateChannelAccess(state.role, RadioChannel.VictimChannel, null).canTransmit)

        vm.simulateIncomingRadioTransmission()
        assertFalse(vm.uiState.value.isSimulatingRemoteRx)
        assertEquals(TransmissionState.SILENT_CAPTURE, vm.uiState.value.transmissionState)
    }

    @Test
    fun totAutoCutoff_releasesChannelAtZero() = runTest {
        val vm = PulsoViewModel(startTelemetry = false)
        vm.setSimulatedEventCount(1)
        vm.onSiVoyButtonClicked() // Confirms Event 1 (30s TOT)

        vm.jumpTotToLastFiveSeconds()
        assertEquals(TransmissionState.TRANSMITTING_SELF, vm.uiState.value.transmissionState)
        assertEquals(5, vm.uiState.value.totRemainingSeconds)

        advanceTimeBy(5500L)
        assertEquals(TransmissionState.IDLE, vm.uiState.value.transmissionState)
        assertNull(vm.uiState.value.activeSpeakerName)
        assertTrue(vm.uiState.value.totCutoffBanner?.contains("30s") == true)
    }

    @Test
    fun adminTot180sAutoCutoff_releasesChannelAtZero() = runTest {
        val vm = PulsoViewModel(startTelemetry = false)
        vm.testTotCutoff180s()

        assertEquals(OperatorRole.ADMIN, vm.uiState.value.role)
        assertEquals(RadioChannel.AdminChannel, vm.uiState.value.selectedChannel)
        assertEquals(180, vm.uiState.value.totLimitSeconds)
        assertEquals(5, vm.uiState.value.totRemainingSeconds)

        advanceTimeBy(5500L)
        assertEquals(TransmissionState.IDLE, vm.uiState.value.transmissionState)
        assertTrue(vm.uiState.value.totCutoffBanner?.contains("180s") == true)
    }
}
