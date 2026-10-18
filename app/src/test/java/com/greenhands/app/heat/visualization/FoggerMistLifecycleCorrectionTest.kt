package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArEffectHostState
import com.greenhands.app.ar.render.ArFoggerMistHost
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.ArPhysicsEffectCleanup
import com.greenhands.app.ar.render.ArPhysicsEffectLifecycle
import com.greenhands.app.ar.render.CirculationRingSceneMath
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoggerMistLifecycleCorrectionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
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
    fun rememberKeysAreOnlyTheLiveEngineIdentity() {
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
        assertTrue("fogFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertTrue("circulationFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS.forEach { key ->
            assertFalse(key in ArLiveRendererOwnership.REMEMBER_KEYS)
        }
        assertFalse(ArLiveRendererOwnership.chromeDisposesRenderer())
        assertFalse(CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN)
    }

    @Test
    fun selectionLabelsAndSheetsDoNotRecreateTheRenderer() = runTest {
        val vm = demoVm("cfg-fog-life-select")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 71)
        val dispose = host.lifecycle.disposeCount
        val recreate = host.lifecycle.rendererRecreateCount
        val epoch = host.resourceEpoch
        val ids = vm.state.value.instances.map { it.id }
        vm.select(ids[0])
        host.noteRecomposition()
        host.lifecycle.noteUiChromeChanged()
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        vm.select(ids[1])
        vm.toggleLabelsVisible()
        vm.setSelectedTool(SelectedEquipmentTool.EXACT)
        vm.toggleBehaviorSheet()
        host.uiChromeChanged()
        host.noteRecomposition()
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        assertEquals(dispose, host.lifecycle.disposeCount)
        assertEquals(recreate, host.lifecycle.rendererRecreateCount)
        assertEquals(epoch, host.resourceEpoch)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertEquals(2, host.attachedUuids.size)
    }

    @Test
    fun hudAndSheetRecompositionDoesNotDisposeTheRenderer() {
        val lifecycle = ArPhysicsEffectLifecycle(72)
        assertTrue(lifecycle.attach(72))
        assertTrue(lifecycle.bindGroups(2))
        val dispose = lifecycle.disposeCount
        val sceneOps = lifecycle.sceneOperationCount
        repeat(6) { lifecycle.noteUiChromeChanged() }
        lifecycle.noteRecomposition()
        assertEquals(dispose, lifecycle.disposeCount)
        assertEquals(sceneOps, lifecycle.sceneOperationCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, lifecycle.state)
        assertEquals(2, lifecycle.attachedGroupCount)
        assertEquals(0, lifecycle.engineOperationCount)
    }

    @Test
    fun stopClearsFogGroupsWithoutDestroyingTheHost() = runTest {
        val vm = demoVm("cfg-fog-life-stop")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 73)
        assertEquals(1, host.attachedUuids.size)
        vm.stopBehavior()
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertTrue(lifecycleClearKeepsHost(host))
        assertEquals(1, vm.state.value.instances.size)
        assertTrue(vm.state.value.displayedTemperatureC != null || vm.state.value.simulatedTemperatureC != null)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertEquals(0, host.lifecycle.engineOperationCount)
    }

    @Test
    fun cleanupIsIdempotentAndDoesNotDestroyAResourceTwice() {
        val lifecycle = ArPhysicsEffectLifecycle(74)
        assertTrue(lifecycle.attach(74))
        assertTrue(lifecycle.registerOwnedResource("fog-mesh-a", 74))
        assertTrue(lifecycle.destroyOwnedResource("fog-mesh-a"))
        assertFalse(lifecycle.destroyOwnedResource("fog-mesh-a"))
        assertEquals(1, lifecycle.resourceDestroyCount)
        assertTrue(lifecycle.disposeBeforeHostDestruction())
        val sceneOps = lifecycle.sceneOperationCount
        val resource = lifecycle.resourceDestroyCount
        assertTrue(lifecycle.disposeBeforeHostDestruction())
        assertTrue(lifecycle.disposeBeforeHostDestruction())
        assertEquals(sceneOps, lifecycle.sceneOperationCount)
        assertEquals(resource, lifecycle.resourceDestroyCount)
        assertEquals(ArEffectHostState.DISPOSED, lifecycle.state)
    }

    @Test
    fun unavailableHostPerformsNoSceneOrEngineCalls() {
        val lifecycle = ArPhysicsEffectLifecycle(75)
        assertTrue(lifecycle.attach(75))
        assertTrue(lifecycle.bindGroups(2))
        assertTrue(lifecycle.registerOwnedResource("fog-vb-old", 75))
        lifecycle.markHostUnavailable()
        val sceneOps = lifecycle.sceneOperationCount
        val engineOps = lifecycle.engineOperationCount
        val resource = lifecycle.resourceDestroyCount
        assertTrue(lifecycle.disposeBeforeHostDestruction())
        assertEquals(sceneOps, lifecycle.sceneOperationCount)
        assertEquals(engineOps, lifecycle.engineOperationCount)
        assertEquals(resource, lifecycle.resourceDestroyCount)
        assertEquals(ArEffectHostState.DISPOSED, lifecycle.state)
        assertEquals(listOf("cancel-callbacks-only"), lifecycle.lastCleanupOrder)
        assertTrue(lifecycle.abandonAfterHostUnavailable())
        assertEquals(sceneOps, lifecycle.sceneOperationCount)
        assertEquals(engineOps, lifecycle.engineOperationCount)
    }

    @Test
    fun leavingArClearsEffectsBeforeHostSceneDestroy() {
        val lifecycle = ArPhysicsEffectLifecycle(76)
        assertTrue(lifecycle.attach(76))
        assertTrue(lifecycle.bindGroups(1))
        val order = lifecycle.leaveArSequence()
        assertEquals(ArPhysicsEffectCleanup.LEAVE_AR, order)
        val detach = order.indexOf(ArPhysicsEffectCleanup.DETACH_NODES)
        val hostDestroy = order.indexOf(ArPhysicsEffectCleanup.HOST_DESTROY)
        assertTrue(detach >= 0)
        assertTrue(hostDestroy > detach)
        assertEquals(ArEffectHostState.DISPOSED, lifecycle.state)
    }

    @Test
    fun repositionClearsEffectsBeforeAnchorReplacement() = runTest {
        val vm = demoVm("cfg-fog-life-reposition")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 77)
        vm.onArReposition()
        val order = host.lifecycle.repositionSequence(visualizationStillRunning = vm.state.value.behaviorRunning)
        assertEquals(ArPhysicsEffectCleanup.REPOSITION, order)
        assertTrue(
            order.indexOf(ArPhysicsEffectCleanup.DETACH_NODES) <
                order.indexOf(ArPhysicsEffectCleanup.REPLACE_ANCHOR)
        )
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertTrue(vm.state.value.behaviorRunning)
        assertFalse(vm.foggerEffectFrame().isEmpty)
    }

    @Test
    fun aNewEngineIdentityNeverReceivesOldEngineBoundResources() {
        val oldHost = ArPhysicsEffectLifecycle(81)
        assertTrue(oldHost.attach(81))
        assertTrue(oldHost.registerOwnedResource("fog-geometry-1", 81))
        val newHost = ArPhysicsEffectLifecycle(82)
        assertTrue(newHost.attach(82))
        assertFalse(newHost.acceptEngineBoundResource(81, "fog-geometry-1"))
        assertFalse(newHost.registerOwnedResource("fog-geometry-1", 81))
        assertTrue(newHost.acceptEngineBoundResource(82, "fog-geometry-2"))
        assertFalse(oldHost.attach(82))
        oldHost.disposeBeforeHostDestruction()
        assertFalse(newHost.acceptEngineBoundResource(81, "fog-geometry-1"))
        assertEquals(0, newHost.engineOperationCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, newHost.state)
    }

    @Test
    fun offRemovesOnlyThatUuidGroup() = runTest {
        val vm = demoVm("cfg-fog-life-off")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val ids = vm.state.value.instances.map { it.id }
        assertEquals(2, vm.foggerEffectFrame().groups.size)
        vm.setSimulatedHumidity(80.0)
        ids.forEach { id ->
            assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[id])
            assertEquals(null, vm.foggerEffectFrame().group(id))
        }
        assertTrue(vm.foggerEffectFrame().isEmpty)
    }

    @Test
    fun bindsToGreenhouseRootWithoutChildTabletopScale() = runTest {
        val vm = demoVm("cfg-fog-life-bind")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val request = ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED)
        assertEquals(CirculationRingSceneMath.GREENHOUSE_ROOT_NAME, request.parentName)
        assertFalse(request.applyTabletopScaleToChildren)
        val host = ArFoggerMistHost(90)
        host.lifecycle.attach(90)
        host.bind(request)
        assertEquals(CirculationRingSceneMath.GREENHOUSE_ROOT_NAME, host.parentName)
        assertEquals(1, host.attachedUuids.size)
    }

    private fun lifecycleClearKeepsHost(host: ArFoggerMistHost): Boolean {
        val dispose = host.lifecycle.disposeCount
        assertTrue(host.lifecycle.clearEffectsWhileSceneAlive())
        host.clear()
        assertEquals(dispose, host.lifecycle.disposeCount)
        assertEquals(0, host.lifecycle.attachedGroupCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        return true
    }

    private fun attachedHost(vm: EquipmentPlacementViewModel, engineIdentity: Int): ArFoggerMistHost {
        val host = ArFoggerMistHost(engineIdentity)
        assertTrue(host.lifecycle.attach(engineIdentity))
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        host.lifecycle.bindGroups(host.attachedUuids.size)
        return host
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
