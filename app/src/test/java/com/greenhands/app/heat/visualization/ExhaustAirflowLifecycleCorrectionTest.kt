package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArEffectHostState
import com.greenhands.app.ar.render.ArExhaustAirflowHost
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
class ExhaustAirflowLifecycleCorrectionTest {

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
        assertTrue("exhaustFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertTrue("circulationFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertTrue("padFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS.forEach { key ->
            assertFalse(key in ArLiveRendererOwnership.REMEMBER_KEYS)
        }
        assertFalse(ArLiveRendererOwnership.chromeDisposesRenderer())
    }

    @Test
    fun selectionDoesNotDisposeOrRecreateTheRenderer() = runTest {
        val vm = demoVm("cfg-exh-life-select")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 41)
        val dispose = host.lifecycle.disposeCount
        val recreate = host.lifecycle.rendererRecreateCount
        val epoch = host.resourceEpoch
        val ids = vm.state.value.instances.map { it.id }
        vm.select(ids[0])
        host.noteRecomposition()
        host.lifecycle.noteUiChromeChanged()
        host.bind(ArExhaustAirflowHost.request(vm.exhaustEffectFrame(), ArTabletopPhase.PLACED))
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
        val vm = demoVm("cfg-exh-life-labels")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 42)
        val dispose = host.lifecycle.disposeCount
        vm.toggleLabelsVisible()
        vm.setSelectedTool(SelectedEquipmentTool.EXACT)
        vm.setSelectedTool(SelectedEquipmentTool.MOVE)
        host.uiChromeChanged()
        host.bind(ArExhaustAirflowHost.request(vm.exhaustEffectFrame(), ArTabletopPhase.PLACED))
        assertEquals(dispose, host.lifecycle.disposeCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertFalse(vm.exhaustEffectFrame().isEmpty)
    }

    @Test
    fun hudAndSheetRecompositionDoesNotDisposeTheRenderer() {
        val lifecycle = ArPhysicsEffectLifecycle(43)
        assertTrue(lifecycle.attach(43))
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
        val vm = demoVm("cfg-exh-life-stop")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 44)
        assertEquals(1, host.attachedUuids.size)
        vm.stopBehavior()
        assertTrue(vm.exhaustEffectFrame().isEmpty)
        assertTrue(lifecycleClearKeepsHost(host))
        assertEquals(1, vm.state.value.instances.size)
        assertTrue(vm.state.value.displayedTemperatureC != null)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertEquals(0, host.lifecycle.engineOperationCount)
    }

    @Test
    fun cleanupIsIdempotentAndDoesNotDestroyAResourceTwice() {
        val lifecycle = ArPhysicsEffectLifecycle(45)
        assertTrue(lifecycle.attach(45))
        assertTrue(lifecycle.registerOwnedResource("exh-mesh-a", 45))
        assertTrue(lifecycle.destroyOwnedResource("exh-mesh-a"))
        assertFalse(lifecycle.destroyOwnedResource("exh-mesh-a"))
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
        val lifecycle = ArPhysicsEffectLifecycle(46)
        assertTrue(lifecycle.attach(46))
        assertTrue(lifecycle.bindGroups(2))
        assertTrue(lifecycle.registerOwnedResource("exh-vb-old", 46))
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
        val lifecycle = ArPhysicsEffectLifecycle(47)
        assertTrue(lifecycle.attach(47))
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
        val vm = demoVm("cfg-exh-life-reposition")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = attachedHost(vm, engineIdentity = 48)
        vm.onArReposition()
        val order = host.lifecycle.repositionSequence(visualizationStillRunning = vm.state.value.behaviorRunning)
        assertEquals(ArPhysicsEffectCleanup.REPOSITION, order)
        assertTrue(
            order.indexOf(ArPhysicsEffectCleanup.DETACH_NODES) <
                order.indexOf(ArPhysicsEffectCleanup.REPLACE_ANCHOR)
        )
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        assertTrue(vm.state.value.behaviorRunning)
        assertFalse(vm.exhaustEffectFrame().isEmpty)
    }

    @Test
    fun aNewEngineIdentityNeverReceivesOldEngineBoundResources() {
        val oldHost = ArPhysicsEffectLifecycle(51)
        assertTrue(oldHost.attach(51))
        assertTrue(oldHost.registerOwnedResource("exh-geometry-1", 51))
        val newHost = ArPhysicsEffectLifecycle(52)
        assertTrue(newHost.attach(52))
        assertFalse(newHost.acceptEngineBoundResource(51, "exh-geometry-1"))
        assertFalse(newHost.registerOwnedResource("exh-geometry-1", 51))
        assertTrue(newHost.acceptEngineBoundResource(52, "exh-geometry-2"))
        assertFalse(oldHost.attach(52))
        oldHost.disposeBeforeHostDestruction()
        assertFalse(newHost.acceptEngineBoundResource(51, "exh-geometry-1"))
        assertEquals(0, newHost.engineOperationCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, newHost.state)
    }

    @Test
    fun offRemovesOnlyThatUuidGroup() = runTest {
        val vm = demoVm("cfg-exh-life-off")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val ids = vm.state.value.instances.map { it.id }
        assertEquals(2, vm.exhaustEffectFrame().groups.size)
        vm.select(ids[0])
        vm.setSimulatedTemperature(26.5)
        ids.forEach { id ->
            assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[id])
            assertEquals(null, vm.exhaustEffectFrame().group(id))
        }
        assertTrue(vm.exhaustEffectFrame().isEmpty)
    }

    private fun lifecycleClearKeepsHost(host: ArExhaustAirflowHost): Boolean {
        val dispose = host.lifecycle.disposeCount
        assertTrue(host.lifecycle.clearEffectsWhileSceneAlive())
        host.clear()
        assertEquals(dispose, host.lifecycle.disposeCount)
        assertEquals(0, host.lifecycle.attachedGroupCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, host.lifecycle.state)
        return true
    }

    private fun attachedHost(vm: EquipmentPlacementViewModel, engineIdentity: Int): ArExhaustAirflowHost {
        val host = ArExhaustAirflowHost(engineIdentity)
        assertTrue(host.lifecycle.attach(engineIdentity))
        host.bind(ArExhaustAirflowHost.request(vm.exhaustEffectFrame(), ArTabletopPhase.PLACED))
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
