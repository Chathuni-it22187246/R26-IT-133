package com.greenhands.app.ar

import com.greenhands.app.ar.domain.ArAvailabilityGate
import com.greenhands.app.ar.domain.ArCoordinateMapper
import com.greenhands.app.ar.domain.ArCoreAvailabilityKind
import com.greenhands.app.ar.domain.ArGateStatus
import com.greenhands.app.ar.domain.ArGreenhouseVisuals
import com.greenhands.app.ar.domain.ArInstallOutcome
import com.greenhands.app.ar.domain.ArLayoutContents
import com.greenhands.app.ar.domain.ArSessionFailure
import com.greenhands.app.ar.domain.ArTabletopGestures
import com.greenhands.app.ar.domain.ArTabletopLighting
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.ar.domain.ArTabletopSession
import com.greenhands.app.ar.domain.ArTabletopUiState
import com.greenhands.app.ar.domain.CameraPermissionState
import com.greenhands.app.preview3d.domain.GreenhouseRenderParts
import com.greenhands.app.preview3d.domain.GreenhouseSurfaceLooks
import com.greenhands.app.preview3d.domain.MeshKind
import com.greenhands.app.preview3d.domain.SceneScaleMapper
import kotlin.math.min
import com.greenhands.app.ar.ui.ArTabletopViewModel
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.LayoutOrigin
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArAvailabilityAndPermissionTest {

    @Test
    fun cameraDeniedBlocksSessionAndOffers3dFallback() {
        val status = ArAvailabilityGate.resolve(
            permission = CameraPermissionState.DENIED,
            availability = ArCoreAvailabilityKind.SUPPORTED_INSTALLED
        )
        assertEquals(ArGateStatus.PERMISSION_DENIED, status)
        assertFalse(ArAvailabilityGate.canOpenLiveSession(status))
        assertTrue(ArAvailabilityGate.offersContinueIn3d(status))
    }

    @Test
    fun permissionUnknownDoesNotOpenSession() {
        val status = ArAvailabilityGate.resolve(
            permission = CameraPermissionState.UNKNOWN,
            availability = ArCoreAvailabilityKind.SUPPORTED_INSTALLED
        )
        assertEquals(ArGateStatus.CHECKING, status)
        assertFalse(ArAvailabilityGate.canOpenLiveSession(status))
        assertFalse(ArAvailabilityGate.offersContinueIn3d(status))
    }

    @Test
    fun grantedAndInstalledOpensLiveSession() {
        val status = ArAvailabilityGate.resolve(
            permission = CameraPermissionState.GRANTED,
            availability = ArCoreAvailabilityKind.SUPPORTED_INSTALLED
        )
        assertEquals(ArGateStatus.READY, status)
        assertTrue(ArAvailabilityGate.canOpenLiveSession(status))
        assertFalse(ArAvailabilityGate.offersContinueIn3d(status))
    }

    @Test
    fun missingOrStaleArServicesAreHandled() {
        assertEquals(
            ArGateStatus.NEEDS_INSTALL,
            ArAvailabilityGate.resolve(
                CameraPermissionState.GRANTED,
                ArCoreAvailabilityKind.SUPPORTED_NOT_INSTALLED
            )
        )
        assertEquals(
            ArGateStatus.NEEDS_UPDATE,
            ArAvailabilityGate.resolve(
                CameraPermissionState.GRANTED,
                ArCoreAvailabilityKind.SUPPORTED_APK_TOO_OLD
            )
        )
        assertTrue(
            ArAvailabilityGate.offersContinueIn3d(
                ArAvailabilityGate.resolve(
                    CameraPermissionState.GRANTED,
                    ArCoreAvailabilityKind.SUPPORTED_NOT_INSTALLED
                )
            )
        )
    }

    @Test
    fun installDeclinedAndTransientErrorsFallBackTo3d() {
        assertEquals(
            ArGateStatus.INSTALL_DECLINED,
            ArAvailabilityGate.resolve(
                CameraPermissionState.GRANTED,
                ArCoreAvailabilityKind.SUPPORTED_NOT_INSTALLED,
                install = ArInstallOutcome.DECLINED
            )
        )
        assertEquals(
            ArGateStatus.TRANSIENT_ERROR,
            ArAvailabilityGate.resolve(
                CameraPermissionState.GRANTED,
                ArCoreAvailabilityKind.UNKNOWN_ERROR
            )
        )
        assertTrue(ArAvailabilityGate.isTransient(ArCoreAvailabilityKind.UNKNOWN_CHECKING))
        assertTrue(ArAvailabilityGate.isTransient(ArCoreAvailabilityKind.UNKNOWN_TIMED_OUT))
    }

    @Test
    fun viewModelMapsPermissionAndAvailability() {
        val vm = ArTabletopViewModel()
        assertEquals(ArGateStatus.CHECKING, vm.state.value.gate)
        assertEquals(ArTabletopPhase.GATE, vm.state.value.phase)
        vm.onPermission(false)
        assertEquals(ArGateStatus.PERMISSION_DENIED, vm.state.value.gate)
        assertEquals(ArTabletopPhase.GATE, vm.state.value.phase)
        vm.onPermission(true)
        vm.onAvailability(ArCoreAvailabilityKind.SUPPORTED_INSTALLED)
        assertEquals(ArGateStatus.READY, vm.state.value.gate)
        assertEquals(ArTabletopPhase.DETECTING, vm.state.value.phase)
        assertTrue(vm.state.value.canOpenLiveSession)
    }
}

