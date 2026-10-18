package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentPose
import kotlin.math.cos
import kotlin.math.sin

/**
 * Documented fogger.glb nozzle outlets in asset-local metres (glTF Y-up).
 *
 * [tools/generate_equipment_glb.js] builds one brass nozzle stack on the pipe
 * hanger. The catalog pivot is the nozzle outlet; the visible orifice box is at
 * local (0, −0.05, 0). Spray is asset −Y → domain −Z. Pipe runs along local +X.
 *
 * This is visualization geometry only. It does not add water-flow sources.
 */
object FoggerNozzleLayout {
    const val ASSET_SPRAY_LOCAL_Y = -1.0
    const val PIVOT_IS_NOZZLE_OUTLET = true
    const val DOCUMENTED_OUTLET_COUNT = 1

    /**
     * Visible nozzle-tip box centre from `generate_equipment_glb.js`
     * (`nozzle.addBox(0, -0.05, 0, 0.03, 0.012, 0.03)`).
     */
    val LOCAL_OUTLETS: List<Triple<Double, Double, Double>> = listOf(
        Triple(0.0, -0.05, 0.0)
    )

    val AABB_CENTER_LOCAL_Y: Double
        get() {
            val aabb = EquipmentAssetCatalog.fogger.localAabb
            return (aabb.minY + aabb.maxY) * 0.5
        }

    fun domainOutlets(position: DomainPoint, yawDeg: Double): List<DomainPoint> =
        LOCAL_OUTLETS.map { (lx, ly, lz) ->
            EquipmentPose.localToDomain(position, yawDeg, lx, ly, lz)
        }

    /**
     * Horizontal laterals around the downward spray axis: local +X (pipe) and
     * local +Z (asset forward), mapped through [EquipmentPose] yaw.
     */
    fun laterals(yawDeg: Double): Pair<Triple<Double, Double, Double>, Triple<Double, Double, Double>> {
        val yaw = Math.toRadians(yawDeg)
        val c = cos(yaw)
        val s = sin(yaw)
        val pipe = Triple(-s, c, 0.0)
        val forward = Triple(c, s, 0.0)
        return pipe to forward
    }
}
