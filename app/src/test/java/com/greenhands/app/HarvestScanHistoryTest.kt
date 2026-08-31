package com.greenhands.app

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.environment.PreviewEnvironment
import com.greenhands.app.harvest.data.HarvestRecordFactory
import com.greenhands.app.harvest.data.InMemoryScanHistoryRepository
import com.greenhands.app.harvest.data.local.HarvestScanRecordMapper
import com.greenhands.app.harvest.domain.DiseaseMatcher
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HarvestEnvironmentContext
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecision
import com.greenhands.app.harvest.model.HarvestSaveStatus
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.ScanRecord
import com.greenhands.app.harvest.model.ScanType
import com.greenhands.app.harvest.ui.HarvestViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestScanHistoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val engine = HarvestDecisionEngine()
    private val matcher = DiseaseMatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun factorySavesValidFruitRecord() {
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        val decision = engine.decideTomato(fruit, MaturityCalculator.assess(75, 65, 90))
        val record = HarvestRecordFactory.fruit(
            decision = decision,
            cropType = "Tomato",
            variety = "Thilina",
            transplantDateUtcMillis = 1_720_000_000_000L,
            daysSinceTransplant = 75,
            environment = PreviewEnvironment.snapshot,
            id = "fruit-1",
            scannedAtEpochMillis = 1_724_680_200_000L
        )
        assertNotNull(record)
        assertEquals(ScanType.FRUIT_SCAN, record!!.scanType)
        assertEquals("Tomato", record.cropType)
        assertEquals("Thilina", record.variety)
        assertEquals(HarvestDecision.READY_TO_HARVEST, record.harvestDecision)
        assertEquals("READY TO HARVEST", record.harvestDecisionLabel)
        assertEquals(38f, record.greenPercent)
        assertEquals(HarvestEnvironmentContext.SOURCE_PREVIEW, record.environmentSource)
        assertTrue(record.isPreviewEnvironment)
        assertEquals(PreviewEnvironment.SAMPLE_TEMPERATURE_C, record.temperatureC)
        assertEquals(PreviewEnvironment.SAMPLE_HUMIDITY_PERCENT, record.humidityPercent)
        assertTrue(record.decisionReasons.isNotEmpty())
    }

    @Test
    fun factorySavesValidLeafRecord() {
        val leaf = leaf(green = 82f, yellow = 6f, brown = 4f, white = 3f)
        val assessment = matcher.assess(leaf, emptyList())
        val record = HarvestRecordFactory.leaf(
            assessment = assessment,
            cropType = "Tomato",
            variety = null,
            transplantDateUtcMillis = null,
            environment = PreviewEnvironment.snapshot,
            id = "leaf-1",
            scannedAtEpochMillis = 1_724_680_500_000L
        )
        assertNotNull(record)
        assertEquals(ScanType.LEAF_SCAN, record!!.scanType)
        assertNull(record.variety)
        assertNull(record.transplantDateUtcMillis)
        assertEquals(82f, record.greenPercent)
        assertEquals(3f, record.whitePalePercent)
        assertNotNull(record.plantHealthStatus)
        assertEquals(HarvestEnvironmentContext.SOURCE_PREVIEW, record.environmentSource)
        assertEquals("Tomato • Leaf Scan", record.listHeadline)
    }

    @Test
    fun factoryDoesNotSaveWithoutValidMeasurement() {
        val fruitDecision = engine.decideTomato(null, MaturityCalculator.assess(75, 65, 90))
        assertTrue(fruitDecision.scanRequired)
        assertNull(
            HarvestRecordFactory.fruit(
                decision = fruitDecision,
                cropType = "Tomato",
                variety = null,
                transplantDateUtcMillis = null,
                daysSinceTransplant = null,
                environment = PreviewEnvironment.snapshot,
                id = "fruit-none",
                scannedAtEpochMillis = 1L
            )
        )

        val leafAssessment = matcher.assess(null, emptyList())
        assertTrue(leafAssessment.scanRequired)
        assertNull(
            HarvestRecordFactory.leaf(
                assessment = leafAssessment,
                cropType = "Tomato",
                variety = null,
                transplantDateUtcMillis = null,
                environment = PreviewEnvironment.snapshot,
                id = "leaf-none",
                scannedAtEpochMillis = 1L
            )
        )
    }

    @Test
    fun recordsReturnedNewestFirst() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val older = sampleFruit(id = "old", scannedAt = 100L)
        val newer = sampleFruit(id = "new", scannedAt = 200L, green = 40f)
        repo.add(older)
        repo.add(newer)
        val records = repo.records.first()
        assertEquals(listOf("new", "old"), records.map { it.id })
    }

    @Test
    fun previewSourcePreservedThroughMapper() {
        val original = sampleFruit(id = "preview-1", scannedAt = 50L)
        assertEquals(ScanRecord.ENVIRONMENT_SOURCE_PREVIEW, original.environmentSource)
        val roundTrip = HarvestScanRecordMapper.toDomain(HarvestScanRecordMapper.toEntity(original))
        assertEquals(original, roundTrip)
        assertEquals(ScanRecord.ENVIRONMENT_SOURCE_PREVIEW, roundTrip.environmentSource)
        assertTrue(roundTrip.isPreviewEnvironment)
        assertNotEqualsLive(roundTrip)
    }

    @Test
    fun nullableVarietyAndTransplantDateAreStored() {
        val record = HarvestRecordFactory.fruit(
            decision = engine.decideTomato(
                fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f),
                MaturityCalculator.assessTomato(null, null)
            ),
            cropType = "Tomato",
            variety = null,
            transplantDateUtcMillis = null,
            daysSinceTransplant = null,
            environment = PreviewEnvironment.snapshot,
            id = "nullable-1",
            scannedAtEpochMillis = 10L
        )
        assertNotNull(record)
        assertNull(record!!.variety)
        assertNull(record.transplantDateUtcMillis)
        assertNull(record.daysSinceTransplant)
        val roundTrip = HarvestScanRecordMapper.toDomain(HarvestScanRecordMapper.toEntity(record))
        assertNull(roundTrip.variety)
        assertNull(roundTrip.transplantDateUtcMillis)
        assertNull(roundTrip.daysSinceTransplant)
    }

    @Test
    fun deletingOneRecordRemovesOnlyThatRecord() = runTest {
        val repo = InMemoryScanHistoryRepository()
        repo.add(sampleFruit(id = "keep", scannedAt = 200L))
        repo.add(sampleFruit(id = "drop", scannedAt = 100L, green = 41f))
        repo.delete("drop")
        val remaining = repo.records.first()
        assertEquals(listOf("keep"), remaining.map { it.id })
        assertNull(repo.getById("drop"))
        assertNotNull(repo.getById("keep"))
    }

    @Test
    fun savedSnapshotDoesNotChangeWhenCurrentSessionStateChanges() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val vm = HarvestViewModel(
            scanHistoryRepository = repo,
            clock = { 1_000L },
            newRecordId = { "snap-1" }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        vm.selectVariety("ignored")
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        val decision = engine.decideTomato(fruit, MaturityCalculator.assess(75, 65, 90))
        val saved = HarvestRecordFactory.fruit(
            decision = decision,
            cropType = "Tomato",
            variety = "Thilina",
            transplantDateUtcMillis = 1_700_000_000_000L,
            daysSinceTransplant = 70,
            environment = PreviewEnvironment.snapshot,
            id = "snap-1",
            scannedAtEpochMillis = 1_000L
        )!!
        repo.add(saved)

        vm.clearVariety()
        vm.clearPlantingDate()
        val stored = repo.getById("snap-1")!!
        assertEquals("Thilina", stored.variety)
        assertEquals(1_700_000_000_000L, stored.transplantDateUtcMillis)
        assertEquals(70, stored.daysSinceTransplant)
        assertEquals("READY TO HARVEST", stored.harvestDecisionLabel)
        assertNull(vm.state.value.selectedVariety)
        assertNull(vm.state.value.plantingDateUtcMillis)
        assertEquals("Thilina", vm.state.value.recentScans.first().variety)
    }

    @Test
    fun viewModelSavesFruitAndRejectsDuplicateTapAndMissingScan() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val ids = ArrayDeque(listOf("a", "b"))
        val vm = HarvestViewModel(
            scanHistoryRepository = repo,
            clock = { 5_000L },
            newRecordId = { ids.removeFirst() }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        val fruit = fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f)
        val decision = engine.decideTomato(fruit, MaturityCalculator.assess(75, 65, 90))
        vm.saveFruitScan(decision)
        assertEquals(HarvestSaveStatus.SAVED, vm.fruitSaveStatus.value)
        assertEquals(1, repo.records.first().size)
        assertEquals(HarvestEnvironmentContext.SOURCE_DISCONNECTED, repo.records.first().first().environmentSource)

        vm.saveFruitScan(decision)
        assertEquals(HarvestSaveStatus.ALREADY_SAVED, vm.fruitSaveStatus.value)
        assertEquals(1, repo.records.first().size)
    }

    @Test
    fun viewModelDoesNotSaveWithoutValidFruitMeasurement() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val vm = HarvestViewModel(scanHistoryRepository = repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        val missing = engine.decideTomato(null, MaturityCalculator.assess(75, 65, 90))
        vm.saveFruitScan(missing)
        assertEquals(HarvestSaveStatus.NO_VALID_SCAN, vm.fruitSaveStatus.value)
        assertTrue(repo.records.first().isEmpty())
    }

    @Test
    fun viewModelSavesLeafRecord() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val vm = HarvestViewModel(
            scanHistoryRepository = repo,
            clock = { 9_000L },
            newRecordId = { "leaf-saved" }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        val leaf = leaf(green = 82f, yellow = 6f, brown = 4f, white = 3f)
        val assessment = matcher.assess(leaf, emptyList())
        vm.saveLeafScan(assessment)
        assertEquals(HarvestSaveStatus.SAVED, vm.leafSaveStatus.value)
        val stored = repo.getById("leaf-saved")!!
        assertEquals(ScanType.LEAF_SCAN, stored.scanType)
        assertEquals(HarvestEnvironmentContext.SOURCE_DISCONNECTED, stored.environmentSource)
        assertEquals(82f, stored.greenPercent)
    }

    @Test
    fun liveEnvironmentSourceIsNotForcedWhenPreviewSnapshotIsSaved() {
        val live = GreenhouseEnvironmentSnapshot(
            connectionState = GreenhouseConnectionState.LIVE,
            temperatureC = 24.0,
            relativeHumidityPercent = 68.0
        )
        val previewRecord = sampleFruit(id = "p", scannedAt = 1L)
        val liveRecord = HarvestRecordFactory.fruit(
            decision = engine.decideTomato(
                fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f),
                MaturityCalculator.assess(75, 65, 90)
            ),
            cropType = "Tomato",
            variety = null,
            transplantDateUtcMillis = null,
            daysSinceTransplant = null,
            environment = live,
            id = "l",
            scannedAtEpochMillis = 2L
        )!!
        assertEquals(HarvestEnvironmentContext.SOURCE_PREVIEW, previewRecord.environmentSource)
        assertEquals(HarvestEnvironmentContext.SOURCE_LIVE, liveRecord.environmentSource)
        assertFalse(previewRecord.environmentSource == HarvestEnvironmentContext.SOURCE_LIVE)
    }

    private fun assertNotEqualsLive(record: ScanRecord) {
        assertFalse(record.environmentSource == HarvestEnvironmentContext.SOURCE_LIVE)
    }

    private fun sampleFruit(
        id: String,
        scannedAt: Long,
        green: Float = 38f
    ): ScanRecord = HarvestRecordFactory.fruit(
        decision = engine.decideTomato(
            fruit(green = green, yellow = 34f, red = 18f, brown = 6f),
            MaturityCalculator.assess(75, 65, 90)
        ),
        cropType = "Tomato",
        variety = "Thilina",
        transplantDateUtcMillis = 1_700_000_000_000L,
        daysSinceTransplant = 75,
        environment = PreviewEnvironment.snapshot,
        id = id,
        scannedAtEpochMillis = scannedAt
    )!!

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
