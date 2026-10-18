package com.greenhands.app.ar.domain

data class ArTabletopUiState(
    val gate: ArGateStatus = ArGateStatus.CHECKING,
    val phase: ArTabletopPhase = ArTabletopPhase.GATE,
    val permission: CameraPermissionState = CameraPermissionState.UNKNOWN,
    val availability: ArCoreAvailabilityKind? = null,
    val hasValidPlacementHit: Boolean = false,
    val visualScaleFactor: Double = ArTabletopScale.DEFAULT_PINCH,
    val yawDeg: Double = 0.0,
    val depthOcclusionEnabled: Boolean = false,
    val repositionEpoch: Int = 0,
    val gestureMode: ArGestureMode = ArGestureMode.TABLETOP,
    val sessionNote: String = SESSION_ONLY_NOTE
) {
    val canOpenLiveSession: Boolean get() = ArAvailabilityGate.canOpenLiveSession(gate)
    val showFallback: Boolean get() = ArAvailabilityGate.offersContinueIn3d(gate)
    val isUnsupportedFallback: Boolean get() = ArAvailabilityGate.isUnsupportedFallback(gate)
    val isDetecting: Boolean get() = phase == ArTabletopPhase.DETECTING
    val isPlaced: Boolean get() = phase == ArTabletopPhase.PLACED
    val showPlacementIndicator: Boolean get() = isDetecting && hasValidPlacementHit
    val showFindSurfaceInstruction: Boolean get() = isDetecting && !hasValidPlacementHit
    val showTapToPlaceInstruction: Boolean get() = isDetecting && hasValidPlacementHit

    companion object {
        const val SESSION_ONLY_NOTE =
            "AR placement, scale and rotation are for this session only. They are not stored as a physical location."
    }
}

object ArTabletopSession {
    fun afterPermission(granted: Boolean): CameraPermissionState =
        if (granted) CameraPermissionState.GRANTED else CameraPermissionState.DENIED

    fun enterDetecting(state: ArTabletopUiState): ArTabletopUiState {
        if (!state.canOpenLiveSession) return state
        return state.copy(
            phase = ArTabletopPhase.DETECTING,
            hasValidPlacementHit = false
        )
    }

    fun place(state: ArTabletopUiState): ArTabletopUiState {
        if (state.phase != ArTabletopPhase.DETECTING) return state
        return state.copy(
            phase = ArTabletopPhase.PLACED,
            hasValidPlacementHit = false
        )
    }

    fun resetView(state: ArTabletopUiState): ArTabletopUiState {
        if (state.phase != ArTabletopPhase.PLACED) return state
        return state.copy(
            visualScaleFactor = ArTabletopScale.DEFAULT_PINCH,
            yawDeg = 0.0
        )
    }

    fun reposition(state: ArTabletopUiState): ArTabletopUiState {
        if (state.phase != ArTabletopPhase.PLACED && state.phase != ArTabletopPhase.DETECTING) {
            return state
        }
        return state.copy(
            phase = ArTabletopPhase.DETECTING,
            hasValidPlacementHit = false,
            visualScaleFactor = ArTabletopScale.DEFAULT_PINCH,
            yawDeg = 0.0,
            repositionEpoch = state.repositionEpoch + 1,
            gestureMode = ArEquipmentGestures.afterReposition()
        )
    }

    fun pinch(state: ArTabletopUiState, scaleFactor: Double): ArTabletopUiState {
        if (state.phase != ArTabletopPhase.PLACED) return state
        return state.copy(visualScaleFactor = ArTabletopGestures.applyPinch(state.visualScaleFactor, scaleFactor))
    }

    fun rotate(state: ArTabletopUiState, deltaDeg: Double): ArTabletopUiState {
        if (state.phase != ArTabletopPhase.PLACED) return state
        return state.copy(yawDeg = ArTabletopGestures.applyHorizontalRotation(state.yawDeg, deltaDeg))
    }

    fun setGestureMode(state: ArTabletopUiState, mode: ArGestureMode): ArTabletopUiState {
        if (state.gestureMode == mode) return state
        return state.copy(gestureMode = mode)
    }

    fun setValidHit(state: ArTabletopUiState, valid: Boolean): ArTabletopUiState {
        if (state.phase != ArTabletopPhase.DETECTING) {
            return if (!state.hasValidPlacementHit) state else state.copy(hasValidPlacementHit = false)
        }
        if (state.hasValidPlacementHit == valid) return state
        return state.copy(hasValidPlacementHit = valid)
    }
}
