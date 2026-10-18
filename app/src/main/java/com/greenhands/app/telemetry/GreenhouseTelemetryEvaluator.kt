package com.greenhands.app.telemetry

/**
 * Pure live/stale/disconnected evaluation. Cached or aged Firebase payloads
 * never become Live. Callers must tick this even when no new event arrives.
 */
object GreenhouseTelemetryEvaluator {
    fun evaluate(
        dto: CurrentTelemetryDto?,
        nowMillis: Long,
        lastAccepted: AcceptedLiveTelemetry?,
        awaitingFirstEvent: Boolean,
        connectionError: Boolean,
        policy: TelemetryFreshnessPolicy = TelemetryFreshnessPolicy()
    ): GreenhouseTelemetryEvaluation {
        if (connectionError) {
            return GreenhouseTelemetryEvaluation(
                GreenhouseTelemetryState.Disconnected(lastAccepted, TelemetryCopy.CONNECTION_ERROR),
                lastAccepted
            )
        }
        val parsed = dto?.let(GreenhouseTelemetryMapper::parseAuthoritative)
        val snapshot = parsed ?: lastAccepted
        if (snapshot != null) {
            return classify(snapshot, nowMillis, policy)
        }
        if (awaitingFirstEvent && dto == null) {
            return GreenhouseTelemetryEvaluation(GreenhouseTelemetryState.Connecting, null)
        }
        return GreenhouseTelemetryEvaluation(
            GreenhouseTelemetryState.Disconnected(null, TelemetryCopy.CONNECTION_ERROR),
            null
        )
    }

    fun classify(
        snapshot: AcceptedLiveTelemetry,
        nowMillis: Long,
        policy: TelemetryFreshnessPolicy = TelemetryFreshnessPolicy()
    ): GreenhouseTelemetryEvaluation {
        val age = nowMillis - snapshot.timestampMillis
        val state = when {
            age < 0L -> GreenhouseTelemetryState.Stale(snapshot)
            age > policy.staleAfterMs -> GreenhouseTelemetryState.Stale(snapshot)
            else -> GreenhouseTelemetryState.Live(snapshot)
        }
        return GreenhouseTelemetryEvaluation(state, snapshot)
    }
}

data class GreenhouseTelemetryEvaluation(
    val state: GreenhouseTelemetryState,
    val lastAccepted: AcceptedLiveTelemetry?
)
