package com.greenhands.app.harvest.detection

/**
 * Maps a box from analysis-frame normalized coordinates onto a PreviewView
 * that uses FILL_CENTER (uniform scale, center crop).
 *
 * [viewWidth]/[viewHeight] must be the PreviewView size, not a padded UI pane.
 * Overlaying the box in a smaller inset window uses a different crop than
 * CameraX FILL_CENTER on the full preview and will displace the rectangle.
 */
object PreviewFillCenterMapper {
    fun mapToView(
        box: NormalizedRect,
        frameWidth: Int,
        frameHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ): NormalizedRect {
        if (frameWidth <= 0 || frameHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return box
        }
        val frameAspect = frameWidth.toFloat() / frameHeight.toFloat()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val visibleW: Float
        val visibleH: Float
        if (frameAspect > viewAspect) {
            visibleH = 1f
            visibleW = viewAspect / frameAspect
        } else {
            visibleW = 1f
            visibleH = frameAspect / viewAspect
        }
        if (visibleW <= 0f || visibleH <= 0f) return box
        val originX = (1f - visibleW) / 2f
        val originY = (1f - visibleH) / 2f
        fun mapX(x: Float): Float = ((x - originX) / visibleW)
        fun mapY(y: Float): Float = ((y - originY) / visibleH)
        return NormalizedRect(
            left = mapX(box.left),
            top = mapY(box.top),
            right = mapX(box.right),
            bottom = mapY(box.bottom)
        ).clamp()
    }
}
