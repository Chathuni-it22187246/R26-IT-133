package com.greenhands.app

import com.greenhands.app.harvest.detection.HarvestFruitStabilityDiag
import com.greenhands.app.harvest.detection.HarvestScanDiag
import com.greenhands.app.harvest.detection.HarvestScanLiveSample
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.YoloTensorLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HarvestFruitStabilityDiagTest {

    @Before
    fun resetDiag() {
        HarvestFruitStabilityDiag.reset()
    }

    @Test
    fun lowConfidenceFruitFailsConfGateOnly() {
        val fruit = detection(
            ScanTargetType.TOMATO_FRUIT,
            confidence = 0.526f,
            box = centeredBox(area = 0.20f)
        )
        val snap = HarvestFruitStabilityDiag.observe(
            detections = listOf(fruit),
            selected = fruit,
            streak = 0
        )
        assertEquals(0.526f, snap.fruitSelConf!!, 0.0001f)
        assertEquals(0.526f, snap.fruitMaxConf!!, 0.0001f)
        assertEquals(HarvestFruitStabilityDiag.RESET_LOW_CONF, snap.resetReason)
        assertFalse(snap.gateConf)
        assertTrue(snap.gateSize)
        assertTrue(snap.gateCenter)
        assertFalse(snap.gateStable)
        assertNull(snap.fruitIou)
        assertEquals(0.0f, snap.fruitDx!!, 0.02f)
        assertEquals(0.0f, snap.fruitDy!!, 0.02f)
    }

    @Test
    fun leafOnlyIsWrongTargetAndKeepsMaxFruitNa() {
        val leaf = detection(ScanTargetType.TOMATO_LEAF, confidence = 0.91f)
        val snap = HarvestFruitStabilityDiag.observe(
            detections = listOf(leaf),
            selected = leaf,
            streak = 0
        )
        assertNull(snap.fruitSelConf)
        assertNull(snap.fruitMaxConf)
        assertEquals(HarvestFruitStabilityDiag.RESET_WRONG_TARGET, snap.resetReason)
        assertFalse(snap.gateConf)
        assertFalse(snap.gateSize)
        assertFalse(snap.gateCenter)
        assertFalse(snap.gateStable)
    }

    @Test
    fun highestFruitConfidenceIsLoggedWhenLeafIsSelected() {
        val fruit = detection(
            ScanTargetType.TOMATO_FRUIT,
            confidence = 0.48f,
            box = centeredBox(0.18f)
        )
        val leaf = detection(ScanTargetType.TOMATO_LEAF, confidence = 0.88f)
        val snap = HarvestFruitStabilityDiag.observe(
            detections = listOf(fruit, leaf),
            selected = leaf,
            streak = 0
        )
        assertNull(snap.fruitSelConf)
        assertEquals(0.48f, snap.fruitMaxConf!!, 0.0001f)
        assertEquals(fruit.boundingBox, snap.fruitBox)
        assertEquals(HarvestFruitStabilityDiag.RESET_LOW_CONF, snap.resetReason)
    }

    @Test
    fun iouUsesPreviousFruitBoxEvenAfterLockMiss() {
        val first = detection(
            ScanTargetType.TOMATO_FRUIT,
            confidence = 0.50f,
            box = NormalizedRect(0.30f, 0.30f, 0.70f, 0.70f)
        )
        HarvestFruitStabilityDiag.observe(listOf(first), first, streak = 0)
        val second = detection(
            ScanTargetType.TOMATO_FRUIT,
            confidence = 0.52f,
            box = NormalizedRect(0.32f, 0.32f, 0.72f, 0.72f)
        )
        val snap = HarvestFruitStabilityDiag.observe(listOf(second), second, streak = 0)
        assertTrue(snap.fruitIou != null && snap.fruitIou!! > 0.7f)
        assertEquals(HarvestFruitStabilityDiag.RESET_LOW_CONF, snap.resetReason)
    }

    @Test
    fun lockReadySimilarBoxesReportNoneNotATrackerReset() {
        val a = detection(
            ScanTargetType.TOMATO_FRUIT,
            confidence = 0.80f,
            box = NormalizedRect(0.30f, 0.30f, 0.70f, 0.70f)
        )
        val first = HarvestFruitStabilityDiag.observe(listOf(a), a, streak = 1)
        assertEquals(HarvestFruitStabilityDiag.RESET_FIRST, first.resetReason)
        assertTrue(first.gateConf && first.gateSize && first.gateCenter)
        assertFalse(first.gateStable)
        val b = detection(
            ScanTargetType.TOMATO_FRUIT,
            confidence = 0.81f,
            box = NormalizedRect(0.31f, 0.31f, 0.71f, 0.71f)
        )
        val second = HarvestFruitStabilityDiag.observe(listOf(b), b, streak = 2)
        assertEquals(HarvestFruitStabilityDiag.RESET_NONE, second.resetReason)
        assertTrue((second.fruitIou ?: 0f) > 0.8f)
        val stable = HarvestFruitStabilityDiag.observe(listOf(b), b, streak = 8)
        assertTrue(stable.gateStable)
        assertEquals(HarvestFruitStabilityDiag.RESET_NONE, stable.resetReason)
    }

    @Test
    fun jumpedLockBoxReportsIouReset() {
        val a = detection(
            ScanTargetType.TOMATO_FRUIT,
            confidence = 0.80f,
            box = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)
        )
        HarvestFruitStabilityDiag.observe(listOf(a), a, streak = 1)
        val b = detection(
            ScanTargetType.TOMATO_FRUIT,
            confidence = 0.82f,
            box = NormalizedRect(0.45f, 0.25f, 0.95f, 0.75f)
        )
        val snap = HarvestFruitStabilityDiag.observe(listOf(b), b, streak = 1)
        assertEquals(HarvestFruitStabilityDiag.RESET_IOU, snap.resetReason)
        assertTrue(snap.gateConf)
        assertTrue((snap.fruitIou ?: 1f) < 0.55f)
    }

    @Test
    fun singleLiveLineIncludesStabilityFields() {
        val box = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)
        val line = HarvestScanDiag.liveLine(
            HarvestScanLiveSample(
                cluster = false,
                convertMs = 10L,
                preprocessMs = 20L,
                inferMs = 400L,
                parseMs = 5L,
                totalMs = 650L,
                fps = 1.5f,
                rawFruitCount = 1,
                acceptedFruitCount = 0,
                selectedConfidence = 0.526f,
                selectedArea = box.area,
                reason = TargetRejectReason.LOW_CONFIDENCE,
                streak = 0,
                focusRequested = false,
                autoCapture = false,
                layoutCached = true,
                layout = YoloTensorLayout.CHANNEL_MAJOR,
                rawLeafCount = 0,
                selectedType = ScanTargetType.TOMATO_FRUIT,
                fruitSelConf = 0.526f,
                fruitMaxConf = 0.526f,
                fruitBox = box,
                fruitArea = box.area,
                fruitDx = 0f,
                fruitDy = 0f,
                fruitIou = 0.812f,
                stabilityResetReason = HarvestFruitStabilityDiag.RESET_LOW_CONF,
                gateConf = false,
                gateSize = true,
                gateCenter = true,
                gateStable = false
            )
        )
        assertTrue(line.contains("mode=SINGLE_LIVE"))
        assertTrue(line.contains("fruitSel=0.526"))
        assertTrue(line.contains("fruitMax=0.526"))
        assertTrue(line.contains("fruitBox=ltrb=0.250,0.250,0.750,0.750"))
        assertTrue(line.contains("fruitArea=0.250"))
        assertTrue(line.contains("dx=+0.000"))
        assertTrue(line.contains("dy=+0.000"))
        assertTrue(line.contains("iou=0.812"))
        assertTrue(line.contains("reset=LOW_CONF"))
        assertTrue(line.contains("gateConf=0"))
        assertTrue(line.contains("gateSize=1"))
        assertTrue(line.contains("gateCenter=1"))
        assertTrue(line.contains("gateStable=0"))
        assertTrue(line.contains("reason=LOW_CONFIDENCE"))
    }

    @Test
    fun clusterLiveOmitsFruitStabilitySuffix() {
        val line = HarvestScanDiag.liveLine(
            HarvestScanLiveSample(
                cluster = true,
                convertMs = 10L,
                preprocessMs = 20L,
                inferMs = 400L,
                parseMs = 5L,
                totalMs = 650L,
                fps = 1.5f,
                rawFruitCount = 2,
                acceptedFruitCount = 2,
                selectedConfidence = 0.80f,
                selectedArea = 0.12f,
                reason = null,
                streak = 8,
                focusRequested = false,
                autoCapture = false,
                layoutCached = true,
                fruitSelConf = 0.80f,
                fruitMaxConf = 0.80f,
                stabilityResetReason = HarvestFruitStabilityDiag.RESET_NONE,
                gateConf = true
            )
        )
        assertTrue(line.contains("mode=CLUSTER_LIVE"))
        assertFalse(line.contains("fruitSel="))
        assertFalse(line.contains("reset="))
        assertFalse(line.contains("gateConf="))
    }

    @Test
    fun resetClearsPreviousFruitBox() {
        val fruit = detection(ScanTargetType.TOMATO_FRUIT, 0.70f, centeredBox(0.20f))
        HarvestFruitStabilityDiag.observe(listOf(fruit), fruit, 1)
        HarvestFruitStabilityDiag.reset()
        val again = HarvestFruitStabilityDiag.observe(listOf(fruit), fruit, 1)
        assertNull(again.fruitIou)
        assertEquals(HarvestFruitStabilityDiag.RESET_FIRST, again.resetReason)
    }

    @Test
    fun lockThresholdsAreUnchanged() {
        assertEquals(0.65f, TargetDetectionCalibration.MIN_DETECTION_CONFIDENCE, 0.0f)
        assertEquals(0.25f, TargetDetectionCalibration.DETECTOR_SCORE_FLOOR, 0.0f)
        assertEquals(8, TargetDetectionCalibration.REQUIRED_STABLE_FRAMES)
        assertEquals(0.55f, TargetDetectionCalibration.STABILITY_IOU_MIN, 0.0f)
        assertEquals(0.10f, TargetDetectionCalibration.MIN_TARGET_AREA_RATIO, 0.0f)
        assertEquals(0.22f, TargetDetectionCalibration.CENTER_TOLERANCE, 0.0f)
    }

    private fun centeredBox(area: Float): NormalizedRect {
        val side = kotlin.math.sqrt(area.toDouble()).toFloat()
        val half = side / 2f
        return NormalizedRect(0.5f - half, 0.5f - half, 0.5f + half, 0.5f + half)
    }

    private fun detection(
        type: ScanTargetType,
        confidence: Float,
        box: NormalizedRect = NormalizedRect(0.30f, 0.30f, 0.70f, 0.70f)
    ): TargetDetection = TargetDetection(
        targetType = type,
        confidence = confidence,
        boundingBox = box,
        timestampMs = 1L
    )
}
