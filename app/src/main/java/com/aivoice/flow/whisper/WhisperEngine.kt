package com.aivoice.flow.whisper

import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the native whisper context.
 *
 * Loading the weights takes a second or two, so the dictation service creates
 * one engine when it starts and keeps it alive for the whole session; each
 * utterance then only costs the encode/decode passes.
 */
class WhisperEngine private constructor(ptr: Long) : AutoCloseable {

    private val contextPtr = AtomicLong(ptr)

    val isClosed: Boolean get() = contextPtr.get() == 0L

    /**
     * Transcribes 16 kHz mono audio.
     *
     * @param language whisper language code, or [LANGUAGE_AUTO].
     * @param prompt optional priming text that nudges spelling/terminology.
     */
    @Synchronized
    fun transcribe(
        samples: FloatArray,
        language: String = LANGUAGE_AUTO,
        prompt: String = "",
    ): Result {
        val ptr = contextPtr.get()
        if (ptr == 0L) return Result("", "")

        // whisper works on 30 s windows and misbehaves on very short buffers,
        // so pad anything under a second with silence.
        val padded = if (samples.size < MIN_SAMPLES) {
            samples.copyOf(MIN_SAMPLES)
        } else {
            samples
        }

        val started = System.currentTimeMillis()
        val text = WhisperNative.transcribe(ptr, padded, threadCount(), language, prompt)
        val detected = WhisperNative.detectedLanguage(ptr)
        Log.i(
            TAG,
            "transcribed ${padded.size / SAMPLE_RATE.toFloat()}s in " +
                "${System.currentTimeMillis() - started}ms (lang=$detected)",
        )
        return Result(text, detected)
    }

    @Synchronized
    override fun close() {
        val ptr = contextPtr.getAndSet(0L)
        if (ptr != 0L) WhisperNative.freeContext(ptr)
    }

    data class Result(val text: String, val detectedLanguage: String)

    companion object {
        private const val TAG = "WhisperEngine"

        const val SAMPLE_RATE = 16_000
        const val LANGUAGE_AUTO = "auto"
        private const val MIN_SAMPLES = SAMPLE_RATE

        /**
         * Big cores only. Spilling onto the little cores of a phone SoC makes
         * whisper slower, not faster, because every matmul waits on the
         * slowest thread in the pool.
         */
        fun threadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return (cores - 2).coerceIn(2, 6)
        }

        fun load(modelFile: File): WhisperEngine? {
            if (!modelFile.exists()) {
                Log.e(TAG, "model missing at ${modelFile.absolutePath}")
                return null
            }
            val ptr = WhisperNative.initContext(modelFile.absolutePath)
            if (ptr == 0L) {
                Log.e(TAG, "whisper_init failed for ${modelFile.absolutePath}")
                return null
            }
            Log.i(TAG, "whisper system info: ${WhisperNative.systemInfo()}")
            return WhisperEngine(ptr)
        }
    }
}
