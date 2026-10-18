package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.domain.HeatValidation
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Phase 3C.3B session-only equipment runtime. Physics thresholds stay in Phase 3A;
 * this layer applies the same hysteresis per placed UUID and never persists ON/OFF.
 */
object ArRuntimePhase3C3B {
    const val STATUS = "Phase 3C.3B equipment runtime state controller"
    const val STARTS_ON_DOUBLE_TAP = false
    const val PERSISTS_WITH_LAYOUT = false
    const val ANIMATES_GLB = false
    const val VISUALIZATION_SEPARATE_FROM_LIVE_MONITORING = true
    const val STOP_HIDES_VISUAL_EFFECTS_ONLY = true
    const val STOP_DOES_NOT_CONTROL_PHYSICAL_EQUIPMENT = true
}

object ArRuntimeCopy {
    const val BEHAVIOR = "Visualization"
    const val START = "Start Visualization"
    const val STOP = "Stop Visualization"
    const val RUNNING = "Running"
    const val NOT_RUNNING = ArEquipmentStatusCopy.NOT_RUNNING
    const val ON = "ON"
    const val OFF = "OFF"
    const val SIMULATED = "Simulated"
    const val PREDICTED = "Predicted"
    const val ACTUAL = "Actual"
    const val INPUT_DEMO = "Demo simulation"
    const val INPUT_LIVE = "My Greenhouse — live reading"
    const val SIMULATED_DISCLAIMER = "Demo simulation — not a live IoT reading"
    const val SENSOR_NOT_CONNECTED = ArHudContent.SENSOR_NOT_CONNECTED
    const val MY_GREENHOUSE_START_DISABLED =
        "Sensor not connected. Start Visualization needs a live temperature and humidity reading."
    const val MISSING_CIRCULATION =
        "Circulation thresholds are missing. Start Visualization cannot guess CON or CDP."
    const val MISSING_EXHAUST =
        "Exhaust thresholds are missing. Start Visualization cannot guess EON or EDP."
    const val MISSING_FOGGER =
        "Fogger thresholds are missing. Start Visualization cannot guess FON or FDP."
    const val INVALID_THRESHOLDS = "Equipment thresholds are invalid. Start Visualization cannot guess replacements."
    const val EDP_EQUALS_CON = "Exhaust off point must equal circulation on point (EDP = CON)."
    const val MISSING_CON_FOR_EDP =
        "Cannot verify EDP = CON because circulation ON is missing."
    const val MISSING_TEMPERATURE = "A temperature reading is required to start Visualization."
    const val MISSING_HUMIDITY = "A humidity reading is required to start Visualization."
    const val MISSING_TEMPERATURE_AND_HUMIDITY =
        "Temperature and humidity readings are required to start Visualization."
    const val INPUT_UNAVAILABLE =
        "Required temperature or humidity input is unavailable. Visualization stopped."
    const val CONFIGURATION_CHANGED = "Visualization stopped because the configuration changed."
}

/**
 * Source of an equipment ON/OFF description. Demo input is simulated. A later
 * temperature/humidity-only IoT feed is predicted. Actual is reserved for
 * device/relay telemetry and must not be claimed without it.
 */
enum class EquipmentStateProvenance {
    SIMULATED,
    PREDICTED,
    ACTUAL
}

object EquipmentStateSource {
    fun forInput(
        source: EquipmentRuntimeInputSource,
        deviceTelemetryReported: Boolean = false
    ): EquipmentStateProvenance {
        if (deviceTelemetryReported) return EquipmentStateProvenance.ACTUAL
        return when (source) {
            EquipmentRuntimeInputSource.DEMO_SIMULATED -> EquipmentStateProvenance.SIMULATED
            EquipmentRuntimeInputSource.MY_GREENHOUSE_LIVE -> EquipmentStateProvenance.PREDICTED
        }
    }

    fun forMode(
        mode: PreviewMode,
        deviceTelemetryReported: Boolean = false
    ): EquipmentStateProvenance {
        if (deviceTelemetryReported) return EquipmentStateProvenance.ACTUAL
        return if (mode == PreviewMode.DEMO) {
            EquipmentStateProvenance.SIMULATED
        } else {
            EquipmentStateProvenance.PREDICTED
        }
    }

