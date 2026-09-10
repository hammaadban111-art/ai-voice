package com.hammaad.voiceappv4.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hammaad.voiceappv4.R
import com.hammaad.voiceappv4.accessibility.FieldPolicy
import com.hammaad.voiceappv4.accessibility.TextInsertion
import com.hammaad.voiceappv4.audio.PcmAudioRecorder
import com.hammaad.voiceappv4.data.AppPreferences
import com.hammaad.voiceappv4.network.GeminiApi
import com.hammaad.voiceappv4.network.GeminiException
import com.hammaad.voiceappv4.overlay.BubbleOverlayView
import com.hammaad.voiceappv4.security.SecureApiKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Canonical Android runtime owner: focused-field policy, capture, transcription, insertion, and history. */
class DictationAccessibilityService : AccessibilityService(), BubbleOverlayView.Actions {
    private lateinit var preferences: AppPreferences
    private lateinit var keyStore: SecureApiKeyStore
    private lateinit var windowManager: WindowManager
    private lateinit var overlay: BubbleOverlayView
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val recorder by lazy { PcmAudioRecorder(this) }
    private val api = GeminiApi()
    private var live: GeminiApi.LiveSession? = null
    private var recording: PcmAudioRecorder.Recording? = null
    private var liveFailed = false
    private var finishing = false
    private var finalHandled = false
    private var targetPackage = ""
    private var lastTranscript = ""
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { applySettingsAndReevaluate() }
    }
    private val reevaluate = Runnable { reevaluateContext() }
    private val fallbackTimeout = Runnable { if (finishing && !finalHandled) runBatchFallback() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferences = AppPreferences(this)
        keyStore = SecureApiKeyStore(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = BubbleOverlayView(this, this)
        preferences.register(preferenceListener)
        createNotificationChannel()
        preferences.log("Accessibility service connected")
        scheduleReevaluate(250)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::preferences.isInitialized) return
        if (!finishing && overlayOrNull()?.state != BubbleOverlayView.State.RECORDING) scheduleReevaluate(160)
    }

    override fun onInterrupt() {
        if (::preferences.isInitialized) preferences.log("Accessibility service interrupted")
        cancelRecording()
        hideOverlay()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::preferences.isInitialized) preferences.unregister(preferenceListener)
        cancelRecording()
        hideOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun scheduleReevaluate(delay: Long) {
        handler.removeCallbacks(reevaluate)
        handler.postDelayed(reevaluate, delay)
    }

    private fun reevaluateContext() {
        if (!::overlay.isInitialized || finishing || overlay.state == BubbleOverlayView.State.RECORDING) return
        val settings = preferences.read()
        val globallyReady = settings.bubbleEnabled && keyStore.hasKey() &&
            System.currentTimeMillis() >= settings.snoozedUntil && Settings.canDrawOverlays(this)
        if (!globallyReady) { hideOverlay(); return }
        val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val packageName = focus?.packageName?.toString().orEmpty()
        val allowed = focus != null && focus.isFocused && FieldPolicy.isAllowed(
            packageName, focus.isEditable, focus.isPassword, focus.inputType, settings.excludedPackages,
        )
        val keyboardVisible = runCatching { windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }.getOrDefault(false)
        if (allowed && keyboardVisible) {
            targetPackage = packageName
            showIdleOverlay()
        } else hideOverlay()
        focus?.recycle()
    }

    private fun showIdleOverlay() {
        val settings = preferences.read()
        overlay.alpha = settings.bubbleOpacity
        overlay.isHapticFeedbackEnabled = settings.haptics
        if (params == null) {
            val size = dp(settings.bubbleSizeDp)
            val metrics = resources.displayMetrics
            params = WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = if (settings.bubbleX >= 0) settings.bubbleX else metrics.widthPixels - size - dp(14)
                y = if (settings.bubbleY >= 0) settings.bubbleY else (metrics.heightPixels * .62f).toInt()
            }
            runCatching { windowManager.addView(overlay, params) }.onFailure {
                params = null
                preferences.log("Overlay add failed: ${it.message.orEmpty()}")
            }
        } else if (overlay.state != BubbleOverlayView.State.IDLE) {
            overlay.showState(BubbleOverlayView.State.IDLE)
            resize(dp(settings.bubbleSizeDp), dp(settings.bubbleSizeDp))
        }
    }

    private fun hideOverlay() {
        params?.let { runCatching { windowManager.removeView(overlay) } }
        params = null
    }

    override fun onStart(pushToTalk: Boolean) {
        if (finishing || overlay.state != BubbleOverlayView.State.IDLE) return
        val settings = preferences.read()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showFailure("Microphone permission was revoked", "Open Voxta to restore access")
            return
        }
        val apiKey = keyStore.get()
        if (apiKey.isBlank()) { showFailure("Gemini API key is missing", "Open Voxta Settings") ; return }
        if (runCatching { startForegroundForMicrophone() }.isFailure) {
            showFailure("Android blocked microphone startup", "Open Voxta and restore microphone or background access")
            return
        }
        liveFailed = false; finishing = false; finalHandled = false; recording = null; lastTranscript = ""
        overlay.showState(BubbleOverlayView.State.RECORDING, if (pushToTalk) "Release to finish" else "Listening")
        resize(dp(286), dp(72))
        live = api.openLive(apiKey, settings, object : GeminiApi.LiveListener {
            override fun onReady() { preferences.log("Gemini Live connected") }
            override fun onInterim(text: String) { handler.post { if (!finishing) overlay.statusText = text.takeLast(36) } }
            override fun onFinal(text: String) { handler.post { if (finishing && !finalHandled) completeWithTranscript(text, "live") } }
            override fun onError(message: String) { handler.post { liveFailed = true; preferences.log("Gemini Live: $message"); if (finishing && !finalHandled) runBatchFallback() } }
        })
        val started = recorder.start(
            onChunk = { live?.sendAudio(it) },
            onLevel = { value -> handler.post { if (::overlay.isInitialized) overlay.level = value } },
            onError = { message -> handler.post { preferences.log(message); if (!finishing) { cancelRecording(); showFailure(message, "") } } },
        )
        if (!started) { live?.close(); live = null; stopForeground(STOP_FOREGROUND_REMOVE) }
        else preferences.log("Recording started in $targetPackage")
    }

    override fun onStop() {
        if (overlay.state != BubbleOverlayView.State.RECORDING || finishing) return
        finishing = true
        recording = recorder.stop()
        live?.finish()
        overlay.showState(BubbleOverlayView.State.TRANSCRIBING, if (liveFailed) "Using reliable fallback…" else "Finishing transcription…")
        resize(dp(286), dp(72))
        stopForeground(STOP_FOREGROUND_REMOVE)
        val capture = recording
        if (capture == null || capture.pcm.isEmpty() || capture.durationMs < 180) {
            showFailure("No speech was captured", "Try again and speak after the red microphone appears")
            resetSession(closeLive = true)
            return
        }
        if (liveFailed) runBatchFallback() else handler.postDelayed(fallbackTimeout, 8_000)
    }

    override fun onCancel() {
        cancelRecording()
        preferences.log("Recording cancelled")
        showIdleOrHide()
    }

    private fun cancelRecording() {
        handler.removeCallbacks(fallbackTimeout)
        runCatching { recorder.cancel() }
        live?.close(); live = null
        finishing = false; finalHandled = false; recording = null
        if (::overlay.isInitialized && overlay.state == BubbleOverlayView.State.RECORDING) stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    private fun runBatchFallback() {
        if (finalHandled) return
        handler.removeCallbacks(fallbackTimeout)
        val capture = recording ?: return
        live?.close(); live = null
        overlay.statusText = "Transcribing recorded audio…"
        scope.launch {
            runCatching { api.batchTranscribe(keyStore.get(), capture.pcm, preferences.read()) }
                .onSuccess { completeWithTranscript(it, "batch fallback") }
                .onFailure { failure ->
                    val message = when ((failure as? GeminiException)?.kind) {
                        GeminiException.Kind.INVALID_KEY -> "Gemini rejected the API key"
                        GeminiException.Kind.QUOTA -> "Gemini quota is exhausted"
                        GeminiException.Kind.OFFLINE -> "No internet connection"
                        GeminiException.Kind.EMPTY -> "Gemini returned no transcript"
                        else -> "Transcription failed: ${failure.message.orEmpty()}"
                    }
                    preferences.log(message)
                    showFailure(message, "Your audio was discarded; no transcript was available")
                    resetSession(closeLive = true)
                }
        }
    }

    private fun completeWithTranscript(text: String, source: String) {
        if (finalHandled) return
        val clean = text.trim()
        if (clean.isBlank()) { runBatchFallback(); return }
        finalHandled = true
        handler.removeCallbacks(fallbackTimeout)
        live?.close(); live = null
        lastTranscript = clean
        recording?.pcm?.fill(0)
        recording = null
        val inserted = insertAtFocus(clean)
        preferences.log("Transcript from $source; insertion=${if (inserted) "ok" else "failed"}")
        if (inserted) {
            preferences.addHistory(clean, targetPackage)
            resetSession(closeLive = false)
            showIdleOrHide()
        } else {
            finishing = false
            showFailure("Couldn't insert automatically", clean)
        }
    }

    private fun insertAtFocus(transcript: String): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return try {
            val settings = preferences.read()
            val pkg = node.packageName?.toString().orEmpty()
            if (!FieldPolicy.isAllowed(pkg, node.isEditable, node.isPassword, node.inputType, settings.excludedPackages)) return false
            val existing = node.text?.toString().orEmpty()
            val start = node.textSelectionStart.takeIf { it >= 0 } ?: existing.length
            val end = node.textSelectionEnd.takeIf { it >= 0 } ?: start
            val result = TextInsertion.merge(existing, minOf(start, end), maxOf(start, end), transcript)
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result.text) }
            val changed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (changed) node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, result.cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, result.cursor)
            })
            changed
        } finally { node.recycle() }
    }

    override fun onPaste() {
        if (lastTranscript.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Voxta transcript", lastTranscript))
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val pasted = node?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true
        node?.recycle()
        Toast.makeText(this, if (pasted) "Transcript pasted" else "Transcript copied — tap Paste in the field", Toast.LENGTH_LONG).show()
        preferences.addHistory(lastTranscript, targetPackage)
        resetSession(closeLive = true)
        showIdleOrHide()
    }

    override fun onDismissFailure() {
        lastTranscript = ""
        resetSession(closeLive = true)
        showIdleOrHide()
    }

    override fun onDrag(dx: Int, dy: Int) {
        val p = params ?: return
        val metrics = resources.displayMetrics
        p.x = (p.x + dx).coerceIn(0, metrics.widthPixels - p.width)
        p.y = (p.y + dy).coerceIn(dp(24), metrics.heightPixels - p.height - dp(24))
        runCatching { windowManager.updateViewLayout(overlay, p) }
    }

    override fun onSnap() {
        val p = params ?: return
        val metrics = resources.displayMetrics
        p.x = if (p.x + p.width / 2 < metrics.widthPixels / 2) dp(10) else metrics.widthPixels - p.width - dp(10)
        runCatching { windowManager.updateViewLayout(overlay, p) }
        preferences.update { it.copy(bubbleX = p.x, bubbleY = p.y) }
    }

    private fun showFailure(message: String, transcript: String) {
        if (params == null) showIdleOverlay()
        overlay.showState(BubbleOverlayView.State.FAILURE, message, transcript)
        resize(dp(326), dp(136))
    }

    private fun resetSession(closeLive: Boolean) {
        handler.removeCallbacks(fallbackTimeout)
        if (closeLive) live?.close()
        live = null; recording = null; finishing = false; finalHandled = false; liveFailed = false
    }

    private fun showIdleOrHide() {
        overlay.showState(BubbleOverlayView.State.IDLE)
        hideOverlay()
        scheduleReevaluate(180)
    }

    private fun resize(width: Int, height: Int) {
        val p = params ?: return
        p.width = width; p.height = height
        val metrics = resources.displayMetrics
        p.x = p.x.coerceIn(0, metrics.widthPixels - width)
        p.y = p.y.coerceIn(0, metrics.heightPixels - height)
        runCatching { windowManager.updateViewLayout(overlay, p) }
    }

    private fun applySettingsAndReevaluate() {
        if (!::overlay.isInitialized) return
        if (overlay.state == BubbleOverlayView.State.IDLE) { hideOverlay(); scheduleReevaluate(100) }
    }

    private fun startForegroundForMicrophone() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Voxta is listening")
            .setContentText("Audio is used only for this dictation and is not saved.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_description)
        })
    }

    private fun overlayOrNull(): BubbleOverlayView? = if (::overlay.isInitialized) overlay else null
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "active_dictation"
        private const val NOTIFICATION_ID = 4104
    }
}
