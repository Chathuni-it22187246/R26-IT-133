package com.greenhands.app.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface TelemetryHeartbeat {
    fun start(onBeat: () -> Unit)
    fun stop()
}

class CoroutineTelemetryHeartbeat(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 1_000L
) : TelemetryHeartbeat {
    private var job: Job? = null

    override fun start(onBeat: () -> Unit) {
        stop()
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                onBeat()
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }
}

class ManualTelemetryHeartbeat : TelemetryHeartbeat {
    private var beat: (() -> Unit)? = null
    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set

    override fun start(onBeat: () -> Unit) {
        startCount += 1
        beat = onBeat
    }

    override fun stop() {
        stopCount += 1
        beat = null
    }

    fun pulse() {
        beat?.invoke()
    }
}
