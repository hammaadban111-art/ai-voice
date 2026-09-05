package com.dictate.app.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dictate.app.MainActivity
import com.dictate.app.R
import com.dictate.app.accessibility.FieldFocusTracker
import com.dictate.app.asDictateApp
import com.dictate.app.core.BubbleVisibilityPolicy
import com.dictate.app.core.DictateLog
import com.dictate.app.core.GestureAction
import com.dictate.app.core.GestureClassifier
import com.dictate.app.data.settings.DictateSettings
import com.dictate.app.ui.theme.DictateTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hosts the floating dictation bubble as a *single* [WindowManager] overlay
 * window and runs as a foreground service (type `microphone`) so Android
 * does not tear the recording down mid-utterance.
 *
 * Deliberately one window, not two: the compact bubble and the expanded
 * recording pill are mutually exclusive in the same Compose tree (never
 * both present), so a raw [View.OnTouchListener] used to drive the
 * bubble's drag/tap/long-press gestures can never end up sitting on top
 * of — and swallowing touches meant for — the pill's own Cancel/Done/Paste
 * buttons. [isPillShaped] is the single switch both the renderer and the
 * touch listener consult, so they can never disagree about which mode is
 * currently showing.
 */
class BubbleOverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var controller: DictationController
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var overlayView: ComposeView? = null
    private val overlayOwner = OverlayLifecycleOwner()

    @Volatile private var hapticsEnabled = true

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        controller = DictationController(application.asDictateApp(), lifecycleScope)
        overlayOwner.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addOverlayView()
        observeHaptics()
        observeAutoStop()
        isRunning = true
        DictateLog.d("BubbleOverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        isRunning = false
        controller.hide()
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayOwner.onDestroy()
        super.onDestroy()
        DictateLog.d("BubbleOverlayService destroyed")
    }

    // --------------------------------------------------------------- view

    private fun addOverlayView() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            // FLAG_NOT_FOCUSABLE is load-bearing: this window must never be
            // able to steal focus (or the keyboard) away from the text
            // field the user is actually typing into.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 600
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setContent { OverlayContent() }
            setOnTouchListener(OverlayTouchListener())
        }
        overlayView = view
        windowManager.addView(view, overlayParams)

        // WRAP_CONTENT means the window resizes itself when the pill (wider
        // than the bubble) appears; keep it fully on-screen either way.
        view.viewTreeObserver.addOnGlobalLayoutListener { clampToScreen(view) }
    }

    private fun clampToScreen(view: View) {
        if (view.width == 0 || view.height == 0) return
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - view.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - view.height).coerceAtLeast(0)
        val clampedX = overlayParams.x.coerceIn(0, maxX)
        val clampedY = overlayParams.y.coerceIn(0, maxY)
        if (clampedX != overlayParams.x || clampedY != overlayParams.y) {
            overlayParams.x = clampedX
            overlayParams.y = clampedY
            runCatching { windowManager.updateViewLayout(view, overlayParams) }
        }
    }

    @Composable
    private fun OverlayContent() {
        val app = application.asDictateApp()
        val settings by app.settingsRepository.settings.collectAsState(initial = DictateSettings())
        val focus by FieldFocusTracker.state.collectAsState()
        val dictationState by controller.state.collectAsState()
        val amplitude by controller.amplitude.collectAsState()

        val sessionActive = isPillShaped(dictationState)
        val visible = BubbleVisibilityPolicy.shouldShowAnything(
            sessionActive = sessionActive,
            bubbleFeatureEnabled = settings.bubbleEnabled,
            snoozed = settings.isSnoozed,
            editableFieldActive = focus.editableFieldActive,
        )
        val showPill = BubbleVisibilityPolicy.shouldShowAsPill(sessionActive)

        DictateTheme {
            AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                if (showPill) {
                    RecordingPill(
                        state = dictationState,
                        amplitude = amplitude,
                        onCancel = { haptic(); controller.cancel() },
                        onDone = { haptic(); controller.stopAndFinish() },
                        onPaste = (dictationState as? DictationState.Error)?.fallbackText?.let { text ->
                            { copyToClipboard(text); controller.cancel() }
                        },
                    )
                } else {
                    CollapsedBubble(state = dictationState, sizeDp = settings.bubbleSizeDp)
                }
            }
        }
        SideEffect { overlayView?.alpha = settings.bubbleOpacityPercent / 100f }
    }

    // ------------------------------------------------------------ gesture

    private inner class OverlayTouchListener : View.OnTouchListener {
        private val classifier = GestureClassifier(ViewConfiguration.get(this@BubbleOverlayService).scaledTouchSlop)
        private val handler = Handler(Looper.getMainLooper())
        private val longPressRunnable = Runnable {
            if (classifier.canConfirmLongPress()) {
                classifier.confirmLongPress()
                DictateLog.d("gesture: long-press confirmed")
                haptic()
                controller.startRecording()
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            // While the pill is showing, this is Compose's territory: its
            // Cancel/Done/Paste buttons need real click events, not a
            // listener here consuming everything before Compose sees it.
            if (isPillShaped(controller.state.value)) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    classifier.onDown(event.rawX, event.rawY)
                    handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    when (val action = classifier.onMove(event.rawX, event.rawY)) {
                        GestureAction.DragStarted -> handler.removeCallbacks(longPressRunnable)
                        is GestureAction.DragMoved -> {
                            overlayParams.x += action.dx
                            overlayParams.y += action.dy
                            runCatching { windowManager.updateViewLayout(v, overlayParams) }
                        }
                        else -> Unit
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    when (classifier.onUp()) {
                        GestureAction.DragEnded -> snapToNearestEdge(v)
                        GestureAction.LongPressEnded -> {
                            DictateLog.d("gesture: long-press release -> stopAndFinish")
                            haptic()
                            controller.stopAndFinish()
                        }
                        GestureAction.Tap -> {
                            DictateLog.d("gesture: tap -> startRecording")
                            haptic()
                            controller.startRecording()
                        }
                        else -> Unit
                    }
                    return true
                }
            }
            return false
        }

        private fun snapToNearestEdge(view: View) {
            val metrics = resources.displayMetrics
            val bubbleWidth = view.width.takeIf { it > 0 } ?: 150
            val targetX = if (overlayParams.x + bubbleWidth / 2 < metrics.widthPixels / 2) {
                0
            } else {
                metrics.widthPixels - bubbleWidth
            }
            val maxY = (metrics.heightPixels - view.height).coerceAtLeast(0)
            val targetY = overlayParams.y.coerceIn(0, maxY)

            ValueAnimator.ofInt(overlayParams.x, targetX).apply {
                duration = 220
                addUpdateListener {
                    overlayParams.x = it.animatedValue as Int
                    overlayParams.y = targetY
                    runCatching { windowManager.updateViewLayout(view, overlayParams) }
                }
                start()
            }
        }
    }

    private fun haptic() {
        if (!hapticsEnabled) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("dictate", text))
    }

    // ----------------------------------------------------------- lifecycle

    private fun observeHaptics() {
        lifecycleScope.launch {
            application.asDictateApp().settingsRepository.settings
                .distinctUntilChanged { old, new -> old.hapticsEnabled == new.hapticsEnabled }
                .collect { hapticsEnabled = it.hapticsEnabled }
        }
    }

    private fun observeAutoStop() {
        lifecycleScope.launch {
            combine(
                application.asDictateApp().settingsRepository.settings,
                controller.state,
            ) { settings, state -> settings.bubbleEnabled to state }
                .distinctUntilChanged()
                .collect { (enabled, state) ->
                    if (!enabled && state is DictationState.Hidden) stopSelf()
                }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_bubble),
            NotificationManager.IMPORTANCE_MIN,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Dictation bubble is active")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "dictation_bubble"
        private const val NOTIFICATION_ID = 42

        /** The actual, live state of the service — not merely whether the setting says it should be. */
        var isRunning: Boolean = false
            private set

        /** True for every state the pill (not the plain bubble) represents. Shared by the renderer and the gesture listener so they never disagree. */
        fun isPillShaped(state: DictationState): Boolean =
            state !is DictationState.Hidden && state !is DictationState.Ready

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BubbleOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleOverlayService::class.java))
        }
    }
}