    fun claimsActualState(provenance: EquipmentStateProvenance): Boolean =
        provenance == EquipmentStateProvenance.ACTUAL
}

enum class EquipmentRuntimeInputSource {
    DEMO_SIMULATED,
    MY_GREENHOUSE_LIVE
}

enum class ArRuntimeTone {
    ON,
    OFF,
    NOT_RUNNING,
    MIXED,
    EMPTY
}

data class EquipmentRuntimeInput(
    val source: EquipmentRuntimeInputSource,
    val temperatureC: Double?,
    val humidityPercent: Double?,
    val connected: Boolean
) {
    fun sourceLabel(): String = when (source) {
        EquipmentRuntimeInputSource.DEMO_SIMULATED -> ArRuntimeCopy.INPUT_DEMO
        EquipmentRuntimeInputSource.MY_GREENHOUSE_LIVE -> ArRuntimeCopy.INPUT_LIVE
    }
}

data class EquipmentRuntimeThresholds(
    val circulation: CirculationThresholds?,
    val exhaust: ExhaustThresholds?,
    val fogger: FoggerThresholds?
)

object EquipmentRuntimeEvaluator {
    fun label(
        state: EquipmentRuntimeState,
        provenance: EquipmentStateProvenance? = null
    ): String {
        val base = when (state) {
            EquipmentRuntimeState.NOT_RUNNING -> ArRuntimeCopy.NOT_RUNNING
            EquipmentRuntimeState.ON -> ArRuntimeCopy.ON
            EquipmentRuntimeState.OFF -> ArRuntimeCopy.OFF
        }
        if (state == EquipmentRuntimeState.NOT_RUNNING || provenance == null) return base
        return when (provenance) {
            EquipmentStateProvenance.SIMULATED -> "${ArRuntimeCopy.SIMULATED} $base"
            EquipmentStateProvenance.PREDICTED -> "${ArRuntimeCopy.PREDICTED} $base"
            EquipmentStateProvenance.ACTUAL -> "${ArRuntimeCopy.ACTUAL} $base"
        }
    }

    fun tone(state: EquipmentRuntimeState): ArRuntimeTone = when (state) {
        EquipmentRuntimeState.ON -> ArRuntimeTone.ON
        EquipmentRuntimeState.OFF -> ArRuntimeTone.OFF
        EquipmentRuntimeState.NOT_RUNNING -> ArRuntimeTone.NOT_RUNNING
    }

    fun aggregateTone(states: List<EquipmentRuntimeState>): ArRuntimeTone {
        if (states.isEmpty()) return ArRuntimeTone.EMPTY
        val distinct = states.toSet()
        return when {
            distinct.size == 1 -> tone(distinct.single())
            else -> ArRuntimeTone.MIXED
        }
    }

    /**
     * Compact HUD aggregate, e.g. "2 ON" or "1 ON · 1 OFF". All Not running stays
     * "Not running" so a stopped session does not look like OFF. Demo prefixes
     * ON/OFF as Simulated; My Greenhouse IoT without relay telemetry is Predicted.
     */
    fun aggregateLabel(
        states: List<EquipmentRuntimeState>,
        provenance: EquipmentStateProvenance? = null
    ): String {
        if (states.isEmpty()) return ArHudContent.NONE_PLACED
        val on = states.count { it == EquipmentRuntimeState.ON }
        val off = states.count { it == EquipmentRuntimeState.OFF }
        val idle = states.count { it == EquipmentRuntimeState.NOT_RUNNING }
        if (idle == states.size) return ArRuntimeCopy.NOT_RUNNING
        val parts = ArrayList<String>(3)
        if (on > 0) parts += "$on ${label(EquipmentRuntimeState.ON, provenance)}"
        if (off > 0) parts += "$off ${label(EquipmentRuntimeState.OFF, provenance)}"
        if (idle > 0) parts += "$idle ${ArRuntimeCopy.NOT_RUNNING}"
        return parts.joinToString(" · ")
    }

