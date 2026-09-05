package com.dictate.app.gemini

sealed interface TranscriptionEvent {
    data class Partial(val text: String) : TranscriptionEvent
    data class Final(val text: String) : TranscriptionEvent
    data class Error(val message: String, val recoverable: Boolean) : TranscriptionEvent
    data object Connected : TranscriptionEvent
    data object Closed : TranscriptionEvent
}

data class TranscriptionRequest(
    val apiKey: String,
    val mode: com.dictate.app.core.TranscriptionMode,
    val languageCodes: List<String>,
    val customVocabulary: List<String>,
    val liveModel: String,
    val restModel: String,
)
