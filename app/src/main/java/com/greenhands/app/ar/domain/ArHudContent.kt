package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentLabels
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.CropDefaults
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.telemetry.GreenhouseWeatherState
import com.greenhands.app.telemetry.WeatherCopy
import com.greenhands.app.telemetry.acceptedOrNull
import com.greenhands.app.telemetry.formatLiveClimateNumber

/**
 * Phase 3C.3A compact AR environmental HUD. Live IoT values stay nullable;
 * this phase never fabricates sensor readings.
 */
object ArHudPhase3C3A {
    const val STATUS = "Phase 3C.3A compact AR environmental and equipment status HUD"
    const val LIVE_CONTRACT =
        "Nullable temperatureC / relativeHumidityPercent; disconnected until a later IoT phase"
}

enum class LiveSensorUiState {
    LEGACY,
    CONNECTING,
    LIVE,
    STALE,
    DISCONNECTED
}

/**
 * Future IoT reading slot. Null fields mean no measurement. Do not substitute Demo or
 * crop-target values into live temperature or humidity.
 *
 * [uiState] LEGACY keeps Phase 3C.3A labels. Firebase telemetry uses CONNECTING/LIVE/STALE/DISCONNECTED.
 */
data class ArLiveEnvironmentReading(
    val temperatureC: Double? = null,
    val relativeHumidityPercent: Double? = null,
    val connected: Boolean = false,
    val timestampMillis: Long? = null,
    val uiState: LiveSensorUiState = LiveSensorUiState.LEGACY
) {
    companion object {
        val DISCONNECTED = ArLiveEnvironmentReading(
            temperatureC = null,
            relativeHumidityPercent = null,
            connected = false,
            timestampMillis = null,
            uiState = LiveSensorUiState.LEGACY
        )
    }
}

data class ArHudEquipmentLine(
    val type: EquipmentType,
    val count: Int,
    val title: String,
    val state: String,
    val tone: ArRuntimeTone = ArRuntimeTone.NOT_RUNNING
) {
    fun displayLine(): String = "$title — $state"
}

/**
 * Compact HUD equipment rows wrap instead of ellipsizing the count.
 * Layout mode is chosen once for the whole summary: stacked on a narrow
 * width, two aligned columns when every type fits on one line.
 */
object ArHudEquipmentSummary {
    const val GAP_DP = 8f
    const val PX_PER_CHAR = 6.8f
    const val SINGLE_LINE_HEIGHT_DP = 14f
    const val TWO_LINE_HEIGHT_DP = 28f

    fun stacked(
        title: String,
        state: String,
        widthDp: Float,
        pxPerChar: Float = PX_PER_CHAR
    ): Boolean {
        val needed = title.length * pxPerChar + GAP_DP + state.length * pxPerChar
        return needed > widthDp + 0.01f
    }

    fun sectionStacked(
        rows: List<Pair<String, String>>,
        widthDp: Float,
        pxPerChar: Float = PX_PER_CHAR
    ): Boolean = rows.any { (title, state) -> stacked(title, state, widthDp, pxPerChar) }

    fun assignedStacked(
        rows: List<Pair<String, String>>,
        widthDp: Float,
        pxPerChar: Float = PX_PER_CHAR
    ): List<Boolean> {
        val stacked = sectionStacked(rows, widthDp, pxPerChar)
        return rows.map { stacked }
    }

    fun countVisible(title: String): Boolean {
        val count = Regex("""\((\d+)\)\s*$""").find(title.trim())?.groupValues?.get(1)
        return count != null && !title.contains("...")
    }

    fun rowHeightDp(stacked: Boolean): Float =
        if (stacked) TWO_LINE_HEIGHT_DP else SINGLE_LINE_HEIGHT_DP

    val SAMPLE_STATES = listOf(
        "Simulated ON",
        "Simulated OFF",
        "Predicted ON",
        "Predicted OFF",
        "Actual ON",
        "Actual OFF",
        "1 Simulated ON",
        "Not running",
        "Not evaluated",
        "None placed"
    )
}

