package com.dictate.app.overlay

sealed interface DictationState {
    data object Hidden : DictationState
    data object Ready : DictationState
    data object Connecting : DictationState
    data class Recording(val partialText: String = "") : DictationState
    data object Finalizing : DictationState
    data object Inserting : DictationState
    data class Success(val insertedText: String) : DictationState
    data class Error(val message: String, val fallbackText: String? = null) : DictationState
}
