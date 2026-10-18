package com.greenhands.app.preview3d

import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.domain.CropRowValidation
import com.greenhands.app.preview3d.domain.DimensionValidation
import com.greenhands.app.preview3d.domain.GreenhouseMeshBuilder
import com.greenhands.app.preview3d.domain.MeshKind
import com.greenhands.app.preview3d.domain.PreviewCameraFramer
import com.greenhands.app.preview3d.domain.PreviewCameraPose
import com.greenhands.app.preview3d.domain.SceneLayout
import com.greenhands.app.preview3d.domain.SceneScaleMapper
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.preview3d.ui.PreviewSetupViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoGreenhouseProfileTest {

    @Test
    fun demoUsesExactSpecDimensionsAndPreviewAssumption() {
        val demo = DemoGreenhouseProfile.dimensions()
        assertEquals(10.0, demo.lengthM, 0.0)
        assertEquals(5.0, demo.widthM, 0.0)
        assertEquals(3.0, demo.heightM, 0.0)
        assertEquals(ProvenanceClass.PREVIEW_ASSUMPTION, demo.provenance)
        assertEquals(ProvenanceClass.PREVIEW_ASSUMPTION, DemoGreenhouseProfile.PROVENANCE)
        assertTrue(demo.provenanceNote.contains("PREVIEW_ASSUMPTION"))
        assertTrue(demo.diagnosticLine().contains("PREVIEW_ASSUMPTION"))
        assertEquals("Demo greenhouse — 10.0 m × 5.0 m × 3.0 m", DemoGreenhouseProfile.displayLabel())
        assertEquals(3, DemoGreenhouseProfile.CROP_ROW_COUNT)
        assertEquals(PreviewMode.DEMO, PreviewMode.fromRoute("demo"))
        assertEquals(PreviewMode.MY_GREENHOUSE, PreviewMode.fromRoute("MY_GREENHOUSE"))
    }
}

class DimensionValidationTest {

    @Test
    fun acceptedBoundaryValuesAreValid() {
        assertNull(DimensionValidation.length("2").error)
        assertEquals(2.0, DimensionValidation.length("2").value)
        assertNull(DimensionValidation.length("100").error)
        assertNull(DimensionValidation.width("2").error)
        assertNull(DimensionValidation.width("50").error)
        assertNull(DimensionValidation.height("2").error)
        assertNull(DimensionValidation.height("10").error)
        assertTrue(DimensionValidation.allValid(2.0, 2.0, 2.0))
        assertTrue(DimensionValidation.allValid(100.0, 50.0, 10.0))
    }

    @Test
    fun valuesOutsideGuardrailsAreRejected() {
        assertNotNull(DimensionValidation.length("1.9").error)
        assertNotNull(DimensionValidation.length("100.1").error)
        assertNotNull(DimensionValidation.width("1.99").error)
        assertNotNull(DimensionValidation.width("50.01").error)
        assertNotNull(DimensionValidation.height("1.5").error)
        assertNotNull(DimensionValidation.height("10.01").error)
        assertFalse(DimensionValidation.allValid(1.0, 5.0, 3.0))
    }

    @Test
    fun blankNonNumericNaNInfinityAndNonPositiveAreRejected() {
        assertEquals("Enter a length", DimensionValidation.length("").error)
        assertEquals("Enter a length", DimensionValidation.length("   ").error)
        assertEquals("Enter a valid number", DimensionValidation.length("abc").error)
        assertEquals("Enter a finite number", DimensionValidation.length("NaN").error)
        assertEquals("Enter a finite number", DimensionValidation.length("Infinity").error)
        assertEquals("Enter a finite number", DimensionValidation.length("-Infinity").error)
        assertEquals("Length must be greater than 0 m", DimensionValidation.length("0").error)
        assertEquals("Length must be greater than 0 m", DimensionValidation.length("-3").error)
        assertEquals("Width must be greater than 0 m", DimensionValidation.width("0").error)
        assertEquals("Height must be greater than 0 m", DimensionValidation.height("-1").error)
        assertNull(DimensionValidation.parseFinite("NaN"))
        assertNull(DimensionValidation.parseFinite("Infinity"))
        assertEquals(8.5, DimensionValidation.parseFinite("8.5"))
        assertEquals(8.5, DimensionValidation.parseFinite("8,5"))
    }
}

class SceneScaleMapperTest {

