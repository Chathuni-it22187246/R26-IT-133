package com.greenhands.app

import com.greenhands.app.environment.PreviewEnvironment
import com.greenhands.app.harvest.data.HarvestMeasurementStore
import com.greenhands.app.harvest.data.HarvestReferenceRepository
import com.greenhands.app.harvest.data.InMemoryScanHistoryRepository
import com.greenhands.app.harvest.data.ScanHistoryRepository
import com.greenhands.app.harvest.domain.DiseaseMatcher
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HarvestEnvironmentContext
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestReferenceData
import com.greenhands.app.harvest.model.HarvestSaveStatus
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.ObservationRecord
import com.greenhands.app.harvest.model.ScanRecord
import com.greenhands.app.harvest.model.ScanType
import com.greenhands.app.harvest.ui.HarvestViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
class HarvestIntegrationTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val engine = HarvestDecisionEngine()
    private val matcher = DiseaseMatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
        HarvestMeasurementStore.clearSession()
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
        HarvestMeasurementStore.clearSession()
    }

    @Test
    fun fruitAndLeafSessionMeasurementsStaySeparate() {
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        val leaf = leaf(green = 82f, yellow = 6f, brown = 4f, white = 3f)
        val fruitDecision = engine.decideTomato(fruit, MaturityCalculator.assess(75, 65, 90))
        val leafHealth = matcher.assess(leaf, emptyList())

        HarvestMeasurementStore.lastFruit = fruit
        HarvestMeasurementStore.lastFruitDecision = fruitDecision
        HarvestMeasurementStore.lastLeaf = leaf
        HarvestMeasurementStore.lastLeafHealth = leafHealth

        val laterLeaf = leaf(green = 52f, yellow = 20f, brown = 18f, white = 4f)
        HarvestMeasurementStore.lastLeaf = laterLeaf
        HarvestMeasurementStore.lastLeafHealth = matcher.assess(laterLeaf, emptyList())

        assertEquals(fruit, HarvestMeasurementStore.lastFruit)
        assertEquals(fruitDecision, HarvestMeasurementStore.lastFruitDecision)
        assertEquals(laterLeaf, HarvestMeasurementStore.lastLeaf)
        assertNotEquals(leafHealth, HarvestMeasurementStore.lastLeafHealth)
    }

    @Test
    fun currentFruitDecisionIsNotChangedByLeafScan() {
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        val before = engine.decideTomato(fruit, MaturityCalculator.assess(75, 65, 90))
        HarvestMeasurementStore.lastFruit = fruit
        HarvestMeasurementStore.lastFruitDecision = before

        val leaf = leaf(green = 52f, yellow = 20f, brown = 18f, white = 4f)
        HarvestMeasurementStore.lastLeaf = leaf
        HarvestMeasurementStore.lastLeafHealth = matcher.assess(leaf, emptyList())

        val after = engine.decideTomato(
            HarvestMeasurementStore.lastFruit,
            MaturityCalculator.assess(75, 65, 90)
        )
        assertEquals(before.decision, after.decision)
        assertEquals(before.reasons, after.reasons)
        assertEquals(fruit, HarvestMeasurementStore.lastFruit)
    }

    @Test
    fun currentLeafAssessmentIsNotChangedByFruitScan() {
        val leaf = leaf(green = 82f, yellow = 6f, brown = 4f, white = 3f)
        val before = matcher.assess(leaf, emptyList())
        HarvestMeasurementStore.lastLeaf = leaf
        HarvestMeasurementStore.lastLeafHealth = before

        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        HarvestMeasurementStore.lastFruit = fruit
        HarvestMeasurementStore.lastFruitDecision =
            engine.decideTomato(fruit, MaturityCalculator.assess(75, 65, 90))

        val after = matcher.assess(HarvestMeasurementStore.lastLeaf, emptyList())
        assertEquals(before.status, after.status)
        assertEquals(before.possibleDisease, after.possibleDisease)
        assertEquals(leaf, HarvestMeasurementStore.lastLeaf)
    }

    @Test
    fun newScanCanBeSavedAsANewRecord() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val ids = ArrayDeque(listOf("first", "second"))
        val vm = HarvestViewModel(
            scanHistoryRepository = repo,
            clock = { 10_000L },
            newRecordId = { ids.removeFirst() }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        val first = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        val second = fruit(green = 10f, yellow = 18f, red = 62f, brown = 5f)
        vm.saveFruitScan(engine.decideTomato(first, MaturityCalculator.assess(75, 65, 90)))
        vm.prepareFruitSave(second)
        vm.saveFruitScan(engine.decideTomato(second, MaturityCalculator.assess(75, 65, 90)))
        val saved = repo.records.first()
        assertEquals(2, saved.size)
        assertEquals(setOf("first", "second"), saved.map { it.id }.toSet())
        assertEquals(HarvestSaveStatus.SAVED, vm.fruitSaveStatus.value)
    }

    @Test
    fun clearingDateAndVarietyDoesNotAlterSavedHistory() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val vm = HarvestViewModel(
            scanHistoryRepository = repo,
            clock = { 20_000L },
            newRecordId = { "kept" }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        val snapshot = com.greenhands.app.harvest.data.HarvestRecordFactory.fruit(
            decision = engine.decideTomato(
                fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f),
                MaturityCalculator.assess(75, 65, 90)
            ),
            cropType = "Tomato",
            variety = "Thilina",
            transplantDateUtcMillis = 1_700_000_000_000L,
            daysSinceTransplant = 70,
            environment = PreviewEnvironment.snapshot,
            id = "kept",
            scannedAtEpochMillis = 20_000L
        )!!
        repo.add(snapshot)
        vm.clearVariety()
        vm.clearPlantingDate()
        val stored = repo.getById("kept")!!
        assertEquals("Thilina", stored.variety)
        assertEquals(1_700_000_000_000L, stored.transplantDateUtcMillis)
        assertEquals("Thilina", vm.state.value.recentScans.first().variety)
        assertNull(vm.state.value.selectedVariety)
        assertNull(vm.state.value.plantingDateUtcMillis)
    }

    @Test
    fun referenceLoadFailureLeavesSafeEmptyState() = runTest {
        val vm = HarvestViewModel(referenceRepository = FailingHarvestReferenceRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertTrue(vm.state.value.referenceLoadFailed)
        assertTrue(vm.state.value.tomatoVarieties.isEmpty())
        assertTrue(vm.state.value.tomatoDiseases.isEmpty())
        vm.selectVariety("Thilina")
        assertNull(vm.state.value.selectedVariety)
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        val decision = engine.decideTomato(fruit, vm.state.value.maturity)
        assertFalse(decision.scanRequired)
    }

    @Test
    fun historyLoadFailureDoesNotCrashAndShowsEmptyList() = runTest {
        val vm = HarvestViewModel(scanHistoryRepository = FailingLoadHistoryRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertTrue(vm.state.value.historyLoadFailed)
        assertTrue(vm.state.value.recentScans.isEmpty())
    }

    @Test
    fun roomSaveFailureIsReportedWithoutCrashing() = runTest {
        val vm = HarvestViewModel(scanHistoryRepository = FailingSaveHistoryRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        vm.saveFruitScan(engine.decideTomato(fruit, MaturityCalculator.assess(75, 65, 90)))
        assertEquals(HarvestSaveStatus.FAILED, vm.fruitSaveStatus.value)
    }

    @Test
    fun historyDetailUsesStoredSnapshotNotCurrentSession() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val vm = HarvestViewModel(scanHistoryRepository = repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        val stored = com.greenhands.app.harvest.data.HarvestRecordFactory.fruit(
            decision = engine.decideTomato(
                fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f),
                MaturityCalculator.assess(75, 65, 90)
            ),
            cropType = "Tomato",
            variety = "KWR",
            transplantDateUtcMillis = 1_600_000_000_000L,
            daysSinceTransplant = 80,
            environment = PreviewEnvironment.snapshot,
            id = "snap",
            scannedAtEpochMillis = 30_000L
        )!!
        repo.add(stored)
        val fromState = vm.state.value.recentScans.first { it.id == "snap" }
        assertEquals("KWR", fromState.variety)
        assertEquals(80, fromState.daysSinceTransplant)
        assertEquals(HarvestEnvironmentContext.SOURCE_PREVIEW, fromState.environmentSource)
        assertEquals(stored.harvestDecisionLabel, fromState.harvestDecisionLabel)
        assertEquals(ScanType.FRUIT_SCAN, fromState.scanType)
    }

    @Test
    fun disconnectedEnvironmentIsNotShownAsLivePreviewSamples() = runTest {
        val vm = HarvestViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertEquals(
            HarvestEnvironmentContext.SOURCE_DISCONNECTED,
            vm.state.value.environmentContext.sourceLabel
        )
        assertFalse(vm.state.value.environmentContext.isPreview)
        assertEquals("--", vm.state.value.sensorUi.temperatureText)
        assertEquals("--", vm.state.value.sensorUi.humidityText)
        assertEquals("No live sensor data", vm.state.value.sensorUi.statusText)
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        vm.saveFruitScan(engine.decideTomato(fruit, MaturityCalculator.assess(75, 65, 90)))
        val saved = vm.state.value.recentScans.first()
        assertEquals(HarvestEnvironmentContext.SOURCE_DISCONNECTED, saved.environmentSource)
        assertFalse(saved.isPreviewEnvironment)
        assertFalse(saved.environmentSource == HarvestEnvironmentContext.SOURCE_LIVE)
        assertNull(saved.temperatureC)
        assertNull(saved.humidityPercent)
    }

    private class FailingHarvestReferenceRepository : HarvestReferenceRepository {
        override suspend fun load(): HarvestReferenceData = error("csv missing")
        override suspend fun isReady(): Boolean = false
        override suspend fun loadObservations(): List<ObservationRecord> = emptyList()
    }

    private class FailingLoadHistoryRepository : ScanHistoryRepository {
        override val records: Flow<List<ScanRecord>> = flow { error("history load failed") }
        override suspend fun add(record: ScanRecord) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun getById(id: String): ScanRecord? = null
        override suspend fun clear() = Unit
    }

    private class FailingSaveHistoryRepository : ScanHistoryRepository {
        private val inner = InMemoryScanHistoryRepository()
        override val records: Flow<List<ScanRecord>> = inner.records
        override suspend fun add(record: ScanRecord) {
            error("history save failed")
        }
        override suspend fun delete(id: String) = inner.delete(id)
        override suspend fun getById(id: String): ScanRecord? = inner.getById(id)
        override suspend fun clear() = inner.clear()
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
