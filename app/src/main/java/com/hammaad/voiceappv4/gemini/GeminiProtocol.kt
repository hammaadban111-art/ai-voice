package com.hammaad.voiceappv4.gemini

import java.util.Base64
import com.hammaad.voiceappv4.data.TranscriptionMode
import org.json.JSONArray
import org.json.JSONObject

data class GeminiTranscriptionOptions(
    val mode: TranscriptionMode,
    val languageCode: String = "",
    val customVocabulary: List<String> = emptyList(),
)

sealed interface GeminiFailure {
    data object MissingApiKey : GeminiFailure
    data object InvalidApiKey : GeminiFailure
    data object PermissionDenied : GeminiFailure
    data object QuotaExceeded : GeminiFailure
    data object Offline : GeminiFailure
    data object ConnectionLost : GeminiFailure
    data object ServiceUnavailable : GeminiFailure
    data object MalformedResponse : GeminiFailure
    data class Unknown(val message: String) : GeminiFailure
}

sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data class Failure(val error: GeminiFailure) : GeminiResult
}

/** Current v1beta Gemini Live / Interactions JSON shapes, kept independently unit-testable. */
object GeminiProtocol {
    const val LIVE_MODEL = "gemini-3.5-transcribe-live"
    const val BATCH_MODEL = "gemini-3.5-transcribe"
    const val LIVE_ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val INTERACTIONS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
    const val UPLOAD_ENDPOINT = "https://generativelanguage.googleapis.com/upload/v1beta/files"

    fun liveSetup(options: GeminiTranscriptionOptions): String = JSONObject().apply {
        put("setup", JSONObject().apply {
            put("model", "models/$LIVE_MODEL")
            put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT")))
            put("inputAudioTranscription", audioConfig(options))
        })
    }.toString()

    fun audioChunk(pcm: ByteArray): String = JSONObject().apply {
        put("realtimeInput", JSONObject().put("audio", JSONObject().apply {
            put("data", Base64.getEncoder().encodeToString(pcm))
            put("mimeType", "audio/pcm;rate=16000")
        }))
    }.toString()

    fun endAudio(): String = JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString()

    fun batchInteraction(fileUri: String, options: GeminiTranscriptionOptions): String = JSONObject().apply {
        put("model", BATCH_MODEL)
        put("input", JSONArray().put(JSONObject().apply {
            put("type", "audio")
            put("uri", fileUri)
            put("mime_type", "audio/wav")
        }))
        put("generation_config", JSONObject().put("transcription_config", batchAudioConfig(options)))
    }.toString()

    private fun audioConfig(options: GeminiTranscriptionOptions) = JSONObject().apply {
        put("languageCodes", JSONArray().apply { if (options.languageCode.isNotBlank()) put(options.languageCode) })
        put("customVocabulary", JSONArray(options.customVocabulary.filter { it.isNotBlank() }.take(1_000)))
        put("mode", options.mode.name)
    }

    private fun batchAudioConfig(options: GeminiTranscriptionOptions) = JSONObject().apply {
        put("language_codes", JSONArray().apply { if (options.languageCode.isNotBlank()) put(options.languageCode) })
        put("custom_vocabulary", JSONArray(options.customVocabulary.filter { it.isNotBlank() }.take(1_000)))
        put("mode", options.mode.name.lowercase())
    }
}

object GeminiErrorMapper {
    fun fromHttp(code: Int, body: String = ""): GeminiFailure = when (code) {
        400 -> GeminiFailure.Unknown(body.ifBlank { "Gemini rejected the request." })
        401 -> GeminiFailure.InvalidApiKey
        403 -> if (body.contains("API key", ignoreCase = true)) GeminiFailure.InvalidApiKey else GeminiFailure.PermissionDenied
        429 -> GeminiFailure.QuotaExceeded
        500, 502, 503, 504 -> GeminiFailure.ServiceUnavailable
        else -> GeminiFailure.Unknown(body.ifBlank { "Gemini request failed ($code)." })
    }

    fun fromThrowable(error: Throwable): GeminiFailure = when (error) {
        is java.net.UnknownHostException, is java.net.NoRouteToHostException -> GeminiFailure.Offline
        is java.net.SocketTimeoutException, is java.net.SocketException -> GeminiFailure.ConnectionLost
        else -> GeminiFailure.Unknown(error.message ?: "Unexpected network failure.")
    }
}