class ArUnsupportedDeviceFallbackTest {

    @Test
    fun unsupportedDeviceCannotOpenArAndOffers3d() {
        val status = ArAvailabilityGate.resolve(
            permission = CameraPermissionState.GRANTED,
            availability = ArCoreAvailabilityKind.UNSUPPORTED_DEVICE_NOT_CAPABLE
        )
        assertEquals(ArGateStatus.UNSUPPORTED, status)
        assertTrue(ArAvailabilityGate.isUnsupportedFallback(status))
        assertTrue(ArAvailabilityGate.offersContinueIn3d(status))
        assertFalse(ArAvailabilityGate.canOpenLiveSession(status))
        val vm = ArTabletopViewModel()
        vm.onPermission(true)
        vm.onAvailability(ArCoreAvailabilityKind.UNSUPPORTED_DEVICE_NOT_CAPABLE)
        assertEquals(ArGateStatus.UNSUPPORTED, vm.state.value.gate)
        assertTrue(vm.state.value.isUnsupportedFallback)
        assertEquals(ArTabletopPhase.GATE, vm.state.value.phase)
        vm.onSessionFailed(ArSessionFailure.UNSUPPORTED)
        assertEquals(ArGateStatus.UNSUPPORTED, vm.state.value.gate)
    }
}

class ArTabletopScaleTest {

    @Test
    fun demoGreenhouseFitsThirtyCentimetreTabletop() {
        val demo = DemoGreenhouseProfile.dimensions()
        assertEquals(0.03, ArTabletopScale.visualMetresPerDomainMetre(demo), 1e-12)
        assertEquals(0.30, ArTabletopScale.tabletopLengthM(demo), 1e-12)
        assertEquals(0.15, ArTabletopScale.tabletopWidthM(demo), 1e-12)
        assertEquals(0.09, ArTabletopScale.tabletopHeightM(demo), 1e-12)
        val layout = ArTabletopScale.layout(demo, DemoGreenhouseProfile.CROP_ROW_COUNT)
        val preview = SceneScaleMapper.layout(demo, cropRowCount = DemoGreenhouseProfile.CROP_ROW_COUNT)
        assertEquals(preview.uniformScale, layout.uniformScale, 0.0)
        assertEquals(preview.extentX, layout.extentX, 0.0)
        assertEquals(preview.extentY, layout.extentY, 0.0)
        assertEquals(preview.extentZ, layout.extentZ, 0.0)
        assertEquals(SceneScaleMapper.TARGET_LONG_SPAN, layout.targetLongSpan, 0.0)
        assertEquals(0.03, ArTabletopScale.rootScale(), 1e-12)
        assertEquals(0.30, layout.extentX * ArTabletopScale.rootScale(), 1e-12)
        assertEquals(0.09, layout.extentY * ArTabletopScale.rootScale(), 1e-12)
        assertEquals(0.15, layout.extentZ * ArTabletopScale.rootScale(), 1e-12)
        assertEquals(demo.lengthM, 10.0, 0.0)
        assertEquals(demo.widthM, 5.0, 0.0)
        assertEquals(demo.heightM, 3.0, 0.0)
    }

