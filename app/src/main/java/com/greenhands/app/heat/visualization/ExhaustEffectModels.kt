package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot

enum class ExhaustAirflowMode {
    QUANTITATIVE,
    QUALITATIVE
}

data class ExhaustArrowGroup(
    val equipmentUuid: String,
    val originM: DomainPoint,
    val yawDeg: Double,
    val inwardDomainDx: Double,
    val inwardDomainDy: Double,
    val inwardDomainDz: Double,
    val outwardDomainDx: Double,
    val outwardDomainDy: Double,
    val outwardDomainDz: Double,
    val validProbeDistancesM: List<Double>,
    val samples: List<CirculationProbeColorSample>,
    val indoorArrowCount: Int,
    val outdoorArrowCount: Int,
    val indoorMinDistanceM: Double,
    val indoorMaxDistanceM: Double,
    val outdoorMaxDistanceM: Double,
    val mode: ExhaustAirflowMode,
    val targetTemperatureC: Double?,
    val cdpC: Double?,
    val conC: Double?,
    val eonC: Double?,
    val weatherSourceLabel: String?,
    val animationPhase: Double = 0.0
) {
    init {
        require(equipmentUuid.isNotBlank())
        require(indoorArrowCount in 0..ExhaustAirflowVisual.MAX_INDOOR_ARROWS_PER_FAN)
        require(outdoorArrowCount in 0..ExhaustAirflowVisual.MAX_OUTDOOR_ARROWS_PER_FAN)
        require(indoorArrowCount + outdoorArrowCount > 0)
        require(indoorMinDistanceM.isFinite() && indoorMaxDistanceM.isFinite())
        require(outdoorMaxDistanceM.isFinite() && outdoorMaxDistanceM >= 0.0)
        require(indoorMaxDistanceM + 1e-9 >= indoorMinDistanceM)
        require(animationPhase.isFinite())
    }

    val arrowCount: Int get() = indoorArrowCount + outdoorArrowCount

    fun indoorPointAt(distanceM: Double): DomainPoint = DomainPoint(
        xM = originM.xM + inwardDomainDx * distanceM,
        yM = originM.yM + inwardDomainDy * distanceM,
        zM = originM.zM + inwardDomainDz * distanceM
    )

    fun outdoorPointAt(distanceM: Double): DomainPoint = DomainPoint(
        xM = originM.xM + outwardDomainDx * distanceM,
        yM = originM.yM + outwardDomainDy * distanceM,
        zM = originM.zM + outwardDomainDz * distanceM
    )
}

data class ExhaustEffectFrame(
    val groups: List<ExhaustArrowGroup> = emptyList(),
    val limitation: String? = null,
    val snapshot: PhysicsFieldSnapshot? = null,
    val colorSnapshot: PhysicsFieldSnapshot? = null
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val arrowObjectCount: Int get() = groups.sumOf { it.arrowCount }
    val quantitative: Boolean get() = groups.any { it.mode == ExhaustAirflowMode.QUANTITATIVE }

    fun group(uuid: String): ExhaustArrowGroup? = groups.firstOrNull { it.equipmentUuid == uuid }

    companion object {
        val EMPTY = ExhaustEffectFrame()
    }
}
