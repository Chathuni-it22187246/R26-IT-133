package com.greenhands.app

import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.PreviewFillCenterMapper
import com.greenhands.app.harvest.domain.HarvestArgbRotate
import org.junit.Assert.assertEquals
import org.junit.Test

class HarvestCameraGeometryTest {

    @Test
    fun rotate90MovesTopLeftPixelToTopRight() {
        val src = intArrayOf(
            1, 2,
            3, 4,
            5, 6
        )
        val rotated = HarvestArgbRotate.apply(src, width = 2, height = 3, rotationDegrees = 90)
        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(5, rotated.argb[0])
        assertEquals(3, rotated.argb[1])
        assertEquals(1, rotated.argb[2])
        assertEquals(6, rotated.argb[3])
        assertEquals(4, rotated.argb[4])
        assertEquals(2, rotated.argb[5])
        assertEquals(90, rotated.rotationDegrees)
    }

    @Test
    fun matchingAspectLeavesBoxUnchanged() {
        val box = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)
        val mapped = PreviewFillCenterMapper.mapToView(box, 640, 480, 640, 480)
        assertEquals(box.left, mapped.left, 0.001f)
        assertEquals(box.top, mapped.top, 0.001f)
        assertEquals(box.right, mapped.right, 0.001f)
        assertEquals(box.bottom, mapped.bottom, 0.001f)
    }

    @Test
    fun tallerViewCropsAnalysisSides() {
        val center = NormalizedRect(0.45f, 0.40f, 0.55f, 0.60f)
        val mapped = PreviewFillCenterMapper.mapToView(
            box = center,
            frameWidth = 640,
            frameHeight = 480,
            viewWidth = 360,
            viewHeight = 640
        )
        assertEquals(0.5f, mapped.centerX, 0.02f)
        assertEquals(0.5f, mapped.centerY, 0.02f)
    }

    @Test
    fun snapshotUsesAnalysisFrameCoordinatesWithoutPreviewMapping() {
        val box = NormalizedRect(0.10f, 0.20f, 0.40f, 0.80f)
        val width = 200
        val height = 100
        assertEquals(20, (box.left * width).toInt())
        assertEquals(20, (box.top * height).toInt())
        assertEquals(80, (box.right * width).toInt())
        assertEquals(80, (box.bottom * height).toInt())
    }
}
