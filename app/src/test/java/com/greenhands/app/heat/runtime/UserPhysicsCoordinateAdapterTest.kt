package com.greenhands.app.heat.runtime

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.adapter.AdaptiveGridPolicy
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.simulation.model.GreenhouseGeometry
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.SimulationException
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class UserPhysicsCoordinateAdapterTest {

    @Test
    fun userXyzMapsToPhase3ALengthWidthHeight() {
        val domain = DomainPoint(xM = 2.0, yM = 7.0, zM = 1.5)
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
        assertEquals(7.0, physics.xM, 0.0)
        assertEquals(2.0, physics.yM, 0.0)
        assertEquals(1.5, physics.zM, 0.0)
        val roundTrip = UserPhysicsCoordinateAdapter.positionToDomain(physics)
        assertEquals(domain, roundTrip)
    }

    @Test
    fun orientationYawZeroFacesUserPlusXWhichIsPhysicsPlusY() {
        val forward = UserPhysicsCoordinateAdapter.yawToPhysicsForward(0.0)
        assertEquals(0.0, forward.xM, 1e-12)
        assertEquals(1.0, forward.yM, 1e-12)
        assertEquals(0.0, forward.zM, 1e-12)
        val yaw90 = UserPhysicsCoordinateAdapter.yawToPhysicsForward(90.0)
        assertEquals(1.0, yaw90.xM, 1e-12)
        assertEquals(0.0, yaw90.yM, 1e-12)
        assertEquals(0.0, UserPhysicsCoordinateAdapter.physicsForwardToYawDeg(forward), 1e-9)
        assertEquals(90.0, UserPhysicsCoordinateAdapter.physicsForwardToYawDeg(yaw90), 1e-9)
        val indoor = UserPhysicsCoordinateAdapter.yawToPhysicsIndoorApproach(0.0)
        assertEquals(0.0, indoor.xM, 1e-12)
        assertEquals(-1.0, indoor.yM, 1e-12)
        val spray = UserPhysicsCoordinateAdapter.foggerSprayPhysics()
        assertEquals(0.0, spray.xM, 1e-12)
        assertEquals(0.0, spray.yM, 1e-12)
        assertEquals(-1.0, spray.zM, 1e-12)
    }

    @Test
    fun demo10x5x3BoundsArePreserved() {
        val length = DemoGreenhouseProfile.LENGTH_M
        val width = DemoGreenhouseProfile.WIDTH_M
        val height = DemoGreenhouseProfile.HEIGHT_M
        assertEquals(10.0, length, 0.0)
        assertEquals(5.0, width, 0.0)
        assertEquals(3.0, height, 0.0)
        val corners = listOf(
            DomainPoint(0.0, 0.0, 0.0),
            DomainPoint(width, length, height),
            DomainPoint(width / 2.0, length / 2.0, height / 2.0)
        )
        corners.forEach { domain ->
            assertTrue(UserPhysicsCoordinateAdapter.inUserBounds(domain, width, length, height))
            val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
            assertTrue(UserPhysicsCoordinateAdapter.inPhysicsBounds(physics, length, width, height))
            assertEquals(domain, UserPhysicsCoordinateAdapter.positionToDomain(physics))
        }
        val geometry = UserPhysicsCoordinateAdapter.geometry(length, width, height, 0.50)
        assertEquals(length, geometry.lengthM, 0.0)
        assertEquals(width, geometry.widthM, 0.0)
        assertEquals(height, geometry.wallHeightM, 0.0)
    }

    @Test
    fun customGreenhouseDimensionsRoundTrip() {
        val length = 12.5
        val width = 7.25
        val height = 4.0
        val domain = DomainPoint(3.1, 8.4, 2.2)
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
        assertEquals(Vec3(8.4, 3.1, 2.2), physics)
        assertEquals(domain, UserPhysicsCoordinateAdapter.positionToDomain(physics))
        assertTrue(UserPhysicsCoordinateAdapter.inPhysicsBounds(physics, length, width, height))
        val yaw = -45.0
        val recovered = UserPhysicsCoordinateAdapter.physicsForwardToYawDeg(
            UserPhysicsCoordinateAdapter.yawToPhysicsForward(yaw)
        )
        assertTrue(abs(recovered - yaw) < 1e-9)
    }
}

class AdaptiveGridPolicyTest {

    @Test
    fun preferredSpacingIsKeptWhenUnderTheCellCap() {
        val selected = AdaptiveGridPolicy.select(10.0, 5.0, 3.0)
        assertEquals(GreenhouseGeometry.DEFAULT_CELL_EDGE_M, selected.cellEdgeM, 0.0)
        assertTrue(selected.withinCellLimit)
        assertTrue(selected.cellCount <= GreenhouseGeometry.MAX_ACTIVE_CELLS_PHASE_3A)
        assertTrue(selected.requiresOppoBenchmarking)
        assertEquals(20, selected.nx)
        assertEquals(10, selected.ny)
        assertEquals(6, selected.nz)
    }

    @Test
    fun coarsensWhilePreservingProportionsUntilTheCellLimit() {
        val selected = AdaptiveGridPolicy.select(
            lengthM = 40.0,
            widthM = 20.0,
            heightM = 8.0,
            config = AdaptiveGridPolicy.Config(
                preferredCellEdgeM = 0.25,
                maximumCellCount = 8_000
            )
        )
        assertTrue(selected.withinCellLimit)
        assertTrue(selected.cellCount <= 8_000)
        assertTrue(selected.cellEdgeM > 0.25)
        assertTrue(selected.nx.toDouble() / selected.ny.toDouble() > 1.5)
        assertTrue(selected.requiresOppoBenchmarking)
    }

    @Test(expected = SimulationException::class)
    fun rejectsNonPositiveDimensions() {
        AdaptiveGridPolicy.select(10.0, 0.0, 3.0)
    }
}
