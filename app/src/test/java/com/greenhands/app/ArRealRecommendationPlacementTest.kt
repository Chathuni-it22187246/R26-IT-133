package com.greenhands.app

import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArRealRecommendationPlacement
import com.greenhands.app.sensor.ar.ArRecommendationMarker
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.ArVisualizationSnapshot
import com.greenhands.app.sensor.ar.ArWorldMapper
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
class ArRealRecommendationPlacementTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private fun rec(
        rank: Int,
        x: Double,
        z: Double,
        selected: Boolean = true,
        type: SensorType = SensorType.TEMPERATURE,
        gridX: Double = x,
        gridY: Double = z
    ) = ArRecommendationMarker(
        rank = rank,
        label = "P$rank",
        type = type,
        gridX = gridX,
        gridY = gridY,
        xMeters = x,
        zMeters = z,
        selected = selected
    )

    private fun snapshot(
        recommendations: List<ArRecommendationMarker>,
        physical: GreenhousePhysicalConfig = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0),
        filter: SensorType? = null
    ) = ArVisualizationSnapshot(
        physical = physical,
        grid = Greenhouse(10, 8),
        sensors = emptyList(),
        coverageCells = emptyList(),
        recommendations = recommendations,
        coverageRadiusMeters = 1.0,
        selectedTypeFilter = filter,
        generatedAtStep = SensorWorkflowStep.OPTIMIZE
    )

    @Test
    fun recommendationCountAndOrderingPreserved() {
        val snap = snapshot(
            listOf(
                rec(1, 1.5, 2.5),
                rec(2, 3.5, 4.5),
                rec(3, 5.5, 1.5)
            )
        )
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val markers = ArRealRecommendationPlacement.buildRenderMarkers(pose, snap)
        assertEquals(3, markers.size)
        assertEquals("P1", markers[0].label)
        assertEquals("P2", markers[1].label)
        assertEquals("P3", markers[2].label)
        assertEquals(1, markers[0].recommendation.rank)
        assertEquals(2, markers[1].recommendation.rank)
        assertEquals(3, markers[2].recommendation.rank)
    }

    @Test
    fun onlySelectedRecommendationsAreRendered() {
        val snap = snapshot(
            listOf(
                rec(1, 1.5, 1.5, selected = true),
                rec(2, 2.5, 2.5, selected = false)
            )
        )
        assertEquals(1, ArRealRecommendationPlacement.recommendationsForRender(snap).size)
        assertEquals("P1", ArRealRecommendationPlacement.recommendationsForRender(snap).single().label)
    }

    @Test
    fun gridMetersUsedWithoutRecalculation() {
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        val r = rec(1, x = 2.5, z = 3.5, gridX = 2.0, gridY = 3.0)
        val local = ArRealRecommendationPlacement.localPosition(r, physical)
        assertEquals(2.5f, local.x, 1e-4f)
        assertEquals(3.5f, local.z, 1e-4f)
        assertEquals(ArRealRecommendationPlacement.mountHeightMeters(physical), local.y, 1e-4f)
    }

    @Test
    fun worldPositionUsesSameOriginYawAsGreenhouse() {
        val pose = ArWorldMapper.alignedPose(1f, 0.1f, -2f, forwardX = 1f, forwardZ = 0f)
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        val r = rec(1, 2.5, 3.5)
        val world = ArRealRecommendationPlacement.worldPosition(pose, r, physical)!!
        val expected = ArWorldMapper.localToWorld(
            pose,
            2.5f,
            ArRealRecommendationPlacement.mountHeightMeters(physical),
            3.5f
        )!!
        assertEquals(expected.x, world.x, 1e-4f)
        assertEquals(expected.y, world.y, 1e-4f)
        assertEquals(expected.z, world.z, 1e-4f)
    }

    @Test
    fun recommendationStyleIsDistinctFromSensor() {
        assertTrue(
            ArRealRecommendationPlacement.isDistinctFromSensorStyle(
                ArRealRecommendationPlacement.MarkerKind.RECOMMENDATION
            )
        )
        assertFalse(
            ArRealRecommendationPlacement.isDistinctFromSensorStyle(
                ArRealRecommendationPlacement.MarkerKind.SENSOR
            )
        )
    }

    @Test
    fun emptyRecommendationListProducesNoMarkers() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        assertTrue(ArRealRecommendationPlacement.buildRenderMarkers(pose, snapshot(emptyList())).isEmpty())
    }

    @Test
    fun typeFilterRespectsRecommendationType() {
        val snap = snapshot(
            listOf(rec(1, 1.5, 1.5, type = SensorType.HUMIDITY)),
            filter = SensorType.TEMPERATURE
        )
        assertTrue(
            ArRealRecommendationPlacement.recommendationsForRender(snap, SensorType.TEMPERATURE).isEmpty()
        )
        assertEquals(
            1,
            ArRealRecommendationPlacement.recommendationsForRender(snap, SensorType.HUMIDITY).size
        )
    }

    @Test
    fun applyOptimizationClearsRecommendationSnapshot() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(12.0, 8.0, 3.0, 1.0)))
        vm.addSensor(0.0, 0.0, type = SensorType.TEMPERATURE)
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val before = ArVisualizationMapper.from(vm.state.value)
        assertTrue(before.recommendations.isNotEmpty())
        val sensorCountBefore = vm.state.value.sensors.size
        assertTrue(vm.applyOptimization())
        val after = ArVisualizationMapper.from(vm.state.value)
        assertTrue(after.recommendations.isEmpty())
        assertTrue(vm.state.value.sensors.size >= sensorCountBefore)
    }

    @Test
    fun visualizationDoesNotModifySensorsOrSnapshotHelpers() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val before = ArVisualizationMapper.from(vm.state.value)
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        assertNotNull(ArRealRecommendationPlacement.buildRenderMarkers(pose, before))
        val after = ArVisualizationMapper.from(vm.state.value)
        assertEquals(before.sensors, after.sensors)
        assertEquals(before.recommendations, after.recommendations)
    }

    @Test
    fun resetHidesRecommendationVisibility() {
        assertTrue(ArRealRecommendationPlacement.shouldShowRecommendations(ArOriginPlacementPhase.ALIGNED))
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 0f, 0f, 0f)
        state = ArOriginPlacementController.beginSetDirection(state)
        state = ArOriginPlacementController.onDirectionPoint(state, 2f, 0f, 0f).first
        val reset = ArOriginPlacementController.resetAlignment(state)
        assertFalse(ArRealRecommendationPlacement.shouldShowRecommendations(reset.phase))
        assertNull(
            ArRealRecommendationPlacement.worldPosition(
                reset,
                rec(1, 1.5, 1.5),
                GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
            )
        )
    }

    @Test
    fun rendererSourcesAvoidOptimizerAndCoverageCalculator() {
        val roots = listOf(
            "src/main/java/com/greenhands/app/sensor/ar/ArRealRecommendationPlacement.kt",
            "src/main/java/com/greenhands/app/sensor/ar/ArRecommendationNodes.kt",
            "src/main/java/com/greenhands/app/sensor/ui/RealGreenhouseArScreen.kt"
        )
        roots.forEach { relative ->
            val text = java.io.File(relative).takeIf { it.exists() }?.readText()
                ?: java.io.File("../$relative").takeIf { it.exists() }?.readText()
                ?: error("Missing $relative")
            assertFalse(text.contains("SensorPlacementOptimizer"))
            assertFalse(text.contains("CoverageCalculator"))
        }
    }
}
