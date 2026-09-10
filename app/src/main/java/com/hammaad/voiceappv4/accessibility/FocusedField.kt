package com.hammaad.voiceappv4.accessibility

/**
 * Deliberately contains no field text, hint, content description, or accessibility-tree data.
 * It is the only focus information exposed outside the accessibility package.
 */
data class FocusedField(
    val packageName: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

sealed interface InsertionOutcome {
    data object NoFocusedField : InsertionOutcome
    data object RejectedSensitiveField : InsertionOutcome
    data class Inserted(val cursor: Int) : InsertionOutcome
    data object Failed : InsertionOutcome
}
