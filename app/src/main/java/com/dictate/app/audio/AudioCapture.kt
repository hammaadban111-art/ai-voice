package com.dictate.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.dictate.app.core.AudioConfig
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Captures 16 kHz mono 16-bit PCM from the microphone, forwarding chunks to
 * [onChunk] as they arrive while also buffering the full utterance in memory
 * so it can be replayed to the REST fallback if the live session drops.
 * Audio is never written to disk and never retained after transcription.
 */
class AudioCapture {

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val fullBuffer = ByteArrayOutputStream()

    val bufferedPcm: ByteArray
        @Synchronized get() = fullBuffer.toByteArray()

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(scope: CoroutineScope, onChunk: (ByteArray, Int) -> Unit, onError: (Throwable) -> Unit) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            onError(IllegalStateException("Unsupported audio configuration on this device"))
            return
        }
        val bufferSize = minBufferSize * 2

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AudioConfig.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError(IllegalStateException("Failed to initialize AudioRecord"))
            return
        }

        audioRecord = record
        synchronized(this) { fullBuffer.reset() }
        record.startRecording()

        captureJob = scope.launch(Dispatchers.IO) {
            val chunk = ByteArray(bufferSize)
            while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) {
                    synchronized(this@AudioCapture) { fullBuffer.write(chunk, 0, read) }
                    onChunk(chunk, read)
                } else if (read < 0) {
                    onError(IllegalStateException("AudioRecord read error: $read"))
                    break
                }
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.let {
            runCatching {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            }
            it.release()
        }
        audioRecord = null
    }

    @Synchronized
    fun clearBuffer() {
        fullBuffer.reset()
    }
}
