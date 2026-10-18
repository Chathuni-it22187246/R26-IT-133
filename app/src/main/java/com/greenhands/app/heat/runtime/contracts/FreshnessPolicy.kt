package com.greenhands.app.heat.runtime.contracts

/**
 * Configurable design defaults for input freshness. These are not validated scientific constants
 * (PHASE_3D1_SCIENTIFIC_VISUALIZATION_SPEC.md §7.5 / §17.3).
 */
data class FreshnessPolicy(
    val expectedIotIntervalMs: Long = DEFAULT_EXPECTED_IOT_INTERVAL_MS,
    val iotStaleAfterMs: Long = DEFAULT_IOT_STALE_AFTER_MS,
    val weatherStaleAfterMs: Long = DEFAULT_WEATHER_STALE_AFTER_MS
) {
    init {
        require(expectedIotIntervalMs > 0L)
        require(iotStaleAfterMs > 0L)
        require(weatherStaleAfterMs > 0L)
    }

    companion object {
        const val DEFAULT_EXPECTED_IOT_INTERVAL_MS = 5_000L
        const val DEFAULT_IOT_STALE_AFTER_MS = 15_000L
        const val DEFAULT_WEATHER_STALE_AFTER_MS = 30L * 60L * 1_000L
    }
}

enum class FreshnessStatus {
    FRESH,
    STALE,
    MISSING,
    DEMO_CURRENT,
    RECORDED
}

object FreshnessEvaluator {
    fun insideClimate(
        source: InsideClimateSource,
        timestampMillis: Long?,
        nowMillis: Long,
        policy: FreshnessPolicy
    ): FreshnessStatus {
        return when (source) {
            InsideClimateSource.SIMULATED -> FreshnessStatus.DEMO_CURRENT
            InsideClimateSource.RECORDED -> FreshnessStatus.RECORDED
            InsideClimateSource.ACTUAL_IOT -> {
                if (timestampMillis == null) return FreshnessStatus.MISSING
                val age = nowMillis - timestampMillis
                if (age < 0L) return FreshnessStatus.STALE
                if (age >= policy.iotStaleAfterMs) FreshnessStatus.STALE else FreshnessStatus.FRESH
            }
        }
    }

    fun weather(
        weatherDataTimeMillis: Long?,
        nowMillis: Long,
        policy: FreshnessPolicy,
        hasFiniteTemperatureAndHumidity: Boolean
    ): WeatherBoundaryStatus {
        if (!hasFiniteTemperatureAndHumidity || weatherDataTimeMillis == null) {
            return WeatherBoundaryStatus.MISSING
        }
        val age = nowMillis - weatherDataTimeMillis
        if (age < 0L) return WeatherBoundaryStatus.INVALID
        if (age >= policy.weatherStaleAfterMs) return WeatherBoundaryStatus.STALE
        return WeatherBoundaryStatus.AVAILABLE
    }
}
