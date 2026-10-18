package com.greenhands.app.telemetry

/**
 * Read-only listener for `greenhouses/GH_001/weather/current`.
 * Implementations must not call Firebase write APIs or read telemetry/history.
 */
interface CurrentWeatherDataSource {
    fun listen(onEvent: (Result<CurrentWeatherDto?>) -> Unit): TelemetrySubscription
}

object NoOpCurrentWeatherDataSource : CurrentWeatherDataSource {
    override fun listen(onEvent: (Result<CurrentWeatherDto?>) -> Unit): TelemetrySubscription =
        TelemetrySubscription { }
}

class FakeCurrentWeatherDataSource : CurrentWeatherDataSource {
    var listenCount: Int = 0
        private set
    var cancelCount: Int = 0
        private set
    private var listener: ((Result<CurrentWeatherDto?>) -> Unit)? = null

    override fun listen(onEvent: (Result<CurrentWeatherDto?>) -> Unit): TelemetrySubscription {
        listenCount += 1
        listener = onEvent
        return TelemetrySubscription {
            cancelCount += 1
            if (listener === onEvent) listener = null
        }
    }

    fun emit(dto: CurrentWeatherDto?) {
        listener?.invoke(Result.success(dto))
    }

    fun emitError(error: Throwable = IllegalStateException("permission denied")) {
        listener?.invoke(Result.failure(error))
    }

    val listenerActive: Boolean get() = listener != null
}
