package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import java.util.Locale

data class CirculationProbeColorSample(
    val distanceM: Double,
    val temperatureC: Double,
    val color: VisualizationRgb,
    val band: TemperatureLegendBand
)

data class CirculationRingGroup(
    val equipmentUuid: String,
    val originM: DomainPoint,
    val yawDeg: Double,
    val forwardDomainDx: Double,
    val forwardDomainDy: Double,
    val forwardDomainDz: Double,
    val validProbeDistancesM: List<Double>,
    val samples: List<CirculationProbeColorSample>,
    val ringCount: Int,
    val minDistanceM: Double,
    val maxDistanceM: Double,
    val targetTemperatureC: Double?,
    val cdpC: Double?,
    val conC: Double?,
    val eonC: Double?,
    val animationPhase: Double = 0.0
) {
    init {
        require(equipmentUuid.isNotBlank())
        require(ringCount in 1..CirculationRingVisual.MAX_RINGS_PER_FAN)
        require(minDistanceM.isFinite() && maxDistanceM.isFinite())
        require(maxDistanceM + 1e-9 >= minDistanceM)
        require(animationPhase.isFinite())
    }

    fun domainPointAt(distanceM: Double): DomainPoint = DomainPoint(
        xM = originM.xM + forwardDomainDx * distanceM,
        yM = originM.yM + forwardDomainDy * distanceM,
        zM = originM.zM + forwardDomainDz * distanceM
    )
}

data class CirculationEffectFrame(
    val groups: List<CirculationRingGroup> = emptyList(),
    val limitation: String? = null,
    val snapshot: PhysicsFieldSnapshot? = null,
    val colorSnapshot: PhysicsFieldSnapshot? = null
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val ringObjectCount: Int get() = groups.sumOf { it.ringCount }

    fun group(uuid: String): CirculationRingGroup? = groups.firstOrNull { it.equipmentUuid == uuid }

    companion object {
        val EMPTY = CirculationEffectFrame()
    }
}

data class CirculationProbeMarker(
    val id: String,
    val equipmentUuid: String,
    val distanceM: Double,
    val temperatureC: Double,
    val domain: DomainPoint,
    val band: TemperatureLegendBand = TemperatureLegendBand.UNAVAILABLE,
    val relativeHumidityPercent: Double? = null,
    val distanceFromPadM: Double? = null
) {
    val text: String get() = CirculationProbeLabelPlanner.labelText(distanceM, temperatureC)
    val compactDistanceText: String get() = CirculationProbeLabelPlanner.compactDistanceText(distanceM)
    val predictedTemperatureText: String
        get() = CirculationProbeLabelPlanner.temperatureText(temperatureC)
    val classificationLabel: String get() = VisualizationCopy.compactBandLabel(band)
    val classificationColor: VisualizationRgb get() = TemperatureLegendClassifier.colorOf(band)
    val predictedHumidityText: String
        get() {
            val rh = relativeHumidityPercent
            return if (rh != null && rh.isFinite()) {
                String.format(java.util.Locale.US, "%.1f %% RH", rh)
            } else {
                "—"
            }
        }
    val predictedPadDistanceText: String
        get() {
            val fromPad = distanceFromPadM
            return if (fromPad != null && fromPad.isFinite()) {
                CirculationProbeLabelPlanner.compactDistanceText(fromPad)
            } else {
                "—"
            }
        }
}

/**
 * Compact 1–5 m markers along a circulation fan's forward axis.
 * Diagnostic / internal only — not the official public validation transect.
 */
object CirculationProbeLabelPlanner {
    const val SHOWS_PUBLIC_VALIDATION_TRANSECT = OfficialValidationTransect.SHOWS_CIRCULATION_MARKERS
    fun plan(
        visualizationRunning: Boolean,
        labelsVisible: Boolean,
        selectedId: String?,
        instances: List<com.greenhands.app.equipment.model.EquipmentInstance>,
        runtimeById: Map<String, com.greenhands.app.equipment.model.EquipmentRuntimeState>,
        snapshot: PhysicsFieldSnapshot?,
        targetTemperatureC: Double? = null,
        cdpC: Double? = null,
        conC: Double? = null,
        eonC: Double? = null
    ): List<CirculationProbeMarker> {
        if (!visualizationRunning || !labelsVisible) return emptyList()
        val selected = selectedId ?: return emptyList()
        val inst = instances.firstOrNull {
            it.id == selected &&
                it.type == com.greenhands.app.equipment.model.EquipmentType.CIRCULATION_FAN &&
                !it.isGhost
        } ?: return emptyList()
        if (runtimeById[inst.id] != com.greenhands.app.equipment.model.EquipmentRuntimeState.ON) {
            return emptyList()
        }
        if (snapshot == null || !snapshot.finiteValues) return emptyList()
        val (dx, dy, dz) = com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
            .yawToDomainForward(inst.yawDeg)
        return com.greenhands.app.heat.simulation.probe.DistanceProbeSampler.DEFAULT_DISTANCES_M
            .toList()
            .mapNotNull { distance ->
                val domain = DomainPoint(
                    xM = inst.positionM.xM + dx * distance,
                    yM = inst.positionM.yM + dy * distance,
                    zM = inst.positionM.zM + dz * distance
                )
                val temperature = CirculationAirflowEnvelope.predictedTemperatureC(snapshot, domain)
                    ?: CirculationAirflowEnvelope.probeTemperatureC(snapshot, inst.id, distance)
                    ?: return@mapNotNull null
                CirculationProbeMarker(
                    id = markerId(inst.id, distance),
                    equipmentUuid = inst.id,
                    distanceM = distance,
                    temperatureC = temperature,
                    domain = domain,
                    band = TemperatureLegendClassifier.band(
                        temperature,
                        targetTemperatureC,
                        cdpC,
                        conC,
                        eonC
                    )
                )
            }
    }

    fun markerId(equipmentUuid: String, distanceM: Double): String =
        "circ-probe:$equipmentUuid:${distanceM.toInt()}"

    fun compactDistanceText(distanceM: Double): String {
        return if (kotlin.math.abs(distanceM - distanceM.toInt()) < 1e-9) {
            "${distanceM.toInt()} m"
        } else {
            String.format(Locale.US, "%.0f m", distanceM)
        }
    }

    fun temperatureText(temperatureC: Double): String =
        "${String.format(Locale.US, "%.2f", temperatureC)} °C"

    fun labelText(distanceM: Double, temperatureC: Double): String =
        "${compactDistanceText(distanceM)} · ${temperatureText(temperatureC)}"

    fun controlAvailable(
        visualizationRunning: Boolean,
        labelsVisible: Boolean,
        selectedId: String?,
        instances: List<com.greenhands.app.equipment.model.EquipmentInstance>,
        runtimeById: Map<String, com.greenhands.app.equipment.model.EquipmentRuntimeState>
    ): Boolean {
        if (!visualizationRunning || !labelsVisible) return false
        val selected = selectedId ?: return false
        val inst = instances.firstOrNull {
            it.id == selected &&
                it.type == com.greenhands.app.equipment.model.EquipmentType.CIRCULATION_FAN &&
                !it.isGhost
        } ?: return false
        return runtimeById[inst.id] == com.greenhands.app.equipment.model.EquipmentRuntimeState.ON
    }
}
