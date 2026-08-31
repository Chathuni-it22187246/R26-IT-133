package com.greenhands.app

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.environment.InMemoryGreenhouseEnvironmentRepository
import com.greenhands.app.environment.PreviewEnvironment
import com.greenhands.app.environment.PreviewGreenhouseEnvironmentRepository
import com.greenhands.app.harvest.domain.DiseaseMatcher
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HarvestEnvironmentContext
import com.greenhands.app.harvest.domain.HarvestHumidityBand
import com.greenhands.app.harvest.domain.HarvestSensorUiState
import com.greenhands.app.harvest.domain.HarvestTemperatureBand
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecision
import com.greenhands.app.harvest.model.LeafColorMeasurement
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestEnvironmentContextTest {

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
    fun previewRepositoryValuesPropagateIntoHarvestUiState() = runTest {
        val repo = PreviewGreenhouseEnvironmentRepository()
        val vm = HarvestViewModel(environmentRepository = repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }

        val state = vm.state.value
        assertEquals(GreenhouseConnectionState.PREVIEW, state.environment.connectionState)
        assertEquals(PreviewEnvironment.SAMPLE_TEMPERATURE_C, state.environment.temperatureC!!, 0.0)
        assertEquals(PreviewEnvironment.SAMPLE_HUMIDITY_PERCENT, state.environment.relativeHumidityPercent!!, 0.0)
        assertEquals(HarvestEnvironmentContext.SOURCE_PREVIEW, state.environmentContext.sourceLabel)
        assertTrue(state.environmentContext.isPreview)
        assertEquals(HarvestTemperatureBand.SUITABLE, state.environmentContext.temperatureBand)
        assertEquals(HarvestHumidityBand.SUITABLE, state.environmentContext.humidityBand)
        assertEquals(HarvestEnvironmentContext.LABEL_SUITABLE, state.environmentContext.summaryLabel)
        assertEquals(HarvestSensorUiState.PENDING_DEVICE_NOTE, state.environmentContext.sourceDisclaimer)
        assertEquals("--", state.sensorUi.temperatureText)
        assertEquals("--", state.sensorUi.humidityText)
        assertEquals(HarvestSensorUiState.STATUS_NO_DATA, state.sensorUi.statusText)
        assertFalse(state.sensorUi.showsLiveValues)
    }

    @Test
    fun laterSnapshotFromRepositoryReplacesHarvestUiEnvironment() = runTest {
        val repo = InMemoryGreenhouseEnvironmentRepository(PreviewEnvironment.snapshot)
        val vm = HarvestViewModel(environmentRepository = repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }

        assertEquals(25.0, vm.state.value.environment.temperatureC!!, 0.0)
        assertEquals(HarvestEnvironmentContext.SOURCE_PREVIEW, vm.state.value.environmentContext.sourceLabel)

        repo.emit(
            GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.LIVE,
                temperatureC = 24.0,
                relativeHumidityPercent = 68.0,
                sensorOrGreenhouseId = "gh-1",
                serverTimestampMillis = 1_720_000_000_000L
            )
        )

        val live = vm.state.value
        assertEquals(GreenhouseConnectionState.LIVE, live.environment.connectionState)
        assertEquals(24.0, live.environment.temperatureC!!, 0.0)
        assertEquals(68.0, live.environment.relativeHumidityPercent!!, 0.0)
        assertEquals(HarvestEnvironmentContext.SOURCE_LIVE, live.environmentContext.sourceLabel)
        assertFalse(live.environmentContext.isPreview)
        assertNull(live.environmentContext.sourceDisclaimer)
        assertEquals("24°C", live.sensorUi.temperatureText)
        assertEquals("68%", live.sensorUi.humidityText)
        assertEquals(HarvestSensorUiState.STATUS_LIVE, live.sensorUi.statusText)
        assertTrue(live.sensorUi.showsLiveValues)
    }

    @Test
    fun previewSourceRemainsLabelledPreview() {
        val context = HarvestEnvironmentContext.from(PreviewEnvironment.snapshot)
        assertEquals(GreenhouseConnectionState.PREVIEW, context.snapshot.connectionState)
        assertEquals(HarvestEnvironmentContext.SOURCE_PREVIEW, context.sourceLabel)
        assertTrue(context.isPreview)
        assertEquals(HarvestSensorUiState.PENDING_DEVICE_NOTE, context.sourceDisclaimer)
        assertNotEquals(HarvestEnvironmentContext.SOURCE_LIVE, context.sourceLabel)
    }

    @Test
    fun missingEnvironmentValuesAreUnknownAndSafe() = runTest {
        val missing = GreenhouseEnvironmentSnapshot(
            connectionState = GreenhouseConnectionState.OFFLINE_DELAYED,
            temperatureC = null,
            relativeHumidityPercent = null
        )
        val context = HarvestEnvironmentContext.from(missing)
        assertEquals(HarvestTemperatureBand.UNKNOWN, context.temperatureBand)
        assertEquals(HarvestHumidityBand.UNKNOWN, context.humidityBand)
        assertEquals(HarvestEnvironmentContext.LABEL_UNKNOWN, context.summaryLabel)
        assertEquals(HarvestEnvironmentContext.SOURCE_OFFLINE_DELAYED, context.sourceLabel)
        assertTrue(context.isUnknown)
        assertEquals(HarvestEnvironmentContext.OFFLINE_DISCLAIMER, context.sourceDisclaimer)

        val repo = InMemoryGreenhouseEnvironmentRepository(missing)
        val vm = HarvestViewModel(environmentRepository = repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertNull(vm.state.value.environment.temperatureC)
        assertNull(vm.state.value.environment.relativeHumidityPercent)
        assertEquals(HarvestEnvironmentContext.LABEL_UNKNOWN, vm.state.value.environmentContext.summaryLabel)
        assertEquals("—", vm.state.value.environment.temperatureC?.let { it.toString() } ?: "—")
    }

    @Test
    fun warmAndHighHumidityAreDisplayContextOnly() {
        val hotHumid = HarvestEnvironmentContext.from(
            GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.LIVE,
                temperatureC = 32.0,
                relativeHumidityPercent = 88.0
            )
        )
        assertEquals(HarvestTemperatureBand.WARM, hotHumid.temperatureBand)
        assertEquals(HarvestHumidityBand.HIGH, hotHumid.humidityBand)
        assertEquals(
            "${HarvestEnvironmentContext.LABEL_WARM} · ${HarvestEnvironmentContext.LABEL_HIGH_HUMIDITY}",
            hotHumid.summaryLabel
        )
        assertTrue(hotHumid.supportingNote.contains("should be considered during inspection"))
    }

    @Test
    fun harvestDecisionIsUnchangedWhenOnlyEnvironmentValuesChange() {
        val engine = HarvestDecisionEngine()
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        val maturity = MaturityCalculator.assess(75, 65, 90)

        val preview = HarvestEnvironmentContext.from(PreviewEnvironment.snapshot)
        val hotHumid = HarvestEnvironmentContext.from(
            GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.LIVE,
                temperatureC = 35.0,
                relativeHumidityPercent = 92.0
            )
        )
        val missing = HarvestEnvironmentContext.from(
            GreenhouseEnvironmentSnapshot(GreenhouseConnectionState.OFFLINE_DELAYED)
        )

        val withPreview = engine.decideTomato(fruit, maturity)
        val withHotHumid = engine.decideTomato(fruit, maturity)
        val withMissing = engine.decideTomato(fruit, maturity)

        assertEquals(HarvestDecision.READY_TO_HARVEST, withPreview.decision)
        assertEquals(withPreview.decision, withHotHumid.decision)
        assertEquals(withPreview.decision, withMissing.decision)
        assertEquals(withPreview.reasons, withHotHumid.reasons)
        assertEquals(withPreview.reasons, withMissing.reasons)
        assertNotEquals(preview.summaryLabel, hotHumid.summaryLabel)
        assertNotEquals(preview.summaryLabel, missing.summaryLabel)
        assertFalse(withPreview.reasons.any { it.contains("temperature", ignoreCase = true) })
        assertFalse(withPreview.reasons.any { it.contains("humidity", ignoreCase = true) })
    }

    @Test
    fun plantHealthAssessmentIsUnchangedWhenOnlyEnvironmentValuesChange() {
        val matcher = DiseaseMatcher()
        val leaf = leaf(green = 82f, yellow = 6f, brown = 4f, white = 3f)
        val preview = HarvestEnvironmentContext.from(PreviewEnvironment.snapshot)
        val highHumidity = HarvestEnvironmentContext.from(
            GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.LIVE,
                temperatureC = 25.0,
                relativeHumidityPercent = 95.0
            )
        )

        val first = matcher.assess(leaf, emptyList())
        val second = matcher.assess(leaf, emptyList())
        assertEquals(first.status, second.status)
        assertEquals(first.possibleDisease, second.possibleDisease)
        assertEquals(first.reasons, second.reasons)
        assertEquals(HarvestHumidityBand.SUITABLE, preview.humidityBand)
        assertEquals(HarvestHumidityBand.HIGH, highHumidity.humidityBand)
        assertFalse(first.reasons.any { it.contains("humidity", ignoreCase = true) })
    }

    private fun fruit(
        green: Float,
        yellow: Float,
        red: Float,
        brown: Float,
        samples: Int = 1000
    ) = FruitColorMeasurement(
        sampledPixelCount = samples,
        hueMean = 80f,
        saturationMean = 0.55f,
        valueMean = 0.52f,
        greenPercent = green,
        yellowPercent = yellow,
        redPercent = red,
        brownDarkPercent = brown,
        otherPercent = (100f - green - yellow - red - brown).coerceAtLeast(0f)
    )

    private fun leaf(
        green: Float,
        yellow: Float,
        brown: Float,
        white: Float,
        samples: Int = 1000
    ): LeafColorMeasurement {
        val discolored = yellow + brown + white
        return LeafColorMeasurement(
            sampledPixelCount = samples,
            hueMean = 90f,
            saturationMean = 0.45f,
            valueMean = 0.55f,
            greenPercent = green,
            yellowPercent = yellow,
            brownDarkPercent = brown,
            whitePalePercent = white,
            discoloredPercent = discolored,
            otherPercent = (100f - green - discolored).coerceAtLeast(0f)
        )
    }
}