    @Test
    fun customDimensionsStayInMetresWhileVisualSpanIsTabletop() {
        val wide = GreenhouseDimensions(8.0, 20.0, 4.0, ProvenanceClass.MEASURED_INPUT, "custom")
        assertEquals(0.015, ArTabletopScale.visualMetresPerDomainMetre(wide), 1e-12)
        assertEquals(0.12, ArTabletopScale.tabletopLengthM(wide), 1e-12)
        assertEquals(0.30, ArTabletopScale.tabletopWidthM(wide), 1e-12)
        assertEquals(0.06, ArTabletopScale.tabletopHeightM(wide), 1e-12)
        val layout = ArTabletopScale.layout(wide, 4)
        assertEquals(20.0, layout.dimensions.widthM, 0.0)
        assertEquals(8.0, layout.dimensions.lengthM, 0.0)
        assertEquals(4.0, layout.dimensions.heightM, 0.0)
        assertEquals(SceneScaleMapper.TARGET_LONG_SPAN, layout.targetLongSpan, 0.0)
        assertFalse(layout.lengthMappedToX)
        assertEquals(0.30, layout.extentX * ArTabletopScale.rootScale(), 1e-12)
    }

    @Test
    fun tabletopScaleIsAppliedOnceOnTheRoot() {
        assertEquals(ArTabletopScale.rootScale(), ArTabletopScale.worldScale(1.0), 0.0)
        assertEquals(2.0 * ArTabletopScale.rootScale(), ArTabletopScale.worldScale(2.0), 1e-12)
        assertEquals(0.4 * ArTabletopScale.rootScale(), ArTabletopScale.worldScale(0.4), 1e-12)
    }
}

class ArDomainToArCoordinateTest {

    @Test
    fun demoMapsUserWidthLengthHeightOntoTabletopScene() {
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        assertTrue(layout.lengthMappedToX)
        val origin = ArCoordinateMapper.toArScene(DomainPoint(0.0, 0.0, 0.0), layout)
        assertEquals(-5.0, origin.x, 1e-12)
        assertEquals(0.0, origin.y, 1e-12)
        assertEquals(-2.5, origin.z, 1e-12)
        assertEquals(-0.15, origin.x * ArTabletopScale.rootScale(), 1e-12)
        assertEquals(-0.075, origin.z * ArTabletopScale.rootScale(), 1e-12)
        val roofCorner = ArCoordinateMapper.toArScene(DomainPoint(5.0, 10.0, 3.0), layout)
        assertEquals(5.0, roofCorner.x, 1e-12)
        assertEquals(3.0, roofCorner.y, 1e-12)
        assertEquals(2.5, roofCorner.z, 1e-12)
        assertEquals(0.15, roofCorner.x * ArTabletopScale.rootScale(), 1e-12)
        assertEquals(0.09, roofCorner.y * ArTabletopScale.rootScale(), 1e-12)
        assertEquals(0.075, roofCorner.z * ArTabletopScale.rootScale(), 1e-12)
        val centre = ArCoordinateMapper.toArScene(DomainPoint(2.5, 5.0, 0.0), layout)
        assertEquals(0.0, centre.x, 1e-12)
        assertEquals(0.0, centre.y, 1e-12)
        assertEquals(0.0, centre.z, 1e-12)
        assertEquals(ArCoordinateMapper.USER_X, "X — Width position")
        assertEquals(ArCoordinateMapper.USER_Y, "Y — Length position")
        assertEquals(ArCoordinateMapper.USER_Z, "Z — Height position")
    }

