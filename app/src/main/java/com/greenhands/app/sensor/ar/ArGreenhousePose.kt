package com.greenhands.app.sensor.ar

/**
 * Session-only greenhouse alignment pose for Real AR.
 *
 * Greenhouse-local convention (unchanged from virtual preview):
 * - X = length
 * - Y = height
 * - Z = width
 * - Local origin = (0, 0, 0)
 *
 * After alignment, [forwardX]/[forwardZ] is the unit horizontal vector for local +X
 * in the AR world (Y-up, right-handed). Yaw uses:
 * `yawRadians = atan2(forwardZ, forwardX)`.
 *
 * Does NOT store ARCore Anchor instances.
 * Does NOT modify Sensor / Coverage / Optimizer / [ArVisualizationSnapshot].
 */
enum class ArOriginPlacementPhase {
    /** Looking for a horizontal floor plane. */
    SCANNING,

    /** Usable horizontal plane exists; waiting for user tap. */
    PLANE_FOUND,

    /** User placed the greenhouse origin; direction not set yet. */
    ORIGIN_PLACED,

    /** Waiting for second tap to define length (+X) direction. */
    SETTING_DIRECTION,

    /** Origin + yaw established. */
    ALIGNED
}

data class ArGreenhousePose(
    val phase: ArOriginPlacementPhase = ArOriginPlacementPhase.SCANNING,
    /** World-space origin translation (meters). Null until origin is placed. */
    val worldTranslationX: Float? = null,
    val worldTranslationY: Float? = null,
    val worldTranslationZ: Float? = null,
    /**
     * Unit horizontal forward (local +X / length) in AR world XZ.
     * Null until [ArOriginPlacementPhase.ALIGNED].
     */
    val forwardX: Float? = null,
    val forwardZ: Float? = null,
    /** `atan2(forwardZ, forwardX)` — null until aligned. */
    val yawRadians: Float? = null,
    /** Optional second-tap world point on the floor (diagnostics / marker). */
    val directionWorldX: Float? = null,
    val directionWorldY: Float? = null,
    val directionWorldZ: Float? = null
) {
    val isOriginPlaced: Boolean
        get() = phase == ArOriginPlacementPhase.ORIGIN_PLACED ||
            phase == ArOriginPlacementPhase.SETTING_DIRECTION ||
            phase == ArOriginPlacementPhase.ALIGNED

    val isAligned: Boolean get() = phase == ArOriginPlacementPhase.ALIGNED

    companion object {
        const val LOCAL_ORIGIN_X = 0f
        const val LOCAL_ORIGIN_Y = 0f
        const val LOCAL_ORIGIN_Z = 0f
    }
}

/**
 * Pure state transitions for origin + yaw alignment (10E-C / 10E-D).
 * Unit-testable without ARCore hardware.
 */
object ArOriginPlacementController {

    fun onHorizontalPlaneDetected(state: ArGreenhousePose): ArGreenhousePose =
        when (state.phase) {
            ArOriginPlacementPhase.SCANNING ->
                state.copy(phase = ArOriginPlacementPhase.PLANE_FOUND)
            else -> state
        }

    fun canAcceptOriginTap(state: ArGreenhousePose): Boolean =
        state.phase == ArOriginPlacementPhase.PLANE_FOUND

    fun onOriginPlaced(
        state: ArGreenhousePose,
        worldTx: Float,
        worldTy: Float,
        worldTz: Float
    ): ArGreenhousePose {
        if (!canAcceptOriginTap(state)) return state
        return state.copy(
            phase = ArOriginPlacementPhase.ORIGIN_PLACED,
            worldTranslationX = worldTx,
            worldTranslationY = worldTy,
            worldTranslationZ = worldTz,
            forwardX = null,
            forwardZ = null,
            yawRadians = null,
            directionWorldX = null,
            directionWorldY = null,
            directionWorldZ = null
        )
    }

    fun beginSetDirection(state: ArGreenhousePose): ArGreenhousePose {
        if (state.phase != ArOriginPlacementPhase.ORIGIN_PLACED &&
            state.phase != ArOriginPlacementPhase.ALIGNED
        ) {
            return state
        }
        return state.copy(
            phase = ArOriginPlacementPhase.SETTING_DIRECTION,
            forwardX = null,
            forwardZ = null,
            yawRadians = null,
            directionWorldX = null,
            directionWorldY = null,
            directionWorldZ = null
        )
    }

    fun canAcceptDirectionTap(state: ArGreenhousePose): Boolean =
        state.phase == ArOriginPlacementPhase.SETTING_DIRECTION

    /**
     * Second floor tap defines length (+X). Rejects near-zero horizontal distance.
     */
    fun onDirectionPoint(
        state: ArGreenhousePose,
        worldPx: Float,
        worldPy: Float,
        worldPz: Float
    ): Pair<ArGreenhousePose, ArDirectionTapResult> {
        if (!canAcceptDirectionTap(state)) {
            return state to ArDirectionTapResult.INVALID_STATE
        }
        val ox = state.worldTranslationX ?: return state to ArDirectionTapResult.MISSING_ORIGIN
        val oy = state.worldTranslationY ?: return state to ArDirectionTapResult.MISSING_ORIGIN
        val oz = state.worldTranslationZ ?: return state to ArDirectionTapResult.MISSING_ORIGIN
        return when (
            val dir = ArWorldMapper.directionFromPoints(ox, oy, oz, worldPx, worldPy, worldPz)
        ) {
            is ArWorldMapper.DirectionResult.TooClose ->
                state to ArDirectionTapResult.TOO_CLOSE
            is ArWorldMapper.DirectionResult.Ok -> {
                state.copy(
                    phase = ArOriginPlacementPhase.ALIGNED,
                    forwardX = dir.forwardX,
                    forwardZ = dir.forwardZ,
                    yawRadians = dir.yawRadians,
                    directionWorldX = worldPx,
                    directionWorldY = worldPy,
                    directionWorldZ = worldPz
                ) to ArDirectionTapResult.OK
            }
        }
    }

    /** Keeps origin; clears yaw/direction; returns to SETTING_DIRECTION. */
    fun resetAlignment(state: ArGreenhousePose): ArGreenhousePose {
        if (!state.isOriginPlaced) return state
        return state.copy(
            phase = ArOriginPlacementPhase.SETTING_DIRECTION,
            forwardX = null,
            forwardZ = null,
            yawRadians = null,
            directionWorldX = null,
            directionWorldY = null,
            directionWorldZ = null
        )
    }

    /** Clears origin and orientation; returns to scanning. */
    fun resetOrigin(@Suppress("UNUSED_PARAMETER") state: ArGreenhousePose): ArGreenhousePose =
        ArGreenhousePose()

    fun instructionPhase(state: ArGreenhousePose): ArOriginPlacementPhase = state.phase
}

enum class ArDirectionTapResult {
    OK,
    TOO_CLOSE,
    MISSING_ORIGIN,
    INVALID_STATE
}
