package com.greenhands.app.ar.ui

import androidx.lifecycle.ViewModel
import com.greenhands.app.ar.domain.ArCoreAvailabilityKind
import com.greenhands.app.ar.domain.ArGateStatus
import com.greenhands.app.ar.domain.ArInstallOutcome
import com.greenhands.app.ar.domain.ArSessionFailure
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.ArTabletopSession
import com.greenhands.app.ar.domain.ArGestureMode
import com.greenhands.app.ar.domain.ArTabletopUiState
import com.greenhands.app.ar.domain.ArAvailabilityGate
import com.greenhands.app.ar.domain.CameraPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ArTabletopViewModel : ViewModel() {
    private val _state = MutableStateFlow(ArTabletopUiState())
    val state: StateFlow<ArTabletopUiState> = _state.asStateFlow()

    fun onPermission(granted: Boolean) {
        val permission = ArTabletopSession.afterPermission(granted)
        _state.update { current ->
            val gate = ArAvailabilityGate.resolve(
                permission = permission,
                availability = current.availability
            )
            current.copy(
                permission = permission,
                gate = gate,
                phase = if (gate == ArGateStatus.READY) ArTabletopPhase.DETECTING else ArTabletopPhase.GATE
            )
        }
    }

    fun onAvailability(kind: ArCoreAvailabilityKind) {
        _state.update { current ->
            if (current.permission != CameraPermissionState.GRANTED) {
                return@update current.copy(availability = kind)
            }
            val gate = ArAvailabilityGate.resolve(
                permission = current.permission,
                availability = kind
            )
            current.copy(
                availability = kind,
                gate = gate,
                phase = if (gate == ArGateStatus.READY) {
                    if (current.phase == ArTabletopPhase.GATE) ArTabletopPhase.DETECTING else current.phase
                } else {
                    ArTabletopPhase.GATE
                }
            )
        }
    }

    fun onInstallResult(outcome: ArInstallOutcome) {
        _state.update { current ->
            val gate = ArAvailabilityGate.resolve(
                permission = current.permission,
                availability = current.availability,
                install = outcome
            )
            current.copy(
                gate = gate,
                availability = if (outcome == ArInstallOutcome.INSTALLED) {
                    ArCoreAvailabilityKind.SUPPORTED_INSTALLED
                } else {
                    current.availability
                },
                phase = if (gate == ArGateStatus.READY) ArTabletopPhase.DETECTING else ArTabletopPhase.GATE
            )
        }
    }

    fun onSessionFailed(failure: ArSessionFailure) {
        _state.update { current ->
            current.copy(
                gate = ArAvailabilityGate.resolve(
                    permission = current.permission,
                    availability = current.availability,
                    sessionFailure = failure
                ),
                phase = ArTabletopPhase.GATE,
                hasValidPlacementHit = false
            )
        }
    }

    fun setHasValidPlacementHit(valid: Boolean) {
        _state.update { ArTabletopSession.setValidHit(it, valid) }
    }

    fun place() {
        _state.update { ArTabletopSession.place(it) }
    }

    fun resetView() {
        _state.update { ArTabletopSession.resetView(it) }
    }

    fun reposition() {
        _state.update { ArTabletopSession.reposition(it) }
    }

    fun pinch(scaleFactor: Double) {
        _state.update { ArTabletopSession.pinch(it, scaleFactor) }
    }

    fun rotateBy(deltaDeg: Double) {
        _state.update { ArTabletopSession.rotate(it, deltaDeg) }
    }

    fun setGestureMode(mode: ArGestureMode) {
        _state.update { ArTabletopSession.setGestureMode(it, mode) }
    }

    fun setDepthOcclusionEnabled(enabled: Boolean) {
        _state.update { current ->
            if (current.depthOcclusionEnabled == enabled) current
            else current.copy(depthOcclusionEnabled = enabled)
        }
    }
}