/**
 * Per-instance runtime row, keyed by the stable equipment UUID so Fan 1 — ON
 * can differ from Fan 2 — OFF.
 */
data class ArEquipmentInstanceStatus(
    val instanceId: String,
    val type: EquipmentType,
    val name: String,
    val displayNumber: Int,
    val state: String,
    val runtime: EquipmentRuntimeState = EquipmentRuntimeState.NOT_RUNNING
) {
    fun displayLine(): String = "$name — $state"
    val tone: ArRuntimeTone get() = EquipmentRuntimeEvaluator.tone(runtime)
}

data class ArEquipmentStatusSnapshot(
    val instances: List<ArEquipmentInstanceStatus>,
    val byId: Map<String, ArEquipmentInstanceStatus>,
    val aggregates: List<ArHudEquipmentLine>
)

data class ArHudModel(
    val crop: Crop?,
    val cropName: String,
    val stageName: String,
    val optimalTemperatureLabel: String,
    val optimalHumidityLabel: String,
    val liveTemperatureLabel: String,
    val liveHumidityLabel: String,
    val liveStatusLabel: String,
    val equipment: List<ArHudEquipmentLine>,
    val instanceStatuses: List<ArEquipmentInstanceStatus> = emptyList(),
    val outsideTemperatureLabel: String = "",
    val outsideHumidityLabel: String = "",
    val outsideLocationLabel: String = "",
    val outsideAttributionLabel: String = "",
    val outsideStatusLabel: String = "",
    val showsOutsideWeather: Boolean = false
) {
    fun allUserCopy(): List<String> = listOf(
        cropName,
        stageName,
        optimalTemperatureLabel,
        optimalHumidityLabel,
        liveTemperatureLabel,
        liveHumidityLabel,
        liveStatusLabel
    ) + if (showsOutsideWeather) {
        listOf(
            outsideTemperatureLabel,
            outsideHumidityLabel,
            outsideLocationLabel,
            outsideAttributionLabel,
            outsideStatusLabel
        )
    } else {
        emptyList()
    } + equipment.map { it.displayLine() }
}

/**
 * Readable CSP/CDP/CON, ESP/EDP/EON and FSP/FON/FDP meanings for Equipment Status.
 * The compact camera HUD must not show these codes.
 */
object ArThresholdGlossary {
    const val CSP = "CSP — Circulation set point"
    const val CDP = "CDP — Circulation dead/off point"
    const val CON = "CON — Circulation turns ON"
    const val ESP = "ESP — Exhaust set point"
    const val EDP = "EDP — Exhaust turns OFF"
    const val EON = "EON — Exhaust turns ON"
    const val FSP = "FSP — Fogger humidity set point"
    const val FON = "FON — Fogger turns ON below this humidity"
    const val FDP = "FDP — Fogger turns OFF above this humidity"

    val LINES = listOf(CSP, CDP, CON, ESP, EDP, EON, FSP, FON, FDP)

    fun containsThresholdCode(text: String): Boolean =
        THRESHOLD_CODES.any { code ->
            Regex("""(?<![A-Za-z])$code(?![A-Za-z])""").containsMatchIn(text)
        }

    private val THRESHOLD_CODES = listOf("CSP", "CDP", "CON", "ESP", "EDP", "EON", "FSP", "FON", "FDP")
}

