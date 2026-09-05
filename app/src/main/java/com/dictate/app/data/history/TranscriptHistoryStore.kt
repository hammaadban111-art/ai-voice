package com.dictate.app.data.history

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class TranscriptEntry(val timestampMs: Long, val text: String)

/**
 * Local-only transcript history. Plain JSON on internal storage, never
 * uploaded anywhere. Callers must check [DictateSettings.saveHistory]
 * before calling [append]; this store never sends data over the network.
 */
class TranscriptHistoryStore(context: Context) {

    private val file = File(context.filesDir, "transcript_history.json")

    @Synchronized
    fun append(text: String, maxEntries: Int = 200) {
        val entries = readAll().toMutableList()
        entries.add(0, TranscriptEntry(System.currentTimeMillis(), text))
        while (entries.size > maxEntries) entries.removeAt(entries.size - 1)
        writeAll(entries)
    }

    @Synchronized
    fun readAll(): List<TranscriptEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                TranscriptEntry(obj.getLong("t"), obj.getString("text"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun writeAll(entries: List<TranscriptEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("t", entry.timestampMs).put("text", entry.text))
        }
        file.writeText(array.toString())
    }
}
