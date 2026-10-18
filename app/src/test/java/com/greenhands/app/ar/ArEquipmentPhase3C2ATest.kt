package com.greenhands.app.ar

import com.greenhands.app.ar.domain.ArCoordinateMapper
import com.greenhands.app.ar.domain.ArCoreAvailabilityKind
import com.greenhands.app.ar.domain.ArEquipmentGestures
import com.greenhands.app.ar.domain.ArGestureMode
import com.greenhands.app.ar.domain.ArHeaderCopy
import com.greenhands.app.ar.domain.ArLayoutContents
import com.greenhands.app.ar.domain.ArOverlayLayout
import com.greenhands.app.ar.domain.ArPlacementAdapter
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.ar.domain.ArTabletopSession
import com.greenhands.app.ar.ui.ArTabletopViewModel
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.ArPlacementAdapterContract
import com.greenhands.app.equipment.domain.CompactLiveCoordinates
import com.greenhands.app.equipment.domain.InstanceNumbering
import com.greenhands.app.equipment.domain.MovementPlanes
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArWorldToGreenhouseCoordinateTest {

    @Test
    fun worldToLocalAccountsForTabletopScaleAndYaw() {
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        val domain = DomainPoint(1.20, 4.50, 2.10)
        val scene = ArCoordinateMapper.toArScene(domain, layout)
        val origin = ArPlacementAdapter.Vec3(0.42, 0.01, -0.18)
        val yaw = 35.0
        val scale = ArTabletopScale.worldScale(1.6)
        val world = ArPlacementAdapter.sceneToWorld(scene, origin, yaw, scale)
        val backScene = ArPlacementAdapter.worldToScene(world, origin, yaw, scale)
        assertEquals(scene.x, backScene.x, 1e-9)
        assertEquals(scene.y, backScene.y, 1e-9)
        assertEquals(scene.z, backScene.z, 1e-9)
        val backDomain = ArPlacementAdapter.worldToDomain(world, origin, yaw, scale, layout)
        assertEquals(1.20, backDomain.xM, 1e-9)
        assertEquals(4.50, backDomain.yM, 1e-9)
        assertEquals(2.10, backDomain.zM, 1e-9)
        val unscaled = ArPlacementAdapter.sceneToWorld(scene, origin, 0.0, ArTabletopScale.rootScale())
        val scaled = ArPlacementAdapter.sceneToWorld(scene, origin, 0.0, scale)
        assertEquals(
            (scaled.x - origin.x) / (unscaled.x - origin.x),
            1.6,
            1e-9
        )
        val yaw0 = ArPlacementAdapter.sceneToWorld(scene, origin, 0.0, scale)
        val yaw90 = ArPlacementAdapter.sceneToWorld(scene, origin, 90.0, scale)
        assertEquals(origin.y + (scene.y * scale), yaw90.y, 1e-9)
        assertNotEquals(yaw0.x, yaw90.x, 1e-6)
        assertNotEquals(yaw0.z, yaw90.z, 1e-6)
    }

    @Test
    fun worldRayToSceneThenDomainRoundTripsUnderRotation() {
        val layout = ArTabletopScale.layout(
            GreenhouseDimensions(4.0, 12.0, 3.0, ProvenanceClass.MEASURED_INPUT, "wide"),
            2
        )
        assertFalse(layout.lengthMappedToX)
        val domain = DomainPoint(3.25, 1.10, 1.80)
        val origin = ArPlacementAdapter.Vec3(-0.2, 0.0, 0.5)
        val yaw = -48.0
        val scale = ArTabletopScale.worldScale(0.8)
        val world = ArPlacementAdapter.domainToWorld(domain, origin, yaw, scale, layout)
        val ray = ArPlacementAdapter.worldRayToScene(
            originWorld = ArPlacementAdapter.Vec3(world.x, world.y + 2.0, world.z),
            directionWorld = ArPlacementAdapter.Vec3(0.0, -1.0, 0.0),
            rootOrigin = origin,
            yawDeg = yaw,
            uniformScale = scale
        )
        val hit = ArPlacementAdapter.hitDomain(
            originScene = ray.first,
            directionScene = ray.second,
            type = EquipmentType.FOGGER,
            current = domain,
            layout = layout,
            lockFarWall = true
        )
        assertTrue(hit != null)
        assertEquals(domain.xM, hit!!.xM, 1e-6)
        assertEquals(domain.yM, hit.yM, 1e-6)
    }
}

