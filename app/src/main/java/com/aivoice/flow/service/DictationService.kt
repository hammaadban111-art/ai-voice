package com.aivoice.flow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.widget.Toast
import com.aivoice.flow.R
import com.aivoice.flow.Settings
import com.aivoice.flow.audio.AudioRecorder
import com.aivoice.flow.ui.MainActivity
import com.aivoice.flow.whisper.ModelStore
import com.aivoice.flow.whisper.WhisperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that owns the whole dictation loop:
 * overlay tap -> microphone -> whisper -> focused text field.
 *
 * It runs as a `microphone` foreground service so recording keeps working
 * while the user is inside another app, which is the entire point of the
 * floating button.
 */
class DictationService : Service() {

    enum class State { LOADING, IDLE, RECORDING, TRANSCRIBING, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: Settings
    private lateinit var recorder: AudioRecorder
    private var bubble: OverlayBubble? = null
    private var engine: WhisperEngine? = null

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        recorder = AudioRecorder(onLevel = { level ->
            scope.launch { bubble?.renderLevel(level) }
        })

        createNotificationChannel()
        startInForeground(buildNotification(getString(R.string.notif_loading)))

        bubble = OverlayBubble(
            context = this,
            settings = settings,
            onTap = ::onBubbleTapped,
            onLongPress = ::onBubbleLongPressed,
        ).also {
            it.show()
            it.render(State.LOADING)
        }

        loadEngine()
        running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running.value = false
        if (recorder.isRecording) recorder.cancel()
        bubble?.hide()
        bubble = null
        engine?.close()
        engine = null
        scope.cancel()
        state.value = State.LOADING
        super.onDestroy()
    }

    // -- pipeline ----------------------------------------------------------

    private fun loadEngine() {
        setState(State.LOADING)
        scope.launch {
            val loaded = withContext(Dispatchers.Default) {
                if (!ModelStore.install(applicationContext)) return@withContext null
                WhisperEngine.load(ModelStore.modelFile(applicationContext))
            }
            if (loaded == null) {
                setState(State.ERROR)
                notify(getString(R.string.notif_model_failed))
                toast(getString(R.string.toast_model_failed))
            } else {
                engine = loaded
                setState(State.IDLE)
                notify(getString(R.string.notif_ready))
            }
        }
    }

    private fun onBubbleTapped() {
        when (state.value) {
            State.LOADING -> toast(getString(R.string.toast_still_loading))
            State.ERROR -> loadEngine()
            State.TRANSCRIBING -> toast(getString(R.string.toast_transcribing))
            State.IDLE -> startRecording()
            State.RECORDING -> finishRecording()
        }
    }

    /** Long press cycles the dictation language without leaving the app. */
    private fun onBubbleLongPressed() {
        val languages = Settings.Language.entries
        val next = languages[(languages.indexOf(Settings.Language.fromCode(settings.language)) + 1) % languages.size]
        settings.language = next.code
        toast(getString(R.string.toast_language_switched, next.label))
    }

    private fun startRecording() {
        if (!recorder.start()) {
            setState(State.ERROR)
            toast(getString(R.string.toast_mic_failed))
            return
        }
        setState(State.RECORDING)
        notify(getString(R.string.notif_recording))
    }

    private fun finishRecording() {
        val samples = recorder.stop()
        bubble?.renderLevel(0f)

        if (samples.size < MIN_USEFUL_SAMPLES) {
            setState(State.IDLE)
            toast(getString(R.string.toast_too_short))
            notify(getString(R.string.notif_ready))
            return
        }

        setState(State.TRANSCRIBING)
        notify(getString(R.string.notif_transcribing))

        scope.launch {
            val active = engine
            if (active == null) {
                setState(State.ERROR)
                return@launch
            }
            val result = withContext(Dispatchers.Default) {
                active.transcribe(samples, settings.language)
            }
            deliver(result.text)
            setState(State.IDLE)
            notify(getString(R.string.notif_ready))
        }
    }

    private fun deliver(text: String) {
        if (text.isBlank()) {
            toast(getString(R.string.toast_nothing_heard))
            return
        }

        val a11y = FlowAccessibilityService.instance
        if (a11y == null) {
            TextInjector.copyToClipboard(this, text)
            toast(getString(R.string.toast_a11y_off))
            return
        }

        when (TextInjector.inject(a11y, text)) {
            TextInjector.Outcome.INSERTED, TextInjector.Outcome.PASTED -> Unit
            TextInjector.Outcome.CLIPBOARD_ONLY ->
                toast(getString(R.string.toast_copied_instead))
        }
    }

    // -- plumbing ----------------------------------------------------------

    private fun setState(next: State) {
        state.value = next
        bubble?.render(next)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DictationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.action_stop), stop).build(),
            )
            .build()
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    /**
     * The `microphone` type is mandatory from Android 14 on, and the platform
     * only grants it while the app is visible - hence the setup screen is what
     * starts this service.
     */
    private fun startInForeground(notification: Notification) {
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "dictation"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.aivoice.flow.STOP"

        /** Under ~0.3 s the user almost certainly mis-tapped. */
        private const val MIN_USEFUL_SAMPLES = WhisperEngine.SAMPLE_RATE * 3 / 10

        private val state = MutableStateFlow(State.LOADING)
        private val running = MutableStateFlow(false)

        val currentState: StateFlow<State> = state.asStateFlow()
        val isRunning: StateFlow<Boolean> = running.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, DictationService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DictationService::class.java))
        }
    }
}
