package com.dictate.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dictate.app.asDictateApp
import com.dictate.app.core.DictateLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Detects the currently focused editable field across apps (for showing
 * the dictation bubble) and performs the actual text insertion once a
 * transcript is ready. Never reads or transmits the contents of the
 * screen beyond the single focused field needed for insertion.
 *
 * The bubble must be HIDDEN by default and only appear for a real,
 * non-sensitive editable field in another app — never over Dictate's own
 * screens, never on the launcher, and never left stuck on after focus or
 * the keyboard goes away. See [refreshFocusedField] for how each of those
 * is guaranteed rather than merely hoped for.
 */
class DictationAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var excludedApps: Set<String> = emptySet()
    private var currentFocusedNode: AccessibilityNodeInfo? = null
    private var pendingHideJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope.launch {
            application.asDictateApp().settingsRepository.settings.collect { settings ->
                excludedApps = settings.excludedApps
            }
        }
        // A field may already be focused (e.g. the service was just enabled
        // while the keyboard was already up) — don't wait for the next event.
        refreshFocusedField(checkKeyboardClosed = false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            -> refreshFocusedField(checkKeyboardClosed = false)
            AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                // The IME window appearing/disappearing doesn't necessarily
                // touch the focused app's own window, so it needs its own
                // trigger — this is specifically what catches "the keyboard
                // was dismissed but the field is technically still focused".
                // Only *this* event type lets a missing IME window override
                // an otherwise-editable field: requiring it everywhere would
                // also hide the bubble for hardware-keyboard typing, where
                // no IME window ever appears.
                refreshFocusedField(checkKeyboardClosed = true)
            else -> Unit
        }
    }

    private fun refreshFocusedField(checkKeyboardClosed: Boolean) {
        val root = rootInActiveWindow
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        currentFocusedNode = focused

        val focusedPackageName = root?.packageName?.toString()
        val isOwnApp = focusedPackageName == packageName // `packageName`: Service's own app, any build variant
        val isExcluded = isOwnApp || (focusedPackageName != null && focusedPackageName in excludedApps)
        val isEditable = focused?.isEditable == true
        val isSensitive = focused != null && TextInserter.isSensitive(focused)
        val keyboardJustClosed = checkKeyboardClosed && !isImeWindowPresent()

        val shouldShow = isEditable && !isExcluded && !isSensitive && !keyboardJustClosed
        DictateLog.d(
            "focus: pkg=$focusedPackageName editable=$isEditable excluded=$isExcluded " +
                "sensitive=$isSensitive keyboardJustClosed=$keyboardJustClosed -> show=$shouldShow",
        )
        applyVisibility(shouldShow, focusedPackageName, isSensitive)
    }

    private fun isImeWindowPresent(): Boolean = runCatching {
        windows?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
    }.getOrDefault(true) // if we can't tell, don't let a false negative hide a legitimately focused field

    /**
     * Shows immediately (no reason to delay something the user is looking
     * at right now) but debounces hides briefly so a field-to-field focus
     * hop, or a transient window-list blip, doesn't flicker the bubble off
     * and back on.
     */
    private fun applyVisibility(shouldShow: Boolean, packageName: String?, isSensitive: Boolean) {
        if (shouldShow) {
            pendingHideJob?.cancel()
            pendingHideJob = null
            FieldFocusTracker.update(FocusedFieldState(true, packageName, isSensitive))
            return
        }
        if (!FieldFocusTracker.state.value.editableFieldActive) return
        pendingHideJob?.cancel()
        pendingHideJob = scope.launch {
            delay(HIDE_DEBOUNCE_MS)
            FieldFocusTracker.update(FocusedFieldState(false, packageName, isSensitive))
        }
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
        pendingHideJob?.cancel()
        scope.cancel()
        FieldFocusTracker.update(FocusedFieldState())
    }

    companion object {
        private const val HIDE_DEBOUNCE_MS = 180L

        /**
         * Live reference used only to route text insertion to a running
         * service instance. Never used as a status signal — whether
         * accessibility is "enabled" is answered exclusively by
         * [com.dictate.app.core.PermissionState.isAccessibilityServiceEnabled],
         * which asks Android directly instead of trusting in-process state
         * that can lag behind what the user just changed in Settings.
         */
        var instance: DictationAccessibilityService? = null
            private set
    }
}
