package com.greenhands.app

import com.greenhands.app.sensor.ar.ArCoverageCell
import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArRealCoveragePlacement
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.ArVisualizationSnapshot
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.SensorWorkflowStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
class ArRealCoveragePlacementTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private fun cell(col: Int, row: Int, state: CellCoverageState) =
        ArCoverageCell(column = col, row = row, state = state)

    private fun snapshot(
        physical: GreenhousePhysicalConfig,
        cells: List<ArCoverageCell>,
        filter: SensorType? = null,
        gridW: Int = 10,
        gridH: Int = 8
    ) = ArVisualizationSnapshot(
        physical = physical,
        grid = Greenhouse(gridW, gridH),
        sensors = emptyList(),
        coverageCells = cells,
        recommendations = emptyList(),
        coverageRadiusMeters = physical.cellSizeMeters,
        selectedTypeFilter = filter,
        generatedAtStep = SensorWorkflowStep.COVERAGE
    )

    @Test
    fun cellZeroZeroMapsToHalfCellSizeCenter() {
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        val local = ArRealCoveragePlacement.localCenter(cell(0, 0, CellCoverageState.COVERED), physical)
        assertEquals(0.5f, local.x, 1e-4f)
        assertEquals(0.5f, local.z, 1e-4f)
        assertEquals(ArRealCoveragePlacement.COVERAGE_FLOOR_Y_METERS, local.y, 1e-5f)
    }

    @Test
    fun dynamicCellSizeWorks() {
        val physical = GreenhousePhysicalConfig(10.0, 5.0, 3.0, 0.5)
        val local = ArRealCoveragePlacement.localCenter(cell(0, 0, CellCoverageState.COVERED), physical)
        assertEquals(0.25f, local.x, 1e-4f)
        assertEquals(0.25f, local.z, 1e-4f)
        val c2 = ArRealCoveragePlacement.localCenter(cell(2, 1, CellCoverageState.BLIND_SPOT), physical)
        assertEquals(1.25f, c2.x, 1e-4f)
        assertEquals(0.75f, c2.z, 1e-4f)
    }

    @Test
    fun dynamicGreenhouseDimensionsWork() {
        val physical = GreenhousePhysicalConfig(20.0, 10.0, 4.0, 0.5)
        val far = ArRealCoveragePlacement.localCenter(cell(39, 19, CellCoverageState.COVERED), physical)
        assertEquals(19.75f, far.x, 1e-4f)
        assertEquals(9.75f, far.z, 1e-4f)
    }

    @Test
    fun coverageStateMapsToGreenAmberRed() {
        val covered = ArRealCoveragePlacement.fillColor(CellCoverageState.COVERED)
        val overlap = ArRealCoveragePlacement.fillColor(CellCoverageState.OVERLAP)
        val blind = ArRealCoveragePlacement.fillColor(CellCoverageState.BLIND_SPOT)
        // Opaque AR pastels: green / amber / red families (alpha ignored by SceneView materials).
        assertEquals(1f, covered.alpha, 1e-3f)
        assertEquals(1f, overlap.alpha, 1e-3f)
        assertEquals(1f, blind.alpha, 1e-3f)
        assertTrue(covered.green > covered.red && covered.green > covered.blue)
        assertTrue(overlap.red > overlap.blue && overlap.green > overlap.blue * 0.5f)
        assertTrue(blind.red > blind.green && blind.red > blind.blue)
    }

    @Test
    fun monitoringFilterIsAll() {
        assertTrue(ArRealCoveragePlacement.isMonitoringFilter(null))
        assertFalse(ArRealCoveragePlacement.isMonitoringFilter(SensorType.TEMPERATURE))
    }

    @Test
    fun allFilterUsesMonitoringCoverageFromMapper() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(4.0, 4.0, 3.0, 1.0)))
        vm.addSensor(0.0, 0.0, type = SensorType.TEMPERATURE)
        vm.addSensor(0.0, 0.0, type = SensorType.HUMIDITY)
        val monitoring = ArVisualizationMapper.from(vm.state.value, selectedTypeFilter = null)
        // Cross-type co-coverage must not be OVERLAP under All.
        assertTrue(monitoring.coverageCells.none { it.state == CellCoverageState.OVERLAP })
        assertTrue(
            monitoring.coverageCells.any {
                it.column == 0 && it.row == 0 && it.state == CellCoverageState.COVERED
            }
        )
    }

    @Test
    fun typeFilterUsesTypeCoverageAndSameTypeOverlap() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(6.0, 6.0, 3.0, 1.0)))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val typeSnap = ArVisualizationMapper.from(vm.state.value, SensorType.TEMPERATURE)
        assertEquals(SensorType.TEMPERATURE, typeSnap.selectedTypeFilter)
        assertTrue(typeSnap.coverageCells.any { it.state == CellCoverageState.OVERLAP })
    }

    @Test
    fun coverageUsesSameOriginYawAsSensors() {
        val pose = ArWorldMapper.alignedPose(2f, 0.05f, -1f, forwardX = 1f, forwardZ = 0f)
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        val c = cell(0, 0, CellCoverageState.COVERED)
        val world = ArRealCoveragePlacement.worldPosition(pose, c, physical)!!
        val expected = ArWorldMapper.localToWorld(
            pose,
            0.5f,
            ArRealCoveragePlacement.COVERAGE_FLOOR_Y_METERS,
            0.5f
        )!!
        assertEquals(expected.x, world.x, 1e-4f)
        assertEquals(expected.y, world.y, 1e-4f)
        assertEquals(expected.z, world.z, 1e-4f)
    }

    @Test
    fun resetHidesCoverageVisibility() {
        assertTrue(ArRealCoveragePlacement.shouldShowCoverage(ArOriginPlacementPhase.ALIGNED))
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 0f, 0f, 0f)
        state = ArOriginPlacementController.beginSetDirection(state)
        state = ArOriginPlacementController.onDirectionPoint(state, 2f, 0f, 0f).first
        assertTrue(ArRealCoveragePlacement.shouldShowCoverage(state.phase))
        val reset = ArOriginPlacementController.resetAlignment(state)
        assertFalse(ArRealCoveragePlacement.shouldShowCoverage(reset.phase))
        assertNull(
            ArRealCoveragePlacement.worldPosition(
                reset,
                cell(0, 0, CellCoverageState.COVERED),
                GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
            )
        )
    }

    @Test
    fun emptyCoverageProducesNoRenderCells() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val snap = snapshot(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0), emptyList())
        assertTrue(ArRealCoveragePlacement.buildRenderCells(pose, snap).isEmpty())
    }

    @Test
    fun metricsAggregateExistingCellStates() {
        val metrics = ArRealCoveragePlacement.metricsFromCells(
            listOf(
                cell(0, 0, CellCoverageState.COVERED),
                cell(1, 0, CellCoverageState.COVERED),
                cell(0, 1, CellCoverageState.OVERLAP),
                cell(1, 1, CellCoverageState.BLIND_SPOT)
            )
        )
        assertEquals(2, metrics.coveredCells)
        assertEquals(1, metrics.overlapCells)
        assertEquals(1, metrics.blindSpotCells)
        assertEquals(4, metrics.totalCells)
        assertEquals(75.0, metrics.coveragePercent, 1e-6)
    }

    @Test
    fun snapshotUnchangedByCoverageHelpers() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val before = ArVisualizationMapper.from(vm.state.value)
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        assertNotNull(ArRealCoveragePlacement.buildRenderCells(pose, before))
        val after = ArVisualizationMapper.from(vm.state.value)
        assertEquals(before.coverageCells, after.coverageCells)
        assertEquals(before.sensors.size, after.sensors.size)
    }

    @Test
    fun rendererSourcesAvoidCoverageCalculatorAndOptimizer() {
        val roots = listOf(
            "src/main/java/com/greenhands/app/sensor/ar/ArRealCoveragePlacement.kt",
            "src/main/java/com/greenhands/app/sensor/ar/ArCoverageNodes.kt",
            "src/main/java/com/greenhands/app/sensor/ui/RealGreenhouseArScreen.kt"
        )
        roots.forEach { relative ->
            val text = java.io.File(relative).takeIf { it.exists() }?.readText()
                ?: java.io.File("../$relative").takeIf { it.exists() }?.readText()
                ?: error("Missing $relative")
            assertFalse(text.contains("CoverageCalculator"))
            assertFalse(text.contains("SensorPlacementOptimizer"))
        }
    }
}
