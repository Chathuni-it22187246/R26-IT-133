package com.greenhands.app.telemetry

import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus

/**
 * Pure live/stale/unavailable evaluation for modelled outside weather.
 * Cached or aged Firebase payloads never become Live.
 */
object GreenhouseWeatherEvaluator {
    fun evaluate(
        dto: CurrentWeatherDto?,
        nowMillis: Long,
        lastAccepted: AcceptedWeather?,
        awaitingFirstEvent: Boolean,
        connectionError: Boolean,
        policy: FreshnessPolicy = FreshnessPolicy()
    ): GreenhouseWeatherEvaluation {
        if (connectionError) {
            val snapshot = lastAccepted
            return GreenhouseWeatherEvaluation(
                GreenhouseWeatherState.Unavailable(
                    lastKnown = snapshot,
                    boundary = staleOrMissingBoundary(snapshot, nowMillis, policy)
                ),
                lastAccepted
            )
        }
        val parsed = dto?.let(GreenhouseWeatherMapper::parseAuthoritative)
        val snapshot = parsed ?: lastAccepted
        if (snapshot != null) {
            return classify(snapshot, nowMillis, policy)
        }
        if (awaitingFirstEvent && dto == null) {
            return GreenhouseWeatherEvaluation(GreenhouseWeatherState.Connecting, null)
        }
        return GreenhouseWeatherEvaluation(
            GreenhouseWeatherState.Unavailable(),
            null
        )
    }

    fun classify(
        snapshot: AcceptedWeather,
        nowMillis: Long,
        policy: FreshnessPolicy = FreshnessPolicy()
    ): GreenhouseWeatherEvaluation {
        val boundary = GreenhouseWeatherMapper.toBoundary(snapshot, nowMillis, policy)
        val state = when (boundary.status) {
            WeatherBoundaryStatus.AVAILABLE -> GreenhouseWeatherState.Live(snapshot, boundary)
            WeatherBoundaryStatus.STALE -> GreenhouseWeatherState.Stale(snapshot, boundary)
            WeatherBoundaryStatus.INVALID,
            WeatherBoundaryStatus.MISSING -> GreenhouseWeatherState.Unavailable(snapshot, boundary)
        }
        return GreenhouseWeatherEvaluation(state, snapshot)
    }

    private fun staleOrMissingBoundary(
        snapshot: AcceptedWeather?,
        nowMillis: Long,
        policy: FreshnessPolicy
    ): WeatherBoundary {
        if (snapshot == null) return WeatherBoundary.missing()
        return GreenhouseWeatherMapper.toBoundary(snapshot, nowMillis, policy)
    }
}

data class GreenhouseWeatherEvaluation(
    val state: GreenhouseWeatherState,
    val lastAccepted: AcceptedWeather?
)