class ArDragClampAndCollisionTest {
    private fun ctx(): PlacementContext {
        val height = 3.0
        return PlacementContext(10.0, 5.0, height, 3, height * CropRowGeometry.HEIGHT_FILL)
    }

    @Test
    fun dragClampsMountingAndRejectsOverlap() {
        val context = ctx()
        val fan = PlacementSuggester.suggest(context, 1, 0, 0).single()
        val outside = DomainPoint(-2.0, 40.0, 12.0)
        val moved = ArPlacementAdapter.applyDragHit(fan, outside, context, lockFarWall = true)
        assertEquals(fan.positionM.zM, moved.zM, 1e-9)
        val after = PlacementValidator.clampElevation(fan.copy(positionM = moved), context)
        val range = PlacementValidator.elevationRange(fan.type, context, fan.dimensions)
        assertTrue(after.positionM.zM in range)
        val peer = fan.copy(id = "peer", displayNumber = 2)
        val overlapping = ArPlacementAdapter.applyDragHit(peer, fan.positionM, context, true)
        val trial = peer.copy(positionM = overlapping)
        assertFalse(PlacementValidator.validate(trial, listOf(fan), context).isValid)
        assertTrue(PlacementValidator.validate(fan, emptyList(), context).isValid)
    }

    @Test
    fun exhaustYStaysLockedToEndWallWhileXAndZMove() {
        val context = ctx()
        val exhaust = PlacementValidator.snapExhaust(
            EquipmentAssetCatalog.newInstance(
                EquipmentType.EXHAUST_FAN,
                "ex-1",
                DomainPoint(2.5, 10.0, 1.6),
                90.0,
                displayNumber = 1
            ),
            context
        )
        assertTrue(ArPlacementAdapter.exhaustYLocked(exhaust, context))
        val hit = DomainPoint(1.4, 4.2, 1.85)
        val moved = ArPlacementAdapter.applyDragHit(exhaust, hit, context, lockFarWall = true)
        assertEquals(context.lengthM, moved.yM, 1e-9)
        assertEquals(1.4, moved.xM, 1e-9)
        assertEquals(1.85, moved.zM, 1e-6)
        val plane = MovementPlanes.applyHit(exhaust.type, exhaust.positionM, hit, context, true)
        assertEquals(context.lengthM, plane.yM, 0.0)
        assertEquals(1.4, plane.xM, 0.0)
        assertEquals(1.85, plane.zM, 0.0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArStableNumberingAndPersistenceTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val climate = OverlayClimate(null, null, "—", "—", "Not published", null, null, null)

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun enteringArDoesNotAddEquipment() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        vm.attach("grower@greenhands.app", "cfg-ar", 10.0, 5.0, 3.0, 3, climate, PreviewMode.DEMO)
        assertTrue(vm.state.value.instances.isEmpty())
        assertTrue(ArLayoutContents.visibleEquipment(vm.state.value.instances).isEmpty())
        assertEquals(ArPlacementAdapterContract.STATUS, "Phase 3C.2B research-guided suggestions and Apply Layout")
        assertFalse(ArPlacementAdapterContract.REQUIRES_LATER.contains("equipment placement inside AR"))
        assertFalse(ArPlacementAdapterContract.REQUIRES_LATER.contains("Apply Layout"))
    }

    @Test
    fun deletingFan1DoesNotRenameFan2AndRequiresExplicitDelete() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg-del-ar", 10.0, 5.0, 3.0, 3, climate)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        vm.duplicateSelected()
        val fan1 = vm.state.value.instances.single { it.displayNumber == 1 }
        val fan2 = vm.state.value.instances.single { it.displayNumber == 2 }
        vm.select(fan1.id)
        assertEquals(2, vm.state.value.instances.size)
        assertEquals("Circulation Fan 2", fan2.displayLabel())
        vm.requestDelete()
        assertTrue(vm.state.value.showDeleteConfirm)
        assertEquals(2, vm.state.value.instances.size)
        vm.deleteSelected()
        val remaining = vm.state.value.instances.single()
        assertEquals(fan2.id, remaining.id)
        assertEquals("Circulation Fan 2", remaining.displayLabel())
        assertEquals(2, remaining.displayNumber)
        assertEquals(1, InstanceNumbering.nextNumber(EquipmentType.CIRCULATION_FAN, vm.state.value.instances))
    }