    private fun dims(length: Double, width: Double, height: Double) = GreenhouseDimensions(
        lengthM = length,
        widthM = width,
        heightM = height,
        provenance = ProvenanceClass.MEASURED_INPUT,
        provenanceNote = "test"
    )

    @Test
    fun demoProfileMapsWithUniformScaleOne() {
        val layout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
        assertEquals(1.0, layout.uniformScale, 0.0)
        assertEquals(10.0, layout.extentX, 0.0)
        assertEquals(5.0, layout.extentZ, 0.0)
        assertEquals(3.0, layout.extentY, 0.0)
        assertTrue(layout.lengthMappedToX)
        assertEquals(layout.uniformScale, layout.scaleAlongLength(), 1e-12)
        assertEquals(layout.uniformScale, layout.scaleAlongWidth(), 1e-12)
        assertEquals(layout.uniformScale, layout.scaleAlongHeight(), 1e-12)
    }

    @Test
    fun longestHorizontalAxisReceivesTargetSpan() {
        val wide = SceneScaleMapper.layout(dims(4.0, 12.0, 3.0))
        assertFalse(wide.lengthMappedToX)
        assertEquals(SceneScaleMapper.TARGET_LONG_SPAN, wide.extentX, 1e-12)
        assertEquals(4.0 * wide.uniformScale, wide.extentZ, 1e-12)
        assertEquals(3.0 * wide.uniformScale, wide.extentY, 1e-12)
        assertEquals(wide.uniformScale, wide.scaleAlongLength(), 1e-12)
        assertEquals(wide.uniformScale, wide.scaleAlongWidth(), 1e-12)
        assertEquals(wide.uniformScale, wide.scaleAlongHeight(), 1e-12)
    }

    @Test
    fun proportionsArePreservedAcrossDifferentAbsoluteSizes() {
        val small = SceneScaleMapper.layout(dims(10.0, 5.0, 3.0))
        val large = SceneScaleMapper.layout(dims(20.0, 10.0, 6.0))
        assertEquals(small.extentX / small.extentZ, large.extentX / large.extentZ, 1e-12)
        assertEquals(small.extentX / small.extentY, large.extentX / large.extentY, 1e-12)
        assertEquals(small.extentZ / small.extentY, large.extentZ / large.extentY, 1e-12)
        assertEquals(2.0, large.extentForLength / large.extentForWidth, 1e-12)
        assertEquals(20.0 / 6.0, large.extentForLength / large.extentY, 1e-12)
    }

    @Test
    fun noAxisIsStretchedIndependently() {
        val layout = SceneScaleMapper.layout(dims(7.5, 3.25, 2.5))
        assertEquals(layout.scaleAlongLength(), layout.scaleAlongWidth(), 1e-12)
        assertEquals(layout.scaleAlongLength(), layout.scaleAlongHeight(), 1e-12)
        assertEquals(
            layout.dimensions.lengthM / layout.dimensions.widthM,
            layout.extentForLength / layout.extentForWidth,
            1e-12
        )
        assertEquals(
            layout.dimensions.lengthM / layout.dimensions.heightM,
            layout.extentForLength / layout.extentY,
            1e-12
        )
    }
}

class GreenhouseMeshBuilderTest {

    @Test
    fun meshKeepsFloorProportionsAndIncludesCropRows() {
        val layout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
        val mesh = GreenhouseMeshBuilder.build(layout)
        val floor = mesh.single { it.id == "floor" }
        assertEquals(layout.extentX, floor.sizeX, 1e-9)
        assertEquals(layout.extentZ, floor.sizeZ, 1e-9)
        val crops = mesh.filter { it.kind == MeshKind.CROP }
        assertEquals(3, crops.size)
        crops.forEach { crop ->
            assertTrue(crop.minX >= floor.minX - 1e-9)
            assertTrue(crop.maxX <= floor.maxX + 1e-9)
            assertTrue(crop.minZ >= floor.minZ - 1e-9)
            assertTrue(crop.maxZ <= floor.maxZ + 1e-9)
        }
        assertTrue(mesh.count { it.kind == MeshKind.FRAME } >= 4)
        assertTrue(mesh.count { it.kind == MeshKind.PANEL } >= 4)
        val maxY = mesh.filter { it.kind != MeshKind.GROUND }.maxOf { it.centerY + it.sizeY * 0.5 }
        assertEquals(layout.extentY, maxY, 1e-9)
        val ground = mesh.single { it.id == "ground" }
        assertEquals(layout.extentX * GreenhouseMeshBuilder.GROUND_SPAN_FACTOR_X, ground.sizeX, 1e-9)
        assertEquals(layout.extentZ * GreenhouseMeshBuilder.GROUND_SPAN_FACTOR_Z, ground.sizeZ, 1e-9)
        assertTrue(ground.sizeX < layout.extentX * 1.25)
        assertTrue(ground.sizeZ < layout.extentZ * 1.25)
    }

