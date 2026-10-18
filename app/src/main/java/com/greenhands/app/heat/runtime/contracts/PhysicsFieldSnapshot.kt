package com.greenhands.app.heat.runtime.contracts

import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.adapter.UuidEquipmentReadiness
import com.greenhands.app.heat.simulation.model.GreenhouseGeometry
import java.util.Arrays

data class EquipmentProbeSample(
    val equipmentUuid: String,
    val equipmentType: EquipmentType,
    val axis: EquipmentProbeAxis,
    val distanceM: Double,
    val temperatureC: Double?,
    val relativeHumidityPercent: Double?,
    val airSpeedMps: Double?,
    val availability: ProbeAvailability,
    val validationStatus: FieldValidationStatus,
    val reason: String?
)

data class GridGeometrySnapshot(
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val cellEdgeM: Double,
    val nx: Int,
    val ny: Int,
    val nz: Int,
    val cellCount: Int,
    val activeCellCount: Int
)

/**
 * Immutable field snapshot. Arrays are copies of the live grid and are not written back.
 */
class PhysicsFieldSnapshot(
    val elapsedTimeS: Double,
    val grid: GridGeometrySnapshot,
    val temperatureC: DoubleArray,
    val humidityRatioKgKg: DoubleArray,
    val relativeHumidityPercent: DoubleArray,
    val meanTemperatureC: Double,
    val meanRelativeHumidityPercent: Double,
    val referenceTemperatureC: Double,
    val referenceRelativeHumidityPercent: Double,
    val insideSource: InsideClimateSource,
    val insideSourceLabel: String,
    val weatherStatus: WeatherBoundaryStatus,
    val weatherSourceLabel: String,
    val weatherProvenance: WeatherBoundaryProvenance,
    val weatherKind: WeatherBoundaryKind,
    val validationStatus: FieldValidationStatus,
    val equipment: List<UuidEquipmentReadiness>,
    val runtimeById: Map<String, EquipmentRuntimeState>,
    val warnings: List<String>,
    val unavailableModuleReasons: Map<String, String>,
    val probes: List<EquipmentProbeSample>,
    val wellMixedVentilation: Boolean,
    val finiteValues: Boolean,
    val airPressurePa: Double = 101_325.0,
    val fanPad: FanPadFieldSnapshot = FanPadFieldSnapshot.NONE
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhysicsFieldSnapshot) return false
        return elapsedTimeS == other.elapsedTimeS &&
            grid == other.grid &&
            temperatureC.contentEquals(other.temperatureC) &&
            humidityRatioKgKg.contentEquals(other.humidityRatioKgKg) &&
            relativeHumidityPercent.contentEquals(other.relativeHumidityPercent) &&
            meanTemperatureC == other.meanTemperatureC &&
            meanRelativeHumidityPercent == other.meanRelativeHumidityPercent &&
            referenceTemperatureC == other.referenceTemperatureC &&
            referenceRelativeHumidityPercent == other.referenceRelativeHumidityPercent &&
            insideSource == other.insideSource &&
            insideSourceLabel == other.insideSourceLabel &&
            weatherStatus == other.weatherStatus &&
            weatherSourceLabel == other.weatherSourceLabel &&
            weatherProvenance == other.weatherProvenance &&
            weatherKind == other.weatherKind &&
            validationStatus == other.validationStatus &&
            equipment == other.equipment &&
            runtimeById == other.runtimeById &&
            warnings == other.warnings &&
            unavailableModuleReasons == other.unavailableModuleReasons &&
            probes == other.probes &&
            wellMixedVentilation == other.wellMixedVentilation &&
            finiteValues == other.finiteValues &&
            airPressurePa == other.airPressurePa &&
            fanPad == other.fanPad
    }

    override fun hashCode(): Int {
        var result = elapsedTimeS.hashCode()
        result = 31 * result + grid.hashCode()
        result = 31 * result + Arrays.hashCode(temperatureC)
        result = 31 * result + Arrays.hashCode(humidityRatioKgKg)
        result = 31 * result + Arrays.hashCode(relativeHumidityPercent)
        result = 31 * result + meanTemperatureC.hashCode()
        result = 31 * result + validationStatus.hashCode()
        result = 31 * result + runtimeById.hashCode()
        return result
    }
}

fun GreenhouseGeometry.toSnapshot(activeCellCount: Int, cellEdgeOverrideM: Double? = null): GridGeometrySnapshot =
    GridGeometrySnapshot(
        lengthM = lengthM,
        widthM = widthM,
        heightM = ridgeHeightM,
        cellEdgeM = cellEdgeOverrideM ?: cellEdgeM,
        nx = nx,
        ny = ny,
        nz = nz,
        cellCount = cellCount,
        activeCellCount = activeCellCount
    )
