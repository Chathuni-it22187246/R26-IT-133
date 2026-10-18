package com.greenhands.app.ar.render

/**
 * Testable AR effect-renderer ownership. The live Filament class
 * [ArCirculationRingRenderer] mirrors these transitions. SceneView 2.2.1
 * [io.github.sceneview.node.Node.parent] reaches [com.google.android.filament.Scene.removeEntities],
 * which must never run after the host Scene is destroyed.
 */
enum class ArEffectHostState {
    UNATTACHED,
    SCENE_ALIVE,
    HOST_UNAVAILABLE,
    DISPOSED
}

object ArPhysicsEffectCleanup {
    const val STOP_VISUAL_CALLBACKS = "stop-visual-callbacks"
    const val PREVENT_SYNC = "prevent-ring-sync"
    const val DETACH_NODES = "detach-effect-nodes"
    const val DESTROY_OWNED = "destroy-renderer-owned-resources"
    const val MARK_DISPOSED = "mark-disposed"
    const val HOST_DESTROY = "host-scene-engine-destroy"
    const val REPLACE_ANCHOR = "replace-anchor"
    const val RECREATE_AFTER_PLACE = "recreate-effects-after-placement"

    val LEAVE_AR: List<String> = listOf(
        STOP_VISUAL_CALLBACKS,
        PREVENT_SYNC,
        DETACH_NODES,
        DESTROY_OWNED,
        MARK_DISPOSED,
        HOST_DESTROY
    )

    val REPOSITION: List<String> = listOf(
        STOP_VISUAL_CALLBACKS,
        PREVENT_SYNC,
        DETACH_NODES,
        REPLACE_ANCHOR,
        RECREATE_AFTER_PLACE
    )
}

