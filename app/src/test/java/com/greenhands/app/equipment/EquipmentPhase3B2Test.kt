package com.greenhands.app.equipment

import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.AirflowUnits
import com.greenhands.app.equipment.domain.DomainSceneCoordinateAdapter
import com.greenhands.app.equipment.domain.EquipmentRecommendationEngine
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.RecommendationInputs
import com.greenhands.app.equipment.model.RecommendationStatus
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.domain.PreviewCameraFramer
import com.greenhands.app.preview3d.domain.SceneScaleMapper
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GlbAssetTest {
    @Test
    fun threeReusableGlbAssetsAreValidGlb2() {
        EquipmentType.entries.forEach { type ->
            val file = glbFile(type.assetFileName)
            assertTrue("${type.assetFileName} missing at ${file.absolutePath}", file.isFile)
            val bytes = file.readBytes()
            assertTrue(bytes.size > 64)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0x46546C67, buf.int)
            assertEquals(2, buf.int)
            assertEquals(bytes.size, buf.int)
            val jsonLen = buf.int
            assertEquals(0x4E4F534A, buf.int)
            val json = String(bytes, 20, jsonLen, Charsets.UTF_8)
            assertTrue(json.contains("\"asset\""))
            assertTrue(json.contains("2.0"))
            assertTrue(json.contains("meshes"))
            assertTrue(json.contains(type.assetFileName.removeSuffix(".glb")))
            assertFalse(json.contains("CubeNode"))
        }
    }

    @Test
    fun multipleInstancesShareOneAssetPath() {
        val a = EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "a", DomainPoint(1.0, 1.0, 1.5), 0.0)
        val b = EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "b", DomainPoint(2.0, 1.0, 1.5), 180.0)
        assertEquals(a.assetPath, b.assetPath)
        assertEquals("models/equipment/circulation_fan.glb", a.assetPath)
        assertNotEquals(a.id, b.id)
        assertEquals(EquipmentAssetCatalog.ASSET_VERSION, a.assetVersion)
    }
}

class DomainSceneCoordinateAdapterTest {
    private fun demoLayout() = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
    private fun customLayout() = SceneScaleMapper.layout(
        GreenhouseDimensions(50.0, 10.0, 5.0, ProvenanceClass.MEASURED_INPUT, "test")
    )

    @Test
    fun demoMapsUserXToSceneZAndUserYToSceneX() {
        val layout = demoLayout()
        assertTrue(layout.lengthMappedToX)
        val origin = DomainSceneCoordinateAdapter.toScene(DomainPoint(0.0, 0.0, 0.0), layout)
        assertEquals(-5.0, origin.x, 1e-9)
        assertEquals(0.0, origin.y, 1e-9)
        assertEquals(-2.5, origin.z, 1e-9)
        val far = DomainSceneCoordinateAdapter.toScene(
            DomainPoint(layout.dimensions.widthM, layout.dimensions.lengthM, 3.0),
            layout
        )
        assertEquals(5.0, far.x, 1e-9)
        assertEquals(3.0, far.y, 1e-9)
        assertEquals(2.5, far.z, 1e-9)
        val widthEdge = DomainSceneCoordinateAdapter.toScene(DomainPoint(5.0, 0.0, 0.0), layout)
        assertEquals(-5.0, widthEdge.x, 1e-9)
        assertEquals(2.5, widthEdge.z, 1e-9)
        val lengthEdge = DomainSceneCoordinateAdapter.toScene(DomainPoint(0.0, 10.0, 0.0), layout)
        assertEquals(5.0, lengthEdge.x, 1e-9)
        assertEquals(-2.5, lengthEdge.z, 1e-9)
    }