    @Test
    fun generatedRowCountMatchesSelectionAndStaysInsideWithoutOverlap() {
        val counts = listOf(1, 3, 6, 20)
        counts.forEach { count ->
            val layout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions(), cropRowCount = count)
            val mesh = GreenhouseMeshBuilder.build(layout)
            val floor = mesh.single { it.id == "floor" }
            val crops = mesh.filter { it.kind == MeshKind.CROP }.sortedBy { it.centerZ }
            assertEquals(count, crops.size)
            crops.forEach { crop ->
                assertTrue(crop.minX >= floor.minX - 1e-9)
                assertTrue(crop.maxX <= floor.maxX + 1e-9)
                assertTrue(crop.minZ >= floor.minZ - 1e-9)
                assertTrue(crop.maxZ <= floor.maxZ + 1e-9)
            }
            for (i in 0 until crops.lastIndex) {
                assertTrue(crops[i].maxZ <= crops[i + 1].minZ + 1e-9)
            }
        }
        val wide = SceneScaleMapper.layout(
            GreenhouseDimensions(
                lengthM = 4.0,
                widthM = 12.0,
                heightM = 3.0,
                provenance = ProvenanceClass.MEASURED_INPUT,
                provenanceNote = "test"
            ),
            cropRowCount = 6
        )
        assertFalse(wide.lengthMappedToX)
        val wideCrops = GreenhouseMeshBuilder.build(wide).filter { it.kind == MeshKind.CROP }.sortedBy { it.centerX }
        assertEquals(6, wideCrops.size)
        for (i in 0 until wideCrops.lastIndex) {
            assertTrue(wideCrops[i].maxX <= wideCrops[i + 1].minX + 1e-9)
        }
    }

    @Test
    fun domainCropBoxesMatchPlaceholderFractionsForRowCounts() {
        listOf(1, 3, 6, 20).forEach { count ->
            val boxes = CropRowGeometry.domainBoxes(10.0, 5.0, 3.0, count)
            assertEquals(count, boxes.size)
            boxes.forEach { box ->
                assertTrue(box.minXM >= 0.0)
                assertTrue(box.maxXM <= 5.0)
                assertTrue(box.minYM >= 0.0)
                assertTrue(box.maxYM <= 10.0)
                assertEquals(3.0 * CropRowGeometry.HEIGHT_FILL, box.maxZM, 1e-9)
            }
        }
    }
}

class CropRowValidationTest {

    @Test
    fun acceptedRowCountsAreValid() {
        listOf("1", "3", "20").forEach { raw ->
            val parsed = CropRowValidation.parse(raw)
            assertNull(parsed.error)
            assertEquals(raw.toInt(), parsed.value)
            assertTrue(parsed.isValid)
        }
    }

    @Test
    fun invalidRowCountsAreRejected() {
        assertEquals("Enter the number of crop rows", CropRowValidation.parse("").error)
        assertEquals("Enter the number of crop rows", CropRowValidation.parse("   ").error)
        assertEquals("Enter a whole number", CropRowValidation.parse("abc").error)
        assertEquals("Enter a whole number", CropRowValidation.parse("3.5").error)
        assertEquals("Enter a whole number", CropRowValidation.parse("3.0").error)
        assertEquals("Number of crop rows must be between 1 and 20", CropRowValidation.parse("0").error)
        assertEquals("Number of crop rows must be between 1 and 20", CropRowValidation.parse("-1").error)
        assertEquals("Number of crop rows must be between 1 and 20", CropRowValidation.parse("21").error)
    }
}

class PreviewSetupViewModelTest {