    @Test
    fun roundTripKeepsDomainMetresForDemoAndCustom() {
        val layouts = listOf(
            ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3),
            ArTabletopScale.layout(
                GreenhouseDimensions(50.0, 10.0, 5.0, ProvenanceClass.MEASURED_INPUT, "long"),
                5
            ),
            ArTabletopScale.layout(
                GreenhouseDimensions(4.0, 12.0, 3.0, ProvenanceClass.MEASURED_INPUT, "wide"),
                2
            )
        )
        layouts.forEach { layout ->
            val points = listOf(
                DomainPoint(0.0, 0.0, 0.0),
                DomainPoint(layout.dimensions.widthM, layout.dimensions.lengthM, layout.dimensions.heightM),
                DomainPoint(1.23, 0.45, 2.01)
            )
            points.forEach { point ->
                val back = ArCoordinateMapper.toDomain(ArCoordinateMapper.toArScene(point, layout), layout)
                assertEquals(point.xM, back.xM, 1e-9)
                assertEquals(point.yM, back.yM, 1e-9)
                assertEquals(point.zM, back.zM, 1e-9)
            }
        }
    }
}

class ArEquipmentLayoutContentsTest {

    @Test
    fun emptyLayoutDoesNotInventEquipment() {
        assertTrue(ArLayoutContents.visibleEquipment(emptyList()).isEmpty())
        assertTrue(ArLayoutContents.visibleEquipment(null).isEmpty())
        val empty = EquipmentLayout(
            layoutId = "lay",
            configurationId = "cfg",
            userId = "grower@greenhands.app",
            lengthM = 10.0,
            widthM = 5.0,
            heightM = 3.0,
            cropRowCount = 3,
            instances = emptyList(),
            origin = LayoutOrigin.MANUAL,
            explicitlySaved = true
        )
        assertTrue(ArLayoutContents.visibleEquipment(empty).isEmpty())
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        assertTrue(ArLayoutContents.placements(emptyList(), layout).isEmpty())
    }

    @Test
    fun savedLayoutKeepsIdsLabelsAndDomainMetres() {
        val fan = EquipmentAssetCatalog.newInstance(
            EquipmentType.CIRCULATION_FAN,
            "fan-1",
            DomainPoint(1.25, 4.5, 1.60),
            90.0,
            displayNumber = 1
        )
        val fogger = EquipmentAssetCatalog.newInstance(
            EquipmentType.FOGGER,
            "fog-2",
            DomainPoint(2.40, 6.10, 2.20),
            0.0,
            displayNumber = 1
        )
        val saved = EquipmentLayout(
            layoutId = "lay",
            configurationId = "cfg",
            userId = "grower@greenhands.app",
            lengthM = 10.0,
            widthM = 5.0,
            heightM = 3.0,
            cropRowCount = 3,
            instances = listOf(fan, fogger),
            origin = LayoutOrigin.MANUAL,
            explicitlySaved = true
        )
        val shown = ArLayoutContents.visibleEquipment(saved)
        assertEquals(2, shown.size)
        assertTrue(ArLayoutContents.preservesIdsAndCoordinates(listOf(fan, fogger), shown))
        assertEquals("fan-1", shown[0].id)
        assertEquals("Circulation Fan 1", shown[0].displayLabel())
        assertEquals(1.25, shown[0].positionM.xM, 0.0)
        assertEquals(4.5, shown[0].positionM.yM, 0.0)
        assertEquals(1.60, shown[0].positionM.zM, 0.0)
        assertEquals(90.0, shown[0].yawDeg, 0.0)
        val scene = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        val placements = ArLayoutContents.placements(shown, scene)
        assertEquals(listOf("fan-1", "fog-2"), placements.map { it.id })
        assertEquals(fan.positionM.xM, placements[0].instance.positionM.xM, 0.0)
    }
}

class ArResetRepositionTest {

    @Test
    fun resetViewRestoresScaleAndYawWithoutLeavingAnchor() {
        var state = readyDetecting()
        state = ArTabletopSession.place(state)
        state = ArTabletopSession.pinch(state, 2.0)
        state = ArTabletopSession.rotate(state, 45.0)
        assertEquals(ArTabletopPhase.PLACED, state.phase)
        assertEquals(2.0, state.visualScaleFactor, 1e-9)
        val reset = ArTabletopSession.resetView(state)
        assertEquals(ArTabletopPhase.PLACED, reset.phase)
        assertEquals(1.0, reset.visualScaleFactor, 0.0)
        assertEquals(0.0, reset.yawDeg, 0.0)
        assertEquals(0, reset.repositionEpoch)
    }

