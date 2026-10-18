package com.greenhands.app.equipment

import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.DomainSceneCoordinateAdapter
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.domain.UserPlacementCoordinates
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.domain.PreviewCameraFramer
import com.greenhands.app.preview3d.domain.SceneScaleMapper
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import kotlin.math.hypot
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
class Phase3B2CorrectionsTest {
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

    private fun boundVm(): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg-fix", 10.0, 5.0, 3.0, 3, climate)
        vm.enterPlacementMode()
        return vm
    }

    @Test
    fun extremeZoomThenResetViewRestoresHomeFraming() {
        val layout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
        val aspect = PreviewCameraFramer.PORTRAIT_ASPECT
        val home = PreviewCameraFramer.frame(layout, aspect)
        val dir = PreviewCameraFramer.viewDirection(
            PreviewCameraFramer.AZIMUTH_DEG,
            PreviewCameraFramer.ELEVATION_DEG
        )
        val zoomed = home.copy(
            eyeX = home.targetX + dir.first * 0.04,
            eyeY = home.targetY + dir.second * 0.04,
            eyeZ = home.targetZ + dir.third * 0.04,
            distance = 0.04
        )
        val zoomShift = hypot(
            hypot(zoomed.eyeX - home.eyeX, zoomed.eyeY - home.eyeY),
            zoomed.eyeZ - home.eyeZ
        )
        assertTrue(zoomShift > 1.0)
        val reset = PreviewCameraFramer.frame(layout, aspect)
        assertEquals(home, reset)
        assertEquals(home.eyeX, reset.eyeX, 1e-12)
        assertEquals(home.eyeY, reset.eyeY, 1e-12)
        assertEquals(home.eyeZ, reset.eyeZ, 1e-12)
        assertEquals(PreviewCameraFramer.AZIMUTH_DEG, 40.0, 0.0)
        assertEquals(PreviewCameraFramer.ELEVATION_DEG, 26.0, 0.0)
        PreviewCameraFramer.visualBounds(layout).corners().forEach { (x, y, z) ->
            assertTrue(PreviewCameraFramer.fitsInPortraitFrame(x, y, z, reset))
        }
    }

    @Test
    fun resetViewDoesNotMoveEquipmentOrChangeSelection() = runTest {
        val vm = boundVm()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        vm.duplicateSelected()
        val selectedId = vm.state.value.instances.first().id
        vm.select(selectedId)
        val poses = vm.state.value.instances.associate { it.id to it.positionM }
        val epoch = vm.state.value.cameraResetEpoch
        vm.resetView()
        assertEquals(epoch + 1, vm.state.value.cameraResetEpoch)
        assertEquals(selectedId, vm.state.value.selectedId)
        assertEquals(2, vm.state.value.instances.size)
        vm.state.value.instances.forEach { inst ->
            assertEquals(poses.getValue(inst.id).xM, inst.positionM.xM, 0.0)
            assertEquals(poses.getValue(inst.id).yM, inst.positionM.yM, 0.0)
            assertEquals(poses.getValue(inst.id).zM, inst.positionM.zM, 0.0)
        }
        assertFalse(vm.state.value.showUndo)
        assertFalse(vm.state.value.statusMessage.orEmpty().contains("Deleted", ignoreCase = true))
    }

    @Test
    fun selectingDoesNotDeleteOrShowDeleteNotification() = runTest {
        val vm = boundVm()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        vm.duplicateSelected()
        val fan1 = vm.state.value.instances.single { it.displayNumber == 1 }
        val fan2 = vm.state.value.instances.single { it.displayNumber == 2 }
        vm.select(fan1.id)
        assertEquals(fan1.id, vm.state.value.selectedId)
        assertEquals(setOf(fan1.id, fan2.id), vm.state.value.instances.map { it.id }.toSet())
        assertFalse(vm.state.value.showUndo)
        assertFalse(vm.state.value.showDeleteConfirm)
        assertFalse(vm.state.value.statusMessage.orEmpty().contains("Deleted", ignoreCase = true))
        vm.deleteSelected()
        assertEquals(2, vm.state.value.instances.size)
        vm.select(fan2.id)
        assertEquals(fan2.id, vm.state.value.selectedId)
        assertEquals(2, vm.state.value.instances.size)
        assertEquals("Circulation Fan 2", vm.state.value.selected?.displayLabel())
        vm.requestDelete()
        vm.select(fan1.id)
        assertFalse(vm.state.value.showDeleteConfirm)
        vm.deleteSelected()
        assertEquals(2, vm.state.value.instances.size)
        assertTrue(vm.state.value.instances.any { it.id == fan1.id })
        assertTrue(vm.state.value.instances.any { it.id == fan2.id })
    }

    @Test
    fun confirmedDeleteRemovesOnlySelectedId() = runTest {
        val vm = boundVm()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        vm.duplicateSelected()
        val fan1 = vm.state.value.instances.single { it.displayNumber == 1 }
        val fan2 = vm.state.value.instances.single { it.displayNumber == 2 }
        val fan2Pose = fan2.positionM
        vm.select(fan1.id)
        vm.requestDelete()
        assertEquals("Circulation Fan 1", vm.state.value.pendingDeleteLabel)
        vm.deleteSelected()
        assertEquals(listOf(fan2.id), vm.state.value.instances.map { it.id })
        assertEquals(fan2Pose, vm.state.value.instances.single().positionM)
        assertFalse(vm.state.value.showUndo)
        assertFalse(vm.state.value.statusMessage.orEmpty().contains("Deleted", ignoreCase = true))
    }

    @Test
    fun userXBoundedByWidthYByLengthZByHeight() {
        val width = 5.0
        val length = 10.0
        val height = 3.0
        assertEquals("X — Width position", UserPlacementCoordinates.LABEL_X)
        assertEquals("Y — Length position", UserPlacementCoordinates.LABEL_Y)
        assertEquals("Z — Height position", UserPlacementCoordinates.LABEL_Z)
        assertEquals(0.0..width, UserPlacementCoordinates.xRangeM(width))
        assertEquals(0.0..length, UserPlacementCoordinates.yRangeM(length))
        assertEquals(0.0..height, UserPlacementCoordinates.zRangeM(height))
        assertTrue(UserPlacementCoordinates.inBounds(DomainPoint(0.0, 0.0, 0.0), width, length, height))
        assertTrue(UserPlacementCoordinates.inBounds(DomainPoint(width, length, height), width, length, height))
        assertFalse(UserPlacementCoordinates.inBounds(DomainPoint(width + 0.01, 1.0, 1.0), width, length, height))
        assertFalse(UserPlacementCoordinates.inBounds(DomainPoint(1.0, length + 0.01, 1.0), width, length, height))
        assertFalse(UserPlacementCoordinates.inBounds(DomainPoint(1.0, 1.0, height + 0.01), width, length, height))
        val context = PlacementContext(length, width, height, 3, 0.78)
        assertFalse(
            PlacementValidator.validate(
                EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "x", DomainPoint(6.0, 2.0, 1.6), 90.0),
                emptyList(),
                context
            ).isValid
        )
        assertFalse(
            PlacementValidator.validate(
                EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "y", DomainPoint(2.0, 11.0, 1.6), 90.0),
                emptyList(),
                context
            ).isValid
        )
        assertFalse(
            PlacementValidator.validate(
                EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "z", DomainPoint(2.0, 5.0, 4.0), 90.0),
                emptyList(),
                context
            ).isValid
        )
    }

    @Test
    fun userSceneCoordinatesRoundTripIncludingWideHouse() {
        val layouts = listOf(
            SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions()),
            SceneScaleMapper.layout(GreenhouseDimensions(50.0, 10.0, 5.0, ProvenanceClass.MEASURED_INPUT, "long")),
            SceneScaleMapper.layout(GreenhouseDimensions(4.0, 12.0, 3.0, ProvenanceClass.MEASURED_INPUT, "wide"))
        )
        layouts.forEach { layout ->
            val points = listOf(
                DomainPoint(0.0, 0.0, 0.0),
                DomainPoint(layout.dimensions.widthM, layout.dimensions.lengthM, layout.dimensions.heightM),
                DomainPoint(layout.dimensions.widthM * 0.25, layout.dimensions.lengthM * 0.8, 1.1)
            )
            points.forEach { point ->
                val back = DomainSceneCoordinateAdapter.toDomain(
                    DomainSceneCoordinateAdapter.toScene(point, layout),
                    layout
                )
                assertEquals(point.xM, back.xM, 1e-9)
                assertEquals(point.yM, back.yM, 1e-9)
                assertEquals(point.zM, back.zM, 1e-9)
            }
            val yaw = 37.5
            assertEquals(
                yaw,
                DomainSceneCoordinateAdapter.toDomainYawDeg(
                    DomainSceneCoordinateAdapter.toSceneYawDeg(yaw, layout),
                    layout
                ),
                1e-9
            )
        }
        val demo = layouts.first()
        val widthScene = DomainSceneCoordinateAdapter.toScene(DomainPoint(5.0, 0.0, 1.5), demo)
        val lengthScene = DomainSceneCoordinateAdapter.toScene(DomainPoint(0.0, 10.0, 1.5), demo)
        assertEquals(2.5, widthScene.z, 1e-9)
        assertEquals(5.0, lengthScene.x, 1e-9)
        assertEquals(1.5, widthScene.y, 1e-9)
    }

    @Test
    fun oldSavedLengthXWidthYMigratesToWidthXLengthY() {
        val legacyInst = "old-fan,CIRCULATION_FAN,8.0,1.2,1.6,0.0,3b2.1,SUSPENDED_OR_BAY,NOT_RUNNING,0,true,"
        val legacy = "v=1|layoutId=old|configId=cfg|userId=u|len=10.0|wid=5.0|hgt=3.0|rows=3|inst=$legacyInst"
        val decoded = EquipmentLayoutCodec.decode(legacy)!!
        assertEquals(UserPlacementCoordinates.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(EquipmentLayout.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1.2, decoded.instances.single().positionM.xM, 0.0)
        assertEquals(8.0, decoded.instances.single().positionM.yM, 0.0)
        assertEquals(1.6, decoded.instances.single().positionM.zM, 0.0)
        assertEquals(90.0, decoded.instances.single().yawDeg, 0.0)
        val reencoded = EquipmentLayoutCodec.encode(decoded)
        assertTrue(reencoded.startsWith("v=2|"))
        val roundTrip = EquipmentLayoutCodec.decode(reencoded)!!
        assertEquals(1.2, roundTrip.instances.single().positionM.xM, 0.0)
        assertEquals(8.0, roundTrip.instances.single().positionM.yM, 0.0)
        assertEquals(90.0, roundTrip.instances.single().yawDeg, 0.0)
        val (migrated, yaw) = UserPlacementCoordinates.migrateFromLengthXWidthY(DomainPoint(8.0, 1.2, 1.6), 0.0)
        assertEquals(1.2, migrated.xM, 0.0)
        assertEquals(8.0, migrated.yM, 0.0)
        assertEquals(90.0, yaw, 0.0)
    }

    @Test
    fun suggestionsUseWidthXAndLengthY() {
        val context = PlacementContext(10.0, 5.0, 3.0, 3, 0.78)
        val suggested = PlacementSuggester.suggest(context, 4, 2, 3)
        suggested.forEach { inst ->
            assertTrue(inst.positionM.xM in 0.0..context.widthM)
            assertTrue(inst.positionM.yM in 0.0..context.lengthM)
            assertTrue(inst.positionM.zM in 0.0..context.heightM)
        }
        val fans = suggested.filter { it.type == EquipmentType.CIRCULATION_FAN }
        assertTrue(fans.map { it.positionM.xM }.toSet().size >= 2)
        assertTrue(fans.map { it.positionM.yM }.distinct().size > 1)
        fans.forEach { assertTrue(it.yawDeg == 90.0 || it.yawDeg == 270.0) }
        val exhaust = suggested.filter { it.type == EquipmentType.EXHAUST_FAN }
        exhaust.forEach {
            assertTrue(it.positionM.yM == 0.0 || it.positionM.yM == context.lengthM)
            assertTrue(it.positionM.xM in 0.0..context.widthM)
            assertTrue(it.yawDeg == 90.0 || it.yawDeg == 270.0)
        }
        val foggers = suggested.filter { it.type == EquipmentType.FOGGER }
        foggers.forEach {
            assertTrue(it.positionM.xM in 0.0..context.widthM)
            assertTrue(it.positionM.yM in 0.0..context.lengthM)
            assertTrue(it.positionM.zM > context.canopyHeightM)
        }
    }
}
