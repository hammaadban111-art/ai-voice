package com.dictate.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks whether a dictation has actually completed successfully (audio
 * captured, transcribed, and inserted) since the app was installed. Backs
 * the onboarding "Test dictation" checklist item, which must only be
 * checked off after a real success — never assumed from earlier steps.
 */
object DictationTestState {
    private val _succeeded = MutableStateFlow(false)
    val succeeded: StateFlow<Boolean> = _succeeded

    fun markSuccess() {
        _succeeded.value = true
    }
}
