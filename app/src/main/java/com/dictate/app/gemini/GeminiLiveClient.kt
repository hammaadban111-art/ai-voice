package com.dictate.app.gemini

import android.util.Base64
import com.dictate.app.core.TranscriptionMode
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

private const val LIVE_WS_HOST = "wss://generativelanguage.googleapis.com/ws/" +
    "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

/**
 * Streams microphone audio to Gemini Live's transcription model over a
 * WebSocket (BidiGenerateContent) and surfaces interim/final transcription
 * events. VAD is driven manually (activityStart/activityEnd) so a user's
 * own tap/long-press gesture — not silence detection — decides when an
 * utterance ends.
 *
 * The API key is only ever placed in the connection URL sent directly to
 * Google's endpoint; it is never logged.
 */
class GeminiLiveClient {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var listener: ((TranscriptionEvent) -> Unit)? = null
    private var activityStarted = false

    fun connect(request: TranscriptionRequest, onEvent: (TranscriptionEvent) -> Unit) {
        listener = onEvent
        val httpRequest = Request.Builder()
            .url("$LIVE_WS_HOST?key=${request.apiKey}")
            .build()

        webSocket = client.newWebSocket(httpRequest, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(buildSetupMessage(request).toString())
                onEvent(TranscriptionEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text, onEvent)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onEvent(TranscriptionEvent.Closed)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(TranscriptionEvent.Error(t.message ?: "Connection failed", recoverable = true))
            }
        })
    }

    fun sendAudioChunk(pcm: ByteArray, length: Int) {
        val socket = webSocket ?: return
        if (!activityStarted) {
            socket.send(JSONObject().put("realtimeInput", JSONObject().put("activityStart", JSONObject())).toString())
            activityStarted = true
        }
        val encoded = Base64.encodeToString(pcm, 0, length, Base64.NO_WRAP)
        val message = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("data", encoded)
                    .put("mimeType", "audio/pcm;rate=16000"),
            ),
        )
        socket.send(message.toString())
    }

    /** Signals the end of the utterance and asks Gemini to flush the final transcript. */
    fun finish() {
        val socket = webSocket ?: return
        if (activityStarted) {
            socket.send(JSONObject().put("realtimeInput", JSONObject().put("activityEnd", JSONObject())).toString())
        }
        socket.send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString())
    }

    fun close() {
        webSocket?.close(1000, "done")
        webSocket = null
        activityStarted = false
        listener = null
    }

    private fun buildSetupMessage(request: TranscriptionRequest): JSONObject {
        val vocabulary = JSONArray().apply { request.customVocabulary.forEach { put(it) } }
        val languages = JSONArray().apply { request.languageCodes.forEach { put(it) } }
        val inputAudioTranscription = JSONObject()
            .put("languageCodes", languages)
            .put("customVocabulary", vocabulary)
            .put("mode", if (request.mode == TranscriptionMode.SMART) "SMART" else "VERBATIM")

        val setup = JSONObject()
            .put("model", "models/${request.liveModel}")
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT")))
            .put("inputAudioTranscription", inputAudioTranscription)
            .put(
                "realtimeInputConfig",
                JSONObject().put("automaticActivityDetection", JSONObject().put("disabled", true)),
            )

        return JSONObject().put("setup", setup)
    }

    private fun handleServerMessage(text: String, onEvent: (TranscriptionEvent) -> Unit) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        val serverContent = json.optJSONObject("serverContent") ?: return

        serverContent.optJSONObject("interimInputTranscription")?.optString("text")?.let {
            if (it.isNotEmpty()) onEvent(TranscriptionEvent.Partial(it))
        }
        serverContent.optJSONObject("inputTranscription")?.optString("text")?.let {
            if (it.isNotEmpty()) onEvent(TranscriptionEvent.Final(it))
        }
    }
}
