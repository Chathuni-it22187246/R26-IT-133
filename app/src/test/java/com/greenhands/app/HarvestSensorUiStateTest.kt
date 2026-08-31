package com.greenhands.app

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.environment.InMemoryGreenhouseEnvironmentRepository
import com.greenhands.app.environment.PreviewEnvironment
import com.greenhands.app.environment.UnconnectedEnvironment
import com.greenhands.app.harvest.domain.HarvestSensorUiState
import com.greenhands.app.harvest.ui.HarvestViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class HarvestSensorUiStateTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun liveSensorStateDisplaysTemperatureAndHumidity() {
        val ui = HarvestSensorUiState.from(
            GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.LIVE,
                temperatureC = 28.0,
                relativeHumidityPercent = 74.0,
                serverTimestampMillis = 1_720_000_000_000L
            )
        )
        assertEquals("28°C", ui.temperatureText)
        assertEquals("74%", ui.humidityText)
        assertEquals(HarvestSensorUiState.STATUS_LIVE, ui.statusText)
        assertTrue(ui.showsLiveValues)
        assertFalse(ui.isConnecting)
        assertNull(ui.pendingDeviceNote)
    }

    @Test
    fun noDataStateDisplaysPlaceholders() {
        val ui = HarvestSensorUiState.from(UnconnectedEnvironment.snapshot)
        assertEquals("--", ui.temperatureText)
        assertEquals("--", ui.humidityText)
        assertEquals(HarvestSensorUiState.STATUS_NO_DATA, ui.statusText)
        assertFalse(ui.showsLiveValues)
        assertEquals(HarvestSensorUiState.PENDING_DEVICE_NOTE, ui.pendingDeviceNote)
    }

    @Test
    fun previewSamplesAreNotShownAsLiveValues() {
        val ui = HarvestSensorUiState.from(PreviewEnvironment.snapshot)
        assertEquals(25.0, PreviewEnvironment.SAMPLE_TEMPERATURE_C, 0.0)
        assertEquals(70.0, PreviewEnvironment.SAMPLE_HUMIDITY_PERCENT, 0.0)
        assertEquals("--", ui.temperatureText)
        assertEquals("--", ui.humidityText)
        assertFalse(ui.temperatureText.contains("25"))
        assertFalse(ui.humidityText.contains("70"))
        assertEquals(HarvestSensorUiState.STATUS_NO_DATA, ui.statusText)
        assertFalse(ui.showsLiveValues)
    }

    @Test
    fun connectingStateShowsConnectingMessage() {
        val ui = HarvestSensorUiState.from(
            GreenhouseEnvironmentSnapshot(GreenhouseConnectionState.CONNECTING)
        )
        assertTrue(ui.isConnecting)
        assertEquals(HarvestSensorUiState.STATUS_CONNECTING, ui.statusText)
        assertEquals("--", ui.temperatureText)
    }

    @Test
    fun sensorStateUpdatesPropagateToHarvestUi() = runTest {
        val repo = InMemoryGreenhouseEnvironmentRepository(UnconnectedEnvironment.snapshot)
        val vm = HarvestViewModel(environmentRepository = repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertEquals("--", vm.state.value.sensorUi.temperatureText)
        assertEquals(HarvestSensorUiState.STATUS_NO_DATA, vm.state.value.sensorUi.statusText)

        repo.emit(
            GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.LIVE,
                temperatureC = 28.4,
                relativeHumidityPercent = 74.0
            )
        )
        assertEquals("28.4°C", vm.state.value.sensorUi.temperatureText)
        assertEquals("74%", vm.state.value.sensorUi.humidityText)
        assertEquals(HarvestSensorUiState.STATUS_LIVE, vm.state.value.sensorUi.statusText)
        assertTrue(vm.state.value.sensorUi.showsLiveValues)
    }

    @Test
    fun defaultHarvestViewModelDoesNotUseHardcodedPreviewSamples() = runTest {
        val vm = HarvestViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertNull(vm.state.value.environment.temperatureC)
        assertNull(vm.state.value.environment.relativeHumidityPercent)
        assertEquals("--", vm.state.value.sensorUi.temperatureText)
        assertEquals("--", vm.state.value.sensorUi.humidityText)
        assertFalse(vm.state.value.sensorUi.temperatureText.contains("25"))
        assertFalse(vm.state.value.sensorUi.humidityText.contains("70"))
    }
}
