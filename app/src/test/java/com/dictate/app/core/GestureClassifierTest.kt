package com.dictate.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureClassifierTest {

    private val slop = 10

    @Test
    fun `short tap without movement is a Tap`() {
        val classifier = GestureClassifier(slop)
        classifier.onDown(100f, 100f)
        assertEquals(GestureAction.Tap, classifier.onUp())
    }

    @Test
    fun `movement within slop is still a Tap`() {
        val classifier = GestureClassifier(slop)
        classifier.onDown(100f, 100f)
        assertEquals(GestureAction.Ignored, classifier.onMove(105f, 103f))
        assertEquals(GestureAction.Tap, classifier.onUp())
    }

    @Test
    fun `movement beyond slop starts a drag and never resolves as tap`() {
        val classifier = GestureClassifier(slop)
        classifier.onDown(100f, 100f)
        assertEquals(GestureAction.DragStarted, classifier.onMove(130f, 100f))
        assertTrue(classifier.isDragging)
        assertEquals(GestureAction.DragEnded, classifier.onUp())
    }

    @Test
    fun `drag reports incremental deltas after starting`() {
        val classifier = GestureClassifier(slop)
        classifier.onDown(100f, 100f)
        classifier.onMove(130f, 100f) // crosses slop, DragStarted
        val moved = classifier.onMove(140f, 105f)
        assertEquals(GestureAction.DragMoved(10, 5), moved)
    }

    @Test
    fun `a drag in progress blocks long-press confirmation`() {
        val classifier = GestureClassifier(slop)
        classifier.onDown(100f, 100f)
        assertTrue(classifier.canConfirmLongPress())
        classifier.onMove(130f, 100f)
        assertFalse(classifier.canConfirmLongPress())
    }

    @Test
    fun `confirmed long-press resolves as LongPressEnded on release`() {
        val classifier = GestureClassifier(slop)
        classifier.onDown(100f, 100f)
        assertTrue(classifier.canConfirmLongPress())
        classifier.confirmLongPress()
        assertEquals(GestureAction.LongPressEnded, classifier.onUp())
    }

    @Test
    fun `a new down resets drag and long-press state`() {
        val classifier = GestureClassifier(slop)
        classifier.onDown(100f, 100f)
        classifier.onMove(200f, 200f)
        classifier.onUp()
        classifier.onDown(50f, 50f)
        assertFalse(classifier.isDragging)
        assertFalse(classifier.isLongPress)
        assertEquals(GestureAction.Tap, classifier.onUp())
    }
}
