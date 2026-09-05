package com.dictate.app.core

import kotlin.math.abs

sealed interface GestureAction {
    /** Emitted once, the instant a drag first crosses the touch-slop threshold. */
    data object DragStarted : GestureAction
    data class DragMoved(val dx: Int, val dy: Int) : GestureAction
    data object DragEnded : GestureAction
    data object Tap : GestureAction
    data object LongPressEnded : GestureAction
    data object Ignored : GestureAction
}

/**
 * Framework-free tap/long-press/drag classifier for the draggable bubble.
 * Screen coordinates and timing are supplied by the caller (a raw
 * [android.view.View.OnTouchListener] in production, fixed values in
 * tests) so the actual decision — is this a tap, a drag, or a long-press
 * release — can be unit-tested without touching Android at all.
 *
 * The long-press *timer* itself is still the caller's job (e.g. a
 * `Handler.postDelayed`): this class only decides what firing that timer
 * means (see [confirmLongPress]) and never fires it on its own, since a
 * drag that starts after the timer was scheduled must be able to cancel it.
 */
class GestureClassifier(private val touchSlopPx: Int) {

    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f

    var isDragging = false
        private set
    var isLongPress = false
        private set

    fun onDown(x: Float, y: Float) {
        startX = x; startY = y; lastX = x; lastY = y
        isDragging = false
        isLongPress = false
    }

    /** True only if a drag hasn't already started and long-press hasn't already fired. */
    fun canConfirmLongPress(): Boolean = !isDragging && !isLongPress

    fun confirmLongPress() {
        isLongPress = true
    }

    fun onMove(x: Float, y: Float): GestureAction {
        if (!isDragging) {
            val dx = x - startX
            val dy = y - startY
            if (abs(dx) <= touchSlopPx && abs(dy) <= touchSlopPx) return GestureAction.Ignored
            isDragging = true
            lastX = x; lastY = y
            return GestureAction.DragStarted
        }
        val moveDx = (x - lastX).toInt()
        val moveDy = (y - lastY).toInt()
        lastX = x; lastY = y
        return GestureAction.DragMoved(moveDx, moveDy)
    }

    fun onUp(): GestureAction = when {
        isDragging -> GestureAction.DragEnded
        isLongPress -> GestureAction.LongPressEnded
        else -> GestureAction.Tap
    }
}
