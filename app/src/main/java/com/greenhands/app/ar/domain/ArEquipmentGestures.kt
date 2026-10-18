package com.greenhands.app.ar.domain

/**
 * Separates one-finger equipment movement from whole-greenhouse pinch/rotate.
 * Palette and other Compose chrome are outside [ARSceneView] and are never consumed here.
 */
enum class ArGestureMode {
    TABLETOP,
    EQUIPMENT_MOVE
}

object ArEquipmentGestures {
    fun oneFingerMovesEquipment(
        phase: ArTabletopPhase,
        gestureMode: ArGestureMode,
        selectedId: String?,
        dragging: Boolean = false
    ): Boolean {
        if (phase != ArTabletopPhase.PLACED) return false
        if (gestureMode != ArGestureMode.EQUIPMENT_MOVE) return false
        return dragging || selectedId != null
    }

    fun pinchScalesGreenhouse(phase: ArTabletopPhase, pointerCount: Int): Boolean =
        phase == ArTabletopPhase.PLACED && pointerCount >= 2

    fun twoFingerRotatesGreenhouse(phase: ArTabletopPhase, pointerCount: Int): Boolean =
        phase == ArTabletopPhase.PLACED && pointerCount >= 2

    fun tapSelectsEquipment(phase: ArTabletopPhase): Boolean =
        phase == ArTabletopPhase.PLACED

    fun tapPlacesGreenhouse(phase: ArTabletopPhase): Boolean =
        phase == ArTabletopPhase.DETECTING

    fun afterSelect(): ArGestureMode = ArGestureMode.EQUIPMENT_MOVE

    fun afterClearSelection(): ArGestureMode = ArGestureMode.TABLETOP

    fun afterReposition(): ArGestureMode = ArGestureMode.TABLETOP
}
