package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.ar.render.ArEffectHostState
import com.greenhands.app.ar.render.ArEnvironmentalFieldHost
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.ArPhysicsEffectCleanup
import com.greenhands.app.ar.render.ArPhysicsEffectLifecycle
import com.greenhands.app.ar.render.CirculationRingSceneMath
import com.greenhands.app.ar.render.EnvironmentalFieldPatchMesh
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class EnvironmentalFieldLifecycleTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val climate = OverlayClimate(
        crop = com.greenhands.app.heat.model.Crop.TOMATO,
        stage = com.greenhands.app.heat.profile.CropProfileRegistry.stagesFor(
            com.greenhands.app.heat.model.Crop.TOMATO
        ).first { it.id == "vegetative" },
        temperatureLabel = "25.0°C",
        humidityLabel = "70.0 % RH",
        vpdLabel = "Not published",
        circulation = circ,
        exhaust = HeatFormulas.exhaustFromCirculation(circ),
        fogger = HeatFormulas.fogger(70.0),
        optimalTemperatureC = 25.0,
        optimalHumidityPercent = 70.0
    )

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun rendererPlanNeverCreatesANodePerCellAndRespectsCaps() = runTest {
        val vm = startedVm("cfg-field-caps")
        val frame = vm.environmentalFieldFrame()
        assertFalse(frame.nodePerCell)
        assertFalse(EnvironmentalFieldVisual.NODE_PER_CELL)
        assertFalse(EnvironmentalFieldVisual.CREATES_OR_DESTROYS_NODES_PER_CELL)
        assertTrue(frame.cellCount <= EnvironmentalFieldVisual.MAX_CELLS)
        assertTrue(frame.activeNodeCount <= EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
        assertTrue(frame.bands.size <= 6)
        assertEquals(6, EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
        assertEquals(240, EnvironmentalFieldVisual.MAX_CELLS)
        assertTrue(EnvironmentalFieldVisual.MATERIALS_POOLED)
        assertFalse(EnvironmentalFieldVisual.UPDATES_GEOMETRY_PER_FRAME)
        assertEquals("GeometryNode.updateGeometry", EnvironmentalFieldVisual.SCENE_VIEW_VERTEX_UPDATE_API)
        assertEquals(
            "pooled GeometryNode combined patches per classification band",
            EnvironmentalFieldVisual.RENDER_METHOD
        )
    }

    @Test
    fun geometryUpdatesOnlyForNewSnapshotsOrViewChanges() = runTest {
        val vm = startedVm("cfg-field-geom")
        val host = ArEnvironmentalFieldHost(41)
        assertTrue(host.lifecycle.attach(41))
        val first = vm.environmentalFieldFrame()
        assertTrue(host.bind(ArEnvironmentalFieldHost.request(first, ArTabletopPhase.PLACED)))
        assertEquals(1, host.geometryUpdateCount)
        assertFalse(host.bind(ArEnvironmentalFieldHost.request(first, ArTabletopPhase.PLACED)))
        assertEquals(1, host.geometryUpdateCount)
        vm.setFieldHeight(EnvironmentalFieldHeight.CROP)
        val cropped = vm.environmentalFieldFrame()
        assertTrue(host.bind(ArEnvironmentalFieldHost.request(cropped, ArTabletopPhase.PLACED)))
        assertEquals(2, host.geometryUpdateCount)
        assertEquals(EnvironmentalFieldGeometryUpdateReason.SNAPSHOT, host.lastUpdateReason)
        assertTrue(host.activeNodeCount <= EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
        assertTrue(host.cellCount <= EnvironmentalFieldVisual.MAX_CELLS)
        assertEquals(CirculationRingSceneMath.GREENHOUSE_ROOT_NAME, host.parentName)
        assertFalse(host.applyTabletopScaleToChildren)
    }

    @Test
    fun fieldFollowsGreenhouseRootAndDoesNotOutliveTheHost() {
        assertEquals("ar_greenhouse_root", CirculationRingSceneMath.GREENHOUSE_ROOT_NAME)
        assertTrue(EnvironmentalFieldPatchMesh.followsGreenhouseRoot())
        assertFalse(EnvironmentalFieldPatchMesh.appliesTabletopScaleToChildren())
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        assertTrue(layout.lengthMappedToX)
        val lifecycle = ArPhysicsEffectLifecycle(42)
        assertTrue(lifecycle.attach(42))
        assertTrue(lifecycle.registerOwnedResource("env-field-root", 42))
        assertTrue(lifecycle.disposeBeforeHostDestruction())
        assertEquals(ArEffectHostState.DISPOSED, lifecycle.state)
        assertFalse(lifecycle.acceptEngineBoundResource(42, "late-field"))
        val other = ArPhysicsEffectLifecycle(43)
        assertTrue(other.attach(43))
        assertFalse(other.acceptEngineBoundResource(42, "env-field-root"))
    }

    @Test
    fun stopRepositionAndLeaveArCleanupIsIdempotent() = runTest {
        val vm = startedVm("cfg-field-life")
        val host = ArEnvironmentalFieldHost(44)
        assertTrue(host.lifecycle.attach(44))
        host.bind(ArEnvironmentalFieldHost.request(vm.environmentalFieldFrame(), ArTabletopPhase.PLACED))
        val dispose = host.lifecycle.disposeCount
        assertTrue(host.lifecycle.clearEffectsWhileSceneAlive())
        host.clear()
        assertEquals(dispose, host.lifecycle.disposeCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        vm.onArReposition()
        val order = host.lifecycle.repositionSequence(visualizationStillRunning = vm.state.value.behaviorRunning)
        assertEquals(ArPhysicsEffectCleanup.REPOSITION, order)
        assertTrue(
            order.indexOf(ArPhysicsEffectCleanup.DETACH_NODES) <
                order.indexOf(ArPhysicsEffectCleanup.REPLACE_ANCHOR)
        )
        val leave = host.lifecycle.leaveArSequence()
        assertEquals(ArPhysicsEffectCleanup.LEAVE_AR, leave)
        val sceneOps = host.lifecycle.sceneOperationCount
        host.lifecycle.disposeBeforeHostDestruction()
        assertEquals(sceneOps, host.lifecycle.sceneOperationCount)
        host.destroy()
        host.destroy()
        assertTrue(host.disposeCount >= 1)
    }

    @Test
    fun noEmissiveFactorAndNoSolverCallFromVisualFrames() {
        assertFalse(EnvironmentalFieldAppearance.WRITES_EMISSIVE_FACTOR)
        assertFalse(Phase3D2C.WRITES_EMISSIVE_FACTOR)
        assertFalse(ArCirculationColorMaterial.writesEmissiveFactor())
        assertFalse(ArCirculationColorMaterial.maySetParameter("emissiveFactor"))
        val write = ArCirculationColorMaterial.frameColorWrite(
            EnvironmentalFieldPalette.temperatureColor(TemperatureLegendBand.TARGET)
                .withAlpha(EnvironmentalFieldAppearance.MATERIAL_ALPHA),
            fade = 1f
        )
        assertEquals(setOf("color"), write.uniformNames)
        assertFalse(write.requestsEmissiveFactor)
        assertFalse(EnvironmentalFieldVisual.ADVANCES_SOLVER_FROM_FRAMES)
        assertFalse(Phase3D2C.ADVANCES_SOLVER_FROM_FRAMES)
        assertFalse("fieldFrame" in ArLiveRendererOwnership.REMEMBER_KEYS)
        assertTrue("fieldFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertTrue("environmentalLayer" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
        assertFalse(ArLiveRendererOwnership.chromeDisposesRenderer())
    }

    @Test
    fun unusedPatchSlotsAreDegenerateAndCapacityIsFixed() {
        assertEquals(24, EnvironmentalFieldPatchMesh.VERTICES_PER_CELL)
        val hidden = EnvironmentalFieldPatchMesh.padded(emptyList(), 4)
        assertEquals(4, hidden.size)
        assertTrue(hidden.all { !it.visible })
        val vertices = EnvironmentalFieldPatchMesh.verticesOf(hidden)
        assertEquals(4 * EnvironmentalFieldPatchMesh.VERTICES_PER_CELL, vertices.size)
        assertTrue(vertices.all { it.position.x == 0f && it.position.y == 0f && it.position.z == 0f })
    }

    @Test
    fun hostIdentitySurvivesUnplacedAnchoredLayerStartStopRepositionAndDispose() = runTest {
        val vm = demoVm("cfg-field-compose-seq")
        val host = ArEnvironmentalFieldHost(101)
        assertTrue(host.lifecycle.attach(101))
        val identity = System.identityHashCode(host)

        val unplaced = vm.environmentalFieldFrame()
        assertEquals(
            EnvironmentalFieldLegendModel.HIDDEN,
            EnvironmentalFieldOverlayHost.legendModel(placed = false, unplaced.legend)
        )
        assertFalse(
            host.bind(ArEnvironmentalFieldHost.request(unplaced, ArTabletopPhase.DETECTING))
        )

        val anchored = vm.environmentalFieldFrame()
        assertEquals(EnvironmentalFieldLayer.TEMPERATURE, anchored.layer)
        assertEquals(EnvironmentalFieldAvailability.HIDDEN_BEFORE_START, anchored.availability)
        assertTrue(anchored.isEmpty)
        host.bind(ArEnvironmentalFieldHost.request(anchored, ArTabletopPhase.PLACED))
        assertEquals(anchored.legend, EnvironmentalFieldOverlayHost.legendModel(true, anchored.legend))

        vm.setSimulatedTemperature(28.0)
        vm.setSimulatedHumidity(55.0)
        vm.startBehavior()
        val started = vm.environmentalFieldFrame()
        assertFalse(started.isEmpty)
        assertTrue(host.bind(ArEnvironmentalFieldHost.request(started, ArTabletopPhase.PLACED)))
        val afterStartUpdates = host.geometryUpdateCount

        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.HUMIDITY)
        val humidity = vm.environmentalFieldFrame()
        assertEquals(EnvironmentalFieldLayer.HUMIDITY, humidity.layer)
        assertTrue(host.bind(ArEnvironmentalFieldHost.request(humidity, ArTabletopPhase.PLACED)))

        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.OFF)
        val off = vm.environmentalFieldFrame()
        assertTrue(off.isEmpty)
        assertFalse(host.bind(ArEnvironmentalFieldHost.request(off, ArTabletopPhase.PLACED)))

        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.TEMPERATURE)
        val temperatureAgain = vm.environmentalFieldFrame()
        assertFalse(temperatureAgain.isEmpty)
        host.bind(ArEnvironmentalFieldHost.request(temperatureAgain, ArTabletopPhase.PLACED))

        vm.stopBehavior()
        val stopped = vm.environmentalFieldFrame()
        assertTrue(stopped.isEmpty)
        assertNull(vm.state.value.physicsSnapshot)
        host.bind(ArEnvironmentalFieldHost.request(stopped, ArTabletopPhase.PLACED))

        vm.onArReposition()
        host.clear()
        assertFalse(
            host.bind(
                ArEnvironmentalFieldHost.request(vm.environmentalFieldFrame(), ArTabletopPhase.DETECTING)
            )
        )
        assertTrue(vm.environmentalFieldFrame().isEmpty)

        host.bind(ArEnvironmentalFieldHost.request(vm.environmentalFieldFrame(), ArTabletopPhase.PLACED))
        assertTrue(vm.environmentalFieldFrame().isEmpty)
        assertEquals(identity, System.identityHashCode(host))
        assertTrue(host.geometryUpdateCount >= afterStartUpdates)
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
        assertFalse("fieldFrame" in ArLiveRendererOwnership.REMEMBER_KEYS)

        host.destroy()
        host.destroy()
        assertTrue(host.disposeCount >= 1)
        assertEquals(ArEffectHostState.DISPOSED, host.lifecycle.state)
    }

    @Test
    fun materialsArePooledOncePerLiveRenderer() {
        val host = ArEnvironmentalFieldHost(45)
        host.noteMaterialCreated()
        host.noteMaterialCreated()
        host.noteNodeCreated()
        assertEquals(2, host.materialCreateCount)
        assertEquals(1, host.nodeCreateCount)
        assertTrue(EnvironmentalFieldVisual.MATERIALS_POOLED)
        assertEquals(6, EnvironmentalFieldVisual.MAX_MATERIALS)
        assertFalse(EnvironmentalFieldVisual.CREATES_OR_DESTROYS_NODES_PER_FRAME)
    }

    private fun startedVm(configId: String): EquipmentPlacementViewModel {
        val vm = demoVm(configId)
        vm.setSimulatedTemperature(28.0)
        vm.setSimulatedHumidity(55.0)
        vm.startBehavior()
        return vm
    }

    private fun demoVm(configId: String): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            configId,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        return vm
    }
}