class ArPhysicsEffectLifecycle(
    val engineIdentity: Int
) {
    var state: ArEffectHostState = ArEffectHostState.UNATTACHED
        private set
    var syncEnabled: Boolean = false
        private set
    var visualUpdatesEnabled: Boolean = false
        private set
    var sceneOperationCount: Int = 0
        private set
    var engineOperationCount: Int = 0
        private set
    var resourceDestroyCount: Int = 0
        private set
    var abandonCount: Int = 0
        private set
    var disposeCount: Int = 0
        private set
    var clearWhileAliveCount: Int = 0
        private set
    var attachedGroupCount: Int = 0
        private set
    var lastCleanupOrder: List<String> = emptyList()
        private set
    var lastRepositionOrder: List<String> = emptyList()
        private set
    var rendererRecreateCount: Int = 0
        private set

    private val destroyedResourceIds = linkedSetOf<String>()
    private val ownedResourceIds = linkedSetOf<String>()

    fun attach(liveEngineIdentity: Int): Boolean {
        if (state == ArEffectHostState.DISPOSED) return false
        if (liveEngineIdentity != engineIdentity) return false
        state = ArEffectHostState.SCENE_ALIVE
        syncEnabled = true
        visualUpdatesEnabled = true
        return true
    }

    fun noteUiChromeChanged() {
        // Selection, Labels, Move/Table, sheets, HUD, probe publication, and
        // 5-second display updates must not dispose or recreate the renderer.
    }

    fun noteRecomposition() = noteUiChromeChanged()

    fun bindGroups(count: Int): Boolean {
        if (state != ArEffectHostState.SCENE_ALIVE || !syncEnabled) return false
        attachedGroupCount = count.coerceAtLeast(0)
        return true
    }

    fun registerOwnedResource(resourceId: String, fromEngineIdentity: Int): Boolean {
        if (fromEngineIdentity != engineIdentity) return false
        if (state == ArEffectHostState.DISPOSED) return false
        ownedResourceIds += resourceId
        return true
    }

    fun acceptEngineBoundResource(fromEngineIdentity: Int, resourceId: String): Boolean {
        return registerOwnedResource(resourceId, fromEngineIdentity)
    }

    fun destroyOwnedResource(resourceId: String): Boolean {
        if (resourceId in destroyedResourceIds) return false
        if (state != ArEffectHostState.SCENE_ALIVE) return false
        destroyedResourceIds += resourceId
        resourceDestroyCount++
        sceneOperationCount++
        return true
    }

    fun clearEffectsWhileSceneAlive(): Boolean {
        if (state != ArEffectHostState.SCENE_ALIVE) return false
        visualUpdatesEnabled = false
        syncEnabled = false
        if (attachedGroupCount > 0) {
            sceneOperationCount++
            lastCleanupOrder = listOf(
                ArPhysicsEffectCleanup.STOP_VISUAL_CALLBACKS,
                ArPhysicsEffectCleanup.PREVENT_SYNC,
                ArPhysicsEffectCleanup.DETACH_NODES
            )
        }
        attachedGroupCount = 0
        clearWhileAliveCount++
        syncEnabled = true
        visualUpdatesEnabled = true
        return true
    }

    fun disposeBeforeHostDestruction(): Boolean {
        if (state == ArEffectHostState.DISPOSED) {
            disposeCount++
            return true
        }
        if (state != ArEffectHostState.SCENE_ALIVE) {
            return abandonAfterHostUnavailable()
        }
        visualUpdatesEnabled = false
        syncEnabled = false
        if (attachedGroupCount > 0 || ownedResourceIds.isNotEmpty()) {
            sceneOperationCount++
            ownedResourceIds.forEach { id ->
                if (id !in destroyedResourceIds) {
                    destroyedResourceIds += id
                    resourceDestroyCount++
                }
            }
        }
        attachedGroupCount = 0
        ownedResourceIds.clear()
        state = ArEffectHostState.DISPOSED
        disposeCount++
        lastCleanupOrder = listOf(
            ArPhysicsEffectCleanup.STOP_VISUAL_CALLBACKS,
            ArPhysicsEffectCleanup.PREVENT_SYNC,
            ArPhysicsEffectCleanup.DETACH_NODES,
            ArPhysicsEffectCleanup.DESTROY_OWNED,
            ArPhysicsEffectCleanup.MARK_DISPOSED
        )
        return true
    }

    fun abandonAfterHostUnavailable(): Boolean {
        val sceneOpsBefore = sceneOperationCount
        val engineOpsBefore = engineOperationCount
        visualUpdatesEnabled = false
        syncEnabled = false
        attachedGroupCount = 0
        ownedResourceIds.clear()
        if (state != ArEffectHostState.DISPOSED) {
            state = ArEffectHostState.DISPOSED
            abandonCount++
        } else {
            abandonCount++
        }
        disposeCount++
        lastCleanupOrder = listOf("cancel-callbacks-only")
        check(sceneOperationCount == sceneOpsBefore) {
            "abandonAfterHostUnavailable must not call Scene APIs"
        }
        check(engineOperationCount == engineOpsBefore) {
            "abandonAfterHostUnavailable must not call Engine APIs"
        }
        return true
    }

    fun markHostUnavailable() {
        if (state == ArEffectHostState.DISPOSED) return
        syncEnabled = false
        visualUpdatesEnabled = false
        state = ArEffectHostState.HOST_UNAVAILABLE
    }

    fun releaseOnComposeDisposal() {
        if (state == ArEffectHostState.SCENE_ALIVE) {
            disposeBeforeHostDestruction()
        } else {
            abandonAfterHostUnavailable()
        }
    }

    fun leaveArSequence(): List<String> {
        val attached = attachedGroupCount
        disposeBeforeHostDestruction()
        lastCleanupOrder = ArPhysicsEffectCleanup.LEAVE_AR
        check(ArPhysicsEffectCleanup.LEAVE_AR.indexOf(ArPhysicsEffectCleanup.DETACH_NODES) <
            ArPhysicsEffectCleanup.LEAVE_AR.indexOf(ArPhysicsEffectCleanup.HOST_DESTROY))
        check(attached == 0 || sceneOperationCount > 0)
        return lastCleanupOrder
    }

    fun repositionSequence(visualizationStillRunning: Boolean): List<String> {
        stopVisualUpdates()
        clearEffectsWhileSceneAlive()
        lastRepositionOrder = ArPhysicsEffectCleanup.REPOSITION
        if (visualizationStillRunning && state == ArEffectHostState.SCENE_ALIVE) {
            syncEnabled = true
            visualUpdatesEnabled = true
        }
        return lastRepositionOrder
    }

    fun stopVisualUpdates() {
        visualUpdatesEnabled = false
        syncEnabled = false
    }

    fun noteRendererRecreated() {
        rendererRecreateCount++
    }

    fun sceneOpsAllowed(): Boolean = state == ArEffectHostState.SCENE_ALIVE

    fun destroyedResourceOnce(resourceId: String): Boolean = resourceId in destroyedResourceIds
}
