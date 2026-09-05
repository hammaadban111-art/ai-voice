package com.dictate.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStatusTest {

    private val allGood = SetupStatus(
        microphoneGranted = true,
        accessibilityEnabled = true,
        overlayGranted = true,
        apiKeyPresent = true,
        bubbleFeatureEnabled = true,
    )

    @Test
    fun `complete only when every required item is true`() {
        assertTrue(allGood.requiredComplete)
    }

    @Test
    fun `missing api key blocks completion even if everything else is granted`() {
        assertFalse(allGood.copy(apiKeyPresent = false).requiredComplete)
    }

    @Test
    fun `missing microphone permission blocks completion`() {
        assertFalse(allGood.copy(microphoneGranted = false).requiredComplete)
    }

    @Test
    fun `missing accessibility blocks completion`() {
        assertFalse(allGood.copy(accessibilityEnabled = false).requiredComplete)
    }

    @Test
    fun `missing overlay permission blocks completion`() {
        assertFalse(allGood.copy(overlayGranted = false).requiredComplete)
    }

    @Test
    fun `bubble feature disabled blocks completion`() {
        assertFalse(allGood.copy(bubbleFeatureEnabled = false).requiredComplete)
    }

    @Test
    fun `test dictation success is not part of required completion`() {
        // SetupStatus has no field for "test dictation succeeded" at all —
        // completing setup must never depend on it.
        assertTrue(allGood.requiredComplete)
    }
}
