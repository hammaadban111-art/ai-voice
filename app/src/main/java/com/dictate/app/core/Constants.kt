package com.dictate.app.core

object GeminiModels {
    const val LIVE_TRANSCRIBE = "gemini-3.5-transcribe-live"
    const val REST_TRANSCRIBE = "gemini-3.5-transcribe"
}

object AudioConfig {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
}

enum class TranscriptionMode { SMART, VERBATIM }

enum class LanguageMode { AUTO, MANUAL }