    @Test
    fun roundTripKeepsMetres() {
        listOf(demoLayout(), customLayout(), SceneScaleMapper.layout(GreenhouseDimensions(4.0, 12.0, 3.0, ProvenanceClass.MEASURED_INPUT, "wide"))).forEach { layout ->
            val points = listOf(
                DomainPoint(0.0, 0.0, 0.0),
                DomainPoint(layout.dimensions.widthM, layout.dimensions.lengthM, layout.dimensions.heightM),
                DomainPoint(1.23, 0.45, 2.01)
            )
            points.forEach { p ->
                val back = DomainSceneCoordinateAdapter.toDomain(DomainSceneCoordinateAdapter.toScene(p, layout), layout)
                assertEquals(p.xM, back.xM, 1e-9)
                assertEquals(p.yM, back.yM, 1e-9)
                assertEquals(p.zM, back.zM, 1e-9)
            }
            val yaw = 37.5
            assertEquals(yaw, DomainSceneCoordinateAdapter.toDomainYawDeg(DomainSceneCoordinateAdapter.toSceneYawDeg(yaw, layout), layout), 1e-9)
        }
    }
}

class PlacementConstraintTest {
    private fun ctx(length: Double, width: Double, height: Double, rows: Int): PlacementContext {
        val canopy = height * CropRowGeometry.HEIGHT_FILL
        return PlacementContext(length, width, height, rows, canopy)
    }

    @Test
    fun domainBoundsRejectOutsideEnvelope() {
        val context = ctx(10.0, 5.0, 3.0, 3)
        val outsideX = EquipmentAssetCatalog.newInstance(
            EquipmentType.CIRCULATION_FAN, "ox", DomainPoint(12.0, 2.5, 1.6), 0.0
        )
        val outsideY = EquipmentAssetCatalog.newInstance(
            EquipmentType.CIRCULATION_FAN, "oy", DomainPoint(2.5, 12.0, 1.6), 0.0
        )
        val outsideZ = EquipmentAssetCatalog.newInstance(
            EquipmentType.CIRCULATION_FAN, "oz", DomainPoint(2.5, 5.0, 3.4), 0.0
        )
        assertFalse(PlacementValidator.validate(outsideX, emptyList(), context).isValid)
        assertFalse(PlacementValidator.validate(outsideY, emptyList(), context).isValid)
        assertFalse(PlacementValidator.validate(outsideZ, emptyList(), context).isValid)
        assertFalse(com.greenhands.app.equipment.domain.UserPlacementCoordinates.inBounds(outsideX.positionM, 5.0, 10.0, 3.0))
        assertFalse(com.greenhands.app.equipment.domain.UserPlacementCoordinates.inBounds(outsideY.positionM, 5.0, 10.0, 3.0))
        assertFalse(com.greenhands.app.equipment.domain.UserPlacementCoordinates.inBounds(outsideZ.positionM, 5.0, 10.0, 3.0))
        assertTrue(
            com.greenhands.app.equipment.domain.UserPlacementCoordinates.inBounds(
                DomainPoint(5.0, 10.0, 3.0), 5.0, 10.0, 3.0
            )
        )
    }

    @Test
    fun uniqueIdsAndModelDimensions() {
        val context = ctx(10.0, 5.0, 3.0, 3)
        val one = PlacementSuggester.suggest(context, 2, 1, 2)
        assertEquals(2, one.count { it.type == EquipmentType.CIRCULATION_FAN })
        assertEquals(one.map { it.id }.distinct().size, one.size)
        one.filter { it.type == EquipmentType.CIRCULATION_FAN }.forEach {
            assertEquals(0.42, it.dimensions.widthM, 1e-9)
            assertEquals(0.25, it.dimensions.depthM, 1e-9)
            assertEquals(0.515, it.dimensions.heightM, 1e-9)
        }
    }

    @Test
    fun cropAndEquipmentCollisionDetected() {
        val context = ctx(10.0, 5.0, 3.0, 3)
        val crop = context.cropBoxes[1]
        val overlapping = EquipmentAssetCatalog.newInstance(
            EquipmentType.FOGGER,
            "bad",
            DomainPoint((crop.minXM + crop.maxXM) * 0.5, (crop.minYM + crop.maxYM) * 0.5, 0.2),
            0.0
        )
        val result = PlacementValidator.validate(overlapping, emptyList(), context)
        assertFalse(result.isValid)
        assertTrue(result.reasons.any { it.contains("crop", ignoreCase = true) || it.contains("canopy", ignoreCase = true) || it.contains("overhead") })
    }