    @Test
    fun repositionReturnsToPlaneDetectionAndClearsSessionPose() {
        var state = readyDetecting()
        state = ArTabletopSession.setValidHit(state, true)
        state = ArTabletopSession.place(state)
        state = ArTabletopSession.pinch(state, 1.5)
        state = ArTabletopSession.rotate(state, -30.0)
        val moved = ArTabletopSession.reposition(state)
        assertEquals(ArTabletopPhase.DETECTING, moved.phase)
        assertFalse(moved.hasValidPlacementHit)
        assertEquals(1.0, moved.visualScaleFactor, 0.0)
        assertEquals(0.0, moved.yawDeg, 0.0)
        assertEquals(1, moved.repositionEpoch)
        val vm = ArTabletopViewModel()
        vm.onPermission(true)
        vm.onAvailability(ArCoreAvailabilityKind.SUPPORTED_INSTALLED)
        vm.setHasValidPlacementHit(true)
        vm.place()
        vm.pinch(1.8)
        vm.reposition()
        assertEquals(ArTabletopPhase.DETECTING, vm.state.value.phase)
        assertEquals(1, vm.state.value.repositionEpoch)
        assertFalse(vm.state.value.hasValidPlacementHit)
    }

    @Test
    fun pinchAndYawAreSessionOnlyAndClamped() {
        assertEquals(0.4, ArTabletopGestures.applyPinch(1.0, 0.1), 1e-9)
        assertEquals(3.0, ArTabletopGestures.applyPinch(1.0, 10.0), 1e-9)
        assertEquals(-20.0, ArTabletopGestures.applyHorizontalRotation(0.0, 20.0), 1e-9)
        val note = ArTabletopUiState.SESSION_ONLY_NOTE
        assertTrue(note.contains("session only"))
        assertFalse(note.contains("saved physical"))
    }

    private fun readyDetecting(): ArTabletopUiState {
        val vm = ArTabletopViewModel()
        vm.onPermission(true)
        vm.onAvailability(ArCoreAvailabilityKind.SUPPORTED_INSTALLED)
        return vm.state.value
    }
}

class ArGreenhouseVisualsTest {

    @Test
    fun materialsAreAssignedBySemanticPartType() {
        MeshKind.values().forEach { kind ->
            val look = GreenhouseSurfaceLooks.of(kind)
            val style = ArGreenhouseVisuals.style(kind)
            assertEquals(look.red, style.red, 0f)
            assertEquals(look.green, style.green, 0f)
            assertEquals(look.blue, style.blue, 0f)
            assertEquals(look.alpha, style.alpha, 0f)
            assertEquals(look.doubleSided, style.doubleSided)
            assertEquals(look.usesTransparentMaterial, style.usesTransparentMaterial)
        }
        val frame = ArGreenhouseVisuals.style(MeshKind.FRAME)
        val panel = ArGreenhouseVisuals.style(MeshKind.PANEL)
        val floor = ArGreenhouseVisuals.style(MeshKind.FLOOR)
        val crop = ArGreenhouseVisuals.style(MeshKind.CROP)
        assertFalse(frame.isTransparent)
        assertEquals(1f, frame.alpha, 0f)
        assertTrue(frame.red > 0.55f && frame.red < 0.75f)
        assertTrue(panel.usesTransparentMaterial)
        assertEquals(0.28f, panel.alpha, 0f)
        assertTrue(panel.doubleSided)
        assertTrue(panel.blue > panel.red)
        assertFalse(floor.isTransparent)
        assertTrue(floor.red > floor.blue)
        assertFalse(crop.isTransparent)
        assertTrue(crop.green > crop.red && crop.green > crop.blue)
        val kindsInOrder = listOf(MeshKind.CROP, MeshKind.PANEL, MeshKind.FRAME, MeshKind.FLOOR)
        kindsInOrder.forEach { kind ->
            assertEquals(GreenhouseSurfaceLooks.of(kind).alpha, ArGreenhouseVisuals.style(kind).alpha, 0f)
        }
    }