    fun notRunningById(instances: List<EquipmentInstance>): Map<String, EquipmentRuntimeState> =
        placed(instances).associate { it.id to EquipmentRuntimeState.NOT_RUNNING }

    fun runtimeInput(
        mode: PreviewMode,
        simulatedTemperatureC: Double?,
        simulatedHumidityPercent: Double?,
        live: ArLiveEnvironmentReading
    ): EquipmentRuntimeInput {
        return if (mode == PreviewMode.DEMO) {
            EquipmentRuntimeInput(
                source = EquipmentRuntimeInputSource.DEMO_SIMULATED,
                temperatureC = simulatedTemperatureC,
                humidityPercent = simulatedHumidityPercent,
                connected = true
            )
        } else {
            EquipmentRuntimeInput(
                source = EquipmentRuntimeInputSource.MY_GREENHOUSE_LIVE,
                temperatureC = live.temperatureC.takeIf { live.connected },
                humidityPercent = live.relativeHumidityPercent.takeIf { live.connected },
                connected = live.connected
            )
        }
    }

    fun startBlockReason(
        instances: List<EquipmentInstance>,
        thresholds: EquipmentRuntimeThresholds,
        input: EquipmentRuntimeInput,
        mode: PreviewMode,
        liveReadingFresh: Boolean = true
    ): String? {
        if (mode == PreviewMode.MY_GREENHOUSE && (!input.connected || !liveReadingFresh)) {
            return ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED
        }
        return validate(instances, thresholds, input.temperatureC, input.humidityPercent)
    }

    fun validate(
        instances: List<EquipmentInstance>,
        thresholds: EquipmentRuntimeThresholds,
        temperatureC: Double?,
        humidityPercent: Double?
    ): String? {
        val types = placed(instances).map { it.type }.toSet()
        val thresholdError = validateThresholds(types, thresholds)
        if (thresholdError != null) return thresholdError
        return validateReadings(types, temperatureC, humidityPercent)
    }

    fun validateThresholds(
        types: Set<EquipmentType>,
        thresholds: EquipmentRuntimeThresholds
    ): String? {
        val needsCirculation = EquipmentType.CIRCULATION_FAN in types || EquipmentType.EXHAUST_FAN in types
        val circulation = thresholds.circulation
        if (EquipmentType.CIRCULATION_FAN in types && circulation == null) {
            return ArRuntimeCopy.MISSING_CIRCULATION
        }
        if (circulation != null && needsCirculation) {
            if (!finite(circulation.cdp, circulation.csp, circulation.con)) {
                return ArRuntimeCopy.INVALID_THRESHOLDS
            }
            HeatValidation.circulationOrder(circulation.cdp, circulation.csp, circulation.con)?.let {
                return ArRuntimeCopy.INVALID_THRESHOLDS
            }
        }
        if (EquipmentType.EXHAUST_FAN in types) {
            val exhaust = thresholds.exhaust ?: return ArRuntimeCopy.MISSING_EXHAUST
            if (!finite(exhaust.edp, exhaust.esp, exhaust.eon)) {
                return ArRuntimeCopy.INVALID_THRESHOLDS
            }
            HeatValidation.exhaustOrder(exhaust.edp, exhaust.esp, exhaust.eon)?.let {
                return ArRuntimeCopy.INVALID_THRESHOLDS
            }
            val con = circulation?.con ?: return ArRuntimeCopy.MISSING_CON_FOR_EDP
            if (exhaust.edp != con) return ArRuntimeCopy.EDP_EQUALS_CON
        }
        if (EquipmentType.FOGGER in types) {
            val fogger = thresholds.fogger ?: return ArRuntimeCopy.MISSING_FOGGER
            if (!finite(fogger.fon, fogger.fsp, fogger.fdp)) {
                return ArRuntimeCopy.INVALID_THRESHOLDS
            }
            HeatValidation.foggerOrder(fogger.fon, fogger.fsp, fogger.fdp)?.let {
                return ArRuntimeCopy.INVALID_THRESHOLDS
            }
        }
        return null
    }

