package com.hammaad.voiceappv4.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Packs in-memory 16-bit mono PCM into a WAV container for the batch fallback upload. */
object WavEncoder {
    fun encode16KhzMono(pcm: ByteArray): ByteArray = pcm16Mono(pcm, PcmAudioRecorder.SAMPLE_RATE)

    fun pcm16Mono(pcm: ByteArray, sampleRate: Int = PcmAudioRecorder.SAMPLE_RATE): ByteArray {
        require(sampleRate > 0)
        val output = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        output.put("RIFF".toByteArray())
        output.putInt(36 + pcm.size)
        output.put("WAVEfmt ".toByteArray())
        output.putInt(16)
        output.putShort(1)
        output.putShort(1)
        output.putInt(sampleRate)
        output.putInt(sampleRate * 2)
        output.putShort(2)
        output.putShort(16)
        output.put("data".toByteArray())
        output.putInt(pcm.size)
        output.put(pcm)
        return output.array()
    }
}
