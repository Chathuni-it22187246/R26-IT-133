package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.FanPadCopy
import com.greenhands.app.heat.runtime.contracts.FanPadWallSelection
import com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall
import kotlin.math.abs

/**
 * Fan-and-pad layout: all exhaust fans share one wall; the pad sits on the opposite
 * wall. User X = width, Y = length. Camera/tabletop yaw is ignored.
 */
object FanPadLayoutResolver {
    const val END_WALL_TOLERANCE_M = 1.25

    data class ExhaustWallResult(
        val commonWall: GreenhouseEndWall?,
        val padWall: GreenhouseEndWall?,
        val conflict: Boolean,
        val explanation: String?
    )

    fun exhaustEndWall(
        instance: EquipmentInstance,
        lengthM: Double,
        widthM: Double
    ): GreenhouseEndWall? {
        if (instance.isGhost || instance.type != EquipmentType.EXHAUST_FAN) return null
        if (!lengthM.isFinite() || lengthM <= 0.0 || !widthM.isFinite() || widthM <= 0.0) return null
        val x = instance.positionM.xM
        val y = instance.positionM.yM
        val hits = listOf(
            GreenhouseEndWall.NEAR_Y0 to abs(y),
            GreenhouseEndWall.FAR_Y_LENGTH to abs(lengthM - y),
            GreenhouseEndWall.NEAR_X0 to abs(x),
            GreenhouseEndWall.FAR_X_WIDTH to abs(widthM - x)
        ).filter { it.second <= END_WALL_TOLERANCE_M }
        if (hits.isEmpty()) return null
        return hits.minWith(
            compareBy({ it.second }, { if (isLengthAxis(it.first)) 0 else 1 })
        ).first
    }

    fun resolve(
        instances: List<EquipmentInstance>,
        lengthM: Double,
        widthM: Double,
        wallSelection: FanPadWallSelection
    ): ExhaustWallResult {
        val exhausts = instances.filter { !it.isGhost && it.type == EquipmentType.EXHAUST_FAN }
        if (exhausts.isEmpty()) {
            return ExhaustWallResult(
                commonWall = null,
                padWall = null,
                conflict = false,
                explanation = FanPadCopy.NO_EXHAUST
            )
        }
        val walls = exhausts.map { exhaustEndWall(it, lengthM, widthM) }
        if (walls.any { it == null }) {
            return ExhaustWallResult(
                commonWall = null,
                padWall = null,
                conflict = true,
                explanation = FanPadCopy.EXHAUST_NOT_ON_END_WALL
            )
        }
        val distinct = walls.filterNotNull().toSet()
        if (distinct.size != 1) {
            return ExhaustWallResult(
                commonWall = null,
                padWall = null,
                conflict = true,
                explanation = FanPadCopy.CONFLICTING_EXHAUST_WALLS
            )
        }
        val exhaustWall = distinct.single()
        val autoPad = opposite(exhaustWall)
        val padWall = when (wallSelection) {
            FanPadWallSelection.AUTO -> autoPad
            FanPadWallSelection.NEAR_Y0 -> GreenhouseEndWall.NEAR_Y0
            FanPadWallSelection.FAR_Y_LENGTH -> GreenhouseEndWall.FAR_Y_LENGTH
            FanPadWallSelection.NEAR_X0 -> GreenhouseEndWall.NEAR_X0
            FanPadWallSelection.FAR_X_WIDTH -> GreenhouseEndWall.FAR_X_WIDTH
        }
        if (padWall != autoPad) {
            return ExhaustWallResult(
                commonWall = exhaustWall,
                padWall = null,
                conflict = true,
                explanation = FanPadCopy.CONFLICTING_EXHAUST_WALLS
            )
        }
        return ExhaustWallResult(
            commonWall = exhaustWall,
            padWall = padWall,
            conflict = false,
            explanation = null
        )
    }

    fun opposite(wall: GreenhouseEndWall): GreenhouseEndWall = when (wall) {
        GreenhouseEndWall.NEAR_Y0 -> GreenhouseEndWall.FAR_Y_LENGTH
        GreenhouseEndWall.FAR_Y_LENGTH -> GreenhouseEndWall.NEAR_Y0
        GreenhouseEndWall.NEAR_X0 -> GreenhouseEndWall.FAR_X_WIDTH
        GreenhouseEndWall.FAR_X_WIDTH -> GreenhouseEndWall.NEAR_X0
    }

    fun isLengthAxis(wall: GreenhouseEndWall): Boolean = when (wall) {
        GreenhouseEndWall.NEAR_Y0, GreenhouseEndWall.FAR_Y_LENGTH -> true
        GreenhouseEndWall.NEAR_X0, GreenhouseEndWall.FAR_X_WIDTH -> false
    }

    fun airflowSpanM(padWall: GreenhouseEndWall, lengthM: Double, widthM: Double): Double =
        if (isLengthAxis(padWall)) lengthM else widthM

    fun wallRunM(padWall: GreenhouseEndWall, lengthM: Double, widthM: Double): Double =
        if (isLengthAxis(padWall)) widthM else lengthM

    fun inwardDomain(padWall: GreenhouseEndWall): Triple<Double, Double, Double> = when (padWall) {
        GreenhouseEndWall.NEAR_Y0 -> Triple(0.0, 1.0, 0.0)
        GreenhouseEndWall.FAR_Y_LENGTH -> Triple(0.0, -1.0, 0.0)
        GreenhouseEndWall.NEAR_X0 -> Triple(1.0, 0.0, 0.0)
        GreenhouseEndWall.FAR_X_WIDTH -> Triple(-1.0, 0.0, 0.0)
    }

    fun inwardPhysics(padWall: GreenhouseEndWall) =
        UserPhysicsCoordinateAdapter.directionToPhysics(
            inwardDomain(padWall).first,
            inwardDomain(padWall).second,
            inwardDomain(padWall).third
        )
}
