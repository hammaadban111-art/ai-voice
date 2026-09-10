package com.hammaad.voiceappv4.gemini

import com.hammaad.voiceappv4.data.TranscriptionMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiProtocolTest {
    @Test fun `live setup uses required model PCM transcription and smart mode`() {
        val setup = JSONObject(GeminiProtocol.liveSetup(GeminiTranscriptionOptions(
            mode = TranscriptionMode.SMART, languageCode = "en-IN", customVocabulary = listOf("OxygenOS"),
        ))).getJSONObject("setup")
        assertEquals("models/gemini-3.5-transcribe-live", setup.getString("model"))
        assertEquals("TEXT", setup.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0))
        assertEquals("SMART", setup.getJSONObject("inputAudioTranscription").getString("mode"))
        assertEquals("en-IN", setup.getJSONObject("inputAudioTranscription").getJSONArray("languageCodes").getString(0))
    }

    @Test fun `audio message is live compatible and has end signal`() {
        val audio = JSONObject(GeminiProtocol.audioChunk(byteArrayOf(1, 2))).getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", audio.getString("mimeType"))
        assertFalse(audio.getString("data").isBlank())
        assertTrue(JSONObject(GeminiProtocol.endAudio()).getJSONObject("realtimeInput").getBoolean("audioStreamEnd"))
    }

    @Test fun `http failures become safe product errors`() {
        assertEquals(GeminiFailure.InvalidApiKey, GeminiErrorMapper.fromHttp(401))
        assertEquals(GeminiFailure.QuotaExceeded, GeminiErrorMapper.fromHttp(429))
        assertEquals(GeminiFailure.ServiceUnavailable, GeminiErrorMapper.fromHttp(503))
    }
}