    @Test
    fun equipmentOverlapDetected() {
        val context = ctx(10.0, 5.0, 3.0, 3)
        val a = EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "a", DomainPoint(2.0, 0.6, 1.6), 0.0)
        val b = EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "b", DomainPoint(2.05, 0.6, 1.6), 0.0)
        assertFalse(PlacementValidator.validate(b, listOf(a), context).isValid)
    }

    @Test
    fun exhaustMustMountOnEndWallAndWarnsIfInward() {
        val context = ctx(10.0, 5.0, 3.0, 3)
        val floating = EquipmentAssetCatalog.newInstance(EquipmentType.EXHAUST_FAN, "mid", DomainPoint(5.0, 2.5, 1.5), 0.0)
        assertFalse(PlacementValidator.validate(floating, emptyList(), context).isValid)
        val snapped = PlacementValidator.snapExhaust(floating, context)
        assertTrue(snapped.positionM.yM == 0.0 || snapped.positionM.yM == 10.0)
        val inward = snapped.copy(yawDeg = if (snapped.positionM.yM == 10.0) 270.0 else 90.0)
        val validity = PlacementValidator.validate(inward, emptyList(), context)
        assertTrue(validity.warnings.any { it.contains("inward", ignoreCase = true) })
    }

    @Test
    fun foggerOverheadAndCirculationAlternateSides() {
        val context = ctx(10.0, 5.0, 3.0, 6)
        val foggers = PlacementSuggester.foggerOverhead(context, 4) { _, i -> "f$i" }
        foggers.forEach {
            assertTrue(it.positionM.zM > context.canopyHeightM)
            assertTrue(it.positionM.zM < context.heightM)
        }
        val fans = PlacementSuggester.circulationLoop(context, 4) { _, i -> "c$i" }
        assertTrue(fans[0].positionM.xM < fans[1].positionM.xM)
        assertEquals(90.0, fans[0].yawDeg, 0.0)
        assertEquals(270.0, fans[1].yawDeg, 0.0)
        assertNotEquals(fans[0].positionM.yM, fans[1].positionM.yM, 0.01)
    }

    @Test
    fun suggestionsAreDeterministicForDemoAndCustomAndRowCounts() {
        listOf(1, 3, 6, 20).forEach { rows ->
            listOf(ctx(10.0, 5.0, 3.0, rows), ctx(50.0, 10.0, 5.0, rows)).forEach { context ->
                val a = PlacementSuggester.suggest(context, 3, 2, 4)
                val b = PlacementSuggester.suggest(context, 3, 2, 4)
                assertEquals(a.map { it.positionM }, b.map { it.positionM })
                assertEquals(a.map { it.yawDeg }, b.map { it.yawDeg })
            }
        }
    }
}

class AirflowUnitsTest {
    @Test
    fun conversionsRoundTrip() {
        assertEquals(10.76391041671, AirflowUnits.squareMetresToSquareFeet(1.0), 1e-9)
        val cfm = 1000.0
        val m3s = AirflowUnits.cubicFeetPerMinuteToM3s(cfm)
        assertEquals(cfm, AirflowUnits.m3sToCubicFeetPerMinute(m3s), 1e-9)
        val kgS = AirflowUnits.litresPerHourToKgS(7.2)
        assertEquals(7.2, AirflowUnits.kgSToLitresPerHour(kgS), 1e-9)
    }
}

class EquipmentRecommendationEngineTest {
    private fun base() = RecommendationInputs(lengthM = 10.0, widthM = 5.0, heightM = 3.0, cropRowCount = 3)

    @Test
    fun missingRatingsCannotCalculate() {
        val rec = EquipmentRecommendationEngine.recommend(base())
        assertEquals(RecommendationStatus.CANNOT_CALCULATE, rec.circulation.status)
        assertTrue(rec.circulation.equationNote.contains("rated airflow is required"))
        assertEquals(RecommendationStatus.CANNOT_CALCULATE, rec.exhaust.status)
        assertEquals(RecommendationStatus.CANNOT_CALCULATE, rec.fogger.status)
    }