    @Test
    fun demoModeOpensWithPreviewAssumptionProfile() {
        val vm = PreviewSetupViewModel()
        assertFalse(vm.state.value.canOpenPreview)
        vm.selectDemo()
        assertTrue(vm.state.value.canOpenPreview)
        val dims = vm.state.value.resolvedDimensions()
        assertNotNull(dims)
        assertEquals(10.0, dims!!.lengthM, 0.0)
        assertEquals(5.0, dims.widthM, 0.0)
        assertEquals(3.0, dims.heightM, 0.0)
        assertEquals(ProvenanceClass.PREVIEW_ASSUMPTION, dims.provenance)
    }

    @Test
    fun myGreenhouseBlocksInvalidAndAcceptsValidDimensions() {
        val vm = PreviewSetupViewModel()
        vm.selectMyGreenhouse()
        assertFalse(vm.state.value.canOpenPreview)
        assertNotNull(vm.state.value.lengthError)

        vm.onLengthInput("NaN")
        vm.onWidthInput("Infinity")
        vm.onHeightInput("0")
        assertFalse(vm.state.value.canOpenPreview)
        assertEquals("Enter a finite number", vm.state.value.lengthError)
        assertEquals("Enter a finite number", vm.state.value.widthError)
        assertEquals("Height must be greater than 0 m", vm.state.value.heightError)

        vm.onLengthInput("8")
        vm.onWidthInput("4")
        vm.onHeightInput("3")
        assertTrue(vm.state.value.canOpenPreview)
        val dims = vm.state.value.resolvedDimensions()!!
        assertEquals(8.0, dims.lengthM, 0.0)
        assertEquals(4.0, dims.widthM, 0.0)
        assertEquals(3.0, dims.heightM, 0.0)
        assertEquals(ProvenanceClass.MEASURED_INPUT, dims.provenance)
        assertTrue(dims.provenance != ProvenanceClass.PREVIEW_ASSUMPTION)
    }

    @Test
    fun cropRowValidationAndDemoAlwaysUsesThreeRows() {
        val vm = PreviewSetupViewModel()
        vm.selectDemo()
        vm.onCropRowInput("20")
        assertTrue(vm.state.value.canOpenPreview)
        assertEquals(3, vm.state.value.resolvedCropRowCount())
        assertEquals(DemoGreenhouseProfile.CROP_ROW_COUNT, vm.state.value.resolvedCropRowCount())

        vm.selectMyGreenhouse()
        vm.onLengthInput("8")
        vm.onWidthInput("4")
        vm.onHeightInput("3")
        vm.onCropRowInput("3")
        assertTrue(vm.state.value.canOpenPreview)
        assertEquals(3, vm.state.value.resolvedCropRowCount())

        listOf("", "0", "-1", "3.5", "abc", "21").forEach { raw ->
            vm.onCropRowInput(raw)
            assertFalse(vm.state.value.canOpenPreview)
            assertNotNull(vm.state.value.cropRowError)
            assertNull(vm.state.value.resolvedCropRowCount())
        }
        vm.onCropRowInput("1")
        assertTrue(vm.state.value.canOpenPreview)
        vm.onCropRowInput("20")
        assertTrue(vm.state.value.canOpenPreview)
        assertEquals(20, vm.state.value.resolvedCropRowCount())
    }
}

class PreviewCameraFramerTest {

    private fun dims(length: Double, width: Double, height: Double) = GreenhouseDimensions(
        lengthM = length,
        widthM = width,
        heightM = height,
        provenance = ProvenanceClass.MEASURED_INPUT,
        provenanceNote = "test"
    )

    @Test
    fun orbitTargetIsTheVisualCentre() {
        val layout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
        val bounds = PreviewCameraFramer.visualBounds(layout)
        val pose = PreviewCameraFramer.frame(layout, PreviewCameraFramer.PORTRAIT_ASPECT)
        assertEquals(bounds.centerX, pose.targetX, 1e-9)
        assertEquals(bounds.centerY, pose.targetY, 1e-9)
        assertEquals(bounds.centerZ, pose.targetZ, 1e-9)
        assertEquals(0.0, pose.targetX, 1e-6)
        assertEquals(0.0, pose.targetZ, 1e-6)
        assertTrue(pose.targetY > 0.0)
        assertTrue(pose.targetY < layout.extentY)
    }

