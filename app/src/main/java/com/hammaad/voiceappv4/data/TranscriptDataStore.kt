package com.hammaad.voiceappv4.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Calendar

data class DictationStatistics(
    val totalWords: Long = 0L,
    val dictationsToday: Int = 0,
)

/** Deterministic word counting for transcript statistics. */
object TranscriptWordCounter {
    fun count(text: String): Long {
        var words = 0L
        var inWord = false
        for (character in text) {
            if (isWhitespace(character)) {
                inWord = false
            } else if (!inWord) {
                words++
                inWord = true
            }
        }
        return words
    }

    internal fun normalizeWhitespace(text: String): String {
        val result = StringBuilder(text.length)
        var pendingSpace = false
        for (character in text) {
            if (isWhitespace(character)) {
                if (result.isNotEmpty()) pendingSpace = true
            } else {
                if (pendingSpace) result.append(' ')
                result.append(character)
                pendingSpace = false
            }
        }
        return result.toString()
    }

    private fun isWhitespace(character: Char): Boolean =
        Character.isWhitespace(character) || Character.isSpaceChar(character)
}

/**
 * Owns the new transcript ledger. The source SharedPreferences value and the old transcript file
 * are inputs only; migration never removes or rewrites either original.
 */
class TranscriptDataStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private val legacyFile = File(context.applicationContext.filesDir, LEGACY_FILE)

    fun migrate(sourceHistory: String?) {
        synchronized(LOCK) {
            if (prefs.getInt(MIGRATION_VERSION, 0) >= CURRENT_MIGRATION_VERSION) return

            val source = parseSourceHistory(sourceHistory)
            val legacy = parseLegacyHistory()
            if (!source.valid || !legacy.valid) return

            val events = readEvents().toMutableList()
            val visible = readVisible().toMutableList()
            val seenIds = events.mapTo(hashSetOf()) { it.id }
            val seenContent = events.mapTo(hashSetOf()) { it.contentKey }

            (source.records + legacy.records).forEach { record ->
                if (seenIds.add(record.id) && seenContent.add(record.contentKey)) {
                    events += Event(record.id, record.contentKey, record.timestampMs, record.words)
                } else {
                    seenIds.add(record.id)
                    seenContent.add(record.contentKey)
                }
                addVisible(visible, record)
            }

            persist(events, capVisible(visible), migrationComplete = true)
        }
    }

    fun add(
        text: String,
        appPackage: String,
        timestampMs: Long,
        stableId: String? = null,
        keepVisible: Boolean,
    ): Boolean {
        if (text.isBlank()) return false
        synchronized(LOCK) {
            val cleanApp = appPackage.trim()
            val contentKey = contentKey(text, timestampMs, cleanApp)
            val id = stableId?.trim()?.takeIf { it.isNotEmpty() } ?: "runtime:$contentKey"
            val events = readEvents().toMutableList()
            val alreadyRecorded = events.any { it.id == id || it.contentKey == contentKey }
            if (!alreadyRecorded) {
                events += Event(id, contentKey, timestampMs, TranscriptWordCounter.count(text))
            }

            val visible = readVisible().toMutableList()
            if (keepVisible) {
                addVisible(
                    visible,
                    Record(id, text, timestampMs, cleanApp, contentKey, TranscriptWordCounter.count(text)),
                )
            }

            val changed = !alreadyRecorded || keepVisible && visible.size != readVisible().size
            if (!changed) return false
            return persist(events, capVisible(visible), migrationComplete = false)
        }
    }

    fun history(): List<TranscriptEntry> = synchronized(LOCK) {
        readVisible().map { TranscriptEntry(it.text, it.timestampMs, it.appPackage) }
    }

    fun statistics(nowMs: Long = System.currentTimeMillis()): DictationStatistics = synchronized(LOCK) {
        val events = readEvents()
        val today = Calendar.getInstance().apply { timeInMillis = nowMs }
        val dictationsToday = events.count { event ->
            Calendar.getInstance().apply { timeInMillis = event.timestampMs }.let { eventDay ->
                eventDay.get(Calendar.ERA) == today.get(Calendar.ERA) &&
                    eventDay.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    eventDay.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            }
        }
        DictationStatistics(
            totalWords = events.sumOf { it.words },
            dictationsToday = dictationsToday,
        )
    }

    fun clearVisibleHistory() {
        synchronized(LOCK) {
            prefs.edit().putString(VISIBLE_HISTORY, "[]").commit()
        }
    }

    private fun persist(events: List<Event>, visible: List<Record>, migrationComplete: Boolean): Boolean {
        val editor = prefs.edit()
            .putString(EVENTS, encodeEvents(events))
            .putLong(TOTAL_WORDS, events.sumOf { it.words })
            .putString(VISIBLE_HISTORY, encodeVisible(visible))
        if (migrationComplete) editor.putInt(MIGRATION_VERSION, CURRENT_MIGRATION_VERSION)
        return editor.commit()
    }

    private fun readEvents(): List<Event> = runCatching {
        val array = JSONArray(prefs.getString(EVENTS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.getString("id")
                val contentKey = item.getString("contentKey")
                val timestamp = item.getLong("timestamp")
                val words = item.getLong("words").coerceAtLeast(0L)
                if (id.isNotBlank() && contentKey.isNotBlank() && timestamp >= 0L) {
                    add(Event(id, contentKey, timestamp, words))
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun readVisible(): List<Record> = runCatching {
        val array = JSONArray(prefs.getString(VISIBLE_HISTORY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val text = item.getString("text")
                val timestamp = item.getLong("timestamp")
                val app = item.optString("app")
                if (text.isNotBlank() && timestamp >= 0L) {
                    val key = contentKey(text, timestamp, app)
                    add(Record(item.optString("id").ifBlank { "visible:$key" }, text, timestamp, app, key, TranscriptWordCounter.count(text)))
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeEvents(events: List<Event>): String = JSONArray().apply {
        events.forEach { event ->
            put(JSONObject().apply {
                put("id", event.id)
                put("contentKey", event.contentKey)
                put("timestamp", event.timestampMs)
                put("words", event.words)
            })
        }
    }.toString()

    private fun encodeVisible(records: List<Record>): String = JSONArray().apply {
        records.forEach { record ->
            put(JSONObject().apply {
                put("id", record.id)
                put("text", record.text)
                put("timestamp", record.timestampMs)
                put("app", record.appPackage)
            })
        }
    }.toString()

    private fun addVisible(target: MutableList<Record>, record: Record) {
        if (target.any { it.id == record.id || it.contentKey == record.contentKey }) return
        target += record
    }

    private fun capVisible(records: List<Record>): List<Record> = records
        .sortedByDescending { it.timestampMs }
        .take(MAX_VISIBLE_HISTORY)

    private fun parseSourceHistory(raw: String?): ParseResult {
        if (raw == null) return ParseResult(emptyList(), valid = true)
        return runCatching {
            val array = JSONArray(raw)
            val records = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: return@runCatching ParseResult(emptyList(), false)
                    val text = item.optString("text")
                    val timestamp = when {
                        item.has("timestamp") -> item.optLong("timestamp", Long.MIN_VALUE)
                        item.has("timestampMs") -> item.optLong("timestampMs", Long.MIN_VALUE)
                        else -> Long.MIN_VALUE
                    }
                    if (text.isBlank() || timestamp < 0L) continue
                    val app = item.optString("app").ifBlank {
                        item.optString("appPackage").ifBlank { item.optString("appLabel") }
                    }
                    val key = contentKey(text, timestamp, app)
                    val rawId = item.optString("id")
                    add(Record(rawId.takeIf { it.isNotBlank() }?.let { "source-id:$it" } ?: "source-content:$key", text, timestamp, app, key, TranscriptWordCounter.count(text)))
                }
            }
            ParseResult(records, true)
        }.getOrElse { ParseResult(emptyList(), false) }
    }

    private fun parseLegacyHistory(): ParseResult {
        if (!legacyFile.exists()) return ParseResult(emptyList(), valid = true)
        return runCatching {
            val array = JSONArray(legacyFile.readText(Charsets.UTF_8))
            val records = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: return@runCatching ParseResult(emptyList(), false)
                    val text = item.optString("text")
                    val timestamp = item.optLong("timestampMs", Long.MIN_VALUE)
                    if (text.isBlank() || timestamp < 0L) continue
                    val app = item.optString("appLabel")
                    val key = contentKey(text, timestamp, app)
                    val rawId = item.optString("id")
                    add(Record(rawId.takeIf { it.isNotBlank() }?.let { "legacy-id:$it" } ?: "legacy-content:$key", text, timestamp, app, key, TranscriptWordCounter.count(text)))
                }
            }
            ParseResult(records, true)
        }.getOrElse { ParseResult(emptyList(), false) }
    }

    private fun contentKey(text: String, timestampMs: Long, appPackage: String): String {
        val canonical = TranscriptWordCounter.normalizeWhitespace(text) + "\u001f" +
            appPackage.trim() + "\u001f" + timestampMs
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class Event(val id: String, val contentKey: String, val timestampMs: Long, val words: Long)

    private data class Record(
        val id: String,
        val text: String,
        val timestampMs: Long,
        val appPackage: String,
        val contentKey: String,
        val words: Long,
    )

    private data class ParseResult(val records: List<Record>, val valid: Boolean)

    private companion object {
        const val STORE = "voice_app_transcript_data_v2"
        const val VISIBLE_HISTORY = "visible_history"
        const val EVENTS = "word_events"
        const val TOTAL_WORDS = "total_words"
        const val MIGRATION_VERSION = "migration_version"
        const val CURRENT_MIGRATION_VERSION = 1
        const val MAX_VISIBLE_HISTORY = 50
        const val LEGACY_FILE = "transcripts.json"
        val LOCK = Any()
    }
}
