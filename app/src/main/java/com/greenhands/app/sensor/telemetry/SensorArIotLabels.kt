package com.greenhands.app.sensor.telemetry

import com.greenhands.app.sensor.ar.ArRealSensorPlacement
import com.greenhands.app.sensor.ar.ArSensorMarker
import com.greenhands.app.sensor.ar.ArVisualizationSnapshot
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.ar.VirtualGreenhouseLabels
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.telemetry.GreenhouseTelemetryPath
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import com.greenhands.app.telemetry.TelemetryCopy
import java.util.Locale

/**
 * Live IoT labels for Sensor Real AR.
 *
 * The Firebase `current` payload is the shared [GREENHOUSE_ID] (`GH_001`)
 * greenhouse reading — not a unique value per conceptual marker. Every
 * temperature marker shows the same authoritative `temperature_c` (DS18B20).
 * Every humidity marker shows the same authoritative `humidity_pct` (HTU21D).
 *
 * Never uses `greenhouse_temp_c`, `raw.htu21d_temperature_c`, weather, Demo,
 * or stale last-known numbers as realtime readings.
 */
enum class SensorArIotFreshness {
    CONNECTING,
    LIVE,
    STALE,
    UNAVAILABLE
}

data class SensorArIotLabel(
    val sensorId: String,
    val type: SensorType,
    val abbrev: String,
    val worldX: Float,
    val worldY: Float,
    val worldZ: Float,
    val localX: Float,
    val localY: Float,
    val localZ: Float,
    val freshness: SensorArIotFreshness,
    val valueText: String?,
    val statusText: String,
    val sharedGreenhouseId: String = GreenhouseTelemetryPath.GREENHOUSE_ID,
    val firebaseField: String
) {
    val titleLine: String get() = "$abbrev · $sensorId"

    val detailLine: String
        get() = when (freshness) {
            SensorArIotFreshness.LIVE -> valueText ?: statusText
            SensorArIotFreshness.CONNECTING -> statusText
            SensorArIotFreshness.STALE -> statusText
            SensorArIotFreshness.UNAVAILABLE -> statusText
        }
}

object SensorArIotLabels {

    const val SHARED_GREENHOUSE_ID = GreenhouseTelemetryPath.GREENHOUSE_ID
    const val TEMPERATURE_FIELD = TelemetryCopy.AUTHORITATIVE_TEMPERATURE
    const val HUMIDITY_FIELD = TelemetryCopy.AUTHORITATIVE_HUMIDITY
    const val FORBIDDEN_HTU21D_TEMPERATURE = TelemetryCopy.RAW_HTU21D_TEMPERATURE

    fun showsLiveClimate(type: SensorType): Boolean = when (type) {
        SensorType.TEMPERATURE, SensorType.HUMIDITY -> true
        SensorType.SOIL_MOISTURE, SensorType.LIGHT_INTENSITY -> false
    }

    fun firebaseField(type: SensorType): String? = when (type) {
        SensorType.TEMPERATURE -> TEMPERATURE_FIELD
        SensorType.HUMIDITY -> HUMIDITY_FIELD
        else -> null
    }

    fun freshnessOf(state: GreenhouseTelemetryState): SensorArIotFreshness = when (state) {
        GreenhouseTelemetryState.Connecting -> SensorArIotFreshness.CONNECTING
        is GreenhouseTelemetryState.Live -> SensorArIotFreshness.LIVE
        is GreenhouseTelemetryState.Stale -> SensorArIotFreshness.STALE
        is GreenhouseTelemetryState.Disconnected -> SensorArIotFreshness.UNAVAILABLE
    }

    fun liveTemperatureC(state: GreenhouseTelemetryState): Double? =
        (state as? GreenhouseTelemetryState.Live)?.snapshot?.temperatureC

    fun liveHumidityPct(state: GreenhouseTelemetryState): Double? =
        (state as? GreenhouseTelemetryState.Live)?.snapshot?.humidityPct

    fun formatLiveTemperature(celsius: Double): String =
        String.format(Locale.US, "%.2f °C · Live", celsius)