    @Test
    fun circulationCountUsesTwoCfmPerSquareFoot() {
        val inputs = base().copy(circulationRatedCfm = 500.0)
        val result = EquipmentRecommendationEngine.circulationCount(inputs)
        val areaFt2 = AirflowUnits.squareMetresToSquareFeet(50.0)
        val expected = kotlin.math.ceil(areaFt2 * 2.0 / 500.0).toInt()
        assertEquals(expected, result.recommendedCount)
        assertEquals(RecommendationStatus.OK, result.status)
    }

    @Test
    fun exhaustCountUsesAirChangeAndStaticPressureRating() {
        val inputs = base().copy(exhaustRatedAirflowM3sAtStaticPressure = 0.5, exhaustStaticPressurePa = 25.0)
        val result = EquipmentRecommendationEngine.exhaustCount(inputs)
        val required = 10.0 * 5.0 * 3.0 / 60.0
        assertEquals(kotlin.math.ceil(required / 0.5).toInt(), result.recommendedCount)
    }

    @Test
    fun foggerCountUsesPhase3ALatentHeat() {
        val inputs = base().copy(
            netSolarAndInternalHeatLoadW = 5000.0,
            foggerWaterFlowKgS = 0.002,
            foggerEvaporationEfficiency = 0.5,
            indoorTemperatureC = 30.0,
            indoorRhPercent = 60.0
        )
        val result = EquipmentRecommendationEngine.foggerCount(inputs)
        assertNotNull(result.recommendedCount)
        assertTrue(result.recommendedCount!! >= 1)
        assertTrue(result.warnings.any { it.contains("Non-evaporated") })
    }

    @Test
    fun userOverrideIsApplied() {
        val rec = EquipmentRecommendationEngine.recommend(
            base().copy(circulationRatedCfm = 500.0, overrideCirculationCount = 7)
        )
        assertEquals(7, rec.circulation.appliedCount)
        assertEquals(RecommendationStatus.OVERRIDE_APPLIED, rec.circulation.status)
        assertEquals(7, rec.suggestedInstances.count { it.type == EquipmentType.CIRCULATION_FAN })
    }

    @Test
    fun outdoorWarmerThanTargetWarnsExhaustCannotCoolBelowOutdoor() {
        val result = EquipmentRecommendationEngine.exhaustCount(
            base().copy(
                exhaustRatedAirflowM3sAtStaticPressure = 0.8,
                outdoorTemperatureC = 32.0,
                optimalTemperatureC = 25.0
            )
        )
        assertTrue(result.warnings.any { it.contains("cannot cool below", ignoreCase = true) })
    }
}

