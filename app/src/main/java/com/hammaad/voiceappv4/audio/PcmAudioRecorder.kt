package com.hammaad.voiceappv4.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class PcmAudioRecorder(private val context: Context) {
    data class Recording(val pcm: ByteArray, val durationMs: Long)

    private val running = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private var buffer = ByteArrayOutputStream()
    private var startedAt = 0L

    fun start(onChunk: (ByteArray) -> Unit, onLevel: (Float) -> Unit, onError: (String) -> Unit): Boolean {
        if (running.get()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission is missing")
            return false
        }
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minimum <= 0) { onError("This device could not create a 16 kHz microphone buffer"); return false }
        val audio = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING, maxOf(minimum * 2, 4096))
        }.getOrElse { onError("Microphone could not start: ${it.message.orEmpty()}"); return false }
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release(); onError("Microphone is unavailable or already in use"); return false
        }
        buffer = ByteArrayOutputStream()
        recorder = audio
        running.set(true)
        startedAt = System.currentTimeMillis()
        runCatching { audio.startRecording() }.onFailure {
            running.set(false); audio.release(); recorder = null; onError("Microphone could not start: ${it.message.orEmpty()}")
            return false
        }
        thread = Thread({ captureLoop(audio, maxOf(minimum, 2048), onChunk, onLevel, onError) }, "voice-pcm-capture").apply { start() }
        return true
    }

    @Synchronized
    fun stop(): Recording {
        running.set(false)
        runCatching { recorder?.stop() }
        thread?.join(700)
        recorder?.release()
        recorder = null
        thread = null
        return Recording(buffer.toByteArray(), (System.currentTimeMillis() - startedAt).coerceAtLeast(0))
    }

    fun cancel() {
        stop()
        buffer.reset()
    }

    private fun captureLoop(audio: AudioRecord, size: Int, onChunk: (ByteArray) -> Unit, onLevel: (Float) -> Unit, onError: (String) -> Unit) {
        val chunk = ByteArray(size)
        try {
            while (running.get() && buffer.size() < MAX_PCM_BYTES) {
                val count = audio.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    val data = chunk.copyOf(count)
                    synchronized(this) { buffer.write(data) }
                    onChunk(data)
                    onLevel(rms(data))
                } else if (count < 0) {
                    onError("Microphone read failed ($count)")
                    running.set(false)
                }
            }
            if (buffer.size() >= MAX_PCM_BYTES) onError("Recording stopped at the 10 minute safety limit")
        } catch (t: Throwable) {
            if (running.get()) onError("Microphone interrupted: ${t.message.orEmpty()}")
        }
    }

    private fun rms(bytes: ByteArray): Float {
        if (bytes.size < 2) return 0f
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < bytes.size) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
            sum += sample.toDouble() * sample
            samples++
            i += 2
        }
        return (sqrt(sum / samples) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_PCM_BYTES = SAMPLE_RATE * 2 * 60 * 10
    }
}

