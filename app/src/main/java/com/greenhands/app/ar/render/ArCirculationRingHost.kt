package com.greenhands.app.ar.render

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.heat.visualization.CirculationEffectFrame

data class ArCirculationRingBindRequest(
    val parentName: String,
    val applyTabletopScaleToChildren: Boolean,
    val placed: Boolean,
    val frame: CirculationEffectFrame
)

/**
 * Testable AR binding contract. The Filament renderer attaches groups to the live
 * greenhouse content root named [CirculationRingSceneMath.GREENHOUSE_ROOT_NAME].
 */
class ArCirculationRingHost(
    engineIdentity: Int = 0
) {
    val lifecycle: ArPhysicsEffectLifecycle = ArPhysicsEffectLifecycle(engineIdentity)

    var parentName: String? = null
        private set
    var attachedUuids: List<String> = emptyList()
        private set
    var applyTabletopScaleToChildren: Boolean = CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN
        private set
    var resourceEpoch: Int = 0
        private set
    var disposeCount: Int = 0
        private set
    var lastFrame: CirculationEffectFrame = CirculationEffectFrame.EMPTY
        private set
    var originByUuid: Map<String, com.greenhands.app.equipment.model.DomainPoint> = emptyMap()
        private set
    var yawByUuid: Map<String, Double> = emptyMap()
        private set

    fun bind(request: ArCirculationRingBindRequest) {
        applyTabletopScaleToChildren = request.applyTabletopScaleToChildren
        lastFrame = request.frame
        if (!request.placed || request.frame.isEmpty) {
            if (attachedUuids.isNotEmpty()) {
                attachedUuids = emptyList()
                parentName = null
                originByUuid = emptyMap()
                yawByUuid = emptyMap()
                resourceEpoch++
            }
            return
        }
        parentName = request.parentName
        val unique = request.frame.groups.distinctBy { it.equipmentUuid }
        val next = unique.map { it.equipmentUuid }
        originByUuid = unique.associate { it.equipmentUuid to it.originM }
        yawByUuid = unique.associate { it.equipmentUuid to it.yawDeg }
        if (next != attachedUuids) {
            attachedUuids = next
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
        if (attachedUuids.isEmpty() && parentName == null) return
        attachedUuids = emptyList()
        parentName = null
        lastFrame = CirculationEffectFrame.EMPTY
        originByUuid = emptyMap()
        yawByUuid = emptyMap()
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
            frame: CirculationEffectFrame,
            phase: ArTabletopPhase,
            parentName: String = CirculationRingSceneMath.GREENHOUSE_ROOT_NAME
        ): ArCirculationRingBindRequest = ArCirculationRingBindRequest(
            parentName = parentName,
            applyTabletopScaleToChildren = CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN,
            placed = phase == ArTabletopPhase.PLACED,
            frame = frame
        )
    }
}
