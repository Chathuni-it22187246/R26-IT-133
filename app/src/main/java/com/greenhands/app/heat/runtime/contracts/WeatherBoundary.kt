package com.greenhands.app.heat.runtime.contracts

import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.simulation.model.ProvenanceClass

enum class WeatherBoundaryStatus {
    AVAILABLE,
    MISSING,
    STALE,
    INVALID
}

enum class WeatherBoundaryKind {
    DEMO_SIMULATED,
    ESTIMATED_OUTSIDE_WEATHER,
    MISSING
}

enum class WeatherBoundaryProvenance {
    DEMO_ONLY_ASSUMPTION,
    WEATHER_API_ESTIMATE,
    MISSING
}

/**
 * Outside weather boundary contract. This phase does not call Open-Meteo.
 * Weather is never labelled Actual and is used only as unconditioned exhaust inlet air.
 *
 * Demo Mode may supply a [WeatherBoundaryKind.DEMO_SIMULATED] boundary. That object is
 * never an Open-Meteo, Actual, Measured or Estimated outside-weather reading, has no
 * greenhouse latitude/longitude, and must not be copied into My Greenhouse.
 */
data class WeatherBoundary(
    val outsideTemperatureC: Double?,
    val outsideHumidityPercent: Double?,
    val windSpeedMps: Double?,
    val windDirectionDegrees: Double?,
    val solarRadiationWm2: Double?,
    val weatherDataTimeMillis: Long?,
    val fetchedAtMillis: Long?,
    val greenhouseLatitude: Double?,
    val greenhouseLongitude: Double?,
    val provider: String?,
    val status: WeatherBoundaryStatus,
    val sourceLabel: String = PhysicsRuntimeCopy.ESTIMATED_OUTSIDE_WEATHER,
    val provenance: WeatherBoundaryProvenance = WeatherBoundaryProvenance.MISSING,
    val kind: WeatherBoundaryKind = WeatherBoundaryKind.MISSING,
    val locationName: String? = null
) {
    fun permitsQuantitativeExhaust(): Boolean =
        status == WeatherBoundaryStatus.AVAILABLE &&
            outsideTemperatureC != null &&
            outsideHumidityPercent != null &&
            outsideTemperatureC.isFinite() &&
            outsideHumidityPercent.isFinite() &&
            outsideHumidityPercent >= 0.0

    fun parameterRecordProvenance(): ProvenanceClass = ProvenanceClass.PREVIEW_ASSUMPTION

    companion object {
        fun missing(): WeatherBoundary = WeatherBoundary(
            outsideTemperatureC = null,
            outsideHumidityPercent = null,
            windSpeedMps = null,
            windDirectionDegrees = null,
            solarRadiationWm2 = null,
            weatherDataTimeMillis = null,
            fetchedAtMillis = null,
            greenhouseLatitude = null,
            greenhouseLongitude = null,
            provider = null,
            status = WeatherBoundaryStatus.MISSING,
            sourceLabel = PhysicsRuntimeCopy.ESTIMATED_OUTSIDE_WEATHER,
            provenance = WeatherBoundaryProvenance.MISSING,
            kind = WeatherBoundaryKind.MISSING,
            locationName = null
        )

        /**
         * Explicit Demo-only simulated exhaust inlet. Not measured weather, not Open-Meteo,
         * and not Estimated outside weather.
         */
        fun demoSimulated(
            outsideTemperatureC: Double,
            outsideHumidityPercent: Double,
            sourceLabel: String = PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY
        ): WeatherBoundary {
            if (!outsideTemperatureC.isFinite() ||
                !outsideHumidityPercent.isFinite() ||
                outsideHumidityPercent < 0.0
            ) {
                return missing()
            }
            return WeatherBoundary(
                outsideTemperatureC = outsideTemperatureC,
                outsideHumidityPercent = outsideHumidityPercent,
                windSpeedMps = null,
                windDirectionDegrees = null,
                solarRadiationWm2 = null,
                weatherDataTimeMillis = null,
                fetchedAtMillis = null,
                greenhouseLatitude = null,
                greenhouseLongitude = null,
                provider = null,
                status = WeatherBoundaryStatus.AVAILABLE,
                sourceLabel = sourceLabel,
                provenance = WeatherBoundaryProvenance.DEMO_ONLY_ASSUMPTION,
                kind = WeatherBoundaryKind.DEMO_SIMULATED,
                locationName = null
            )
        }

        fun estimated(
            outsideTemperatureC: Double?,
            outsideHumidityPercent: Double?,
            weatherDataTimeMillis: Long?,
            fetchedAtMillis: Long?,
            greenhouseLatitude: Double?,
            greenhouseLongitude: Double?,
            nowMillis: Long,
            policy: FreshnessPolicy,
            windSpeedMps: Double? = null,
            windDirectionDegrees: Double? = null,
            solarRadiationWm2: Double? = null,
            provider: String? = "Open-Meteo",
            locationName: String? = null
        ): WeatherBoundary {
            val finiteTh = outsideTemperatureC != null &&
                outsideHumidityPercent != null &&
                outsideTemperatureC.isFinite() &&
                outsideHumidityPercent.isFinite() &&
                outsideHumidityPercent >= 0.0
            val derived = if (!finiteTh || weatherDataTimeMillis == null || fetchedAtMillis == null) {
                if (outsideTemperatureC != null && !outsideTemperatureC.isFinite()) {
                    WeatherBoundaryStatus.INVALID
                } else {
                    WeatherBoundaryStatus.MISSING
                }
            } else {
                FreshnessEvaluator.weather(weatherDataTimeMillis, nowMillis, policy, true)
            }
            return WeatherBoundary(
                outsideTemperatureC = outsideTemperatureC,
                outsideHumidityPercent = outsideHumidityPercent,
                windSpeedMps = windSpeedMps,
                windDirectionDegrees = windDirectionDegrees,
                solarRadiationWm2 = solarRadiationWm2,
                weatherDataTimeMillis = weatherDataTimeMillis,
                fetchedAtMillis = fetchedAtMillis,
                greenhouseLatitude = greenhouseLatitude,
                greenhouseLongitude = greenhouseLongitude,
                provider = provider,
                status = derived,
                sourceLabel = PhysicsRuntimeCopy.ESTIMATED_OUTSIDE_WEATHER,
                provenance = WeatherBoundaryProvenance.WEATHER_API_ESTIMATE,
                kind = WeatherBoundaryKind.ESTIMATED_OUTSIDE_WEATHER,
                locationName = locationName
            )
        }
    }
}
