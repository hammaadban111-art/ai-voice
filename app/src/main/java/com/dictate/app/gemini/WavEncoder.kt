package com.dictate.app.gemini

import com.dictate.app.core.AudioConfig
import java.io.ByteArrayOutputStream

/** Wraps raw 16-bit PCM samples in a minimal WAV (RIFF) container header. */
object WavEncoder {

    fun wrap(pcm: ByteArray, sampleRateHz: Int = AudioConfig.SAMPLE_RATE_HZ, channels: Int = AudioConfig.CHANNELS): ByteArray {
        val byteRate = sampleRateHz * channels * (AudioConfig.BITS_PER_SAMPLE / 8)
        val blockAlign = channels * (AudioConfig.BITS_PER_SAMPLE / 8)
        val out = ByteArrayOutputStream(44 + pcm.size)

        fun writeInt(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun writeShort(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }

        out.write("RIFF".toByteArray())
        writeInt(36 + pcm.size)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        writeInt(16)
        writeShort(1) // PCM
        writeShort(channels)
        writeInt(sampleRateHz)
        writeInt(byteRate)
        writeShort(blockAlign)
        writeShort(AudioConfig.BITS_PER_SAMPLE)
        out.write("data".toByteArray())
        writeInt(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}
