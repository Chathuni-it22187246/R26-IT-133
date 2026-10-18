package com.greenhands.app

import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArRealSensorPlacement
import com.greenhands.app.sensor.ar.ArSensorMarker
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.ArVisualizationSnapshot
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorStatus
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class ArRealSensorPlacementTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private fun marker(
        id: String,
        type: SensorType,
        x: Double,
        z: Double,
        status: SensorStatus = SensorStatus.ACTIVE
    ) = ArSensorMarker(
        id = id,
        type = type,
        status = status,
        gridX = 0.0,
        gridY = 0.0,
        xMeters = x,
        zMeters = z,
        coverageRadiusMeters = 1.0
    )

    private fun snapshot(sensors: List<ArSensorMarker>): ArVisualizationSnapshot {
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        return ArVisualizationSnapshot(
            physical = physical,
            grid = Greenhouse(10, 8),
            sensors = sensors,
            coverageCells = emptyList(),
            recommendations = emptyList(),
            coverageRadiusMeters = 1.0,
            selectedTypeFilter = null,
            generatedAtStep = SensorWorkflowStep.COVERAGE
        )
    }

    @Test
    fun sensorSnapshotMarkerCount() {
        val snap = snapshot(
            listOf(
                marker("S1", SensorType.TEMPERATURE, 1.0, 2.0),
                marker("S2", SensorType.HUMIDITY, 3.0, 4.0)
            )
        )
        assertEquals(2, snap.sensors.size)
        assertEquals(2, ArRealSensorPlacement.sensorsForRender(snap).size)
    }

    @Test
    fun sensorIdTypeStatusAndMetersPreserved() {
        val m = marker("S7", SensorType.SOIL_MOISTURE, 2.5, 3.5, SensorStatus.INACTIVE)
        val snap = snapshot(listOf(m))
        val out = snap.sensors.single()
        assertEquals("S7", out.id)
        assertEquals(SensorType.SOIL_MOISTURE, out.type)
        assertEquals(SensorStatus.INACTIVE, out.status)
        assertEquals(2.5, out.xMeters, 1e-9)
        assertEquals(3.5, out.zMeters, 1e-9)
    }

    @Test
    fun mountingHeightUsesVirtualPreviewConvention() {
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        // 3 * 0.32 = 0.96 → within [0.5, 2.4]
        assertEquals(0.96f, ArRealSensorPlacement.mountHeightMeters(physical), 1e-4f)
        val tall = GreenhousePhysicalConfig(20.0, 10.0, 10.0, 1.0)
        assertEquals(2.4f, ArRealSensorPlacement.mountHeightMeters(tall), 1e-4f)
        val short = GreenhousePhysicalConfig(5.0, 4.0, 1.0, 1.0)
        assertEquals(0.5f, ArRealSensorPlacement.mountHeightMeters(short), 1e-4f)
    }

    @Test
    fun localPositionUsesXMetersMountYAndZMeters() {
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        val sensor = marker("S1", SensorType.TEMPERATURE, 2.5, 3.5)
        val local = ArRealSensorPlacement.localPosition(sensor, physical)
        assertEquals(2.5f, local.x, 1e-4f)
        assertEquals(3.5f, local.z, 1e-4f)
        assertEquals(ArRealSensorPlacement.mountHeightMeters(physical), local.y, 1e-4f)
    }

    @Test
    fun worldPositionUsesSameOriginAndYawAsGreenhouse() {
        val pose = ArWorldMapper.alignedPose(1f, 0.1f, -2f, forwardX = 1f, forwardZ = 0f)
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        val sensor = marker("S1", SensorType.LIGHT_INTENSITY, 2.5, 3.5)
        val world = ArRealSensorPlacement.worldPosition(pose, sensor, physical)!!
        val expected = ArWorldMapper.localToWorld(
            pose,
            2.5f,
            ArRealSensorPlacement.mountHeightMeters(physical),
            3.5f
        )!!
        assertEquals(expected.x, world.x, 1e-4f)
        assertEquals(expected.y, world.y, 1e-4f)
        assertEquals(expected.z, world.z, 1e-4f)
        // local (0,0,0) greenhouse origin still at pose origin
        val origin = ArWorldMapper.localToWorld(pose, 0f, 0f, 0f)!!
        assertEquals(1f, origin.x, 1e-4f)
        assertEquals(0.1f, origin.y, 1e-4f)
        assertEquals(-2f, origin.z, 1e-4f)
    }

    @Test
    fun differentSensorsProduceDifferentWorldPositions() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        val a = ArRealSensorPlacement.worldPosition(
            pose, marker("S1", SensorType.TEMPERATURE, 1.0, 1.0), physical
        )!!
        val b = ArRealSensorPlacement.worldPosition(
            pose, marker("S2", SensorType.HUMIDITY, 4.0, 5.0), physical
        )!!
        assertTrue(abs(a.x - b.x) > 0.5f || abs(a.z - b.z) > 0.5f)
        assertNotEquals(a.x, b.x, 1e-3f)
    }

    @Test
    fun inactiveSensorsRemainRepresented() {
        val snap = snapshot(
            listOf(marker("S9", SensorType.HUMIDITY, 1.0, 1.0, SensorStatus.INACTIVE))
        )
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val markers = ArRealSensorPlacement.buildRenderMarkers(pose, snap)
        assertEquals(1, markers.size)
        assertFalse(markers.single().active)
        assertEquals("H · S9", markers.single().label)
    }

    @Test
    fun emptySensorListProducesNoMarkers() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        assertTrue(ArRealSensorPlacement.buildRenderMarkers(pose, snapshot(emptyList())).isEmpty())
    }

    @Test
    fun markersHiddenUntilAligned() {
        assertFalse(ArRealSensorPlacement.shouldShowMarkers(ArOriginPlacementPhase.ORIGIN_PLACED))
        assertTrue(ArRealSensorPlacement.shouldShowMarkers(ArOriginPlacementPhase.ALIGNED))
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 0f, 0f, 0f)
        assertNull(
            ArRealSensorPlacement.worldPosition(
                state,
                marker("S1", SensorType.TEMPERATURE, 1.0, 1.0),
                GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
            )
        )
    }

    @Test
    fun typeFilterDoesNotMutateSnapshot() {
        val snap = snapshot(
            listOf(
                marker("S1", SensorType.TEMPERATURE, 1.0, 1.0),
                marker("S2", SensorType.HUMIDITY, 2.0, 2.0)
            )
        )
        val filtered = ArRealSensorPlacement.sensorsForRender(snap, SensorType.TEMPERATURE)
        assertEquals(1, filtered.size)
        assertEquals(2, snap.sensors.size)
    }

    @Test
    fun snapshotUnchangedByPlacementHelpers() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val before = ArVisualizationMapper.from(vm.state.value)
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        assertNotNull(ArRealSensorPlacement.buildRenderMarkers(pose, before))
        val after = ArVisualizationMapper.from(vm.state.value)
        assertEquals(before.sensors, after.sensors)
        assertEquals(before.physical, after.physical)
        assertEquals(before.coverageCells.size, after.coverageCells.size)
    }

    @Test
    fun rendererSourcesAvoidCoverageAndOptimizer() {
        val roots = listOf(
            "src/main/java/com/greenhands/app/sensor/ar/ArRealSensorPlacement.kt",
            "src/main/java/com/greenhands/app/sensor/ar/ArSensorMarkerNodes.kt",
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
