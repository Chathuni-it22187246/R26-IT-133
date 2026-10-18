package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall
import com.greenhands.app.heat.simulation.model.IntakeOpening
import com.greenhands.app.heat.simulation.model.Vec3
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Places spatial [IntakeOpening]s on the pad wall face. Coordinates are physics
 * (x = length, y = width, z = height). Pad width/height are in the user frame.
 */
object FanPadIntakeFactory {
    fun openings(
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        cellEdgeM: Double,
        padWall: GreenhouseEndWall,
        padWidthM: Double,
        padHeightM: Double
    ): List<IntakeOpening> {
        if (!lengthM.isFinite() || lengthM <= 0.0 ||
            !widthM.isFinite() || widthM <= 0.0 ||
            !heightM.isFinite() || heightM <= 0.0 ||
            !cellEdgeM.isFinite() || cellEdgeM <= 0.0 ||
            !padWidthM.isFinite() || padWidthM <= 0.0 ||
            !padHeightM.isFinite() || padHeightM <= 0.0
        ) {
            return emptyList()
        }
        val alongWall = min(padWidthM, FanPadLayoutResolver.wallRunM(padWall, lengthM, widthM))
        val wallHeight = min(padHeightM, heightM)
        val nAlong = max(1, ceil(alongWall / cellEdgeM).toInt())
        val nz = max(1, ceil(wallHeight / cellEdgeM).toInt())
        val inward = FanPadLayoutResolver.inwardPhysics(padWall)
        val cellArea = (alongWall * wallHeight) / (nAlong * nz).toDouble()
        val out = ArrayList<IntakeOpening>(nAlong * nz)
        for (ia in 0 until nAlong) {
            val along = (ia + 0.5) * alongWall / nAlong
            for (iz in 0 until nz) {
                val userZ = (iz + 0.5) * wallHeight / nz
                val domain = cellDomain(padWall, lengthM, widthM, alongWall, along, userZ)
                val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
                val position = facePosition(padWall, lengthM, widthM, cellEdgeM, physics)
                out += IntakeOpening(
                    id = "pad-inlet:$ia:$iz",
                    positionM = position,
                    inwardNormal = inward,
                    areaM2 = cellArea
                )
            }
        }
        return out
    }

    fun padCenterDomain(
        lengthM: Double,
        widthM: Double,
        padWall: GreenhouseEndWall,
        padHeightM: Double
    ): DomainPoint {
        val z = (padHeightM * 0.5).coerceAtLeast(0.0)
        return when (padWall) {
            GreenhouseEndWall.NEAR_Y0 -> DomainPoint(widthM * 0.5, 0.04, z)
            GreenhouseEndWall.FAR_Y_LENGTH -> DomainPoint(widthM * 0.5, (lengthM - 0.04).coerceAtLeast(0.0), z)
            GreenhouseEndWall.NEAR_X0 -> DomainPoint(0.04, lengthM * 0.5, z)
            GreenhouseEndWall.FAR_X_WIDTH -> DomainPoint((widthM - 0.04).coerceAtLeast(0.0), lengthM * 0.5, z)
        }
    }

    private fun cellDomain(
        padWall: GreenhouseEndWall,
        lengthM: Double,
        widthM: Double,
        alongWall: Double,
        along: Double,
        userZ: Double
    ): DomainPoint {
        val start = when (padWall) {
            GreenhouseEndWall.NEAR_Y0, GreenhouseEndWall.FAR_Y_LENGTH ->
                ((widthM - alongWall) * 0.5).coerceAtLeast(0.0)
            GreenhouseEndWall.NEAR_X0, GreenhouseEndWall.FAR_X_WIDTH ->
                ((lengthM - alongWall) * 0.5).coerceAtLeast(0.0)
        }
        val face = padCenterDomain(lengthM, widthM, padWall, 1.0)
        return when (padWall) {
            GreenhouseEndWall.NEAR_Y0, GreenhouseEndWall.FAR_Y_LENGTH ->
                DomainPoint(start + along, face.yM, userZ)
            GreenhouseEndWall.NEAR_X0, GreenhouseEndWall.FAR_X_WIDTH ->
                DomainPoint(face.xM, start + along, userZ)
        }
    }

    private fun facePosition(
        padWall: GreenhouseEndWall,
        lengthM: Double,
        widthM: Double,
        cellEdgeM: Double,
        physics: Vec3
    ): Vec3 = when (padWall) {
        GreenhouseEndWall.NEAR_Y0 ->
            Vec3(cellEdgeM * 0.5, physics.yM, physics.zM)
        GreenhouseEndWall.FAR_Y_LENGTH ->
            Vec3((lengthM - cellEdgeM * 0.5).coerceAtLeast(cellEdgeM * 0.25), physics.yM, physics.zM)
        GreenhouseEndWall.NEAR_X0 ->
            Vec3(physics.xM, cellEdgeM * 0.5, physics.zM)
        GreenhouseEndWall.FAR_X_WIDTH ->
            Vec3(physics.xM, (widthM - cellEdgeM * 0.5).coerceAtLeast(cellEdgeM * 0.25), physics.zM)
    }
}
