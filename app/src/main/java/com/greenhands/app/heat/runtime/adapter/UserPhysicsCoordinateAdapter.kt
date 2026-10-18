package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.simulation.model.GreenhouseGeometry
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.SimulationException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Explicit mapping between the user/AR placement frame and frozen Phase 3A physics coordinates.
 *
 * User / AR (schema v2): X = width, Y = length, Z = height, origin at the front-left floor corner.
 * Phase 3A [GreenhouseGeometry]: x = length, y = width, z = height (PHYSICS_MODEL.md §4).
 *
 * physics.xM = domain.yM (length), physics.yM = domain.xM (width), physics.zM = domain.zM.
 * Yaw 0° faces user +X (across width). No hidden swaps; Phase 3A types are not modified.
 */
object UserPhysicsCoordinateAdapter {
    fun positionToPhysics(domain: DomainPoint): Vec3 =
        Vec3(xM = domain.yM, yM = domain.xM, zM = domain.zM)

    fun positionToDomain(physics: Vec3): DomainPoint =
        DomainPoint(xM = physics.yM, yM = physics.xM, zM = physics.zM)

    fun directionToPhysics(domainDx: Double, domainDy: Double, domainDz: Double): Vec3 =
        Vec3(xM = domainDy, yM = domainDx, zM = domainDz)

    fun directionToDomain(physics: Vec3): Triple<Double, Double, Double> =
        Triple(physics.yM, physics.xM, physics.zM)

    /**
     * User yaw 0° faces +X (width). Forward is glTF +Z mapped through [com.greenhands.app.equipment.model.EquipmentPose].
     */
    fun yawToDomainForward(yawDeg: Double): Triple<Double, Double, Double> {
        val yaw = Math.toRadians(yawDeg)
        return Triple(cos(yaw), sin(yaw), 0.0)
    }

    fun yawToPhysicsForward(yawDeg: Double): Vec3 {
        val (dx, dy, dz) = yawToDomainForward(yawDeg)
        return directionToPhysics(dx, dy, dz).unitOrZero()
    }

    fun physicsForwardToYawDeg(physics: Vec3): Double {
        val domainX = physics.yM
        val domainY = physics.xM
        return Math.toDegrees(atan2(domainY, domainX))
    }

    /** Exhaust asset +Z is outward. Indoor replacement-air approach is −outward. */
    fun yawToPhysicsOutwardNormal(yawDeg: Double): Vec3 = yawToPhysicsForward(yawDeg)

    fun yawToPhysicsIndoorApproach(yawDeg: Double): Vec3 =
        yawToPhysicsOutwardNormal(yawDeg).times(-1.0)

    /**
     * Fogger spray is asset −Y, which [com.greenhands.app.equipment.model.EquipmentPose]
     * maps to domain −Z (downward), independent of yaw.
     */
    fun foggerSprayDomain(): Triple<Double, Double, Double> = Triple(0.0, 0.0, -1.0)

    fun foggerSprayPhysics(): Vec3 {
        val (dx, dy, dz) = foggerSprayDomain()
        return directionToPhysics(dx, dy, dz).unitOrZero()
    }

    fun geometry(
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        cellEdgeM: Double
    ): GreenhouseGeometry {
        if (!lengthM.isFinite() || lengthM <= 0.0 ||
            !widthM.isFinite() || widthM <= 0.0 ||
            !heightM.isFinite() || heightM <= 0.0
        ) {
            throw SimulationException(
                "Greenhouse dimensions must be finite and > 0 m (length=$lengthM width=$widthM height=$heightM)"
            )
        }
        return GreenhouseGeometry(
            lengthM = lengthM,
            widthM = widthM,
            wallHeightM = heightM,
            ridgeHeightM = heightM,
            cellEdgeM = cellEdgeM
        )
    }

    fun inUserBounds(
        domain: DomainPoint,
        widthM: Double,
        lengthM: Double,
        heightM: Double
    ): Boolean =
        domain.xM >= 0.0 && domain.xM <= widthM &&
            domain.yM >= 0.0 && domain.yM <= lengthM &&
            domain.zM >= 0.0 && domain.zM <= heightM

    fun inPhysicsBounds(
        physics: Vec3,
        lengthM: Double,
        widthM: Double,
        heightM: Double
    ): Boolean =
        physics.xM >= 0.0 && physics.xM <= lengthM &&
            physics.yM >= 0.0 && physics.yM <= widthM &&
            physics.zM >= 0.0 && physics.zM <= heightM
}