    fun formatLiveHumidity(percent: Double): String =
        String.format(Locale.US, "%.2f %% RH · Live", percent)

    fun statusText(freshness: SensorArIotFreshness): String = when (freshness) {
        SensorArIotFreshness.CONNECTING -> "Connecting…"
        SensorArIotFreshness.LIVE -> TelemetryCopy.LIVE
        SensorArIotFreshness.STALE -> "Stale"
        SensorArIotFreshness.UNAVAILABLE -> "Unavailable"
    }

    /**
     * Labels for active, visible temperature/humidity markers. Soil and light
     * never receive a T/H reading. Multiple same-type markers share GH_001.
     */
    fun labelsFor(
        pose: com.greenhands.app.sensor.ar.ArGreenhousePose,
        snapshot: ArVisualizationSnapshot,
        state: GreenhouseTelemetryState,
        sensorsVisible: Boolean
    ): List<SensorArIotLabel> {
        if (!sensorsVisible) return emptyList()
        if (!ArRealSensorPlacement.shouldShowMarkers(pose.phase)) return emptyList()
        val freshness = freshnessOf(state)
        val markers = ArRealSensorPlacement.buildRenderMarkers(
            pose = pose,
            snapshot = snapshot,
            typeFilter = snapshot.selectedTypeFilter
        )
        return markers.mapNotNull { marker ->
            labelFor(pose, snapshot, marker.sensor, marker.local, freshness, state)
        }
    }

    fun labelFor(
        pose: com.greenhands.app.sensor.ar.ArGreenhousePose,
        snapshot: ArVisualizationSnapshot,
        sensor: ArSensorMarker,
        local: ArRealSensorPlacement.LocalPosition = ArRealSensorPlacement.localPosition(
            sensor,
            snapshot.physical
        ),
        freshness: SensorArIotFreshness,
        state: GreenhouseTelemetryState
    ): SensorArIotLabel? {
        if (!showsLiveClimate(sensor.type)) return null
        if (sensor.status != SensorStatus.ACTIVE) return null
        val field = firebaseField(sensor.type) ?: return null
        val world = ArWorldMapper.localToWorld(pose, local.x, local.y, local.z) ?: return null
        val valueText = when {
            freshness != SensorArIotFreshness.LIVE -> null
            sensor.type == SensorType.TEMPERATURE -> liveTemperatureC(state)?.let(::formatLiveTemperature)
            sensor.type == SensorType.HUMIDITY -> liveHumidityPct(state)?.let(::formatLiveHumidity)
            else -> null
        }
        return SensorArIotLabel(
            sensorId = sensor.id,
            type = sensor.type,
            abbrev = VirtualGreenhouseLabels.sensorAbbrev(sensor),
            worldX = world.x,
            worldY = world.y,
            worldZ = world.z,
            localX = local.x,
            localY = local.y,
            localZ = local.z,
            freshness = freshness,
            valueText = valueText,
            statusText = statusText(freshness),
            sharedGreenhouseId = stateGreenhouseId(state),
            firebaseField = field
        )
    }

    fun usesSharedGh001Reading(labels: List<SensorArIotLabel>, type: SensorType): Boolean {
        val ofType = labels.filter { it.type == type }
        if (ofType.size < 2) return ofType.all { it.sharedGreenhouseId == SHARED_GREENHOUSE_ID }
        return ofType.all { it.sharedGreenhouseId == SHARED_GREENHOUSE_ID } &&
            ofType.map { it.detailLine }.distinct().size == 1
    }

    private fun stateGreenhouseId(state: GreenhouseTelemetryState): String = when (state) {
        is GreenhouseTelemetryState.Live ->
            state.snapshot.greenhouseId
        is GreenhouseTelemetryState.Stale ->
            state.lastKnown?.greenhouseId ?: SHARED_GREENHOUSE_ID
        is GreenhouseTelemetryState.Disconnected ->
            state.lastKnown?.greenhouseId ?: SHARED_GREENHOUSE_ID
        GreenhouseTelemetryState.Connecting -> SHARED_GREENHOUSE_ID
    }
}