    @Test
    fun layoutSavedIn3dReloadsInArAndBack() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val preview = EquipmentPlacementViewModel(repo)
        preview.attach("grower@greenhands.app", "cfg-round", 10.0, 5.0, 3.0, 3, climate)
        preview.enterPlacementMode()
        preview.tapPalette(EquipmentType.CIRCULATION_FAN)
        preview.confirmGhost()
        preview.tapPalette(EquipmentType.EXHAUST_FAN)
        preview.confirmGhost()
        val original = preview.state.value.instances
        assertEquals(2, original.size)
        preview.saveLayout()
        val ar = EquipmentPlacementViewModel(repo)
        ar.attach("grower@greenhands.app", "cfg-round", 10.0, 5.0, 3.0, 3, climate)
        assertEquals(original.map { it.id }, ar.state.value.instances.map { it.id })
        assertEquals(original.map { it.displayLabel() }, ar.state.value.instances.map { it.displayLabel() })
        original.zip(ar.state.value.instances).forEach { (a, b) ->
            assertEquals(a.positionM.xM, b.positionM.xM, 0.0)
            assertEquals(a.positionM.yM, b.positionM.yM, 0.0)
            assertEquals(a.positionM.zM, b.positionM.zM, 0.0)
            assertEquals(a.yawDeg, b.yawDeg, 0.0)
        }
        val moving = ar.state.value.instances.first { it.type == EquipmentType.CIRCULATION_FAN }
        ar.select(moving.id)
        val nextX = moving.positionM.xM + 0.15
        ar.setAxisX(nextX)
        ar.saveLayout()
        val previewAgain = EquipmentPlacementViewModel(repo)
        previewAgain.attach("grower@greenhands.app", "cfg-round", 10.0, 5.0, 3.0, 3, climate)
        val restored = previewAgain.state.value.instances.first { it.id == moving.id }
        assertEquals(ar.state.value.instances.first { it.id == moving.id }.positionM.xM, restored.positionM.xM, 1e-9)
        assertEquals(moving.displayLabel(), restored.displayLabel())
        val exhaust = previewAgain.state.value.instances.first { it.type == EquipmentType.EXHAUST_FAN }
        assertEquals(10.0, exhaust.positionM.yM, 1e-9)
    }
}

class ArGestureModeStateTest {

