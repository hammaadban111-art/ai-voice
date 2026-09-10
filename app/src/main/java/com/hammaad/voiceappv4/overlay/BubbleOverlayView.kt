package com.hammaad.voiceappv4.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.sin

@SuppressLint("ViewConstructor")
class BubbleOverlayView(context: Context, private val actions: Actions) : View(context) {
    enum class State { IDLE, RECORDING, TRANSCRIBING, FAILURE }
    interface Actions {
        fun onStart(pushToTalk: Boolean)
        fun onStop()
        fun onCancel()
        fun onPaste()
        fun onDismissFailure()
        fun onDrag(dx: Int, dy: Int)
        fun onSnap()
    }

    var state: State = State.IDLE; private set
    var level: Float = 0f; set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var statusText: String = ""; set(value) { field = value; invalidate() }
    var transcript: String = ""; set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * density; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private var ptt = false
    private val longPress = Runnable {
        if (!dragging && state == State.IDLE) {
            ptt = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            actions.onStart(true)
        }
    }

    fun showState(next: State, message: String = "", result: String = "") {
        state = next
        statusText = message
        transcript = result
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (state) {
            State.IDLE -> drawIdle(canvas)
            State.RECORDING -> drawRecording(canvas)
            State.TRANSCRIBING -> drawTranscribing(canvas)
            State.FAILURE -> drawFailure(canvas)
        }
    }

    private fun drawIdle(canvas: Canvas) {
        val radius = minOf(width, height) / 2f - 2f * density
        paint.color = Color.rgb(103, 80, 164)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        drawMic(canvas, width / 2f, height / 2f, Color.WHITE)
    }

    private fun drawRecording(canvas: Canvas) {
        drawPanel(canvas)
        paint.color = Color.rgb(186, 26, 26)
        canvas.drawCircle(36f * density, height / 2f, 21f * density, paint)
        drawMic(canvas, 36f * density, height / 2f, Color.WHITE)
        wave.color = Color.rgb(103, 80, 164)
        path.reset()
        val start = 72f * density
        val end = width - 112f * density
        val center = height / 2f
        for (i in 0..32) {
            val x = start + (end - start) * i / 32f
            val amp = (4f + 14f * level) * density
            val y = center + sin(i * .9f + System.currentTimeMillis() / 90f).toFloat() * amp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, wave)
        drawButton(canvas, width - 92f * density, "Cancel", false)
        drawButton(canvas, width - 36f * density, "Done", true)
        postInvalidateDelayed(70)
    }

    private fun drawTranscribing(canvas: Canvas) {
        drawPanel(canvas)
        paint.color = Color.rgb(103, 80, 164)
        canvas.drawCircle(34f * density, height / 2f, 17f * density, paint)
        wave.color = Color.WHITE
        canvas.drawArc(RectF(24f*density, height/2f-10f*density, 44f*density, height/2f+10f*density),
            (System.currentTimeMillis()/5 % 360).toFloat(), 250f, false, wave)
        text(canvas, statusText.ifBlank { "Finishing transcription…" }, 62f*density, height/2f+5f*density, 15f, Color.rgb(45,42,50), Paint.Align.LEFT)
        postInvalidateDelayed(40)
    }

    private fun drawFailure(canvas: Canvas) {
        drawPanel(canvas)
        text(canvas, statusText.ifBlank { "Couldn't insert automatically" }, 16f*density, 27f*density, 15f, Color.rgb(186,26,26), Paint.Align.LEFT)
        val preview = transcript.replace('\n', ' ').take(80)
        text(canvas, preview, 16f*density, 56f*density, 13f, Color.rgb(70,67,74), Paint.Align.LEFT)
        paint.color = Color.rgb(103,80,164)
        canvas.drawRoundRect(RectF(16f*density, 78f*density, 126f*density, 122f*density), 20f*density, 20f*density, paint)
        text(canvas, "Paste", 71f*density, 106f*density, 14f, Color.WHITE, Paint.Align.CENTER)
        text(canvas, "Dismiss", width-55f*density, 106f*density, 14f, Color.rgb(103,80,164), Paint.Align.CENTER)
    }

    private fun drawPanel(canvas: Canvas) {
        paint.color = Color.argb(248, 250, 247, 252)
        paint.setShadowLayer(10f*density, 0f, 3f*density, Color.argb(70,0,0,0))
        setLayerType(LAYER_TYPE_SOFTWARE, paint)
        canvas.drawRoundRect(RectF(5f*density, 5f*density, width-5f*density, height-5f*density), 28f*density, 28f*density, paint)
        paint.clearShadowLayer()
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f*density
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawRoundRect(RectF(cx-5f*density, cy-12f*density, cx+5f*density, cy+5f*density), 5f*density, 5f*density, paint)
        canvas.drawArc(RectF(cx-11f*density, cy-5f*density, cx+11f*density, cy+12f*density), 0f, 180f, false, paint)
        canvas.drawLine(cx, cy+12f*density, cx, cy+17f*density, paint)
        canvas.drawLine(cx-6f*density, cy+17f*density, cx+6f*density, cy+17f*density, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawButton(canvas: Canvas, cx: Float, label: String, primary: Boolean) {
        paint.color = if (primary) Color.rgb(103,80,164) else Color.rgb(231,224,236)
        canvas.drawCircle(cx, height/2f, 24f*density, paint)
        text(canvas, label, cx, height/2f+4f*density, 10f, if (primary) Color.WHITE else Color.rgb(50,45,55), Paint.Align.CENTER)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, sp: Float, color: Int, align: Paint.Align) {
        paint.color = color; paint.textSize = sp*density; paint.textAlign = align; paint.style = Paint.Style.FILL
        canvas.drawText(value, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; lastRawX = event.rawX; lastRawY = event.rawY
                dragging = false; ptt = false
                if (state == State.IDLE) handler.postDelayed(longPress, 430)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == State.IDLE && !ptt) {
                    if (!dragging && (abs(event.x-downX) > slop || abs(event.y-downY) > slop)) {
                        dragging = true; handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        actions.onDrag((event.rawX-lastRawX).toInt(), (event.rawY-lastRawY).toInt())
                        lastRawX = event.rawX; lastRawY = event.rawY
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (ptt) actions.onStop()
                else if (dragging) actions.onSnap()
                else if (event.actionMasked == MotionEvent.ACTION_UP) { performClick(); handleTap(event.x, event.y) }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        when (state) {
            State.IDLE -> actions.onStart(false)
            State.RECORDING -> when {
                x in (width - 118f*density)..(width - 64f*density) -> actions.onCancel()
                x >= width - 64f*density -> actions.onStop()
            }
            State.TRANSCRIBING -> Unit
            State.FAILURE -> when {
                y > 70f*density && x < 150f*density -> actions.onPaste()
                y > 70f*density -> actions.onDismissFailure()
            }
        }
    }
}