    @Test
    fun demoAndMyGreenhouseFitPortraitViewportIncludingRoof() {
        val cases = listOf(
            DemoGreenhouseProfile.dimensions(),
            dims(50.0, 20.0, 2.0)
        )
        cases.forEach { dimensions ->
            val layout = SceneScaleMapper.layout(dimensions)
            val pose = PreviewCameraFramer.frame(layout, PreviewCameraFramer.PORTRAIT_ASPECT)
            val envelope = PreviewCameraFramer.greenhouseEnvelopeBounds(layout)
            val visual = PreviewCameraFramer.visualBounds(layout)
            envelope.corners().forEach { (x, y, z) ->
                assertTrue(
                    "envelope corner ($x,$y,$z) clipped for ${dimensions.lengthM}×${dimensions.widthM}×${dimensions.heightM}",
                    PreviewCameraFramer.fitsInPortraitFrame(x, y, z, pose)
                )
            }
            visual.corners().forEach { (x, y, z) ->
                assertTrue(
                    "visual corner ($x,$y,$z) clipped for ${dimensions.lengthM}×${dimensions.widthM}×${dimensions.heightM}",
                    PreviewCameraFramer.fitsInPortraitFrame(x, y, z, pose)
                )
            }
            assertTrue(pose.eyeY > layout.extentY)
            assertTrue(pose.distance > layout.extentX)
            assertTrue(pose.farPlane > pose.distance + visual.radius)
            assertEquals(
                pose.distance,
                kotlin.math.sqrt(
                    (pose.eyeX - pose.targetX) * (pose.eyeX - pose.targetX) +
                        (pose.eyeY - pose.targetY) * (pose.eyeY - pose.targetY) +
                        (pose.eyeZ - pose.targetZ) * (pose.eyeZ - pose.targetZ)
                ),
                1e-9
            )
        }
    }

    @Test
    fun resetUsesTheSameHomePoseAsTheInitialFrame() {
        val layout = SceneScaleMapper.layout(dims(50.0, 20.0, 2.0))
        val initial = PreviewCameraFramer.frame(layout, PreviewCameraFramer.PORTRAIT_ASPECT)
        val reset = PreviewCameraFramer.frame(layout, PreviewCameraFramer.PORTRAIT_ASPECT)
        assertEquals(initial, reset)
    }

    @Test
    fun framingIsNotADemoOnlyHardCodedEye() {
        val demoLayout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
        val wideLayout = SceneScaleMapper.layout(dims(50.0, 20.0, 2.0))
        val demo = PreviewCameraFramer.frame(demoLayout, PreviewCameraFramer.PORTRAIT_ASPECT)
        val wide = PreviewCameraFramer.frame(wideLayout, PreviewCameraFramer.PORTRAIT_ASPECT)
        assertFalse(demo.eyeX == wide.eyeX && demo.eyeY == wide.eyeY && demo.eyeZ == wide.eyeZ)
        assertTrue(kotlin.math.abs(demo.eyeY - wide.eyeY) > 0.5)

        val previous = previousHardCodedEye(demoLayout)
        val previousPose = PreviewCameraPose(
            eyeX = previous.first,
            eyeY = previous.second,
            eyeZ = previous.third,
            targetX = 0.0,
            targetY = demoLayout.extentY * 0.32,
            targetZ = 0.0,
            distance = kotlin.math.sqrt(
                previous.first * previous.first +
                    (previous.second - demoLayout.extentY * 0.32) *
                    (previous.second - demoLayout.extentY * 0.32) +
                    previous.third * previous.third
            ),
            farPlane = 30.0,
            viewportAspect = PreviewCameraFramer.PORTRAIT_ASPECT
        )
        val envelope = PreviewCameraFramer.greenhouseEnvelopeBounds(demoLayout)
        val previousClips = envelope.corners().any { (x, y, z) ->
            !PreviewCameraFramer.fitsInPortraitFrame(x, y, z, previousPose)
        }
        assertTrue(previousClips)
        assertTrue(demo.farPlane > 30.0)
        assertTrue(wide.farPlane > 30.0)
    }

    @Test
    fun lensConstantsMatchSceneViewTwentyTwoTwoOne() {
        assertEquals(28.0, PreviewCameraFramer.FOCAL_LENGTH_MM, 0.0)
        assertEquals(24.0, PreviewCameraFramer.SENSOR_HEIGHT_MM, 0.0)
        assertEquals(46.4, PreviewCameraFramer.verticalFovDegrees, 0.1)
    }

    private fun previousHardCodedEye(layout: SceneLayout) =
        Triple(
            layout.extentX * 0.58,
            layout.extentY * 0.95,
            layout.extentZ * 0.62 + layout.extentX * 0.42
        )
}
