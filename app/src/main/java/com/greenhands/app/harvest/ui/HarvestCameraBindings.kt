package com.greenhands.app.harvest.ui

/**
 * Bind/release counter so unit tests can assert CameraX is released after leaving a scan.
 * Recomposition with the same DisposableEffect keys does not increment without a new bind.
 */
object HarvestCameraBindings {
    @Volatile
    var bindCount: Int = 0
        private set

    @Volatile
    var releaseCount: Int = 0
        private set

    val active: Int
        get() = (bindCount - releaseCount).coerceAtLeast(0)

    fun onBound() {
        bindCount += 1
    }

    fun onReleased() {
        releaseCount += 1
    }

    fun resetForTests() {
        bindCount = 0
        releaseCount = 0
    }
}
