package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.contracts.FanPadFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot

data class PadPanelSpec(
    val wall: GreenhouseEndWall,
    val originM: DomainPoint,
    val widthM: Double,
    val heightM: Double,
    val wetPadActive: Boolean,
    val yawDeg: Double
)

data class PadInletStreamSpec(
    val originM: DomainPoint,
    val yawDeg: Double,
    val inwardDomainDx: Double,
    val inwardDomainDy: Double,
    val inwardDomainDz: Double,
    val pathM: Double,
    val arrowCount: Int,
    val wetPadActive: Boolean,
    val claimsEvaporativeCooling: Boolean = wetPadActive,
    val targetTemperatureC: Double?,
    val cdpC: Double?,
    val conC: Double?,
    val eonC: Double?
) {
    fun pointAt(distanceM: Double): DomainPoint = DomainPoint(
        xM = originM.xM + inwardDomainDx * distanceM,
        yM = originM.yM + inwardDomainDy * distanceM,
        zM = originM.zM + inwardDomainDz * distanceM
    )
}

data class PadInletEffectFrame(
    val panel: PadPanelSpec? = null,
    val stream: PadInletStreamSpec? = null,
    val snapshot: PhysicsFieldSnapshot? = null,
    val colorSnapshot: PhysicsFieldSnapshot? = null,
    val fanPad: FanPadFieldSnapshot = FanPadFieldSnapshot.NONE,
    val limitation: String? = null
) {
    val isEmpty: Boolean get() = panel == null && stream == null

    companion object {
        val EMPTY = PadInletEffectFrame()
    }
}
