package com.dictate.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleVisibilityPolicyTest {

    @Test
    fun `hidden by default when nothing is focused and no session is running`() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShowAnything(
                sessionActive = false,
                bubbleFeatureEnabled = true,
                snoozed = false,
                editableFieldActive = false,
            ),
        )
    }

    @Test
    fun `shows when a real editable field is focused`() {
        assertTrue(
            BubbleVisibilityPolicy.shouldShowAnything(
                sessionActive = false,
                bubbleFeatureEnabled = true,
                snoozed = false,
                editableFieldActive = true,
            ),
        )
    }

    @Test
    fun `bubble feature disabled means never shown even with a focused field`() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShowAnything(
                sessionActive = false,
                bubbleFeatureEnabled = false,
                snoozed = false,
                editableFieldActive = true,
            ),
        )
    }

    @Test
    fun `snoozed suppresses the contextual bubble even with a focused field`() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShowAnything(
                sessionActive = false,
                bubbleFeatureEnabled = true,
                snoozed = true,
                editableFieldActive = true,
            ),
        )
    }

    @Test
    fun `a password or excluded field is represented as editableFieldActive=false and hides the bubble`() {
        // Sensitivity/exclusion filtering happens upstream (accessibility service);
        // this policy just has to correctly treat that as "not focused".
        assertFalse(
            BubbleVisibilityPolicy.shouldShowAnything(
                sessionActive = false,
                bubbleFeatureEnabled = true,
                snoozed = false,
                editableFieldActive = false,
            ),
        )
    }

    @Test
    fun `losing focus or the keyboard hides it (editableFieldActive flips false)`() {
        val whileFocused = BubbleVisibilityPolicy.shouldShowAnything(false, true, false, true)
        val afterFocusLost = BubbleVisibilityPolicy.shouldShowAnything(false, true, false, false)
        assertTrue(whileFocused)
        assertFalse(afterFocusLost)
    }

    @Test
    fun `an active session stays visible even if focus momentarily drops`() {
        assertTrue(
            BubbleVisibilityPolicy.shouldShowAnything(
                sessionActive = true,
                bubbleFeatureEnabled = true,
                snoozed = false,
                editableFieldActive = false,
            ),
        )
    }

    @Test
    fun `shape is the pill only while a session is active`() {
        assertTrue(BubbleVisibilityPolicy.shouldShowAsPill(sessionActive = true))
        assertFalse(BubbleVisibilityPolicy.shouldShowAsPill(sessionActive = false))
    }
}
