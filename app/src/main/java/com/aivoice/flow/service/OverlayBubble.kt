package com.aivoice.flow.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.aivoice.flow.R
import com.aivoice.flow.Settings
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The floating mic button.
 *
 * Lives in a `TYPE_APPLICATION_OVERLAY` window that never takes focus, so the
 * text field underneath keeps its caret and the keyboard stays up while the
 * user taps to dictate.
 */
class OverlayBubble(
    private val context: Context,
    private val settings: Settings,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val root: View =
        LayoutInflater.from(context).inflate(R.layout.overlay_bubble, null)
    private val icon: ImageView = root.findViewById(R.id.bubble_icon)
    private val spinner: ProgressBar = root.findViewById(R.id.bubble_progress)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // NOT_FOCUSABLE is what keeps input focus (and the IME) on the app
        // underneath; without it the caret we are about to write into is lost.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = settings.bubbleX.takeIf { it >= 0 } ?: defaultX()
        y = settings.bubbleY.takeIf { it >= 0 } ?: defaultY()
    }

    private var added = false

    @SuppressLint("ClickableViewAccessibility") // the bubble is the a11y client
    fun show() {
        if (added) return
        root.setOnTouchListener(DragTapListener())
        windowManager.addView(root, params)
        added = true
    }

    fun hide() {
        if (!added) return
        runCatching { windowManager.removeView(root) }
        added = false
    }

    fun render(state: DictationService.State) {
        val (tint, iconRes) = when (state) {
            DictationService.State.LOADING -> R.color.bubble_busy to R.drawable.ic_mic
            DictationService.State.IDLE -> R.color.bubble_idle to R.drawable.ic_mic
            DictationService.State.RECORDING -> R.color.bubble_recording to R.drawable.ic_stop
            DictationService.State.TRANSCRIBING -> R.color.bubble_busy to R.drawable.ic_mic
            DictationService.State.ERROR -> R.color.bubble_error to R.drawable.ic_mic
        }
        root.backgroundTintList = ContextCompat.getColorStateList(context, tint)
        icon.setImageResource(iconRes)

        val busy = state == DictationService.State.LOADING ||
            state == DictationService.State.TRANSCRIBING
        spinner.visibility = if (busy) View.VISIBLE else View.GONE
        icon.visibility = if (busy) View.INVISIBLE else View.VISIBLE
        if (!busy) {
            root.scaleX = 1f
            root.scaleY = 1f
        }
    }

    /** Scales the bubble with the mic level so the user can see it listening. */
    fun renderLevel(level: Float) {
        val scale = 1f + (level.coerceIn(0f, 1f) * 0.35f)
        root.animate().cancel()
        root.scaleX = scale
        root.scaleY = scale
    }

    private fun defaultX(): Int {
        val metrics = windowManager.currentWindowMetrics.bounds
        return metrics.width() - context.resources.getDimensionPixelSize(R.dimen.bubble_size) -
            context.resources.getDimensionPixelSize(R.dimen.bubble_margin)
    }

    private fun defaultY(): Int = windowManager.currentWindowMetrics.bounds.height() / 2

    /**
     * Distinguishes a drag from a tap from a long press, because a plain
     * OnClickListener on an overlay would swallow the drag gesture.
     */
    private inner class DragTapListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var downAt = 0L
        private var dragging = false
        private var longPressFired = false

        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        private val longPressRunnable = Runnable {
            if (!dragging) {
                longPressFired = true
                root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onLongPress()
            }
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downAt = System.currentTimeMillis()
                    dragging = false
                    longPressFired = false
                    view.postDelayed(longPressRunnable, longPressTimeout)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && hypot(dx, dy) > slop) {
                        dragging = true
                        view.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(root, params) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPressRunnable)
                    if (dragging) {
                        settings.bubbleX = params.x
                        settings.bubbleY = params.y
                    } else if (!longPressFired &&
                        System.currentTimeMillis() - downAt < longPressTimeout &&
                        abs(event.rawX - touchX) <= slop
                    ) {
                        onTap()
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPressRunnable)
                    return true
                }
            }
            return false
        }
    }
}
