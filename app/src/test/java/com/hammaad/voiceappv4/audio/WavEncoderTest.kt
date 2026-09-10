package com.hammaad.voiceappv4.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavEncoderTest {
    @Test fun `wraps PCM in a valid mono sixteen kilohertz wave header`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = WavEncoder.pcm16Mono(pcm)
        assertArrayEquals("RIFF".toByteArray(), wav.copyOfRange(0, 4))
        assertArrayEquals("WAVE".toByteArray(), wav.copyOfRange(8, 12))
        assertEquals(16_000, ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }
}
