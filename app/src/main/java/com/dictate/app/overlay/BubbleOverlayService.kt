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
import com.dictate.app.data.settings.DictateSettings
import com.dictate.app.ui.theme.DictateTheme
import kotlin.math.abs
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hosts the floating dictation bubble as two [TYPE_APPLICATION_OVERLAY]
 * windows — a small draggable circle and, while a dictation is in
 * progress, an expanded pill — and runs as a foreground service (type
 * `microphone`) so Android does not tear the recording down mid-utterance.
 *
 * Two separate overlay windows are used deliberately: the bubble needs a
 * raw [View.OnTouchListener] to distinguish drag/tap/long-press, and
 * installing that on the same view as the pill's Compose buttons would
 * swallow their click events before Compose's gesture system ever sees them.
 */
class BubbleOverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var controller: DictationController
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var pillParams: WindowManager.LayoutParams
    private var bubbleView: ComposeView? = null
    private var pillView: ComposeView? = null
    private val overlayOwner = OverlayLifecycleOwner()

    @Volatile private var hapticsEnabled = true

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        controller = DictationController(application.asDictateApp(), lifecycleScope)
        overlayOwner.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addOverlayViews()
        observeHaptics()
        observeAutoStop()
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
        controller.hide()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        pillView?.let { runCatching { windowManager.removeView(it) } }
        overlayOwner.onDestroy()
        super.onDestroy()
    }

    // --------------------------------------------------------------- views

    private fun baseLayoutParams(): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun addOverlayViews() {
        bubbleParams = baseLayoutParams().apply { x = 0; y = 600 }
        pillParams = baseLayoutParams().apply { x = 0; y = 600 }

        val bubble = ComposeView(this).attachToOverlayLifecycle().apply {
            setContent { BubbleWindowContent() }
            setOnTouchListener(BubbleTouchListener())
        }
        bubbleView = bubble
        windowManager.addView(bubble, bubbleParams)

        val pill = ComposeView(this).attachToOverlayLifecycle().apply {
            setContent { PillWindowContent() }
        }
        pillView = pill
        windowManager.addView(pill, pillParams)
    }

    private fun ComposeView.attachToOverlayLifecycle(): ComposeView = apply {
        setViewTreeLifecycleOwner(overlayOwner)
        setViewTreeViewModelStoreOwner(overlayOwner)
        setViewTreeSavedStateRegistryOwner(overlayOwner)
    }

    @Composable
    private fun BubbleWindowContent() {
        val app = application.asDictateApp()
        val settings by app.settingsRepository.settings.collectAsState(initial = DictateSettings())
        val focus by FieldFocusTracker.state.collectAsState()
        val dictationState by controller.state.collectAsState()
        val sessionActive = dictationState !is DictationState.Hidden && dictationState !is DictationState.Ready
        val visible = !sessionActive && settings.bubbleEnabled && !settings.isSnoozed && focus.editableFieldActive

        DictateTheme {
            AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                CollapsedBubble(state = dictationState, sizeDp = settings.bubbleSizeDp)
            }
        }
        SideEffect { bubbleView?.alpha = settings.bubbleOpacityPercent / 100f }
    }

    @Composable
    private fun PillWindowContent() {
        val dictationState by controller.state.collectAsState()
        val sessionActive = dictationState !is DictationState.Hidden && dictationState !is DictationState.Ready

        DictateTheme {
            AnimatedVisibility(visible = sessionActive, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                RecordingPill(
                    state = dictationState,
                    onCancel = { haptic(); controller.cancel() },
                    onDone = { haptic(); controller.stopAndFinish() },
                    onPaste = (dictationState as? DictationState.Error)?.fallbackText?.let { text ->
                        { copyToClipboard(text); controller.cancel() }
                    },
                )
            }
        }
    }

    // ------------------------------------------------------------ gestures

    private fun beginDictation() {
        pillParams.x = bubbleParams.x
        pillParams.y = bubbleParams.y
        runCatching { pillView?.let { windowManager.updateViewLayout(it, pillParams) } }
        controller.startRecording()
    }

    private inner class BubbleTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var dragging = false
        private var longPressFired = false
        private val handler = Handler(Looper.getMainLooper())
        private val touchSlop = ViewConfiguration.get(this@BubbleOverlayService).scaledTouchSlop
        private val longPressRunnable = Runnable {
            longPressFired = true
            haptic()
            beginDictation()
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    longPressFired = false
                    handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        bubbleParams.x = initialX + dx.toInt()
                        bubbleParams.y = initialY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(v, bubbleParams) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    when {
                        dragging -> snapToNearestEdge(v)
                        longPressFired -> { haptic(); controller.stopAndFinish() }
                        else -> { haptic(); beginDictation() }
                    }
                    return true
                }
            }
            return false
        }

        private fun snapToNearestEdge(view: View) {
            val metrics = resources.displayMetrics
            val bubbleWidth = view.width.takeIf { it > 0 } ?: 150
            val targetX = if (bubbleParams.x + bubbleWidth / 2 < metrics.widthPixels / 2) {
                0
            } else {
                metrics.widthPixels - bubbleWidth
            }
            val maxY = (metrics.heightPixels - view.height).coerceAtLeast(0)
            val targetY = bubbleParams.y.coerceIn(0, maxY)

            ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
                duration = 220
                addUpdateListener {
                    bubbleParams.x = it.animatedValue as Int
                    bubbleParams.y = targetY
                    runCatching { windowManager.updateViewLayout(view, bubbleParams) }
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

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BubbleOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleOverlayService::class.java))
        }
    }
}
