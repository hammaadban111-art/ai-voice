package com.dictate.app.overlay

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.dictate.app.DictateApplication
import com.dictate.app.accessibility.DictationAccessibilityService
import com.dictate.app.audio.AudioCapture
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
 * [isBusy] guards against duplicate recordings from repeated taps.
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

    private val committed = StringBuilder()
    private var isBusy = false
    private var liveConnected = false
    private var finishRequested = false
    private var finalizeJob: Job? = null
    private var lastRequest: TranscriptionRequest? = null

    fun show() {
        if (_state.value is DictationState.Hidden) _state.value = DictationState.Ready
    }

    fun hide() {
        cancel()
        _state.value = DictationState.Hidden
    }

    fun startRecording() {
        if (isBusy) return
        isBusy = true
        committed.clear()
        finishRequested = false
        liveConnected = false
        _state.value = DictationState.Connecting

        scope.launch {
            val settings = app.settingsRepository.settings.first()
            val apiKey = app.secureKeyStore.getApiKey()
            if (apiKey.isNullOrBlank()) {
                _state.value = DictationState.Error("Add your Gemini API key in Settings")
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
        _state.value = DictationState.Finalizing
        audioCapture.stop()
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
        liveClient.close()
        committed.clear()
        isBusy = false
        if (_state.value != DictationState.Hidden) _state.value = DictationState.Ready
    }

    private fun onEvent(event: TranscriptionEvent) {
        scope.launch(Dispatchers.Main) {
            when (event) {
                TranscriptionEvent.Connected -> {
                    liveConnected = true
                    if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        _state.value = DictationState.Error("Microphone permission is required")
                        isBusy = false
                        liveClient.close()
                        return@launch
                    }
                    _state.value = DictationState.Recording("")
                    audioCapture.start(
                        scope = scope,
                        onChunk = { buffer, length -> liveClient.sendAudioChunk(buffer, length) },
                        onError = { },
                    )
                }
                is TranscriptionEvent.Partial -> {
                    if (_state.value is DictationState.Recording) {
                        val preview = (committed.toString() + " " + event.text).trim()
                        _state.value = DictationState.Recording(preview)
                    }
                }
                is TranscriptionEvent.Final -> {
                    if (committed.isNotEmpty()) committed.append(' ')
                    committed.append(event.text)
                    if (_state.value is DictationState.Recording || _state.value is DictationState.Finalizing) {
                        _state.value = DictationState.Recording(committed.toString())
                    }
                }
                is TranscriptionEvent.Error -> {
                    if (finishRequested) {
                        finalizeJob?.cancel()
                        finalizeWithRestFallback()
                    } else if (!liveConnected) {
                        isBusy = false
                        _state.value = DictationState.Error(event.message)
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
            _state.value = DictationState.Error("No speech captured")
            isBusy = false
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { restClient.transcribe(pcm, request) }
            result.onSuccess { insertResult(it) }.onFailure {
                _state.value = DictationState.Error(it.message ?: "Transcription failed")
                isBusy = false
            }
        }
    }

    private fun insertResult(text: String) {
        scope.launch {
            _state.value = DictationState.Inserting
            val inserted = DictationAccessibilityService.instance?.insertTranscript(text) ?: false
            val settings = app.settingsRepository.settings.first()
            if (settings.saveHistory) app.historyStore.append(text)

            _state.value = if (inserted) {
                com.dictate.app.core.DictationTestState.markSuccess()
                DictationState.Success(text)
            } else {
                DictationState.Error("Couldn't insert automatically", fallbackText = text)
            }
            isBusy = false
            audioCapture.clearBuffer()
        }
    }

    private companion object {
        const val FINALIZE_GRACE_PERIOD_MS = 2500L
    }
}
