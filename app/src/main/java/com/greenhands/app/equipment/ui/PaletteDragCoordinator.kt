package com.greenhands.app.equipment.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.greenhands.app.equipment.model.EquipmentType
import kotlin.math.hypot

data class PaletteSlot(
    val type: EquipmentType,
    val windowRect: Rect
)

/**
 * Parent-level pointer tracking so a palette press can continue across the 3D viewport.
 * SceneView (AndroidView) never starts the gesture, so Compose drag on the palette item
 * alone cannot complete a drop. This host sees Initial-pass events for the whole screen.
 */
fun Modifier.placementPalettePointer(
    enabled: Boolean,
    hostOrigin: () -> Offset,
    slots: () -> List<PaletteSlot>,
    viewport: () -> Rect?,
    onTap: (EquipmentType) -> Unit,
    onDragStart: (EquipmentType) -> Unit,
    onDragWindow: (EquipmentType, Offset) -> Unit,
    onDragEnd: (overViewport: Boolean) -> Unit,
    onDragCancel: () -> Unit,
    onFingerWindow: (Offset?) -> Unit,
    touchSlopPx: Float
): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            var type: EquipmentType? = null
            var dragging = false
            var start = Offset.Zero
            var pointerId: Long? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { pointerId == null || it.id.value.toLong() == pointerId }
                    ?: event.changes.firstOrNull()
                    ?: continue
                val window = hostOrigin() + change.position
                val down = change.pressed && !change.previousPressed
                val up = change.changedToUpIgnoreConsumed()
                if (type == null && down) {
                    val slot = slots().firstOrNull { it.windowRect.contains(window) }
                    if (slot != null) {
                        type = slot.type
                        dragging = false
                        start = window
                        pointerId = change.id.value.toLong()
                    }
                    continue
                }
                if (type == null) continue
                if (change.pressed && !up) {
                    val dist = hypot((window.x - start.x).toDouble(), (window.y - start.y).toDouble()).toFloat()
                    if (!dragging && dist >= touchSlopPx) {
                        dragging = true
                        onDragStart(type)
                    }
                    if (dragging) {
                        change.consume()
                        onFingerWindow(window)
                        onDragWindow(type, window)
                    }
                } else {
                    if (dragging) {
                        change.consume()
                        val over = viewport()?.contains(window) == true
                        onFingerWindow(null)
                        onDragEnd(over)
                    } else if (up || !change.pressed) {
                        onFingerWindow(null)
                        // Taps are handled by the palette item clickable so Compose tests keep working.
                    } else {
                        onFingerWindow(null)
                        onDragCancel()
                    }
                    type = null
                    dragging = false
                    pointerId = null
                }
            }
        }
    }
}

fun Modifier.reportWindowRect(onRect: (Rect) -> Unit): Modifier =
    onGloballyPositioned { coords ->
        val origin = coords.positionInWindow()
        val size = coords.size
        onRect(
            Rect(
                origin.x,
                origin.y,
                origin.x + size.width,
                origin.y + size.height
            )
        )
    }
