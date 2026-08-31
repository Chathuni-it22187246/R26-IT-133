package com.greenhands.app.harvest.ar

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.FixedHeightViewSizer
import com.google.ar.sceneform.rendering.ViewRenderable
import com.greenhands.app.R

/**
 * Sceneform host for one tap-to-place result label.
 *
 * Fruit/leaf tracking is not implemented. A later [ArPlacementController]
 * can replace hit-test placement without changing [ArResultData].
 */
class ArResultSceneHost(
    context: Context
) : FrameLayout(context) {

    private val sceneView: ArSceneView = ArSceneView(context)
    private var session: Session? = null
    private var result: ArResultData? = null
    private var cardRenderable: ViewRenderable? = null
    private var placedNode: AnchorNode? = null
    var onPlacementChanged: ((Boolean) -> Unit)? = null

    init {
        addView(
            sceneView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        sceneView.planeRenderer.isVisible = true
        sceneView.scene.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                handleTap(event)
            }
            true
        }
    }

    fun bind(data: ArResultData) {
        result = data
        ensureRenderable()
    }

    fun resumeSession() {
        try {
            if (session == null) {
                val created = Session(context)
                val config = Config(created)
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                sceneView.session = created
                sceneView.setSessionConfig(config, true)
                session = created
            }
            sceneView.resume()
        } catch (_: Throwable) {
        }
    }

    fun pauseSession() {
        try {
            sceneView.pause()
        } catch (_: Throwable) {
        }
    }

    fun destroySession() {
        resetPlacement()
        try {
            sceneView.pause()
            sceneView.destroy()
        } catch (_: Throwable) {
        }
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = null
    }

    fun resetPlacement() {
        placedNode?.anchor?.detach()
        placedNode?.setParent(null)
        placedNode = null
        onPlacementChanged?.invoke(false)
    }

    private fun handleTap(event: MotionEvent) {
        val frame = sceneView.arFrame ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return
        val hit = frame.hitTest(event).firstOrNull { candidate ->
            isPlaneHit(candidate)
        } ?: return
        place(hit)
    }

    private fun isPlaneHit(hit: HitResult): Boolean {
        val trackable = hit.trackable
        return trackable is Plane &&
            trackable.trackingState == TrackingState.TRACKING &&
            trackable.isPoseInPolygon(hit.hitPose)
    }

    private fun place(hit: HitResult) {
        val renderable = cardRenderable ?: return
        resetPlacement()
        val anchor = try {
            hit.createAnchor()
        } catch (_: Throwable) {
            return
        }
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(sceneView.scene)
        val card = Node()
        card.setParent(anchorNode)
        card.localPosition = Vector3(0f, 0.12f, 0f)
        card.renderable = renderable
        bindCard(renderable.view)
        placedNode = anchorNode
        onPlacementChanged?.invoke(true)
    }

    private fun ensureRenderable() {
        if (cardRenderable != null) {
            cardRenderable?.view?.let(::bindCard)
            return
        }
        ViewRenderable.builder()
            .setView(context, R.layout.ar_result_card)
            .setSizer(FixedHeightViewSizer(0.12f))
            .build()
            .thenAccept { renderable ->
                renderable.isShadowCaster = false
                renderable.isShadowReceiver = false
                cardRenderable = renderable
                bindCard(renderable.view)
            }
            .exceptionally { null }
    }

    private fun bindCard(view: android.view.View) {
        val data = result ?: return
        view.findViewById<TextView>(R.id.ar_card_title).text = data.title
        view.findViewById<TextView>(R.id.ar_card_status).text = data.status
        val detail = view.findViewById<TextView>(R.id.ar_card_detail)
        if (data.detail.isNullOrBlank()) {
            detail.visibility = GONE
        } else {
            detail.visibility = VISIBLE
            detail.text = data.detail
        }
        val confidence = view.findViewById<TextView>(R.id.ar_card_confidence)
        val percent = data.confidencePercent
        if (percent == null) {
            confidence.visibility = GONE
        } else {
            confidence.visibility = VISIBLE
            confidence.text = context.getString(R.string.ar_confidence, percent)
        }
    }
}
