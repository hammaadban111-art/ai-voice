package com.dictate.app.accessibility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Inserts dictated text at the current cursor position of a focused,
 * editable [AccessibilityNodeInfo], preserving the surrounding text and
 * moving the selection to just after the inserted text. Falls back to a
 * clipboard + paste gesture if the node does not support direct text
 * setting, and reports failure so the caller can offer a manual Paste
 * action rather than silently dropping a successful transcription.
 */
object TextInserter {

    fun insert(context: Context, node: AccessibilityNodeInfo, text: String): Boolean {
        return insertViaSetText(node, text) || insertViaClipboardPaste(context, node, text)
    }

    private fun insertViaSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable) return false
        val supportsSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        if (!supportsSetText) return false

        val existing = node.text?.toString().orEmpty()
        val selectionStart = node.textSelectionStart.takeIf { it >= 0 } ?: existing.length
        val selectionEnd = node.textSelectionEnd.takeIf { it >= 0 } ?: existing.length
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, existing.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, existing.length)

        val newText = existing.substring(0, start) + text + existing.substring(end)
        val newCursor = start + text.length

        val setTextArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        val setTextOk = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs)
        if (!setTextOk) return false

        val selectionArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
        return true
    }

    private fun insertViaClipboardPaste(context: Context, node: AccessibilityNodeInfo, text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val previousClip = clipboard.primaryClip
        clipboard.setPrimaryClip(ClipData.newPlainText("dictate", text))

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)

        // Restore whatever the user had copied before, best-effort.
        if (previousClip != null) {
            runCatching { clipboard.setPrimaryClip(previousClip) }
        }
        return pasted
    }

    fun isSensitive(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        val hints = listOfNotNull(
            node.hintText?.toString(),
            node.viewIdResourceName,
            node.text?.toString(),
        ).joinToString(" ").lowercase()
        return SENSITIVE_KEYWORDS.any { hints.contains(it) }
    }

    private val SENSITIVE_KEYWORDS = listOf(
        "password", "passcode", "pin", "otp", "cvv", "cvc", "card_number", "security_code",
    )
}