object ArHudContent {
    const val UNAVAILABLE = "Unavailable"
    const val NOT_CONNECTED = "Not connected"
    const val SENSOR_NOT_CONNECTED = "Sensor not connected"
    const val SENSOR_CONNECTING = com.greenhands.app.telemetry.TelemetryCopy.CONNECTING
    const val SENSOR_LIVE = com.greenhands.app.telemetry.TelemetryCopy.LIVE
    const val SENSOR_STALE = com.greenhands.app.telemetry.TelemetryCopy.STALE
    const val SENSOR_DISCONNECTED = com.greenhands.app.telemetry.TelemetryCopy.DISCONNECTED
    const val DEMO_SIMULATION = "Demo simulation"
    const val LIVE_TEMPERATURE_PLACEHOLDER = "-- °C"
    const val LIVE_HUMIDITY_PLACEHOLDER = "-- % RH"
    const val HUMIDITY_UNIT = "% RH"
    const val NOT_RUNNING = ArEquipmentStatusCopy.NOT_RUNNING
    const val NOT_EVALUATED = EquipmentPresentationCopy.NOT_EVALUATED
    const val NONE_PLACED = "None placed"
    const val DEMO_STAGE_ID = "vegetative"
    const val CONTROLS_CIRCULATION = "Circulation controls"
    const val CONTROLS_EXHAUST = "Exhaust controls"
    const val CONTROLS_FOGGER = "Fogger controls"
    const val INSTANCES_HEADING = "Equipment instances"

    private val DUPLICATED_PERCENT_UNIT = Regex("""%\s*%(\s*RH)?""")

    val DEMO_CROP: Crop = Crop.TOMATO

    fun from(
        climate: OverlayClimate,
        mode: PreviewMode,
        live: ArLiveEnvironmentReading = ArLiveEnvironmentReading.DISCONNECTED,
        instances: List<EquipmentInstance> = emptyList(),
        runtimeById: Map<String, EquipmentRuntimeState> = emptyMap(),
        simulatedTemperatureC: Double? = null,
        simulatedHumidityPercent: Double? = null,
        deviceTelemetryReported: Boolean = false,
        visualizationRunning: Boolean = false,
        actualRelayById: Map<String, EquipmentRuntimeState> = emptyMap(),
        weather: GreenhouseWeatherState? = null
    ): ArHudModel {
        val resolved = resolveOverlay(climate, mode)
        val presentation = EquipmentPresentationContext(
            mode = mode,
            visualizationRunning = visualizationRunning,
            actualRelayById = actualRelayById
        )
        val snapshot = workingCopyStatus(
            instances = instances,
            runtimeById = runtimeById,
            presentation = presentation
        )
        val temperatureLabel: String
        val humidityLabel: String
        val statusLabel: String
        val showWeather = mode == PreviewMode.MY_GREENHOUSE
        if (mode == PreviewMode.DEMO) {
            temperatureLabel = temperatureReadingLabel(simulatedTemperatureC)
            humidityLabel = humidityReadingLabel(simulatedHumidityPercent)
            statusLabel = DEMO_SIMULATION
        } else {
            temperatureLabel = liveTemperatureLabel(live)
            humidityLabel = liveHumidityLabel(live)
            statusLabel = liveStatusLabel(live)
        }
        return ArHudModel(
            crop = resolved.crop,
            cropName = resolved.crop?.displayName ?: UNAVAILABLE,
            stageName = resolved.stage?.shortLabel ?: UNAVAILABLE,
            optimalTemperatureLabel = optimalTemperatureLabel(resolved.optimalTemperatureC),
            optimalHumidityLabel = optimalHumidityLabel(resolved.optimalHumidityPercent),
            liveTemperatureLabel = temperatureLabel,
            liveHumidityLabel = humidityLabel,
            liveStatusLabel = statusLabel,
            equipment = snapshot.aggregates,
            instanceStatuses = snapshot.instances,
            outsideTemperatureLabel = if (showWeather) outsideTemperatureLabel(weather) else "",
            outsideHumidityLabel = if (showWeather) outsideHumidityLabel(weather) else "",
            outsideLocationLabel = if (showWeather) outsideLocationLabel(weather) else "",
            outsideAttributionLabel = if (showWeather) WeatherCopy.MODELLED_OPEN_METEO else "",
            outsideStatusLabel = if (showWeather) outsideStatusLabel(weather) else "",
            showsOutsideWeather = showWeather
        )
    }

