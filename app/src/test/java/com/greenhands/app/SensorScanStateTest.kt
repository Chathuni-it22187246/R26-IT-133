package com.greenhands.app

import com.greenhands.app.sensor.model.ScanMode
import com.greenhands.app.sensor.model.ScanPhase
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorScanStateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdleSimulatedScan() = runTest(dispatcher) {
        val vm = SensorPlacementViewModel(scanStepDelayMs = 50)
        advanceUntilIdle()
        val scan = vm.state.value.scan
        assertEquals(ScanPhase.IDLE, scan.phase)
        assertEquals(ScanMode.SIMULATED, scan.mode)
        assertEquals(0, scan.progressPercent)
        assertFalse(scan.canContinueToPlacement)
        assertFalse(scan.isScanning)
    }

    @Test
    fun startScanChangesStateToScanning() = runTest(dispatcher) {
        val vm = SensorPlacementViewModel(scanStepDelayMs = 50)
        advanceUntilIdle()
        vm.startScan()
        testScheduler.runCurrent()
        val scan = vm.state.value.scan
        assertEquals(ScanPhase.SCANNING, scan.phase)
        assertEquals(0, scan.progressPercent)
        assertFalse(scan.canContinueToPlacement)
        assertTrue(scan.isScanning)
    }

    @Test
    fun progressReachesOneHundredAndEndsDetected() = runTest(dispatcher) {
        val vm = SensorPlacementViewModel(scanStepDelayMs = 50)
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        val scan = vm.state.value.scan
        assertEquals(ScanPhase.DETECTED, scan.phase)
        assertEquals(100, scan.progressPercent)
        assertTrue(scan.canContinueToPlacement)
        assertEquals(ScanMode.SIMULATED, scan.mode)
    }

    @Test
    fun resetReturnsToIdle() = runTest(dispatcher) {
        val vm = SensorPlacementViewModel(scanStepDelayMs = 50)
        advanceUntilIdle()
        vm.startScan()
        advanceUntilIdle()
        assertEquals(ScanPhase.DETECTED, vm.state.value.scan.phase)
        vm.resetScan()
        advanceUntilIdle()
        val scan = vm.state.value.scan
        assertEquals(ScanPhase.IDLE, scan.phase)
        assertEquals(0, scan.progressPercent)
        assertFalse(scan.canContinueToPlacement)
    }

    @Test
    fun continueIsOnlyAvailableAfterDetection() = runTest(dispatcher) {
        val vm = SensorPlacementViewModel(scanStepDelayMs = 50)
        advanceUntilIdle()
        assertFalse(vm.state.value.scan.canContinueToPlacement)
        vm.startScan()
        testScheduler.runCurrent()
        assertEquals(ScanPhase.SCANNING, vm.state.value.scan.phase)
        assertFalse(vm.state.value.scan.canContinueToPlacement)
        testScheduler.advanceTimeBy(50)
        testScheduler.runCurrent()
        assertEquals(20, vm.state.value.scan.progressPercent)
        assertFalse(vm.state.value.scan.canContinueToPlacement)
        advanceUntilIdle()
        assertTrue(vm.state.value.scan.canContinueToPlacement)
        vm.resetScan()
        advanceUntilIdle()
        assertFalse(vm.state.value.scan.canContinueToPlacement)
    }

    @Test
    fun scanDoesNotChangeSensorCoverage() = runTest(dispatcher) {
        val vm = SensorPlacementViewModel(scanStepDelayMs = 50)
        advanceUntilIdle()
        vm.addSensor(2.0, 2.0)
        val coverageBefore = vm.state.value.coverage
        val sensorsBefore = vm.state.value.sensors
        vm.startScan()
        advanceUntilIdle()
        assertEquals(sensorsBefore, vm.state.value.sensors)
        assertEquals(coverageBefore, vm.state.value.coverage)
        assertEquals(ScanPhase.DETECTED, vm.state.value.scan.phase)
    }
}
