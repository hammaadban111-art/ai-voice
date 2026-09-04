package com.aivoice.flow.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.aivoice.flow.whisper.WhisperEngine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Captures 16 kHz mono PCM straight into the float buffer whisper wants.
 *
 * Recording runs on its own thread and appends into a growing chunk list, so
 * [stop] can hand back the whole utterance without an extra file round-trip.
 */
class AudioRecorder(private val onLevel: (Float) -> Unit = {}) {

    private val recording = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var record: AudioRecord? = null
    private val chunks = ArrayList<ShortArray>()
    private var totalSamples = 0

    val isRecording: Boolean get() = recording.get()

    /** @return false if the microphone could not be opened. */
    @SuppressLint("MissingPermission") // callers gate on RECORD_AUDIO
    fun start(): Boolean {
        if (recording.get()) return true

        val minBuffer = AudioRecord.getMinBufferSize(
            WhisperEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "unsupported capture configuration")
            return false
        }
        val bufferSize = maxOf(minBuffer * 2, WhisperEngine.SAMPLE_RATE) // ~0.5 s of headroom

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                WhisperEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord construction failed", e)
            return false
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized (permission or mic busy)")
            audioRecord.release()
            return false
        }

        synchronized(chunks) {
            chunks.clear()
            totalSamples = 0
        }
        record = audioRecord
        recording.set(true)
        audioRecord.startRecording()

        recordThread = thread(name = "aivoice-capture", priority = Thread.MAX_PRIORITY) {
            val buffer = ShortArray(READ_SAMPLES)
            while (recording.get()) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                synchronized(chunks) {
                    // Hard cap the utterance so a forgotten recording can't
                    // grow until the process is killed.
                    if (totalSamples < MAX_SAMPLES) {
                        chunks.add(buffer.copyOf(read))
                        totalSamples += read
                    }
                }
                onLevel(peak(buffer, read))
            }
        }
        return true
    }

    /** Stops capture and returns the utterance as normalised mono floats. */
    fun stop(): FloatArray {
        if (!recording.compareAndSet(true, false)) return FloatArray(0)

        recordThread?.join(1_000)
        recordThread = null
        record?.let { r ->
            try {
                r.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "stop() on an already-stopped recorder", e)
            }
            r.release()
        }
        record = null

        synchronized(chunks) {
            val out = FloatArray(totalSamples)
            var offset = 0
            for (chunk in chunks) {
                for (sample in chunk) {
                    out[offset++] = sample / 32768f
                }
            }
            chunks.clear()
            totalSamples = 0
            return out
        }
    }

    /** Aborts capture and throws the audio away. */
    fun cancel() {
        stop()
    }

    private fun peak(buffer: ShortArray, size: Int): Float {
        var max = 0
        for (i in 0 until size) {
            val v = abs(buffer[i].toInt())
            if (v > max) max = v
        }
        return max / 32768f
    }

    companion object {
        private const val TAG = "AudioRecorder"
        private const val READ_SAMPLES = 1600 // 100 ms
        private const val MAX_SECONDS = 120
        private const val MAX_SAMPLES = WhisperEngine.SAMPLE_RATE * MAX_SECONDS
    }
}
