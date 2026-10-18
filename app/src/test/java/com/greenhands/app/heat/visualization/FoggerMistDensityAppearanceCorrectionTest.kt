package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.render.FogCloudFlake
import com.greenhands.app.ar.render.FogDropletMesh
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class FoggerMistDensityAppearanceCorrectionTest {

    private val thresholds = Phase3D2AFixtures.thresholds

    @Before
    fun clearSampler() {
        SnapshotFieldSampler.clearCache()
    }

    @Test
    fun representativeTimesKeepAVisibleCloudNotAHandfulOfLines() {
        val group = onGroup("fog-density-count")
        assertEquals(FogMistVisual.MAX_PARTICLES_PER_FOGGER, group.particleCount)
        assertTrue(group.particleCount > FogMistVisual.MAX_SCENE_NODES_PER_FOGGER)
        assertEquals(3, FogMistVisual.MAX_SCENE_NODES_PER_FOGGER)
        listOf(0.0, 0.45, 0.95, 1.55).forEach { time ->
            val visible = FoggerMistAnimator.visibleParticleCount(group, time)
            assertTrue("visible flakes at t=$time was $visible", visible >= FogMistVisual.MIN_VISIBLE_PARTICLES_PER_FOGGER)
        }
        assertFalse(FogMistAppearance.RAIN_LIKE_VERTICAL_STREAKS)
        assertTrue(FogMistAppearance.CROSSED_PLANES)
        assertTrue(FogDropletMesh.CROSSED_PLANES)
    }

    @Test
    fun crossedMicroFlakesStayVisibleFromOrthogonalViewsAndAreNotRainStreaks() {
        val flake = FogCloudFlake(
            0f, 0f, 0f,
            FogMistAppearance.DROPLET_SIZE_M.toFloat(),
            FogMistAppearance.STREAK_LENGTH_M.toFloat(),
            1f, 0.28f, -0.88f, 0.38f
        )
        val verts = FogDropletMesh.verticesOf(listOf(flake))
        assertEquals(FogDropletMesh.VERTICES_PER_FLAKE, verts.size)
        assertEquals(24, FogDropletMesh.VERTICES_PER_FLAKE)
        assertTrue(FogDropletMesh.visibleFromOrthogonalViews(verts))
        assertTrue(
            FogDropletMesh.clampedAspect(flake.size, flake.streak) <=
                FogMistAppearance.MAX_STREAK_TO_WIDTH + 1e-6f
        )
        assertTrue(
            FogMistAppearance.STREAK_LENGTH_M / FogMistAppearance.DROPLET_SIZE_M <= 2.0
        )
        assertTrue(
            FogMistAppearance.FAR_STREAK_LENGTH_M / FogMistAppearance.FAR_DROPLET_SIZE_M <= 2.0
        )
        assertTrue(
            FogDropletMesh.clampedAspect(0.04f, 0.20f) <=
                FogMistAppearance.MAX_STREAK_TO_WIDTH + 1e-6f
        )
        val a = FogMistDistribution.tiltedAxis("fog-tilt-a", 0, 0.0, 0.0, -1.0)
        val b = FogMistDistribution.tiltedAxis("fog-tilt-a", 1, 0.0, 0.0, -1.0)
        val c = FogMistDistribution.tiltedAxis("fog-tilt-b", 0, 0.0, 0.0, -1.0)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertTrue(abs(a.third) < 0.95)
        assertTrue(FogMistAppearance.NEAR_ALPHA > FogMistAppearance.MID_ALPHA)
        assertTrue(FogMistAppearance.MID_ALPHA > FogMistAppearance.FAR_ALPHA)
        assertTrue(FogMistAppearance.FAR_ALPHA >= 0.38f)
        assertTrue(FogMistAppearance.MATERIAL_ALPHA < 0.50f)
    }

    @Test
    fun stratifiedConeFillsQuadrantsAndWidensWithDistance() {
        val group = onGroup("fog-density-cone")
        assertTrue(FogMistDistribution.hasCentreAndOuterRadialSamples(group.particles))
        assertTrue(FogMistDistribution.uniqueAzimuthBuckets(group.particles) >= 4)
        assertTrue(FogMistDistribution.uniqueDistanceLayers(group.particles) >= 6)
        listOf(0.35, 0.90, 1.40).forEach { time ->
            val points = FoggerMistAnimator.particlePositions(group, time)
            val origin = group.nozzleOrigin(0)
            assertTrue(FogMistDistribution.occupiesAllRadialQuadrants(points, origin))
            assertTrue(FogMistDistribution.coversMultipleRadialDirections(points, origin))
            assertFalse(FogMistDistribution.areCollinear(points))
            val alongs = points.mapIndexed { index, point ->
                FoggerMistAnimator.alongSprayM(group, point, group.particles[index].nozzleIndex)
            }
            val near = FogMistDistribution.meanRadiusInAlongBand(
                points, alongs, origin,
                group.maxDistanceM * 0.15,
                group.maxDistanceM * 0.35
            )
            val far = FogMistDistribution.meanRadiusInAlongBand(
                points, alongs, origin,
                group.maxDistanceM * 0.65,
                group.maxDistanceM * 0.90
            )
            assertTrue("near radius $near far radius $far at t=$time", far > near * 1.15)
            points.forEachIndexed { index, point ->
                assertTrue(
                    FoggerMistAnimator.insideAcceptedCone(
                        group, point, group.particles[index].nozzleIndex
                    )
                )
            }
        }
        assertTrue(group.particles.none { it.radialFraction < FogMistVisual.MIN_RADIAL_FRACTION - 1e-9 })
    }

    @Test
    fun canopyAttenuationIsAbsoluteBoundedAndNotCumulative() {
        val canopy = DemoGreenhouseProfile.HEIGHT_M * CropRowGeometry.HEIGHT_FILL
        assertTrue(FoggerVisualEnvelope.CANOPY_IS_ABSOLUTE_MULTIPLIER)
        assertFalse(FoggerVisualEnvelope.CANOPY_IS_CUMULATIVE)
        assertFalse(FoggerVisualEnvelope.HARD_CLIPS_CANOPY)
        assertTrue(FoggerVisualEnvelope.MIN_CANOPY_ALPHA in 0.35f..0.45f)
        val above = FoggerVisualEnvelope.canopyAttenuation(canopy + 0.3, canopy)
        val mid = FoggerVisualEnvelope.canopyAttenuation(canopy * 0.5, canopy)
        val deep = FoggerVisualEnvelope.canopyAttenuation(0.08, canopy)
        assertEquals(1f, above, 0f)
        assertTrue(mid < above)
        assertTrue(deep < mid)
        assertTrue(deep >= FoggerVisualEnvelope.MIN_CANOPY_ALPHA)
        assertEquals(deep, FoggerVisualEnvelope.canopyAttenuation(0.08, canopy), 0f)
        assertTrue(deep * deep < deep)
        val group = onGroup("fog-density-canopy")
        val time = 0.70
        group.particles.indices.forEach { index ->
            val point = FoggerMistAnimator.particleDomainPoint(group, index, time)
            val travel = FoggerMistAnimator.fadeAtProgress(
                FoggerMistAnimator.particleProgress(group, index, time)
            )
            val canopyMul = FoggerVisualEnvelope.canopyAttenuation(point.zM, group.canopyHeightM)
            val presence = FoggerMistAnimator.visualPresence(group, index, time)
            if (FoggerVisualEnvelope.insideHardBoundaries(
                    point,
                    group.houseLengthM,
                    group.houseWidthM,
                    group.houseHeightM
                )
            ) {
                assertEquals(travel * canopyMul, presence, 1e-5f)
            }
        }
        assertTrue(FoggerMistAnimator.particlePositions(group, 0.55).any { it.zM < canopy - 0.05 })
    }

    @Test
    fun fourOnFoggersEachRenderWithinTheDeclaredGlobalCap() {
        val foggers = listOf(
            overheadFogger("fog-cap-1", 1, 1.2, 2.0),
            overheadFogger("fog-cap-2", 2, 3.8, 2.0),
            overheadFogger("fog-cap-3", 3, 1.2, 8.0),
            overheadFogger("fog-cap-4", 4, 3.8, 8.0)
        )
        val runtimeById = foggers.associate { it.id to EquipmentRuntimeState.ON }
        val snapshot = PhysicsFieldRuntime().run {
            val snap = start(
                Phase3D2AFixtures.demoRequest(
                    foggers,
                    35.0,
                    40.0,
                    weather = WeatherBoundary.demoSimulated(25.0, 70.0),
                    lengthM = DemoGreenhouseProfile.LENGTH_M,
                    widthM = DemoGreenhouseProfile.WIDTH_M,
                    heightM = DemoGreenhouseProfile.HEIGHT_M,
                    runtimeById = runtimeById
                )
            )
            stop()
            snap
        }
        val frame = FoggerEffectPlanner.plan(true, foggers, runtimeById, snapshot, thresholds)
        assertEquals(4, FogMistVisual.MAX_FOGGER_GROUPS)
        assertEquals(144, FogMistVisual.MAX_PARTICLES_PER_FOGGER)
        assertEquals(576, FogMistVisual.MAX_TOTAL_PARTICLES)
        assertEquals(
            FogMistVisual.MAX_FOGGER_GROUPS * FogMistVisual.MAX_PARTICLES_PER_FOGGER,
            FogMistVisual.MAX_TOTAL_PARTICLES
        )
        assertEquals(4, frame.groups.size)
        assertEquals(FogMistVisual.MAX_FOGGER_GROUPS, frame.groups.size)
        frame.groups.forEach { group ->
            assertEquals(FogMistVisual.MAX_PARTICLES_PER_FOGGER, group.particleCount)
        }
        assertEquals(FogMistVisual.MAX_TOTAL_PARTICLES, frame.particleObjectCount)
        assertEquals(FogMistVisual.MAX_TOTAL_SCENE_NODES, frame.sceneNodeObjectCount)
        assertTrue(frame.particleObjectCount <= FogMistVisual.MAX_TOTAL_PARTICLES)
        assertTrue(FogMistDistribution.combinedMeshIncreasesVisibleSpecksWithoutNodeGrowth())
        assertEquals(3, FogMistVisual.MAX_SCENE_NODES_PER_FOGGER)
        assertEquals(12, FogMistVisual.MAX_TOTAL_SCENE_NODES)
    }

    private fun onGroup(id: String): FoggerMistGroup {
        val fog = overheadFogger(id, 1, 2.5, 5.0)
        val snapshot = PhysicsFieldRuntime().run {
            val snap = start(
                Phase3D2AFixtures.demoRequest(
                    listOf(fog),
                    35.0,
                    40.0,
                    weather = WeatherBoundary.demoSimulated(25.0, 70.0),
                    lengthM = DemoGreenhouseProfile.LENGTH_M,
                    widthM = DemoGreenhouseProfile.WIDTH_M,
                    heightM = DemoGreenhouseProfile.HEIGHT_M,
                    runtimeById = mapOf(fog.id to EquipmentRuntimeState.ON)
                )
            )
            stop()
            snap
        }
        return FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        ).groups.single()
    }

    private fun overheadFogger(id: String, number: Int, xM: Double, yM: Double): EquipmentInstance =
        Phase3D2AFixtures.instance(
            EquipmentType.FOGGER, id, number, xM = xM, yM = yM, zM = 2.2, yawDeg = 0.0
        )
}
