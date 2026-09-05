package com.dictate.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCaptureAmplitudeTest {

    private fun pcmOf(samples: List<Short>): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            bytes[index * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[index * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Test
    fun `silence is zero amplitude`() {
        val pcm = pcmOf(List(100) { 0 })
        assertEquals(0f, AudioCapture.rmsAmplitude(pcm, pcm.size), 0.0001f)
    }

    @Test
    fun `full-scale samples produce amplitude close to 1`() {
        val pcm = pcmOf(List(100) { Short.MAX_VALUE })
        val amplitude = AudioCapture.rmsAmplitude(pcm, pcm.size)
        assertTrue("expected near-1 amplitude, got $amplitude", amplitude > 0.9f)
    }

    @Test
    fun `louder audio produces a larger amplitude than quieter audio`() {
        val quiet = pcmOf(List(100) { 1000 })
        val loud = pcmOf(List(100) { 20000 })
        val quietAmplitude = AudioCapture.rmsAmplitude(quiet, quiet.size)
        val loudAmplitude = AudioCapture.rmsAmplitude(loud, loud.size)
        assertTrue(loudAmplitude > quietAmplitude)
    }

    @Test
    fun `amplitude is always clamped to at most 1`() {
        val pcm = pcmOf(List(50) { Short.MIN_VALUE })
        val amplitude = AudioCapture.rmsAmplitude(pcm, pcm.size)
        assertTrue(amplitude <= 1f)
    }

    @Test
    fun `too-short buffer yields zero instead of crashing`() {
        val pcm = ByteArray(1)
        assertEquals(0f, AudioCapture.rmsAmplitude(pcm, pcm.size), 0.0001f)
    }
}
