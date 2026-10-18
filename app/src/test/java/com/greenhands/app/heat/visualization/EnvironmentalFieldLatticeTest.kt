package com.greenhands.app.heat.visualization

import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentalFieldLatticeTest {

    @Test
    fun sameDimensionsProduceTheSameLattice() {
        val first = EnvironmentalFieldLattice.plan(5.0, 10.0, 3.0, canopyHeightM = 0.78)!!
        val second = EnvironmentalFieldLattice.plan(5.0, 10.0, 3.0, canopyHeightM = 0.78)!!
        assertEquals(first, second)
        assertEquals(first.positions, second.positions)
        assertEquals(first.levels, second.levels)
    }

    @Test
    fun demoSamplesSpanMultipleXyzAndStayInsideEnvelope() {
        val lattice = EnvironmentalFieldLattice.plan(
            widthM = DemoGreenhouseProfile.WIDTH_M,
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            canopyHeightM = DemoGreenhouseProfile.HEIGHT_M * CropRowGeometry.HEIGHT_FILL
        )!!
        assertTrue(lattice.countX >= EnvironmentalFieldVisual.MIN_COUNT_X)
        assertTrue(lattice.countY >= EnvironmentalFieldVisual.MIN_COUNT_Y)
        assertEquals(3, lattice.countZ)
        val xs = lattice.positions.map { it.xM }.toSet()
        val ys = lattice.positions.map { it.yM }.toSet()
        val zs = lattice.positions.map { it.zM }.toSet()
        assertTrue(xs.size >= 3)
        assertTrue(ys.size >= 3)
        assertEquals(3, zs.size)
        lattice.positions.forEach { point ->
            assertTrue(point.xM > 0.0 && point.xM < DemoGreenhouseProfile.WIDTH_M)
            assertTrue(point.yM > 0.0 && point.yM < DemoGreenhouseProfile.LENGTH_M)
            assertTrue(point.zM > 0.0 && point.zM < DemoGreenhouseProfile.HEIGHT_M)
        }
        assertTrue(lattice.cellCount <= EnvironmentalFieldVisual.MAX_CELLS)
        assertTrue(lattice.positions.any { it.levelIs(lattice, EnvironmentalFieldLevel.CROP) })
        assertTrue(lattice.positions.any { it.levelIs(lattice, EnvironmentalFieldLevel.MIDDLE) })
        assertTrue(lattice.positions.any { it.levelIs(lattice, EnvironmentalFieldLevel.UPPER) })
        assertTrue(lattice.cropZM < lattice.middleZM)
        assertTrue(lattice.middleZM < lattice.upperZM)
    }

    @Test
    fun axisMappingKeepsXWidthYLengthZHeight() {
        val lattice = EnvironmentalFieldLattice.plan(5.0, 10.0, 3.0)!!
        val maxX = lattice.positions.maxOf { it.xM }
        val maxY = lattice.positions.maxOf { it.yM }
        val maxZ = lattice.positions.maxOf { it.zM }
        assertTrue(maxX <= 5.0)
        assertTrue(maxY <= 10.0)
        assertTrue(maxZ <= 3.0)
        assertTrue(maxY > maxX)
        assertTrue(lattice.spacingXM <= lattice.spacingYM + 1e-9)
    }

    @Test
    fun cropRowsDoNotHardClipEnvironmentalSamples() {
        val boxes = CropRowGeometry.domainBoxes(
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT
        )
        val lattice = EnvironmentalFieldLattice.plan(
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.HEIGHT_M * CropRowGeometry.HEIGHT_FILL
        )!!
        val insideRow = lattice.positions.any { point ->
            boxes.any { box ->
                point.xM >= box.minXM && point.xM <= box.maxXM &&
                    point.yM >= box.minYM && point.yM <= box.maxYM
            }
        }
        assertTrue(insideRow)
        assertTrue(lattice.cellCount > boxes.size)
    }

    @Test
    fun adaptiveGridRespectsTheCellCap() {
        val huge = EnvironmentalFieldLattice.plan(50.0, 80.0, 6.0)!!
        assertTrue(huge.cellCount <= EnvironmentalFieldVisual.MAX_CELLS)
        assertTrue(huge.countX >= 2)
        assertTrue(huge.countY >= 2)
        val tinyCap = EnvironmentalFieldLattice.plan(10.0, 10.0, 3.0, maxCells = 240)!!
        assertTrue(tinyCap.cellCount <= 240)
    }

    @Test
    fun myGreenhouseUsesOwnDimensionsAndDemoDoesNotLeak() {
        val mine = EnvironmentalFieldLattice.plan(4.0, 8.0, 2.5, canopyHeightM = 0.6)!!
        mine.positions.forEach { point ->
            assertTrue(point.xM < 4.0)
            assertTrue(point.yM < 8.0)
            assertTrue(point.zM < 2.5)
            assertFalse(point.xM > 5.0)
            assertFalse(point.yM > 10.0)
        }
        assertNotEquals(DemoGreenhouseProfile.WIDTH_M, mine.widthM, 0.0)
        assertNotEquals(DemoGreenhouseProfile.LENGTH_M, mine.lengthM, 0.0)
        assertNotEquals(DemoGreenhouseProfile.HEIGHT_M, mine.heightM, 0.0)
        val demo = EnvironmentalFieldLattice.plan(
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.HEIGHT_M
        )!!
        assertNotEquals(demo.positions, mine.positions)
    }

    @Test
    fun invalidDimensionsYieldNoLattice() {
        assertEquals(null, EnvironmentalFieldLattice.plan(0.0, 10.0, 3.0))
        assertEquals(null, EnvironmentalFieldLattice.plan(5.0, Double.NaN, 3.0))
        assertEquals(null, EnvironmentalFieldLattice.plan(5.0, 10.0, -1.0))
    }

    @Test
    fun cellCenteredAxisSamplesStayInsideTheSpan() {
        val xs = EnvironmentalFieldLattice.axisSamples(5.0, 5)
        assertEquals(listOf(0.5, 1.5, 2.5, 3.5, 4.5), xs)
        assertNotNull(xs)
    }

    private fun com.greenhands.app.equipment.model.DomainPoint.levelIs(
        lattice: EnvironmentalFieldLatticePlan,
        level: EnvironmentalFieldLevel
    ): Boolean {
        val index = lattice.positions.indexOf(this)
        return index >= 0 && lattice.levels[index] == level
    }
}