class EquipmentLayoutCodecTest {
    @Test
    fun saveRestoreAndLegacyDecode() {
        val original = EquipmentLayout(
            layoutId = "L1",
            configurationId = "C1",
            userId = "grower@greenhands.app",
            lengthM = 10.0,
            widthM = 5.0,
            heightM = 3.0,
            cropRowCount = 3,
            instances = listOf(
                EquipmentAssetCatalog.newInstance(
                    EquipmentType.FOGGER, "f1", DomainPoint(4.0, 2.0, 2.2), 15.0, displayNumber = 1
                )
            ),
            countOverrideFogger = 2
        )
        val decoded = EquipmentLayoutCodec.decode(EquipmentLayoutCodec.encode(original))!!
        assertEquals(EquipmentLayout.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(original.configurationId, decoded.configurationId)
        assertEquals(1, decoded.instances.size)
        assertEquals(4.0, decoded.instances.single().positionM.xM, 0.0)
        assertEquals(2.0, decoded.instances.single().positionM.yM, 0.0)
        assertEquals(1, decoded.instances.single().displayNumber)
        assertEquals("Fogger 1", decoded.instances.single().displayLabel())
        assertEquals(2, decoded.countOverrideFogger)
        val encoded = EquipmentLayoutCodec.encode(original)
        assertTrue(encoded.startsWith("v=2|") || encoded.contains("|v=2|") || encoded.startsWith("v=2"))
        val legacyInst = "old-fan,CIRCULATION_FAN,2.0,0.8,1.6,0.0,3b2.1,SUSPENDED_OR_BAY,NOT_RUNNING,0,true,"
        val legacy = "v=1|layoutId=old|configId=cfg|userId=u|len=10.0|wid=5.0|hgt=3.0|rows=3|inst=$legacyInst"
        val old = EquipmentLayoutCodec.decode(legacy)!!
        assertEquals(2, old.schemaVersion)
        assertEquals(10.0, old.lengthM, 0.0)
        assertEquals(5.0, old.widthM, 0.0)
        assertEquals(1, old.instances.size)
        assertEquals(0.8, old.instances.single().positionM.xM, 0.0)
        assertEquals(2.0, old.instances.single().positionM.yM, 0.0)
        assertEquals(1.6, old.instances.single().positionM.zM, 0.0)
        assertEquals(90.0, old.instances.single().yawDeg, 0.0)
        val emptyLegacy = "v=1|layoutId=empty|configId=cfg|userId=u|len=8.0|wid=4.0|hgt=3.0|rows=1|inst="
        val empty = EquipmentLayoutCodec.decode(emptyLegacy)!!
        assertEquals(2, empty.schemaVersion)
        assertEquals(8.0, empty.lengthM, 0.0)
        assertTrue(empty.instances.isEmpty())
        assertNull(empty.countOverrideCirculation)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentPlacementViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveRestoreEditedLayoutAndUniqueInstances() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo) { java.util.UUID.randomUUID().toString() }
        vm.attach("grower@greenhands.app", "cfg-1", 10.0, 5.0, 3.0, 3, OverlayClimate(null, null, "—", "—", "Not published", null, null, null))
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        vm.duplicateSelected()
        assertEquals(2, vm.state.value.instances.count { it.type == EquipmentType.CIRCULATION_FAN })
        assertEquals(2, vm.state.value.instances.map { it.id }.distinct().size)
        val first = vm.state.value.instances.first()
        vm.select(first.id)
        vm.applyNumericEdit("2.25", "0.80", first.positionM.zM.toString(), "90")
        vm.saveLayout()
        val stored = repo.snapshot("grower@greenhands.app").single()
        assertEquals(2, stored.instances.size)
        assertTrue(stored.instances.any { it.positionM.xM == 2.25 })
        val vm2 = EquipmentPlacementViewModel(repo)
        vm2.attach("grower@greenhands.app", "cfg-1", 10.0, 5.0, 3.0, 3, OverlayClimate(null, null, "—", "—", "Not published", null, null, null))
        assertEquals(2, vm2.state.value.instances.size)
    }
}

class CameraFramingUnaffectedByEquipmentTest {
    @Test
    fun previewCameraPoseIgnoresEquipment() {
        val layout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
        val pose = PreviewCameraFramer.frame(layout, PreviewCameraFramer.PORTRAIT_ASPECT)
        val again = PreviewCameraFramer.frame(layout, PreviewCameraFramer.PORTRAIT_ASPECT)
        assertEquals(pose, again)
        val context = PlacementContext(10.0, 5.0, 3.0, 3, 0.78)
        PlacementSuggester.suggest(context, 4, 2, 6)
        assertEquals(pose, PreviewCameraFramer.frame(layout, PreviewCameraFramer.PORTRAIT_ASPECT))
        assertEquals(10.0, layout.extentX, 0.0)
        assertEquals(5.0, layout.extentZ, 0.0)
        assertEquals(3.0, layout.extentY, 0.0)
    }
}

private fun glbFile(name: String): File {
    val candidates = listOf(
        File("src/main/assets/models/equipment/$name"),
        File("app/src/main/assets/models/equipment/$name"),
        File("../app/src/main/assets/models/equipment/$name")
    )
    return candidates.firstOrNull { it.isFile } ?: candidates.first()
}