    @Test
    fun compactModeSeparatesEquipmentDragFromGreenhousePinch() {
        val vm = ArTabletopViewModel()
        vm.onPermission(true)
        vm.onAvailability(ArCoreAvailabilityKind.SUPPORTED_INSTALLED)
        vm.setHasValidPlacementHit(true)
        vm.place()
        assertEquals(ArTabletopPhase.PLACED, vm.state.value.phase)
        assertEquals(ArGestureMode.TABLETOP, vm.state.value.gestureMode)
        assertFalse(
            ArEquipmentGestures.oneFingerMovesEquipment(
                vm.state.value.phase, vm.state.value.gestureMode, selectedId = "fan-1"
            )
        )
        assertTrue(ArEquipmentGestures.pinchScalesGreenhouse(vm.state.value.phase, pointerCount = 2))
        assertTrue(ArEquipmentGestures.twoFingerRotatesGreenhouse(vm.state.value.phase, pointerCount = 2))
        vm.setGestureMode(ArGestureMode.EQUIPMENT_MOVE)
        assertTrue(
            ArEquipmentGestures.oneFingerMovesEquipment(
                vm.state.value.phase, vm.state.value.gestureMode, selectedId = "fan-1"
            )
        )
        assertTrue(ArEquipmentGestures.pinchScalesGreenhouse(ArTabletopPhase.PLACED, 2))
        assertFalse(ArEquipmentGestures.oneFingerMovesEquipment(ArTabletopPhase.DETECTING, ArGestureMode.EQUIPMENT_MOVE, "fan-1"))
        vm.reposition()
        assertEquals(ArGestureMode.TABLETOP, vm.state.value.gestureMode)
        assertEquals(ArTabletopPhase.DETECTING, vm.state.value.phase)
        val selected = ArTabletopSession.setGestureMode(
            vm.state.value.copy(phase = ArTabletopPhase.PLACED),
            ArEquipmentGestures.afterSelect()
        )
        assertEquals(ArGestureMode.EQUIPMENT_MOVE, selected.gestureMode)
        val live = CompactLiveCoordinates.xyz(1.20, 4.50, 2.10)
        assertEquals("X 1.20 m · Y 4.50 m · Z 2.10 m", live)
    }
}

class ArCompactPortraitHeaderTest {

    @Test
    fun compactPortraitHeaderKeepsUnitOnTheSameLine() {
        val dims = DemoGreenhouseProfile.dimensions()
        val line = ArHeaderCopy.dimensionsLine(dims)
        assertEquals("10.0${ArHeaderCopy.NBSP}m × 5.0${ArHeaderCopy.NBSP}m × 3.0${ArHeaderCopy.NBSP}m", line)
        assertFalse(ArHeaderCopy.unitCanWrapAlone(line))
        val spaced = line.replace(ArHeaderCopy.NBSP, ' ')
        assertTrue(ArHeaderCopy.unitCanWrapAlone(spaced))
        val subtitle = ArHeaderCopy.compactSubtitle(PreviewMode.DEMO, dims, 3)
        assertTrue(subtitle.startsWith("Demo · "))
        assertTrue(subtitle.contains(line))
        assertFalse(subtitle.contains('\n'))
        val slots = ArOverlayLayout.compactPortrait(360f, 800f, placed = true)
        assertTrue(slots.overlappingPairs().isEmpty())
        assertEquals(ArOverlayLayout.HEADER_PORTRAIT_DP, slots.header.height, 0.01f)
        assertTrue(slots.viewport.height >= ArOverlayLayout.MIN_VIEWPORT_DP)
        assertTrue(slots.viewport.height > slots.header.height)
        assertTrue(slots.viewport.height > slots.chrome.height)
        assertTrue(slots.viewport.height > slots.palette.height)
        val labels = ArOverlayLayout.labelViewport(slots.viewport, selectedVisible = true)
        val bar = ArOverlayLayout.selectedBar(slots.viewport)
        assertFalse(labels.overlaps(bar))
        assertTrue(bar.bottom <= slots.viewport.bottom)
        assertTrue(slots.suggestions.area() > 0f)
        assertTrue(slots.suggestionConflicts().isEmpty())
        assertFalse(bar.overlaps(slots.suggestions))
        assertTrue(slots.suggestions.top >= slots.header.bottom)
        assertTrue(slots.suggestions.bottom <= slots.chrome.top + 0.01f)
    }
}