    fun validateReadings(
        types: Set<EquipmentType>,
        temperatureC: Double?,
        humidityPercent: Double?
    ): String? {
        val needsTemperature = EquipmentType.CIRCULATION_FAN in types || EquipmentType.EXHAUST_FAN in types
        val needsHumidity = EquipmentType.FOGGER in types
        val temperatureOk = !needsTemperature || usable(temperatureC)
        val humidityOk = !needsHumidity || usable(humidityPercent)
        return when {
            !temperatureOk && !humidityOk -> ArRuntimeCopy.MISSING_TEMPERATURE_AND_HUMIDITY
            !temperatureOk -> ArRuntimeCopy.MISSING_TEMPERATURE
            !humidityOk -> ArRuntimeCopy.MISSING_HUMIDITY
            else -> null
        }
    }

    /**
     * Same comparisons as [com.greenhands.app.heat.simulation.control.EquipmentController.update]
     * applied independently to each placed UUID.
     */
    fun evaluateAll(
        instances: List<EquipmentInstance>,
        previousById: Map<String, EquipmentRuntimeState>,
        thresholds: EquipmentRuntimeThresholds,
        temperatureC: Double?,
        humidityPercent: Double?
    ): Map<String, EquipmentRuntimeState> {
        return placed(instances).associate { inst ->
            inst.id to evaluateOne(
                type = inst.type,
                previous = previousById[inst.id] ?: EquipmentRuntimeState.OFF,
                thresholds = thresholds,
                temperatureC = temperatureC,
                humidityPercent = humidityPercent
            )
        }
    }

    fun evaluateOne(
        type: EquipmentType,
        previous: EquipmentRuntimeState,
        thresholds: EquipmentRuntimeThresholds,
        temperatureC: Double?,
        humidityPercent: Double?
    ): EquipmentRuntimeState {
        return when (type) {
            EquipmentType.CIRCULATION_FAN -> {
                val circulation = thresholds.circulation ?: return EquipmentRuntimeState.OFF
                val temperature = temperatureC ?: return EquipmentRuntimeState.OFF
                nextCirculation(previous, temperature, circulation)
            }
            EquipmentType.EXHAUST_FAN -> {
                val exhaust = thresholds.exhaust ?: return EquipmentRuntimeState.OFF
                val temperature = temperatureC ?: return EquipmentRuntimeState.OFF
                nextExhaust(previous, temperature, exhaust)
            }
            EquipmentType.FOGGER -> {
                val fogger = thresholds.fogger ?: return EquipmentRuntimeState.OFF
                val humidity = humidityPercent ?: return EquipmentRuntimeState.OFF
                nextFogger(previous, humidity, fogger)
            }
        }
    }

    fun nextCirculation(
        previous: EquipmentRuntimeState,
        temperatureC: Double,
        thresholds: CirculationThresholds
    ): EquipmentRuntimeState {
        var on = previous == EquipmentRuntimeState.ON
        if (temperatureC >= thresholds.con) on = true
        if (temperatureC <= thresholds.cdp) on = false
        return if (on) EquipmentRuntimeState.ON else EquipmentRuntimeState.OFF
    }

    fun nextExhaust(
        previous: EquipmentRuntimeState,
        temperatureC: Double,
        thresholds: ExhaustThresholds
    ): EquipmentRuntimeState {
        var on = previous == EquipmentRuntimeState.ON
        if (temperatureC >= thresholds.eon) on = true
        if (temperatureC <= thresholds.edp) on = false
        return if (on) EquipmentRuntimeState.ON else EquipmentRuntimeState.OFF
    }

    fun nextFogger(
        previous: EquipmentRuntimeState,
        humidityPercent: Double,
        thresholds: FoggerThresholds
    ): EquipmentRuntimeState {
        var on = previous == EquipmentRuntimeState.ON
        if (humidityPercent <= thresholds.fon) on = true
        if (humidityPercent >= thresholds.fdp) on = false
        return if (on) EquipmentRuntimeState.ON else EquipmentRuntimeState.OFF
    }

    private fun placed(instances: List<EquipmentInstance>): List<EquipmentInstance> =
        instances.filter { !it.isGhost }

    private fun finite(vararg values: Double): Boolean = values.all { it.isFinite() }

    private fun usable(value: Double?): Boolean = value != null && value.isFinite()
}
