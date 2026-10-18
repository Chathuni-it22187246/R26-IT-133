package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.simulation.model.GreenhouseGeometry
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler
import com.greenhands.app.heat.simulation.probe.ProbeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryGridTest {

    @Test
    fun roofMaskingExcludesCellsAboveGable() {
        val geometry = GreenhouseGeometry(
            lengthM = 4.0,
            widthM = 4.0,
            wallHeightM = 2.0,
            ridgeHeightM = 3.0,
            cellEdgeM = 0.50
        )
        val grid = GridState.build(geometry, 25.0, 0.01)
        val eaves = geometry.cellCenterM(2, 0, 4)
        val ridge = geometry.cellCenterM(2, 4, 5)
        assertEquals(2.25, eaves.zM, 1e-9)
        assertTrue("eaves cell at z=2.25 should be above wall-height roof", eaves.zM > geometry.roofHeightM(eaves.xM, eaves.yM))
        assertFalse(grid.active[geometry.index(2, 0, 4)])
        assertTrue("ridge cell at z=2.75 should sit under the 3 m ridge", ridge.zM < geometry.roofHeightM(ridge.xM, ridge.yM))
        assertTrue(grid.active[geometry.index(2, 4, 5)])
    }

    @Test
    fun activeCellVolumesAndMeans() {
        val geometry = TestEquipmentFixtures.boxGeometry(4.0, 3.0, 2.0, 0.50)
        val omega = Psychrometrics.humidityRatioKgKg(25.0, 50.0, 101325.0)
        val grid = GridState.build(geometry, 25.0, omega)
        val expected = geometry.nx * geometry.ny * geometry.nz
        assertEquals(expected, grid.activeCellCount)
        assertEquals(4.0 * 3.0 * 2.0, grid.totalActiveVolumeM3(), 1e-12)
        assertEquals(25.0, grid.meanTemperatureC(), 1e-12)
        assertEquals(omega, grid.meanHumidityRatioKgKg(), 1e-12)
    }

    @Test
    fun indexAndCoordinateConversionRoundTrip() {
        val geometry = TestEquipmentFixtures.boxGeometry(4.0, 3.0, 2.5, 0.50)
        val i = 3
        val j = 2
        val k = 1
        val idx = geometry.index(i, j, k)
        assertEquals(i, geometry.iOf(idx))
        assertEquals(j, geometry.jOf(idx))
        assertEquals(k, geometry.kOf(idx))
        val c = geometry.cellCenterM(i, j, k)
        assertEquals((i + 0.5) * 0.50, c.xM, 0.0)
        assertEquals((j + 0.5) * 0.50, c.yM, 0.0)
        assertEquals((k + 0.5) * 0.50, c.zM, 0.0)
    }

    @Test
    fun trilinearInterpolationMatchesKnownLinearField() {
        val geometry = TestEquipmentFixtures.boxGeometry(6.0, 3.0, 2.5, 0.50)
        val grid = GridState.build(geometry, 0.0, 0.01)
        grid.forEachActive { idx, i, j, k ->
            val c = grid.cellCenterM(i, j, k)
            grid.temperatureC[idx] = 10.0 + 2.0 * c.xM + 0.5 * c.yM + 0.25 * c.zM
        }
        val p = Vec3(2.3, 1.1, 1.2)
        val expected = 10.0 + 2.0 * p.xM + 0.5 * p.yM + 0.25 * p.zM
        val interpolated = grid.interpolateScalar(grid.temperatureC, p)!!
        // Trilinear interpolation of a linear field on a uniform grid is exact
        // up to floating-point round-off.
        assertEquals(expected, interpolated, 1e-10)
    }

    @Test
    fun outsideVolumeReturnsNullAndOutsideGreenhouseStatus() {
        val geometry = TestEquipmentFixtures.boxGeometry(4.0, 3.0, 2.0, 0.50)
        val grid = GridState.build(geometry, 25.0, 0.01)
        val outside = Vec3(4.2, 1.5, 1.0)
        assertFalse(geometry.contains(outside))
        assertEquals(null, grid.interpolateScalar(grid.temperatureC, outside))
        val sample = DistanceProbeSampler.samplePoint(grid, outside, 1.0, 101325.0)
        assertEquals(ProbeStatus.OUTSIDE_GREENHOUSE, sample.status)
        assertEquals(null, sample.temperatureC)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveDimensionsAreRejected() {
        GreenhouseGeometry(lengthM = 0.0, widthM = 3.0, wallHeightM = 2.0, ridgeHeightM = 2.0)
    }
}