    @Test
    fun arPartsMatchPreviewMeshAndKeepThinFrames() {
        val demo = DemoGreenhouseProfile.dimensions()
        val previewLayout = SceneScaleMapper.layout(demo, cropRowCount = 3)
        val arLayout = ArTabletopScale.layout(demo, 3)
        val previewParts = GreenhouseRenderParts.of(previewLayout)
        val arParts = GreenhouseRenderParts.of(arLayout)
        assertEquals(previewParts.size, arParts.size)
        previewParts.zip(arParts).forEach { (preview, ar) ->
            assertEquals(preview.id, ar.id)
            assertEquals(preview.kind, ar.kind)
            assertEquals(preview.centerX, ar.centerX, 1e-12)
            assertEquals(preview.centerY, ar.centerY, 1e-12)
            assertEquals(preview.centerZ, ar.centerZ, 1e-12)
            assertEquals(preview.sizeX, ar.sizeX, 1e-12)
            assertEquals(preview.sizeY, ar.sizeY, 1e-12)
            assertEquals(preview.sizeZ, ar.sizeZ, 1e-12)
        }
        val beam = minOf(arLayout.extentX, arLayout.extentZ, arLayout.extentY) * 0.028
        val post = arParts.first { it.id == "post_sw" }
        assertEquals(beam, post.sizeX, 1e-12)
        assertTrue(post.sizeX < arLayout.extentZ * 0.05)
        val panels = arParts.filter { it.kind == MeshKind.PANEL }
        assertTrue(panels.isNotEmpty())
        panels.forEach { panel ->
            assertTrue(ArGreenhouseVisuals.style(panel.kind).usesTransparentMaterial)
            assertTrue(min(min(panel.sizeX, panel.sizeY), panel.sizeZ) < arLayout.extentY * 0.15)
        }
        val crops = arParts.filter { it.kind == MeshKind.CROP }.sortedBy { it.centerZ }
        assertEquals(3, crops.size)
        for (i in 0 until crops.lastIndex) {
            assertTrue(crops[i].maxZ <= crops[i + 1].minZ + 1e-9)
        }
    }

    @Test
    fun intendedSurfacesStayVisibleAndDistinct() {
        val ground = ArGreenhouseVisuals.style(MeshKind.GROUND)
        val floor = ArGreenhouseVisuals.style(MeshKind.FLOOR)
        val frame = ArGreenhouseVisuals.style(MeshKind.FRAME)
        val panel = ArGreenhouseVisuals.style(MeshKind.PANEL)
        val crop = ArGreenhouseVisuals.style(MeshKind.CROP)
        listOf(ground, floor, frame, panel, crop).forEach { style ->
            assertFalse(style.red == 0f && style.green == 0f && style.blue == 0f)
            assertTrue(style.alpha > 0.05f)
        }
        assertEquals(1f, frame.alpha, 0f)
        assertTrue(panel.isTransparent)
        assertTrue(panel.doubleSided)
        assertEquals(1f, floor.alpha, 0f)
        assertEquals(1f, crop.alpha, 0f)
        assertTrue(crop.green > crop.red && crop.green > crop.blue)
        assertTrue(ground.green > ground.red)
    }

    @Test
    fun lightEstimateNeverCollapsesToBlack() {
        assertEquals(ArTabletopLighting.FALLBACK_INTENSITY_LUX, ArTabletopLighting.visibleIntensityLux(null), 0f)
        assertEquals(ArTabletopLighting.FALLBACK_INTENSITY_LUX, ArTabletopLighting.visibleIntensityLux(0f), 1f)
        assertEquals(
            ArTabletopLighting.FALLBACK_INTENSITY_LUX * ArTabletopLighting.MIN_ESTIMATION_FACTOR,
            ArTabletopLighting.visibleIntensityLux(0.001f),
            1f
        )
        assertTrue(ArTabletopLighting.visibleIntensityLux(Float.NaN) >= ArTabletopLighting.FALLBACK_INTENSITY_LUX)
        assertTrue(ArTabletopLighting.keepEstimatedDirection(-0.8f))
        assertFalse(ArTabletopLighting.keepEstimatedDirection(0.4f))
    }
}
