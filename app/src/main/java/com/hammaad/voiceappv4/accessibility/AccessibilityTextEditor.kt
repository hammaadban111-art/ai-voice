package com.hammaad.voiceappv4.accessibility

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/** Performs an accessibility edit without retaining source-app text after this call returns. */
object AccessibilityTextEditor {
    fun insert(node: AccessibilityNodeInfo?, transcript: String): InsertionOutcome {
        if (node == null) return InsertionOutcome.NoFocusedField
        if (!node.isEditable || node.isPassword) return InsertionOutcome.RejectedSensitiveField

        // Text is read only to preserve the existing field around the cursor. It is never logged,
        // persisted, or passed to the network layer.
        val original = node.text?.toString() ?: ""
        val merged = TextInsertion.merge(
            existing = original,
            start = node.textSelectionStart,
            end = node.textSelectionEnd,
            transcript = transcript,
        )
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged.text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) return InsertionOutcome.Failed
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, merged.cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, merged.cursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        return InsertionOutcome.Inserted(merged.cursor)
    }
}
