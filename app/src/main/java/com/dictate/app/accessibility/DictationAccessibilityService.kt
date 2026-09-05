package com.dictate.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dictate.app.asDictateApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Detects the currently focused editable field across apps (for showing
 * the dictation bubble) and performs the actual text insertion once a
 * transcript is ready. Never reads or transmits the contents of the
 * screen beyond the single focused field needed for insertion.
 */
class DictationAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var excludedApps: Set<String> = emptySet()
    private var currentFocusedNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope.launch {
            application.asDictateApp().settingsRepository.settings.collect { settings ->
                excludedApps = settings.excludedApps
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> refreshFocusedField()
            else -> Unit
        }
    }

    private fun refreshFocusedField() {
        val root = rootInActiveWindow
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        currentFocusedNode = focused

        val packageName = root?.packageName?.toString()
        val isExcluded = packageName != null && packageName in excludedApps
        val isEditable = focused?.isEditable == true
        val isSensitive = focused != null && TextInserter.isSensitive(focused)

        FieldFocusTracker.update(
            FocusedFieldState(
                editableFieldActive = isEditable && !isExcluded && !isSensitive,
                packageName = packageName,
                isSensitive = isSensitive,
            ),
        )
    }

    /** Inserts [text] into whatever field was focused when dictation started. Returns success. */
    fun insertTranscript(text: String): Boolean {
        val node = currentFocusedNode ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null || !node.isEditable) return false
        return TextInserter.insert(this, node, text)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
        FieldFocusTracker.update(FocusedFieldState())
    }

    companion object {
        var instance: DictationAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }
}
