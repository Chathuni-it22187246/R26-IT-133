package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArCirculationRingHost
import com.greenhands.app.ar.render.ArEffectHostState
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.ArPhysicsEffectCleanup
import com.greenhands.app.ar.render.ArPhysicsEffectLifecycle
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
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
class CirculationRingLifecycleCorrectionTest {

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
        ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS.forEach { key ->
            assertFalse(key in ArLiveRendererOwnership.REMEMBER_KEYS)
        }
        assertFalse(ArLiveRendererOwnership.chromeDisposesRenderer())
    }

    @Test
    fun selectionDoesNotDisposeOrRecreateTheRenderer() = runTest {
        val vm = demoVm("cfg-life-select")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 11)
        val dispose = host.lifecycle.disposeCount
        val recreate = host.lifecycle.rendererRecreateCount
        val epoch = host.resourceEpoch
        val ids = vm.state.value.instances.map { it.id }
        vm.select(ids[0])
        host.noteRecomposition()
        host.lifecycle.noteUiChromeChanged()
        host.bind(ArCirculationRingHost.request(vm.circulationEffectFrame(), ArTabletopPhase.PLACED))
        vm.select(ids[1])
        host.noteRecomposition()
        assertEquals(dispose, host.lifecycle.disposeCount)
        assertEquals(recreate, host.lifecycle.rendererRecreateCount)
        assertEquals(epoch, host.resourceEpoch)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertEquals(2, host.attachedUuids.size)
    }

    @Test
    fun labelsAndMoveTableDoNotDisposeTheRenderer() = runTest {
        val vm = demoVm("cfg-life-labels")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 12)
        val dispose = host.lifecycle.disposeCount
        vm.toggleLabelsVisible()
        vm.setSelectedTool(SelectedEquipmentTool.EXACT)
        vm.setSelectedTool(SelectedEquipmentTool.MOVE)
        host.uiChromeChanged()
        host.bind(ArCirculationRingHost.request(vm.circulationEffectFrame(), ArTabletopPhase.PLACED))
        assertEquals(dispose, host.lifecycle.disposeCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertFalse(vm.circulationEffectFrame().isEmpty)
    }

    @Test
    fun hudAndSheetRecompositionDoesNotDisposeTheRenderer() {
        val lifecycle = ArPhysicsEffectLifecycle(13)
        assertTrue(lifecycle.attach(13))
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
    fun stopClearsGroupsWithoutDestroyingTheHost() = runTest {
        val vm = demoVm("cfg-life-stop")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 14)
        assertEquals(1, host.attachedUuids.size)
        vm.stopBehavior()
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertTrue(lifecycleClearKeepsHost(host))
        assertEquals(1, vm.state.value.instances.size)
        assertTrue(vm.state.value.displayedTemperatureC != null)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertEquals(0, host.lifecycle.engineOperationCount)
    }

    @Test
    fun cleanupIsIdempotentAndDoesNotDestroyAResourceTwice() {
        val lifecycle = ArPhysicsEffectLifecycle(15)
        assertTrue(lifecycle.attach(15))
        assertTrue(lifecycle.registerOwnedResource("mesh-a", 15))
        assertTrue(lifecycle.destroyOwnedResource("mesh-a"))
        assertFalse(lifecycle.destroyOwnedResource("mesh-a"))
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
    fun disposalWhileHostUnavailablePerformsNoSceneOrEngineCall() {
        val lifecycle = ArPhysicsEffectLifecycle(16)
        assertTrue(lifecycle.attach(16))
        assertTrue(lifecycle.bindGroups(2))
        assertTrue(lifecycle.registerOwnedResource("vb-old", 16))
        lifecycle.markHostUnavailable()
        val sceneOps = lifecycle.sceneOperationCount
        val engineOps = lifecycle.engineOperationCount
        val resource = lifecycle.resourceDestroyCount
        assertTrue(lifecycle.disposeBeforeHostDestruction())
        assertEquals(sceneOps, lifecycle.sceneOperationCount)
        assertEquals(engineOps, lifecycle.engineOperationCount)
        assertEquals(resource, lifecycle.resourceDestroyCount)
        assertEquals(0, lifecycle.engineOperationCount)
        assertEquals(ArEffectHostState.DISPOSED, lifecycle.state)
        assertEquals(listOf("cancel-callbacks-only"), lifecycle.lastCleanupOrder)
        assertTrue(lifecycle.abandonAfterHostUnavailable())
        assertEquals(sceneOps, lifecycle.sceneOperationCount)
        assertEquals(engineOps, lifecycle.engineOperationCount)
    }

    @Test
    fun leavingArClearsEffectsBeforeHostSceneDestroy() {
        val lifecycle = ArPhysicsEffectLifecycle(17)
        assertTrue(lifecycle.attach(17))
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
        val vm = demoVm("cfg-life-reposition")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 18)
        vm.onArReposition()
        val order = host.lifecycle.repositionSequence(visualizationStillRunning = vm.state.value.behaviorRunning)
        assertEquals(ArPhysicsEffectCleanup.REPOSITION, order)
        assertTrue(
            order.indexOf(ArPhysicsEffectCleanup.DETACH_NODES) <
                order.indexOf(ArPhysicsEffectCleanup.REPLACE_ANCHOR)
        )
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertTrue(vm.state.value.behaviorRunning)
        assertFalse(vm.circulationEffectFrame().isEmpty)
    }

    @Test
    fun zeroOneAndTwoCirculationGroupsFollowSafeLifecyclePaths() = runTest {
        val vm = demoVm("cfg-life-counts")
        vm.enterPlacementMode()
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        assertSafeGroupPath(0, vm)

        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        assertSafeGroupPath(1, vm)

        vm.duplicateSelected()
        vm.startBehavior()
        assertSafeGroupPath(2, vm)
    }

    @Test
    fun aNewEngineIdentityNeverReceivesOldEngineBoundResources() {
        val oldHost = ArPhysicsEffectLifecycle(21)
        assertTrue(oldHost.attach(21))
        assertTrue(oldHost.registerOwnedResource("geometry-1", 21))
        val newHost = ArPhysicsEffectLifecycle(22)
        assertTrue(newHost.attach(22))
        assertFalse(newHost.acceptEngineBoundResource(21, "geometry-1"))
        assertFalse(newHost.registerOwnedResource("geometry-1", 21))
        assertTrue(newHost.acceptEngineBoundResource(22, "geometry-2"))
        assertFalse(oldHost.attach(22))
        oldHost.disposeBeforeHostDestruction()
        assertFalse(newHost.acceptEngineBoundResource(21, "geometry-1"))
        assertEquals(0, newHost.engineOperationCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, newHost.state)
    }

    private fun lifecycleClearKeepsHost(host: ArCirculationRingHost): Boolean {
        val dispose = host.lifecycle.disposeCount
        assertTrue(host.lifecycle.clearEffectsWhileSceneAlive())
        host.clear()
        assertEquals(dispose, host.lifecycle.disposeCount)
        assertEquals(0, host.lifecycle.attachedGroupCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        return true
    }

    private fun assertSafeGroupPath(count: Int, vm: EquipmentPlacementViewModel) {
        val lifecycle = ArPhysicsEffectLifecycle(30 + count)
        assertTrue(lifecycle.attach(30 + count))
        val frame = vm.circulationEffectFrame()
        assertEquals(count, frame.groups.size)
        assertTrue(lifecycle.bindGroups(frame.groups.size))
        assertTrue(lifecycle.clearEffectsWhileSceneAlive())
        assertEquals(0, lifecycle.attachedGroupCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, lifecycle.state)
        assertTrue(lifecycle.disposeBeforeHostDestruction())
        val sceneOps = lifecycle.sceneOperationCount
        lifecycle.disposeBeforeHostDestruction()
        assertEquals(sceneOps, lifecycle.sceneOperationCount)
        assertEquals(0, lifecycle.engineOperationCount)
    }

    private fun attachedHost(vm: EquipmentPlacementViewModel, engineIdentity: Int): ArCirculationRingHost {
        val host = ArCirculationRingHost(engineIdentity)
        assertTrue(host.lifecycle.attach(engineIdentity))
        host.bind(ArCirculationRingHost.request(vm.circulationEffectFrame(), ArTabletopPhase.PLACED))
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