    /**
     * Working-copy snapshot. Counts and names come from placed (non-ghost) instances,
     * including unsaved additions and deletions. [runtimeById] is session-only ON/OFF
     * state keyed by equipment UUID; missing keys stay Not running.
     */
    fun workingCopyStatus(
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState> = emptyMap(),
        provenance: EquipmentStateProvenance? = null,
        presentation: EquipmentPresentationContext? = null
    ): ArEquipmentStatusSnapshot {
        val placed = instances.filter { !it.isGhost }
        val rows = placed
            .sortedWith(compareBy({ it.type.ordinal }, { it.displayNumber }, { it.id }))
            .map { inst ->
                val predicted = runtimeById[inst.id] ?: EquipmentRuntimeState.NOT_RUNNING
                val actual = presentation?.actualRelayById?.get(inst.id)
                val displayedRuntime =
                    if (actual == EquipmentRuntimeState.ON || actual == EquipmentRuntimeState.OFF) {
                        actual
                    } else {
                        predicted
                    }
                val state = if (presentation != null) {
                    EquipmentPresentation.label(inst.id, predicted, presentation)
                } else {
                    EquipmentRuntimeEvaluator.label(predicted, provenance)
                }
                ArEquipmentInstanceStatus(
                    instanceId = inst.id,
                    type = inst.type,
                    name = inst.displayLabel(),
                    displayNumber = inst.displayNumber,
                    state = state,
                    runtime = displayedRuntime
                )
            }
        return ArEquipmentStatusSnapshot(
            instances = rows,
            byId = rows.associateBy { it.instanceId },
            aggregates = equipmentLines(placed, runtimeById, provenance, presentation)
        )
    }

    /**
     * Demo Mode may fill missing crop/stage/targets from [CropProfileRegistry].
     * My Greenhouse never receives those Demo defaults.
     */
    fun resolveOverlay(climate: OverlayClimate, mode: PreviewMode): OverlayClimate {
        if (mode != PreviewMode.DEMO) return climate
        val crop = climate.crop ?: DEMO_CROP
        val stage = climate.stage ?: demoStageFor(crop)
        val rec = runCatching { CropDefaults.recommendation(crop, stage.id) }.getOrNull()
        val temp = climate.optimalTemperatureC ?: rec?.uiDayTemperatureC()
        val rh = climate.optimalHumidityPercent ?: rec?.uiDayHumidityPercent()
        val equipment = if (temp != null) HeatFormulas.configurationForTargets(temp, rh) else null
        return climate.copy(
            crop = crop,
            stage = stage,
            optimalTemperatureC = temp,
            optimalHumidityPercent = rh,
            temperatureLabel = if (temp != null) {
                "Optimal temperature: ${formatOneDecimal(temp)}°C"
            } else {
                climate.temperatureLabel
            },
            humidityLabel = if (rh != null) {
                "Optimal humidity: ${humidityReadingLabel(rh)}"
            } else {
                climate.humidityLabel
            },
            circulation = climate.circulation ?: equipment?.circulation,
            exhaust = climate.exhaust ?: equipment?.exhaust,
            fogger = climate.fogger ?: equipment?.fogger
        )
    }

    fun demoStageFor(crop: Crop): GrowthStage {
        val stages = CropProfileRegistry.stagesFor(crop)
        return stages.firstOrNull { it.id == DEMO_STAGE_ID } ?: stages.first()
    }

    fun demoOptimalTemperatureC(): Double =
        CropDefaults.recommendation(DEMO_CROP, DEMO_STAGE_ID).uiDayTemperatureC()

    fun demoOptimalHumidityPercent(): Double =
        CropDefaults.recommendation(DEMO_CROP, DEMO_STAGE_ID).uiDayHumidityPercent()
            ?: error("Demo tomato vegetative humidity is required")

    fun optimalTemperatureLabel(value: Double?): String = temperatureReadingLabel(value, missing = UNAVAILABLE)

    fun optimalHumidityLabel(value: Double?): String = humidityReadingLabel(value, missing = UNAVAILABLE)

