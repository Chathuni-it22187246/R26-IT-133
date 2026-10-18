package com.greenhands.app.heat.simulation.control

/**
 * Cooling Session state machine (PHYSICS_MODEL.md §14.1).
 * COMPLETE only after temperature and RH stay inside the controller deadbands
 * for [stabilizationDurationS] consecutive simulated seconds.
 */
class CoolingSessionTracker(
    private val stabilizationDurationS: Double = 30.0
) {
    var state: CoolingSessionState = CoolingSessionState.IDLE
        private set
    var elapsedCoolingS: Double = 0.0
        private set
    var stabilityElapsedS: Double = 0.0
        private set

    private var equipmentHasActivated: Boolean = false
    private var completionEmitted: Boolean = false

    fun copy(): CoolingSessionTracker = CoolingSessionTracker(stabilizationDurationS).also { clone ->
        clone.state = state
        clone.elapsedCoolingS = elapsedCoolingS
        clone.stabilityElapsedS = stabilityElapsedS
        clone.equipmentHasActivated = equipmentHasActivated
        clone.completionEmitted = completionEmitted
    }

    fun cancel() {
        state = CoolingSessionState.CANCELLED
    }

    fun markUnreachable() {
        if (state != CoolingSessionState.COMPLETE && state != CoolingSessionState.CANCELLED) {
            state = CoolingSessionState.UNREACHABLE
        }
    }

    /**
     * @return true exactly once when the session first reaches COMPLETE.
     */
    fun update(dtS: Double, anyEquipmentOn: Boolean, inConfiguredBand: Boolean): Boolean {
        if (state == CoolingSessionState.CANCELLED || state == CoolingSessionState.UNREACHABLE) {
            return false
        }
        if (state == CoolingSessionState.COMPLETE) {
            return false
        }
        if (!equipmentHasActivated) {
            if (!anyEquipmentOn) return false
            equipmentHasActivated = true
            state = CoolingSessionState.COOLING
        }
        elapsedCoolingS += dtS
        if (inConfiguredBand) {
            if (state == CoolingSessionState.COOLING) {
                state = CoolingSessionState.STABILIZING
            }
            if (state == CoolingSessionState.STABILIZING) {
                stabilityElapsedS += dtS
                if (stabilityElapsedS + 1e-12 >= stabilizationDurationS) {
                    state = CoolingSessionState.COMPLETE
                    if (!completionEmitted) {
                        completionEmitted = true
                        return true
                    }
                }
            }
        } else {
            state = CoolingSessionState.COOLING
            stabilityElapsedS = 0.0
        }
        return false
    }
}
