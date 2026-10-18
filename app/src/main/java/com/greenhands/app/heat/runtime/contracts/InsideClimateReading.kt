package com.greenhands.app.heat.runtime.contracts

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.model.RH_HARD_MAX
import com.greenhands.app.heat.model.RH_HARD_MIN
import com.greenhands.app.heat.model.TEMP_HARD_MAX
import com.greenhands.app.heat.model.TEMP_HARD_MIN
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.simulation.model.ProvenanceClass

enum class InsideClimateSource {
    SIMULATED,
    RECORDED,
    ACTUAL_IOT
}

enum class InputValidity {
    VALID,
    INVALID,
    MISSING
}

/**
 * Immutable inside-climate contract for the field runtime.
 * Demo values are Simulated. Live IoT is Actual at the sensor only.
 */
data class InsideClimateReading(
    val temperatureC: Double?,
    val relativeHumidityPercent: Double?,
    val timestampMillis: Long?,
    val sensorPositionM: DomainPoint?,
    val source: InsideClimateSource,
    val provenance: ProvenanceClass,
    val sourceLabel: String,
    val validity: InputValidity,
    val freshness: FreshnessStatus
) {
    fun isUsableForControl(): Boolean =
        validity == InputValidity.VALID &&
            temperatureC != null &&
            relativeHumidityPercent != null &&
            temperatureC.isFinite() &&
            relativeHumidityPercent.isFinite() &&
            freshness != FreshnessStatus.STALE &&
            freshness != FreshnessStatus.MISSING

    companion object {
        fun simulated(
            temperatureC: Double?,
            relativeHumidityPercent: Double?
        ): InsideClimateReading {
            val validity = validityOf(temperatureC, relativeHumidityPercent)
            return InsideClimateReading(
                temperatureC = temperatureC,
                relativeHumidityPercent = relativeHumidityPercent,
                timestampMillis = null,
                sensorPositionM = null,
                source = InsideClimateSource.SIMULATED,
                provenance = ProvenanceClass.PREVIEW_ASSUMPTION,
                sourceLabel = PhysicsRuntimeCopy.DEMO_SIMULATION,
                validity = validity,
                freshness = FreshnessStatus.DEMO_CURRENT
            )
        }

        fun recorded(
            temperatureC: Double?,
            relativeHumidityPercent: Double?,
            timestampMillis: Long?
        ): InsideClimateReading {
            val validity = if (timestampMillis == null) {
                InputValidity.MISSING
            } else {
                validityOf(temperatureC, relativeHumidityPercent)
            }
            return InsideClimateReading(
                temperatureC = temperatureC,
                relativeHumidityPercent = relativeHumidityPercent,
                timestampMillis = timestampMillis,
                sensorPositionM = null,
                source = InsideClimateSource.RECORDED,
                provenance = ProvenanceClass.MEASURED_INPUT,
                sourceLabel = PhysicsRuntimeCopy.RECORDED_DATA,
                validity = validity,
                freshness = FreshnessStatus.RECORDED
            )
        }

        fun actualIot(
            temperatureC: Double?,
            relativeHumidityPercent: Double?,
            timestampMillis: Long?,
            sensorPositionM: DomainPoint?,
            nowMillis: Long,
            policy: FreshnessPolicy
        ): InsideClimateReading {
            val freshness = FreshnessEvaluator.insideClimate(
                InsideClimateSource.ACTUAL_IOT,
                timestampMillis,
                nowMillis,
                policy
            )
            val numeric = validityOf(temperatureC, relativeHumidityPercent)
            val validity = when {
                timestampMillis == null -> InputValidity.MISSING
                freshness == FreshnessStatus.STALE -> InputValidity.INVALID
                else -> numeric
            }
            return InsideClimateReading(
                temperatureC = temperatureC,
                relativeHumidityPercent = relativeHumidityPercent,
                timestampMillis = timestampMillis,
                sensorPositionM = sensorPositionM,
                source = InsideClimateSource.ACTUAL_IOT,
                provenance = ProvenanceClass.MEASURED_INPUT,
                sourceLabel = PhysicsRuntimeCopy.LIVE_READING,
                validity = validity,
                freshness = freshness
            )
        }

        fun validityOf(temperatureC: Double?, relativeHumidityPercent: Double?): InputValidity {
            if (temperatureC == null || relativeHumidityPercent == null) return InputValidity.MISSING
            if (!temperatureC.isFinite() || !relativeHumidityPercent.isFinite()) return InputValidity.INVALID
            if (temperatureC < TEMP_HARD_MIN || temperatureC > TEMP_HARD_MAX) return InputValidity.INVALID
            if (relativeHumidityPercent < RH_HARD_MIN || relativeHumidityPercent > RH_HARD_MAX) {
                return InputValidity.INVALID
            }
            return InputValidity.VALID
        }
    }
}
