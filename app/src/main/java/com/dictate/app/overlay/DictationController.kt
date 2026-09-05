package com.dictate.app.overlay

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.dictate.app.DictateApplication
import com.dictate.app.accessibility.DictationAccessibilityService
import com.dictate.app.audio.AudioCapture
import com.dictate.app.core.DictateLog
import com.dictate.app.core.DictationTestState
import com.dictate.app.core.LanguageMode
import com.dictate.app.gemini.GeminiLiveClient
import com.dictate.app.gemini.GeminiRestClient
import com.dictate.app.gemini.TranscriptionEvent
import com.dictate.app.gemini.TranscriptionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the Hidden -> Ready -> Connecting -> Recording -> Finalizing ->
 * Inserting -> Success/Error state machine for a single dictation. One
 * instance is shared for the lifetime of [com.dictate.app.overlay.BubbleOverlayService];
 * [isBusy] guards against duplicate recordings from repeated taps, and is
 * the only thing that decides whether a second AudioRecord/WebSocket pair
 * could ever be created — the gesture layer never makes that call itself.
 */
class DictationController(
    private val app: DictateApplication,
    private val scope: CoroutineScope,
) {
    private val liveClient = GeminiLiveClient()
    private val restClient = GeminiRestClient()
    private val audioCapture = AudioCapture()

    private val _state = MutableStateFlow<DictationState>(DictationState.Hidden)
    val state: StateFlow<DictationState> = _state

    private val _amplitude = MutableStateFlow(0f)

    /** Real, live microphone RMS level while [DictationState.Recording], 0 otherwise. */
    val amplitude: StateFlow<Float> = _amplitude

    /** Is a session already in flight? The gesture layer can check this before even trying. */
    val isSessionActive: Boolean get() = isBusy

    private val committed = StringBuilder()
    private var isBusy = false
    private var liveConnected = false
    private var finishRequested = false
    private var finalizeJob: Job? = null
    private var lastRequest: TranscriptionRequest? = null

    private fun setState(newState: DictationState) {
        DictateLog.d("state: ${_state.value::class.simpleName} -> ${newState::class.simpleName}")
        _state.value = newState
    }

    fun show() {
        if (_state.value is DictationState.Hidden) setState(DictationState.Ready)
    }

    fun hide() {
        cancel()
        setState(DictationState.Hidden)
    }

    fun startRecording() {
        if (isBusy) {
            DictateLog.d("startRecording ignored: session already active")
            return
        }
        isBusy = true
        committed.clear()
        finishRequested = false
        liveConnected = false
        setState(DictationState.Connecting)

        scope.launch {
            val settings = app.settingsRepository.settings.first()
            val apiKey = app.secureKeyStore.getApiKey()
            if (apiKey.isNullOrBlank()) {
                setState(DictationState.Error("Add your Gemini API key in Settings"))
                isBusy = false
                return@launch
            }
            val request = TranscriptionRequest(
                apiKey = apiKey,
                mode = settings.transcriptionMode,
                languageCodes = if (settings.languageMode == LanguageMode.AUTO) {
                    emptyList()
                } else {
                    listOf(settings.manualLanguageCode)
                },
                customVocabulary = settings.vocabularyTerms,
                liveModel = settings.liveModelOverride,
                restModel = settings.restModelOverride,
            )
            lastRequest = request
            liveClient.connect(request) { event -> onEvent(event) }
        }
    }

    /** Tap Done / release long-press: stop capturing and produce the final transcript. */
    fun stopAndFinish() {
        val current = _state.value
        if (current !is DictationState.Recording && current !is DictationState.Connecting) return
        finishRequested = true
        setState(DictationState.Finalizing)
        audioCapture.stop()
        _amplitude.value = 0f
        liveClient.finish()
        finalizeJob = scope.launch {
            delay(FINALIZE_GRACE_PERIOD_MS)
            completeFinalize()
        }
    }

    /** Tap Cancel: discard everything, nothing is inserted. */
    fun cancel() {
        finishRequested = false
        finalizeJob?.cancel()
        finalizeJob = null
        audioCapture.stop()
        audioCapture.clearBuffer()
        _amplitude.value = 0f
        liveClient.close()
        committed.clear()
        isBusy = false
        if (_state.value != DictationState.Hidden) setState(DictationState.Ready)
    }

    private fun onEvent(event: TranscriptionEvent) {
        scope.launch(Dispatchers.Main) {
            when (event) {
                TranscriptionEvent.Connected -> {
                    liveConnected = true
                    if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        setState(DictationState.Error("Microphone permission is required"))
                        isBusy = false
                        liveClient.close()
                        return@launch
                    }
                    setState(DictationState.Recording(""))
                    audioCapture.start(
                        scope = scope,
                        onChunk = { buffer, length -> liveClient.sendAudioChunk(buffer, length) },
                        onError = { },
                        onAmplitude = { level -> _amplitude.value = level },
                    )
                }
                is TranscriptionEvent.Partial -> {
                    if (_state.value is DictationState.Recording) {
                        val preview = (committed.toString() + " " + event.text).trim()
                        setState(DictationState.Recording(preview))
                    }
                }
                is TranscriptionEvent.Final -> {
                    if (committed.isNotEmpty()) committed.append(' ')
                    committed.append(event.text)
                    if (_state.value is DictationState.Recording || _state.value is DictationState.Finalizing) {
                        setState(DictationState.Recording(committed.toString()))
                    }
                }
                is TranscriptionEvent.Error -> {
                    if (finishRequested) {
                        finalizeJob?.cancel()
                        finalizeWithRestFallback()
                    } else if (!liveConnected) {
                        isBusy = false
                        setState(DictationState.Error(event.message))
                    }
                }
                TranscriptionEvent.Closed -> {
                    if (finishRequested) {
                        finalizeJob?.cancel()
                        completeFinalize()
                    }
                }
            }
        }
    }

    private fun completeFinalize() {
        if (_state.value !is DictationState.Finalizing) return
        liveClient.close()
        val text = committed.toString().trim()
        if (text.isNotEmpty()) {
            insertResult(text)
        } else {
            finalizeWithRestFallback()
        }
    }

    private fun finalizeWithRestFallback() {
        val request = lastRequest
        val pcm = audioCapture.bufferedPcm
        if (request == null || pcm.isEmpty()) {
            setState(DictationState.Error("No speech captured"))
            isBusy = false
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { restClient.transcribe(pcm, request) }
            result.onSuccess { insertResult(it) }.onFailure {
                setState(DictationState.Error(it.message ?: "Transcription failed"))
                isBusy = false
            }
        }
    }

    private fun insertResult(text: String) {
        scope.launch {
            setState(DictationState.Inserting)
            val inserted = DictationAccessibilityService.instance?.insertTranscript(text) ?: false
            val settings = app.settingsRepository.settings.first()
            if (settings.saveHistory) app.historyStore.append(text)

            setState(
                if (inserted) {
                    DictationTestState.markSuccess()
                    DictationState.Success(text)
                } else {
                    DictationState.Error("Couldn't insert automatically", fallbackText = text)
                },
            )
            isBusy = false
            audioCapture.clearBuffer()
        }
    }

    private companion object {
        const val FINALIZE_GRACE_PERIOD_MS = 2500L
    }
}
