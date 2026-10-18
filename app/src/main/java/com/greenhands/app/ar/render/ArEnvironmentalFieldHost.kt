package com.greenhands.app.ar.render

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.heat.visualization.EnvironmentalFieldFrame
import com.greenhands.app.heat.visualization.EnvironmentalFieldGeometryUpdateReason
import com.greenhands.app.heat.visualization.EnvironmentalFieldVisual

data class ArEnvironmentalFieldBindRequest(
    val parentName: String,
    val applyTabletopScaleToChildren: Boolean,
    val placed: Boolean,
    val frame: EnvironmentalFieldFrame
)

/**
 * Testable AR binding contract for the whole-greenhouse environmental field.
 * Combined meshes attach to the live greenhouse content root.
 */
class ArEnvironmentalFieldHost(
    engineIdentity: Int = 0
) {
    val lifecycle: ArPhysicsEffectLifecycle = ArPhysicsEffectLifecycle(engineIdentity)

    var parentName: String? = null
        private set
    var applyTabletopScaleToChildren: Boolean = CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN
        private set
    var resourceEpoch: Int = 0
        private set
    var disposeCount: Int = 0
        private set
    var lastFrame: EnvironmentalFieldFrame = EnvironmentalFieldFrame.EMPTY
        private set
    var lastSignature: String? = null
        private set
    var geometryUpdateCount: Int = 0
        private set
    var materialCreateCount: Int = 0
        private set
    var nodeCreateCount: Int = 0
        private set
    var activeNodeCount: Int = 0
        private set
    var cellCount: Int = 0
        private set
    var lastUpdateReason: EnvironmentalFieldGeometryUpdateReason =
        EnvironmentalFieldGeometryUpdateReason.NONE
        private set

    fun bind(request: ArEnvironmentalFieldBindRequest): Boolean {
        applyTabletopScaleToChildren = request.applyTabletopScaleToChildren
        lastFrame = request.frame
        if (!request.placed || request.frame.layer == com.greenhands.app.heat.visualization.EnvironmentalFieldLayer.OFF ||
            request.frame.isEmpty
        ) {
            if (activeNodeCount > 0 || parentName != null) {
                activeNodeCount = 0
                cellCount = 0
                parentName = null
                lastSignature = null
                resourceEpoch++
            }
            return false
        }
        parentName = request.parentName
        cellCount = request.frame.cellCount.coerceAtMost(EnvironmentalFieldVisual.MAX_CELLS)
        val nextNodes = request.frame.activeNodeCount.coerceAtMost(EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
        val updated = shouldUpdateGeometry(request.frame.signature, request.frame.geometryUpdateReason)
        if (nextNodes != activeNodeCount) {
            activeNodeCount = nextNodes
            resourceEpoch++
        }
        return updated
    }

    fun shouldUpdateGeometry(
        signature: String,
        reason: EnvironmentalFieldGeometryUpdateReason
    ): Boolean {
        if (signature == lastSignature) {
            lastUpdateReason = EnvironmentalFieldGeometryUpdateReason.NONE
            return false
        }
        lastSignature = signature
        lastUpdateReason = reason
        geometryUpdateCount++
        return true
    }

    fun noteMaterialCreated() {
        materialCreateCount++
    }

    fun noteNodeCreated() {
        nodeCreateCount++
    }

    fun noteRecomposition() {
        lifecycle.noteRecomposition()
    }

    fun uiChromeChanged() {
        lifecycle.noteUiChromeChanged()
    }

    fun clear() {
        if (activeNodeCount == 0 && parentName == null && lastSignature == null) return
        activeNodeCount = 0
        cellCount = 0
        parentName = null
        lastFrame = EnvironmentalFieldFrame.EMPTY
        lastSignature = null
        lastUpdateReason = EnvironmentalFieldGeometryUpdateReason.NONE
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
            frame: EnvironmentalFieldFrame,
            phase: ArTabletopPhase,
            parentName: String = CirculationRingSceneMath.GREENHOUSE_ROOT_NAME
        ): ArEnvironmentalFieldBindRequest = ArEnvironmentalFieldBindRequest(
            parentName = parentName,
            applyTabletopScaleToChildren = CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN,
            placed = phase == ArTabletopPhase.PLACED,
            frame = frame
        )
    }
}
