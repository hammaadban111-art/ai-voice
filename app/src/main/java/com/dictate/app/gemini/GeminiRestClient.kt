package com.dictate.app.gemini

import android.util.Base64
import com.dictate.app.core.TranscriptionMode
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
private const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"

/**
 * Fallback, non-streaming transcription used only when a live session fails
 * after audio has already been captured, so the recording is never lost.
 */
class GeminiRestClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Lightweight key validation used by the Settings "Test connection" action. */
    fun testConnection(apiKey: String): Result<Unit> = runCatching {
        val httpRequest = Request.Builder()
            .url(MODELS_URL)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gemini rejected the API key: HTTP ${response.code}")
            }
        }
    }

    fun transcribe(pcm: ByteArray, request: TranscriptionRequest): Result<String> {
        return runCatching {
            val wav = WavEncoder.wrap(pcm)
            val audioBase64 = Base64.encodeToString(wav, Base64.NO_WRAP)

            val input = JSONArray().put(
                JSONObject()
                    .put("type", "audio")
                    .put("data", audioBase64)
                    .put("mime_type", "audio/wav"),
            )
            val transcriptionConfig = JSONObject()
                .put("language_codes", JSONArray(request.languageCodes))
                .put("custom_vocabulary", JSONArray(request.customVocabulary))
                .put("mode", if (request.mode == TranscriptionMode.SMART) "smart" else "verbatim")

            val body = JSONObject()
                .put("model", request.restModel)
                .put("input", input)
                .put("generation_config", JSONObject().put("transcription_config", transcriptionConfig))
                .toString()
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(INTERACTIONS_URL)
                .addHeader("x-goog-api-key", request.apiKey)
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Gemini transcribe failed: HTTP ${response.code}")
                }
                val json = JSONObject(bodyText)
                json.optString("output_text").takeIf { it.isNotEmpty() }
                    ?: extractFromSteps(json)
                    ?: throw IOException("Gemini transcribe returned no text")
            }
        }
    }

    private fun extractFromSteps(json: JSONObject): String? {
        val steps = json.optJSONArray("steps") ?: return null
        val builder = StringBuilder()
        for (i in 0 until steps.length()) {
            val content = steps.getJSONObject(i).optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                content.getJSONObject(j).optString("text").takeIf { it.isNotEmpty() }?.let(builder::append)
            }
        }
        return builder.toString().ifEmpty { null }
    }
}
