package com.dictate.app.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FocusedFieldState(
    val editableFieldActive: Boolean = false,
    val packageName: String? = null,
    val isSensitive: Boolean = false,
)

/**
 * Shared, in-process signal from [DictationAccessibilityService] to the
 * overlay bubble: is an editable, non-sensitive field currently focused?
 * The accessibility tree contents themselves are never read into this —
 * only these three booleans/strings ever cross the boundary.
 */
object FieldFocusTracker {
    private val _state = MutableStateFlow(FocusedFieldState())
    val state: StateFlow<FocusedFieldState> = _state

    fun update(state: FocusedFieldState) {
        _state.value = state
    }
}
