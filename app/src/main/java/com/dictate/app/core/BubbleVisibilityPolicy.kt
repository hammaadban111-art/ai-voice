package com.dictate.app.core

/**
 * Pure decision logic for whether the overlay should show anything at
 * all, and if so, in which shape. Kept framework-free (no Context, no
 * AccessibilityNodeInfo) so the actual policy — not the Android plumbing
 * that feeds it — is what gets unit-tested.
 *
 * The "Bubble enabled" setting means "allow the contextual bubble
 * system", not "show the bubble permanently": [shouldShowAnything] only
 * returns true when a session is already running (so mid-recording UI
 * never vanishes out from under the user) or a real, non-excluded,
 * non-sensitive editable field is currently focused.
 */
object BubbleVisibilityPolicy {

    fun shouldShowAnything(
        sessionActive: Boolean,
        bubbleFeatureEnabled: Boolean,
        snoozed: Boolean,
        editableFieldActive: Boolean,
    ): Boolean = sessionActive || (bubbleFeatureEnabled && !snoozed && editableFieldActive)

    /** Once something is shown, is it the compact pill (mid-session) or the small bubble? */
    fun shouldShowAsPill(sessionActive: Boolean): Boolean = sessionActive
}