    fun temperatureReadingLabel(value: Double?, missing: String = LIVE_TEMPERATURE_PLACEHOLDER): String =
        if (value != null && value.isFinite()) "${formatOneDecimal(value)} °C" else missing

    fun humidityReadingLabel(value: Double?, missing: String = LIVE_HUMIDITY_PLACEHOLDER): String =
        if (value != null && value.isFinite()) "${formatOneDecimal(value)} $HUMIDITY_UNIT" else missing

    fun hasDuplicatedPercentUnit(text: String): Boolean = DUPLICATED_PERCENT_UNIT.containsMatchIn(text)

    fun liveTemperatureLabel(live: ArLiveEnvironmentReading): String {
        if (live.uiState == LiveSensorUiState.CONNECTING) return LIVE_TEMPERATURE_PLACEHOLDER
        if (live.uiState == LiveSensorUiState.LIVE ||
            live.uiState == LiveSensorUiState.STALE ||
            live.uiState == LiveSensorUiState.DISCONNECTED
        ) {
            val value = live.temperatureC
            if (value == null || !value.isFinite()) {
                return if (live.uiState == LiveSensorUiState.LIVE) UNAVAILABLE else LIVE_TEMPERATURE_PLACEHOLDER
            }
            return "${com.greenhands.app.telemetry.formatLiveClimateNumber(value)} °C"
        }
        if (!live.connected) return LIVE_TEMPERATURE_PLACEHOLDER
        val value = live.temperatureC ?: return UNAVAILABLE
        return temperatureReadingLabel(value)
    }

    fun liveHumidityLabel(live: ArLiveEnvironmentReading): String {
        if (live.uiState == LiveSensorUiState.CONNECTING) return LIVE_HUMIDITY_PLACEHOLDER
        if (live.uiState == LiveSensorUiState.LIVE ||
            live.uiState == LiveSensorUiState.STALE ||
            live.uiState == LiveSensorUiState.DISCONNECTED
        ) {
            val value = live.relativeHumidityPercent
            if (value == null || !value.isFinite()) {
                return if (live.uiState == LiveSensorUiState.LIVE) UNAVAILABLE else LIVE_HUMIDITY_PLACEHOLDER
            }
            return "${com.greenhands.app.telemetry.formatLiveClimateNumber(value)} $HUMIDITY_UNIT"
        }
        if (!live.connected) return LIVE_HUMIDITY_PLACEHOLDER
        val value = live.relativeHumidityPercent ?: return UNAVAILABLE
        return humidityReadingLabel(value)
    }

    fun outsideTemperatureLabel(weather: GreenhouseWeatherState?): String {
        if (weather == null || weather is GreenhouseWeatherState.Connecting) {
            return "Outside $LIVE_TEMPERATURE_PLACEHOLDER"
        }
        val value = weather.acceptedOrNull()?.outsideTemperatureC
        if (value == null || !value.isFinite()) return "Outside $LIVE_TEMPERATURE_PLACEHOLDER"
        return "Outside ${formatLiveClimateNumber(value)} °C"
    }

    fun outsideHumidityLabel(weather: GreenhouseWeatherState?): String {
        if (weather == null || weather is GreenhouseWeatherState.Connecting) {
            return "Outside $LIVE_HUMIDITY_PLACEHOLDER"
        }
        val value = weather.acceptedOrNull()?.outsideHumidityPct
        if (value == null || !value.isFinite()) return "Outside $LIVE_HUMIDITY_PLACEHOLDER"
        return "Outside ${formatLiveClimateNumber(value)} $HUMIDITY_UNIT"
    }

    fun outsideLocationLabel(weather: GreenhouseWeatherState?): String =
        weather?.acceptedOrNull()?.locationName.orEmpty()

    fun outsideStatusLabel(weather: GreenhouseWeatherState?): String = when (weather) {
        null -> WeatherCopy.UNAVAILABLE
        GreenhouseWeatherState.Connecting -> WeatherCopy.CONNECTING
        is GreenhouseWeatherState.Live -> WeatherCopy.LIVE
        is GreenhouseWeatherState.Stale -> WeatherCopy.STALE
        is GreenhouseWeatherState.Unavailable -> WeatherCopy.UNAVAILABLE
    }

