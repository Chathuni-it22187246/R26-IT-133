package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall
import kotlin.math.min

/**
 * Bounded framed cooling-pad layout in domain metres. Not a GLB and not inlet arrows.
 */
object PadPanelLayout {
    const val WALL_INSET_M = 0.08
    const val FACE_OFFSET_M = 0.08
    const val FRAME_M = 0.06
    const val RIB_COUNT = 16
    const val RAIL_COUNT = 4
    const val RIB_WIDTH_M = 0.022
    const val RAIL_HEIGHT_M = 0.018
    const val THICKNESS_M = 0.038
    const val CORRUGATION_M = 0.006
    const val SEPARATE_FROM_INLET_ARROWS = true
    const val RENDER_DESCRIPTION = "framed rectangular pad panel"
    const val INLET_ARROW_DESCRIPTION = "wet-pad inlet airflow arrows"

    data class Quad(
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double,
        val isFrame: Boolean
    ) {
        val width: Double get() = maxX - minX
        val height: Double get() = maxY - minY
    }

    fun sizeInsideWall(
        configuredWidthM: Double,
        configuredHeightM: Double,
        wallRunM: Double,
        houseHeightM: Double
    ): Pair<Double, Double> {
        val width = min(configuredWidthM, (wallRunM - 2.0 * WALL_INSET_M).coerceAtLeast(0.0))
            .coerceAtLeast(0.0)
        val height = min(configuredHeightM, (houseHeightM - 2.0 * WALL_INSET_M).coerceAtLeast(0.0))
            .coerceAtLeast(0.0)
        return width to height
    }

    fun visualOrigin(
        lengthM: Double,
        widthM: Double,
        wall: GreenhouseEndWall,
        panelHeightM: Double
    ): DomainPoint {
        val z = (WALL_INSET_M + panelHeightM * 0.5).coerceAtLeast(0.0)
        return when (wall) {
            GreenhouseEndWall.NEAR_Y0 -> DomainPoint(widthM * 0.5, FACE_OFFSET_M, z)
            GreenhouseEndWall.FAR_Y_LENGTH ->
                DomainPoint(widthM * 0.5, (lengthM - FACE_OFFSET_M).coerceAtLeast(0.0), z)
            GreenhouseEndWall.NEAR_X0 -> DomainPoint(FACE_OFFSET_M, lengthM * 0.5, z)
            GreenhouseEndWall.FAR_X_WIDTH ->
                DomainPoint((widthM - FACE_OFFSET_M).coerceAtLeast(0.0), lengthM * 0.5, z)
        }
    }

    fun boundingBoxInsideWall(
        origin: DomainPoint,
        widthM: Double,
        heightM: Double,
        wall: GreenhouseEndWall,
        lengthM: Double,
        houseWidthM: Double,
        houseHeightM: Double
    ): Boolean {
        if (widthM <= 0.0 || heightM <= 0.0) return false
        if (origin.zM - heightM * 0.5 + 1e-9 < 0.0) return false
        if (origin.zM + heightM * 0.5 > houseHeightM + 1e-9) return false
        return when (wall) {
            GreenhouseEndWall.NEAR_Y0, GreenhouseEndWall.FAR_Y_LENGTH -> {
                val x0 = origin.xM - widthM * 0.5
                val x1 = origin.xM + widthM * 0.5
                x0 + 1e-9 >= 0.0 && x1 <= houseWidthM + 1e-9 &&
                    origin.yM + 1e-9 >= 0.0 && origin.yM <= lengthM + 1e-9
            }
            GreenhouseEndWall.NEAR_X0, GreenhouseEndWall.FAR_X_WIDTH -> {
                val y0 = origin.yM - widthM * 0.5
                val y1 = origin.yM + widthM * 0.5
                y0 + 1e-9 >= 0.0 && y1 <= lengthM + 1e-9 &&
                    origin.xM + 1e-9 >= 0.0 && origin.xM <= houseWidthM + 1e-9
            }
        }
    }

    fun frameM(widthM: Double, heightM: Double): Double =
        min(FRAME_M, min(widthM, heightM) * 0.10).coerceAtLeast(0.012)

    fun quads(widthM: Double, heightM: Double): List<Quad> {
        val hx = widthM * 0.5
        val hy = heightM * 0.5
        val f = frameM(widthM, heightM)
        val out = ArrayList<Quad>(RIB_COUNT + RAIL_COUNT + 4)
        out += Quad(-hx, -hx + f, -hy, hy, isFrame = true)
        out += Quad(hx - f, hx, -hy, hy, isFrame = true)
        out += Quad(-hx + f, hx - f, hy - f, hy, isFrame = true)
        out += Quad(-hx + f, hx - f, -hy, -hy + f, isFrame = true)
        val innerL = -hx + f
        val innerR = hx - f
        val innerB = -hy + f
        val innerT = hy - f
        val innerW = (innerR - innerL).coerceAtLeast(0.0)
        val ribs = RIB_COUNT.coerceAtLeast(4)
        val pitch = if (ribs <= 0) innerW else innerW / ribs
        val ribW = min(RIB_WIDTH_M, pitch * 0.42)
        for (i in 0 until ribs) {
            val cx = innerL + (i + 0.5) * pitch
            val x0 = (cx - ribW * 0.5).coerceAtLeast(innerL)
            val x1 = (cx + ribW * 0.5).coerceAtMost(innerR)
            if (x1 > x0) out += Quad(x0, x1, innerB, innerT, isFrame = false)
        }
        val rails = RAIL_COUNT.coerceAtLeast(1)
        val railH = min(RAIL_HEIGHT_M, (innerT - innerB) * 0.08)
        for (r in 1..rails) {
            val y = innerB + (innerT - innerB) * r / (rails + 1.0)
            val y0 = (y - railH * 0.5).coerceAtLeast(innerB)
            val y1 = (y + railH * 0.5).coerceAtMost(innerT)
            if (y1 > y0) out += Quad(innerL, innerR, y0, y1, isFrame = false)
        }
        return out
    }

    fun ribsStayInsideFrame(widthM: Double, heightM: Double): Boolean {
        val hx = widthM * 0.5
        val hy = heightM * 0.5
        val f = frameM(widthM, heightM)
        val innerL = -hx + f
        val innerR = hx - f
        val innerB = -hy + f
        val innerT = hy - f
        return quads(widthM, heightM).filter { !it.isFrame }.all { q ->
            val inside = q.minX + 1e-9 >= innerL &&
                q.maxX <= innerR + 1e-9 &&
                q.minY + 1e-9 >= innerB &&
                q.maxY <= innerT + 1e-9
            val verticalRib = q.height >= q.width
            val bounded = if (verticalRib) {
                q.width < widthM * 0.35 && q.height <= (innerT - innerB) + 1e-9
            } else {
                q.height < heightM * 0.35 && q.width <= (innerR - innerL) + 1e-9
            }
            inside && bounded
        }
    }

    fun hasBoundedFrame(widthM: Double, heightM: Double): Boolean {
        val frames = quads(widthM, heightM).filter { it.isFrame }
        return frames.size == 4 && frames.all { it.width > 0.0 && it.height > 0.0 }
    }
}
