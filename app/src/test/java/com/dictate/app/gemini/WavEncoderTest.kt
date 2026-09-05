package com.dictate.app.gemini

import org.junit.Assert.assertEquals
import org.junit.Test

class WavEncoderTest {

    @Test
    fun `wraps pcm with a valid 44-byte RIFF header`() {
        val pcm = ByteArray(320) { it.toByte() }
        val wav = WavEncoder.wrap(pcm, sampleRateHz = 16_000, channels = 1)

        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

        val dataSize = (wav[40].toInt() and 0xFF) or
            ((wav[41].toInt() and 0xFF) shl 8) or
            ((wav[42].toInt() and 0xFF) shl 16) or
            ((wav[43].toInt() and 0xFF) shl 24)
        assertEquals(pcm.size, dataSize)
    }
}
