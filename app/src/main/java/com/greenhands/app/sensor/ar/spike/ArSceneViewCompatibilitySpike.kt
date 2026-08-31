package com.greenhands.app.sensor.ar.spike

import com.google.ar.core.ArCoreApk
import io.github.sceneview.ar.ARSceneView

/**
 * Phase 10E-A compile-time compatibility spike only.
 *
 * Proves `io.github.sceneview:arsceneview` and transitive ARCore resolve against
 * the current Kotlin 1.9 / Compose / AGP toolchain.
 *
 * NOT wired to navigation, Virtual Greenhouse, or any Real AR UI.
 */
object ArSceneViewCompatibilitySpike {

    /** Catalog version intentionally pinned for this spike. */
    const val ARSCENEVIEW_VERSION = "2.2.1"

    /** Transitive ARCore version declared by arsceneview 2.2.1 POM. */
    const val EXPECTED_ARCORE_VERSION = "1.43.0"

    fun arSceneViewClassName(): String = ARSceneView::class.java.name

    fun arCoreApkClassName(): String = ArCoreApk::class.java.name
}
