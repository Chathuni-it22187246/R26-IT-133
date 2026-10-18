package com.greenhands.app.telemetry

fun interface TelemetrySubscription {
    fun cancel()
}

/**
 * Read-only listener for `greenhouses/GH_001/telemetry/current`.
 * Implementations must not call Firebase write APIs.
 */
interface CurrentTelemetryDataSource {
    fun listen(onEvent: (Result<CurrentTelemetryDto?>) -> Unit): TelemetrySubscription
}

class FakeCurrentTelemetryDataSource : CurrentTelemetryDataSource {
    var listenCount: Int = 0
        private set
    var cancelCount: Int = 0
        private set
    private var listener: ((Result<CurrentTelemetryDto?>) -> Unit)? = null

    override fun listen(onEvent: (Result<CurrentTelemetryDto?>) -> Unit): TelemetrySubscription {
        listenCount += 1
        listener = onEvent
        return TelemetrySubscription {
            cancelCount += 1
            if (listener === onEvent) listener = null
        }
    }

    fun emit(dto: CurrentTelemetryDto?) {
        listener?.invoke(Result.success(dto))
    }

    fun emitError(error: Throwable = IllegalStateException("permission denied")) {
        listener?.invoke(Result.failure(error))
    }

    val listenerActive: Boolean get() = listener != null
}
