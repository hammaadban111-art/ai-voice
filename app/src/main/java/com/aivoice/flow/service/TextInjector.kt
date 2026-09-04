package com.aivoice.flow.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Writes transcribed text into whatever field currently has input focus.
 *
 * Two strategies, in order:
 *  1. `ACTION_SET_TEXT` on the focused editable node, rebuilding its contents
 *     around the caret so existing text and the cursor position survive.
 *  2. Clipboard + `ACTION_PASTE`, which is what WebViews and some custom
 *     editors accept when they refuse `ACTION_SET_TEXT`.
 *
 * If neither works the text is left on the clipboard so it is never lost.
 */
object TextInjector {

    private const val TAG = "TextInjector"
    private const val CLIP_LABEL = "AI Voice dictation"

    enum class Outcome { INSERTED, PASTED, CLIPBOARD_ONLY }

    fun inject(service: AccessibilityService, text: String): Outcome {
        if (text.isBlank()) return Outcome.CLIPBOARD_ONLY

        val node = focusedEditable(service)
        if (node != null && setTextOnNode(node, text)) return Outcome.INSERTED

        // Everything below needs the text on the clipboard anyway.
        copyToClipboard(service, text)

        if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return Outcome.PASTED
        }
        return Outcome.CLIPBOARD_ONLY
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }

    /** True when there is somewhere to type right now. */
    fun hasEditableTarget(service: AccessibilityService): Boolean {
        return focusedEditable(service) != null
    }

    private fun focusedEditable(service: AccessibilityService): AccessibilityNodeInfo? {
        val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return null

        if (focused.isEditable) return focused

        // Some apps report focus on a wrapper; the editable node is a child.
        return firstEditableDescendant(focused, depth = 3)
    }

    private fun firstEditableDescendant(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth <= 0) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isEditable) return child
            val match = firstEditableDescendant(child, depth - 1)
            if (match != null) return match
        }
        return null
    }

    private fun setTextOnNode(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable) return false

        val existing = node.text?.toString().orEmpty()
        // A node with no reported selection gets the text appended at the end.
        val rawStart = node.textSelectionStart
        val rawEnd = node.textSelectionEnd
        val start = (if (rawStart < 0) existing.length else rawStart).coerceIn(0, existing.length)
        val end = (if (rawEnd < 0) existing.length else rawEnd).coerceIn(start, existing.length)

        val insertion = spacedInsertion(existing, start, end, text)
        val updated = existing.substring(0, start) + insertion + existing.substring(end)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Log.d(TAG, "ACTION_SET_TEXT refused, falling back to paste")
            return false
        }

        // Put the caret after what we just inserted so dictation can continue.
        val caret = start + insertion.length
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        return true
    }

    /**
     * Adds the space a human would type: one before the insertion when it
     * continues a sentence, and none when the field is empty, the caret sits
     * after whitespace, or the transcription already starts with punctuation.
     */
    private fun spacedInsertion(existing: String, start: Int, end: Int, text: String): String {
        if (start == 0) return text
        if (start != end) return text // replacing a selection: insert verbatim
        val before = existing[start - 1]
        if (before.isWhitespace()) return text
        if (text.firstOrNull()?.let { it in ".,!?;:" } == true) return text
        return " $text"
    }
}
