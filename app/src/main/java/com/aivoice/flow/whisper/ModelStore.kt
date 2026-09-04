package com.aivoice.flow.whisper

import android.content.Context
import android.util.Log
import com.aivoice.flow.BuildConfig
import java.io.File
import java.io.FileOutputStream

/**
 * The GGML weights ship inside the APK (stored uncompressed) and are copied
 * once into app storage, because whisper.cpp needs a real file path it can
 * mmap. Everything here is local — the app never talks to a network.
 */
object ModelStore {

    private const val TAG = "ModelStore"
    private const val COPY_BUFFER = 1 shl 20

    fun modelFile(context: Context): File =
        File(context.filesDir, "models/${BuildConfig.WHISPER_MODEL_ASSET.substringAfterLast('/')}")

    fun isInstalled(context: Context): Boolean =
        modelFile(context).length() == BuildConfig.WHISPER_MODEL_BYTES

    /**
     * Unpacks the bundled model if it isn't in place yet.
     *
     * @param onProgress called with 0f..1f while copying.
     * @return true when the model is ready to load.
     */
    fun install(context: Context, onProgress: (Float) -> Unit = {}): Boolean {
        val target = modelFile(context)
        if (isInstalled(context)) return true

        target.parentFile?.mkdirs()
        // A partial file from an interrupted copy would fail the size check
        // above forever, so always start from scratch.
        val staging = File(target.parentFile, target.name + ".part")
        staging.delete()

        return try {
            context.assets.open(BuildConfig.WHISPER_MODEL_ASSET).use { input ->
                FileOutputStream(staging).use { output ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(copied.toFloat() / BuildConfig.WHISPER_MODEL_BYTES)
                    }
                    output.fd.sync()
                }
            }
            if (staging.length() != BuildConfig.WHISPER_MODEL_BYTES) {
                Log.e(TAG, "unpacked ${staging.length()} bytes, expected ${BuildConfig.WHISPER_MODEL_BYTES}")
                staging.delete()
                return false
            }
            target.delete()
            val ok = staging.renameTo(target)
            onProgress(1f)
            ok
        } catch (e: Exception) {
            Log.e(TAG, "failed to unpack model", e)
            staging.delete()
            false
        }
    }
}
