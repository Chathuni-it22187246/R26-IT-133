package com.greenhands.app.heat.simulation.model

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Right-handed greenhouse-local coordinates (PHYSICS_MODEL.md §4):
 * x length (m), y width (m), z height above floor (m), origin at a floor corner.
 */
data class Vec3(
    val xM: Double,
    val yM: Double,
    val zM: Double
) {
    fun plus(other: Vec3): Vec3 = Vec3(xM + other.xM, yM + other.yM, zM + other.zM)
    fun minus(other: Vec3): Vec3 = Vec3(xM - other.xM, yM - other.yM, zM - other.zM)
    fun times(scalar: Double): Vec3 = Vec3(xM * scalar, yM * scalar, zM * scalar)
    fun dot(other: Vec3): Double = xM * other.xM + yM * other.yM + zM * other.zM
    fun lengthM(): Double = sqrt(xM * xM + yM * yM + zM * zM)
    fun unitOrZero(): Vec3 {
        val len = lengthM()
        return if (len <= 0.0 || !len.isFinite()) Vec3(0.0, 0.0, 0.0) else times(1.0 / len)
    }
}

data class AxisAlignedRegion(
    val minM: Vec3,
    val maxM: Vec3
) {
    fun contains(pointM: Vec3): Boolean =
        pointM.xM >= minM.xM && pointM.xM <= maxM.xM &&
            pointM.yM >= minM.yM && pointM.yM <= maxM.yM &&
            pointM.zM >= minM.zM && pointM.zM <= maxM.zM
}

data class GreenhouseGeometry(
    val lengthM: Double,
    val widthM: Double,
    val wallHeightM: Double,
    val ridgeHeightM: Double,
    val cellEdgeM: Double = DEFAULT_CELL_EDGE_M,
    val floorOriginNote: String = "User-selected floor corner (0,0,0)",
    val northOrUserForwardDirectionNote: String = "Local +x is greenhouse length"
) {
    init {
        require(lengthM > 0.0 && lengthM.isFinite()) { "lengthM must be finite and > 0 m" }
        require(widthM > 0.0 && widthM.isFinite()) { "widthM must be finite and > 0 m" }
        require(wallHeightM > 0.0 && wallHeightM.isFinite()) { "wallHeightM must be finite and > 0 m" }
        require(ridgeHeightM.isFinite() && ridgeHeightM >= wallHeightM) {
            "ridgeHeightM must be finite and >= wallHeightM"
        }
        require(cellEdgeM.isFinite() && cellEdgeM >= MIN_CELL_EDGE_M && cellEdgeM <= MAX_CELL_EDGE_M) {
            "cellEdgeM must be in [$MIN_CELL_EDGE_M, $MAX_CELL_EDGE_M] m"
        }
    }

    val nx: Int get() = kotlin.math.ceil(lengthM / cellEdgeM).toInt().coerceAtLeast(1)
    val ny: Int get() = kotlin.math.ceil(widthM / cellEdgeM).toInt().coerceAtLeast(1)
    val nz: Int get() = kotlin.math.ceil(ridgeHeightM / cellEdgeM).toInt().coerceAtLeast(1)
    val cellCount: Int get() = nx * ny * nz

    @Suppress("UNUSED_PARAMETER")
    fun roofHeightM(xM: Double, yM: Double): Double {
        if (widthM <= 0.0) return wallHeightM
        val distToRidgeM = abs(yM - widthM * 0.5)
        val halfWidthM = widthM * 0.5
        val t = ((halfWidthM - distToRidgeM) / halfWidthM).coerceIn(0.0, 1.0)
        return wallHeightM + t * (ridgeHeightM - wallHeightM)
    }

    fun contains(pointM: Vec3, epsilonM: Double = 1e-9): Boolean {
        if (pointM.xM < -epsilonM || pointM.xM > lengthM + epsilonM) return false
        if (pointM.yM < -epsilonM || pointM.yM > widthM + epsilonM) return false
        if (pointM.zM < -epsilonM) return false
        return pointM.zM <= roofHeightM(pointM.xM, pointM.yM) + epsilonM
    }

    fun cellCenterM(i: Int, j: Int, k: Int): Vec3 = Vec3(
        (i + 0.5) * cellEdgeM,
        (j + 0.5) * cellEdgeM,
        (k + 0.5) * cellEdgeM
    )

    fun index(i: Int, j: Int, k: Int): Int = i + nx * (j + ny * k)

    fun iOf(idx: Int): Int = idx % nx
    fun jOf(idx: Int): Int = (idx / nx) % ny
    fun kOf(idx: Int): Int = idx / (nx * ny)

    companion object {
        const val DEFAULT_CELL_EDGE_M = 0.50
        const val MIN_CELL_EDGE_M = 0.25
        const val MAX_CELL_EDGE_M = 1.00
        const val MAX_ACTIVE_CELLS_PHASE_3A = 32_768
    }
}
