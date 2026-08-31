package com.greenhands.app.sensor.domain

import com.greenhands.app.sensor.model.DEFAULT_SCAN_STEP_DELAY_MS
import com.greenhands.app.sensor.model.ScanMode
import com.greenhands.app.sensor.model.ScanPhase
import com.greenhands.app.sensor.model.ScanUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Integration boundary for greenhouse scanning.
 * [ScanMode.SIMULATED] is the current development implementation.
 * [ScanMode.AR_FUTURE] is reserved for a later ARCore session — not implemented here.
 */
interface GreenhouseScanSession {
    val mode: ScanMode
    val state: StateFlow<ScanUiState>
    fun start()
    fun reset()
}

class SimulatedGreenhouseScanSession(
    private val scope: CoroutineScope,
    private val stepDelayMs: Long = DEFAULT_SCAN_STEP_DELAY_MS
) : GreenhouseScanSession {

    override val mode: ScanMode = ScanMode.SIMULATED

    private val _state = MutableStateFlow(ScanUiState(mode = ScanMode.SIMULATED))
    override val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var job: Job? = null

    override fun start() {
        if (_state.value.phase == ScanPhase.SCANNING) return
        job?.cancel()
        job = scope.launch {
            _state.value = ScanUiState(
                mode = ScanMode.SIMULATED,
                phase = ScanPhase.SCANNING,
                progressPercent = 0
            )
            for (percent in listOf(20, 40, 60, 80, 100)) {
                delay(stepDelayMs)
                _state.value = ScanUiState(
                    mode = ScanMode.SIMULATED,
                    phase = if (percent == 100) ScanPhase.DETECTED else ScanPhase.SCANNING,
                    progressPercent = percent
                )
            }
        }
    }

    override fun reset() {
        job?.cancel()
        job = null
        _state.value = ScanUiState(mode = ScanMode.SIMULATED)
    }
}