    fun weatherIsLive(weather: GreenhouseWeatherState?): Boolean =
        weather is GreenhouseWeatherState.Live

    fun liveStatusLabel(live: ArLiveEnvironmentReading): String = when (live.uiState) {
        LiveSensorUiState.CONNECTING -> SENSOR_CONNECTING
        LiveSensorUiState.LIVE -> SENSOR_LIVE
        LiveSensorUiState.STALE -> SENSOR_STALE
        LiveSensorUiState.DISCONNECTED -> SENSOR_DISCONNECTED
        LiveSensorUiState.LEGACY -> if (live.connected) "Sensor connected" else SENSOR_NOT_CONNECTED
    }

    fun equipmentLines(
        instances: List<EquipmentInstance> = emptyList(),
        runtimeById: Map<String, EquipmentRuntimeState> = emptyMap(),
        provenance: EquipmentStateProvenance? = null,
        presentation: EquipmentPresentationContext? = null
    ): List<ArHudEquipmentLine> {
        val placed = instances.filter { !it.isGhost }
        return EquipmentType.entries.map { type ->
            val ofType = placed.filter { it.type == type }
            val count = ofType.size
            val states = ofType.map { runtimeById[it.id] ?: EquipmentRuntimeState.NOT_RUNNING }
            val state = when {
                count == 0 -> NONE_PLACED
                presentation != null -> EquipmentPresentation.aggregate(
                    instanceIds = ofType.map { it.id },
                    predictedById = runtimeById,
                    context = presentation
                )
                else -> EquipmentRuntimeEvaluator.aggregateLabel(states, provenance)
            }
            val tone = when {
                count == 0 -> ArRuntimeTone.EMPTY
                presentation != null && state == EquipmentPresentationCopy.NOT_EVALUATED -> {
                    ArRuntimeTone.NOT_RUNNING
                }
                else -> EquipmentRuntimeEvaluator.aggregateTone(states)
            }
            ArHudEquipmentLine(
                type = type,
                count = count,
                title = aggregateTitle(type, count),
                state = state,
                tone = tone
            )
        }
    }

    fun aggregateTitle(type: EquipmentType, count: Int): String {
        val base = if (count == 1) {
            EquipmentLabels.typeTitle(type)
        } else {
            typeTitlePlural(type)
        }
        return "$base ($count)"
    }

    fun typeTitlePlural(type: EquipmentType): String = when (type) {
        EquipmentType.CIRCULATION_FAN -> "Circulation Fans"
        EquipmentType.EXHAUST_FAN -> "Exhaust Fans"
        EquipmentType.FOGGER -> "Foggers"
    }

    fun controlHeading(type: EquipmentType): String = when (type) {
        EquipmentType.CIRCULATION_FAN -> CONTROLS_CIRCULATION
        EquipmentType.EXHAUST_FAN -> CONTROLS_EXHAUST
        EquipmentType.FOGGER -> CONTROLS_FOGGER
    }

    fun typeFromDisplayName(name: String): EquipmentType = when {
        name.startsWith("Circulation", ignoreCase = true) -> EquipmentType.CIRCULATION_FAN
        name.startsWith("Exhaust", ignoreCase = true) -> EquipmentType.EXHAUST_FAN
        else -> EquipmentType.FOGGER
    }

    fun configuredEquipmentTypes(): List<EquipmentType> = EquipmentType.entries.toList()

    fun usesOnOffRuntimeLabel(state: String): Boolean {
        val trimmed = state.trim()
        return trimmed.equals("ON", ignoreCase = true) ||
            trimmed.equals("OFF", ignoreCase = true) ||
            trimmed.endsWith(" ON", ignoreCase = true) ||
            trimmed.endsWith(" OFF", ignoreCase = true)
    }
}
