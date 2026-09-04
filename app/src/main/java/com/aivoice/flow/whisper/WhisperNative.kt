package com.aivoice.flow.whisper

/** Thin 1:1 binding over `app/src/main/cpp/whisper_jni.cpp`. */
internal object WhisperNative {

    init {
        System.loadLibrary("aivoice")
    }

    /** Returns a whisper_context pointer, or 0 if the model could not be loaded. */
    external fun initContext(modelPath: String): Long

    external fun freeContext(ptr: Long)

    /**
     * Runs the full encode/decode pass over 16 kHz mono float samples.
     *
     * @param language BCP-47-ish whisper language code, or "auto" to detect.
     * @param prompt optional decoder priming text (may be empty).
     */
    external fun transcribe(
        ptr: Long,
        audio: FloatArray,
        nThreads: Int,
        language: String,
        prompt: String,
    ): String

    /** Language whisper settled on during the last [transcribe] call. */
    external fun detectedLanguage(ptr: Long): String

    external fun systemInfo(): String
}
