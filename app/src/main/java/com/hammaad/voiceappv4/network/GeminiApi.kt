package com.hammaad.voiceappv4.network

import android.util.Base64
import com.hammaad.voiceappv4.audio.WavEncoder
import com.hammaad.voiceappv4.data.AppSettings
import com.hammaad.voiceappv4.data.TranscriptionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.net.NoRouteToHostException
import java.net.UnknownHostException

class GeminiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { INVALID_KEY, QUOTA, OFFLINE, SERVER, EMPTY, CONNECTION }
}

/** Canonical Android Gemini backend: Live transcription with in-memory batch fallback. */
class GeminiApi(private val client: OkHttpClient = defaultClient()) {
    suspend fun testKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-transcribe")
                .header("x-goog-api-key", apiKey)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw fromResponse(response)
            }
        }
    }

    suspend fun batchTranscribe(apiKey: String, pcm: ByteArray, settings: AppSettings): String = withContext(Dispatchers.IO) {
        if (pcm.isEmpty()) throw GeminiException(GeminiException.Kind.EMPTY, "No audio was captured")
        val wav = WavEncoder.encode16KhzMono(pcm)
        var remoteName: String? = null
        try {
            val uploadStart = Request.Builder()
                .url("https://generativelanguage.googleapis.com/upload/v1beta/files")
                .header("x-goog-api-key", apiKey)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", wav.size.toString())
                .header("X-Goog-Upload-Header-Content-Type", "audio/wav")
                .post(JSONObject().put("file", JSONObject().put("display_name", "Voxta ephemeral dictation"))
                    .toString().toRequestBody(JSON))
                .build()
            val uploadUrl = client.newCall(uploadStart).execute().use { response ->
                if (!response.isSuccessful) throw fromResponse(response)
                response.header("x-goog-upload-url") ?: throw GeminiException(GeminiException.Kind.SERVER, "Gemini did not return an upload URL")
            }
            val fileJson = client.newCall(Request.Builder().url(uploadUrl)
                .header("Content-Length", wav.size.toString())
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .post(wav.toRequestBody("audio/wav".toMediaType())).build()).execute().use { response ->
                if (!response.isSuccessful) throw fromResponse(response)
                JSONObject(response.body?.string().orEmpty()).getJSONObject("file")
            }
            remoteName = fileJson.getString("name")
            val config = JSONObject()
                .put("language_codes", if (settings.languageCode.isBlank()) JSONArray() else JSONArray().put(settings.languageCode))
                .put("custom_vocabulary", JSONArray(settings.customVocabulary.take(1000)))
                .put("mode", if (settings.mode == TranscriptionMode.SMART) "smart" else JSONObject().put("type", "verbatim"))
            val body = JSONObject()
                .put("model", "gemini-3.5-transcribe")
                .put("input", JSONArray().put(JSONObject()
                    .put("type", "audio")
                    .put("uri", fileJson.getString("uri"))
                    .put("mime_type", "audio/wav")))
                .put("generation_config", JSONObject().put("transcription_config", config))
            client.newCall(Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/interactions")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody(JSON)).build()).execute().use { response ->
                if (!response.isSuccessful) throw fromResponse(response)
                extractText(JSONObject(response.body?.string().orEmpty()))
                    .takeIf { it.isNotBlank() }
                    ?: throw GeminiException(GeminiException.Kind.EMPTY, "Gemini returned no transcript")
            }
        } catch (e: GeminiException) {
            throw e
        } catch (e: Exception) {
            val kind = if (e is UnknownHostException || e is NoRouteToHostException) GeminiException.Kind.OFFLINE else GeminiException.Kind.CONNECTION
            throw GeminiException(kind, e.message ?: "Gemini connection failed")
        } finally {
            remoteName?.let { name ->
                runCatching { client.newCall(Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/$name")
                    .header("x-goog-api-key", apiKey).delete().build()).execute().close() }
            }
            wav.fill(0)
            pcm.fill(0)
        }
    }

    fun openLive(apiKey: String, settings: AppSettings, listener: LiveListener): LiveSession {
        val url = "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
            .toHttpUrl().newBuilder().scheme("wss").addQueryParameter("key", apiKey).build()
        val request = Request.Builder().url(url).build()
        val session = LiveSession(settings, listener)
        session.socket = client.newWebSocket(request, session)
        return session
    }

    interface LiveListener {
        fun onReady()
        fun onInterim(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    class LiveSession(private val settings: AppSettings, private val events: LiveListener) : WebSocketListener() {
        internal lateinit var socket: WebSocket
        private val pending = ArrayDeque<ByteArray>()
        @Volatile private var ready = false
        @Volatile private var closed = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            val transcription = JSONObject()
                .put("languageCodes", if (settings.languageCode.isBlank()) JSONArray() else JSONArray().put(settings.languageCode))
                .put("customVocabulary", JSONArray(settings.customVocabulary.take(1000)))
                .put("mode", settings.mode.name)
            val setup = JSONObject().put("setup", JSONObject()
                .put("model", "models/gemini-3.5-transcribe-live")
                .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT")))
                .put("realtimeInputConfig", JSONObject().put("automaticActivityDetection", JSONObject().put("disabled", true)))
                .put("inputAudioTranscription", transcription))
            webSocket.send(setup.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val json = JSONObject(text)
                if (json.has("setupComplete") || json.has("setup_complete")) {
                    ready = true
                    webSocket.send(JSONObject().put("realtimeInput", JSONObject().put("activityStart", JSONObject())).toString())
                    synchronized(pending) { while (pending.isNotEmpty()) sendNow(webSocket, pending.removeFirst()) }
                    events.onReady()
                }
                val server = json.optJSONObject("serverContent") ?: json.optJSONObject("server_content")
                server?.let {
                    val interim = it.optJSONObject("interimInputTranscription") ?: it.optJSONObject("interim_input_transcription")
                    interim?.optString("text")?.takeIf(String::isNotBlank)?.let(events::onInterim)
                    val final = it.optJSONObject("inputTranscription") ?: it.optJSONObject("input_transcription")
                    final?.optString("text")?.takeIf(String::isNotBlank)?.let(events::onFinal)
                }
                json.optJSONObject("error")?.let { events.onError(it.optString("message", "Gemini Live error")) }
            }.onFailure { events.onError("Unexpected Gemini Live response") }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!closed) events.onError(response?.let { "Gemini Live rejected the connection (${it.code})" } ?: (t.message ?: "Gemini Live connection failed"))
        }

        fun sendAudio(bytes: ByteArray) {
            if (closed) return
            if (ready) sendNow(socket, bytes) else synchronized(pending) {
                pending.addLast(bytes.copyOf())
                while (pending.sumOf { it.size } > 3_200_000 && pending.isNotEmpty()) pending.removeFirst()
            }
        }

        fun finish() {
            if (!closed && ready) socket.send(JSONObject().put("realtimeInput", JSONObject().put("activityEnd", JSONObject())).toString())
        }

        fun close() {
            closed = true
            runCatching { socket.close(1000, "dictation complete") }
            synchronized(pending) { pending.clear() }
        }

        private fun sendNow(webSocket: WebSocket, bytes: ByteArray) {
            val audio = JSONObject().put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("mimeType", "audio/pcm;rate=16000")
            webSocket.send(JSONObject().put("realtimeInput", JSONObject().put("audio", audio)).toString())
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        private fun fromResponse(response: Response): GeminiException {
            val body = response.body?.string().orEmpty()
            val message = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
                ?: "Gemini request failed (${response.code})"
            val kind = when (response.code) {
                400, 401, 403 -> GeminiException.Kind.INVALID_KEY
                429 -> GeminiException.Kind.QUOTA
                in 500..599 -> GeminiException.Kind.SERVER
                else -> GeminiException.Kind.CONNECTION
            }
            return GeminiException(kind, message)
        }

        private fun extractText(json: JSONObject): String {
            json.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
            val outputs = json.optJSONArray("outputs")
            if (outputs != null) for (i in 0 until outputs.length()) {
                outputs.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
            }
            val steps = json.optJSONArray("steps")
            if (steps != null) for (i in steps.length() - 1 downTo 0) {
                val content = steps.optJSONObject(i)?.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) content.optJSONObject(j)?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return ""
        }
    }
}
