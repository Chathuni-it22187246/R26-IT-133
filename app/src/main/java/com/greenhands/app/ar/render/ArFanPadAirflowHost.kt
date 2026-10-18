package com.greenhands.app.ar.render

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.heat.visualization.PadInletEffectFrame

data class ArFanPadBindRequest(
    val parentName: String,
    val applyTabletopScaleToChildren: Boolean,
    val placed: Boolean,
    val frame: PadInletEffectFrame
)

class ArFanPadAirflowHost(
    engineIdentity: Int = 0
) {
    val lifecycle: ArPhysicsEffectLifecycle = ArPhysicsEffectLifecycle(engineIdentity)

    var parentName: String? = null
        private set
    var attached: Boolean = false
        private set
    var applyTabletopScaleToChildren: Boolean = CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN
        private set
    var resourceEpoch: Int = 0
        private set
    var disposeCount: Int = 0
        private set
    var lastFrame: PadInletEffectFrame = PadInletEffectFrame.EMPTY
        private set

    fun bind(request: ArFanPadBindRequest) {
        applyTabletopScaleToChildren = request.applyTabletopScaleToChildren
        lastFrame = request.frame
        if (!request.placed || request.frame.isEmpty) {
            if (attached) {
                attached = false
                parentName = null
                resourceEpoch++
            }
            return
        }
        parentName = request.parentName
        if (!attached) {
            attached = true
            resourceEpoch++
        }
    }

    fun noteRecomposition() {
        lifecycle.noteRecomposition()
    }

    fun uiChromeChanged() {
        lifecycle.noteUiChromeChanged()
    }

    fun clear() {
        if (!attached && parentName == null) return
        attached = false
        parentName = null
        lastFrame = PadInletEffectFrame.EMPTY
        resourceEpoch++
    }

    fun destroy() {
        lifecycle.releaseOnComposeDisposal()
        clear()
        disposeCount++
    }

    fun markDisposed() {
        disposeCount++
    }

    companion object {
        fun request(
            frame: PadInletEffectFrame,
            phase: ArTabletopPhase,
            parentName: String = CirculationRingSceneMath.GREENHOUSE_ROOT_NAME
        ): ArFanPadBindRequest = ArFanPadBindRequest(
            parentName = parentName,
            applyTabletopScaleToChildren = CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN,
            placed = phase == ArTabletopPhase.PLACED,
            frame = frame
        )
    }
}
