package com.greenhands.app

import com.greenhands.app.sensor.ar.ArDirectionTapResult
import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArWorldMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ArWorldMapperTest {

    @Test
    fun localOriginMapsToWorldOrigin() {
        val pose = ArWorldMapper.alignedPose(2f, 0.1f, -3f, forwardX = 1f, forwardZ = 0f)
        val w = ArWorldMapper.localToWorld(pose, 0f, 0f, 0f)!!
        assertEquals(2f, w.x, 1e-5f)
        assertEquals(0.1f, w.y, 1e-5f)
        assertEquals(-3f, w.z, 1e-5f)
    }

    @Test
    fun localPlusXFollowsSelectedLengthDirection() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, forwardX = 0f, forwardZ = 1f)
        val w = ArWorldMapper.localToWorld(pose, 1f, 0f, 0f)!!
        assertEquals(0f, w.x, 1e-5f)
        assertEquals(0f, w.y, 1e-5f)
        assertEquals(1f, w.z, 1e-5f)
    }

    @Test
    fun localPlusZIsPerpendicularWidthOnFloor() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, forwardX = 1f, forwardZ = 0f)
        val w = ArWorldMapper.localToWorld(pose, 0f, 0f, 1f)!!
        assertEquals(0f, w.x, 1e-5f)
        assertEquals(0f, w.y, 1e-5f)
        assertEquals(1f, w.z, 1e-5f)
    }

    @Test
    fun localYRemainsVertical() {
        val pose = ArWorldMapper.alignedPose(1f, 2f, 3f, forwardX = 0.6f, forwardZ = 0.8f)
        val w = ArWorldMapper.localToWorld(pose, 0f, 1.5f, 0f)!!
        assertEquals(1f, w.x, 1e-5f)
        assertEquals(3.5f, w.y, 1e-5f)
        assertEquals(3f, w.z, 1e-5f)
    }

    @Test
    fun nearZeroDirectionIsRejected() {
        val result = ArWorldMapper.directionFromPoints(
            0f, 0f, 0f,
            0.1f, 0f, 0.1f
        )
        assertTrue(result is ArWorldMapper.DirectionResult.TooClose)
        assertTrue(sqrt(0.1f * 0.1f + 0.1f * 0.1f) < ArWorldMapper.MIN_DIRECTION_METERS)
    }

    @Test
    fun directionNormalizationAndYaw() {
        val result = ArWorldMapper.directionFromPoints(0f, 0f, 0f, 3f, 1f, 4f)
        val ok = result as ArWorldMapper.DirectionResult.Ok
        assertEquals(1f, sqrt(ok.forwardX * ok.forwardX + ok.forwardZ * ok.forwardZ), 1e-5f)
        assertEquals(atan2(ok.forwardZ, ok.forwardX), ok.yawRadians, 1e-5f)
        assertEquals(5f, ok.horizontalLength, 1e-5f)
    }

    @Test
    fun sameOriginAndDirectionAreDeterministic() {
        val a = ArWorldMapper.directionFromPoints(1f, 0f, 2f, 4f, 0f, 6f)
        val b = ArWorldMapper.directionFromPoints(1f, 0f, 2f, 4f, 0f, 6f)
        assertEquals(a, b)
        val pose = ArWorldMapper.alignedPose(1f, 0f, 2f, 3f, 4f)
        val p1 = ArWorldMapper.localToWorld(pose, 2f, 0.5f, -1f)!!
        val p2 = ArWorldMapper.localToWorld(pose, 2f, 0.5f, -1f)!!
        assertEquals(p1, p2)
    }

    @Test
    fun differentYawProducesDifferentWorldPoints() {
        val alongX = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val alongZ = ArWorldMapper.alignedPose(0f, 0f, 0f, 0f, 1f)
        val wx = ArWorldMapper.localToWorld(alongX, 1f, 0f, 0f)!!
        val wz = ArWorldMapper.localToWorld(alongZ, 1f, 0f, 0f)!!
        assertTrue(abs(wx.x - wz.x) > 0.5f || abs(wx.z - wz.z) > 0.5f)
    }

    @Test
    fun resetAlignmentClearsYawAndDirection() {
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 0f, 0f, 0f)
        state = ArOriginPlacementController.beginSetDirection(state)
        val (aligned, result) = ArOriginPlacementController.onDirectionPoint(state, 2f, 0f, 0f)
        assertEquals(ArDirectionTapResult.OK, result)
        assertNotNull(aligned.yawRadians)
        val reset = ArOriginPlacementController.resetAlignment(aligned)
        assertEquals(ArOriginPlacementPhase.SETTING_DIRECTION, reset.phase)
        assertNull(reset.yawRadians)
        assertNull(reset.forwardX)
        assertEquals(0f, reset.worldTranslationX!!, 1e-5f)
        assertTrue(ArOriginPlacementController.canAcceptDirectionTap(reset))
    }

    @Test
    fun originResetClearsCompletePose() {
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 1f, 0f, 1f)
        state = ArOriginPlacementController.beginSetDirection(state)
        state = ArOriginPlacementController.onDirectionPoint(state, 3f, 0f, 1f).first
        val cleared = ArOriginPlacementController.resetOrigin(state)
        assertEquals(ArOriginPlacementPhase.SCANNING, cleared.phase)
        assertNull(cleared.worldTranslationX)
        assertNull(cleared.yawRadians)
        assertFalse(cleared.isOriginPlaced)
    }

    @Test
    fun rightHandedBasisMatchesCosSinForm() {
        val yaw = 0.7f
        val fx = cos(yaw)
        val fz = sin(yaw)
        val pose = ArWorldMapper.alignedPose(5f, 1f, -2f, fx, fz)
        assertEquals(yaw, pose.yawRadians!!, 1e-5f)
        val lx = 2f
        val ly = 0.3f
        val lz = -1.5f
        val w = ArWorldMapper.localToWorld(pose, lx, ly, lz)!!
        val expectedX = 5f + lx * fx - lz * fz
        val expectedY = 1f + ly
        val expectedZ = -2f + lx * fz + lz * fx
        assertEquals(expectedX, w.x, 1e-5f)
        assertEquals(expectedY, w.y, 1e-5f)
        assertEquals(expectedZ, w.z, 1e-5f)
    }

    @Test
    fun worldToLocalIsInverseOfLocalToWorld() {
        val pose = ArWorldMapper.alignedPose(2f, 0.5f, -1f, forwardX = 0f, forwardZ = 1f)
        val world = ArWorldMapper.localToWorld(pose, 4f, 0.8f, 2f)!!
        val local = ArWorldMapper.worldToLocal(pose, world.x, world.y, world.z)!!
        assertEquals(4f, local.x, 1e-4f)
        assertEquals(0.8f, local.y, 1e-4f)
        assertEquals(2f, local.z, 1e-4f)
    }

    @Test
    fun directionTooCloseViaController() {
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 0f, 0f, 0f)
        state = ArOriginPlacementController.beginSetDirection(state)
        val (same, result) = ArOriginPlacementController.onDirectionPoint(state, 0.05f, 0f, 0.05f)
        assertEquals(ArDirectionTapResult.TOO_CLOSE, result)
        assertEquals(ArOriginPlacementPhase.SETTING_DIRECTION, same.phase)
    }
}
