package com.hammaad.voiceappv4.data

import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Executable contract vectors for the Voxta 2.0 data migration.
 *
 * Word-count vectors exercise the production TranscriptWordCounter. Aggregate and migration
 * vectors use a deliberately small in-test oracle because no stable end-to-end fixture exists
 * here; those oracle tests specify the contract but are not production sign-off proof. The
 * static gate at the bottom checks the production surface separately.
 * Assumptions encoded here: event IDs are the idempotency key, destination history wins on a
 * duplicate, and "today" is supplied as an epoch-millisecond interval by the caller.
 */
class Voxta20DataContractTest {
    @Test
    fun `word counting ignores repeated whitespace and empty text`() {
        assertEquals(0, TranscriptWordCounter.count(""))
        assertEquals(0, TranscriptWordCounter.count(" \t\n\r "))
        assertEquals(4, TranscriptWordCounter.count("  one\ttwo\nthree   four  "))
        assertEquals(3, TranscriptWordCounter.count("alpha\u00a0beta\ngamma"))
    }

    @Test
    fun `durable aggregate increments exactly once for one dictation`() {
        val durable = mutableMapOf<String, Any>()
        val firstProcess = ContractLedger(durable)
        firstProcess.record("dictation-1", "  hello   world ", occurredAt = 100L, source = "Notes")

        // Re-opening the store and replaying the same completion must be harmless.
        val afterRestart = ContractLedger(durable)
        afterRestart.record("dictation-1", "hello world", occurredAt = 100L, source = "Notes")
        afterRestart.record("dictation-2", "third", occurredAt = 200L, source = "Messages")

        assertEquals(2, afterRestart.snapshot(todayStart = 0L, tomorrowStart = 1_000L).dictationCount)
        assertEquals(3L, afterRestart.snapshot(todayStart = 0L, tomorrowStart = 1_000L).wordCount)
    }

    @Test
    fun `history migration is duplicate safe and idempotent`() {
        val destination = listOf(
            HistoryRecord("same", "already present", 100L, "Notes"),
        )
        val sourceHistory = listOf(
            HistoryRecord("same", "duplicate source copy", 100L, "Notes"),
            HistoryRecord("source-only", "from source history", 200L, "Messages"),
        )
        val legacyJson = legacyTranscriptsJson(
            id = "legacy-only",
            timestamp = "2026-09-10T04:00:00Z",
            text = "from old file",
            source = "Mail",
        )

        val first = migrateHistory(destination, sourceHistory, legacyJson)
        val second = migrateHistory(first, sourceHistory, legacyJson)

        assertEquals(listOf("same", "source-only", "legacy-only"), first.map { it.id })
        assertEquals(first, second)
        assertEquals("Notes", first.first().source)
        assertEquals("Mail", first.last().source)
    }

    @Test
    fun `today-only dictation count excludes adjacent days`() {
        val ledger = ContractLedger(mutableMapOf())
        ledger.record("yesterday", "one", occurredAt = 99L, source = "Notes")
        ledger.record("today", "two words", occurredAt = 100L, source = "Notes")
        ledger.record("tomorrow", "three", occurredAt = 200L, source = "Notes")

        val today = ledger.snapshot(todayStart = 100L, tomorrowStart = 200L)

        assertEquals(1, today.todayDictationCount)
        assertEquals(3, today.dictationCount)
        assertEquals(4L, today.wordCount)
    }

    @Test
    fun `source history and old transcripts json preserve source and stable ID`() {
        val migrated = migrateHistory(
            destination = emptyList(),
            sourceHistory = listOf(HistoryRecord("native-1", "native", 10L, "Voice App V4")),
            legacyJson = legacyTranscriptsJson(
                id = "old-1",
                timestamp = "2026-09-10T05:00:00Z",
                text = "legacy",
                source = "Safari",
            ),
        )

        assertEquals(2, migrated.size)
        assertEquals("native-1", migrated[0].id)
        assertEquals("Voice App V4", migrated[0].source)
        assertEquals("old-1", migrated[1].id)
        assertEquals(Instant.parse("2026-09-10T05:00:00Z").toEpochMilli(), migrated[1].timestamp)
        assertEquals("Safari", migrated[1].source)
    }

    @Test
    fun production_data_layer_exposes_voxta20_migration_surface_before_signoff() {
        val sourceRoot = listOf(File("src/main"), File("app/src/main"))
            .firstOrNull { it.isDirectory }
            ?: error("production source root is unavailable")
        val productionText = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java", "swift") }
            .joinToString("\n") { it.readText() }

        assertTrue(
            "missing production word counter",
            productionText.contains("TranscriptWordCounter.count"),
        )
        assertTrue("missing aggregate totalWords", productionText.contains("totalWords"))
        assertTrue("missing today-only dictationsToday", productionText.contains("dictationsToday"))
        assertTrue("missing history migration entry point", productionText.contains("fun migrate("))
        assertTrue("missing legacy transcript filename", productionText.contains("transcripts.json"))
        assertTrue("missing AppPreferences.addHistory", productionText.contains("fun addHistory("))
    }

    private fun legacyTranscriptsJson(
        id: String,
        timestamp: String,
        text: String,
        source: String,
    ): String = JSONArray().put(
        mapOf(
            "id" to id,
            "timestamp" to timestamp,
            "text" to text,
            "appName" to source,
        ),
    ).toString()

    private fun migrateHistory(
        destination: List<HistoryRecord>,
        sourceHistory: List<HistoryRecord>,
        legacyJson: String,
    ): List<HistoryRecord> {
        val result = destination.toMutableList()
        val seen = result.mapTo(mutableSetOf()) { it.id }
        (sourceHistory + parseLegacy(legacyJson)).forEach { entry ->
            if (seen.add(entry.id)) result += entry
        }
        return result
    }

    private fun parseLegacy(raw: String): List<HistoryRecord> {
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            HistoryRecord(
                id = item.getString("id"),
                text = item.getString("text"),
                timestamp = Instant.parse(item.getString("timestamp")).toEpochMilli(),
                source = item.optString("appName").ifBlank { item.optString("source") },
            )
        }
    }

    private data class HistoryRecord(
        val id: String,
        val text: String,
        val timestamp: Long,
        val source: String,
    )

    private data class Stats(
        val dictationCount: Int,
        val wordCount: Long,
        val todayDictationCount: Int,
    )

    private class ContractLedger(private val durable: MutableMap<String, Any>) {
        private val events: MutableSet<String>
            get() = (durable[EVENTS_KEY] as? MutableSet<String>)
                ?: mutableSetOf<String>().also { durable[EVENTS_KEY] = it }

        fun record(id: String, text: String, occurredAt: Long, source: String) {
            if (!events.add(id)) return
            val records = (durable[RECORDS_KEY] as? MutableList<DictationRecord>)
                ?: mutableListOf<DictationRecord>().also { durable[RECORDS_KEY] = it }
            records += DictationRecord(id, text, occurredAt, source)
        }

        fun snapshot(todayStart: Long, tomorrowStart: Long): Stats {
            val records = durable[RECORDS_KEY] as? List<DictationRecord> ?: emptyList()
            return Stats(
                dictationCount = records.size,
                wordCount = records.sumOf { record ->
                    TranscriptWordCounter.count(record.text).toLong()
                },
                todayDictationCount = records.count { it.occurredAt >= todayStart && it.occurredAt < tomorrowStart },
            )
        }

        private data class DictationRecord(
            val id: String,
            val text: String,
            val occurredAt: Long,
            val source: String,
        )

        private companion object {
            const val EVENTS_KEY = "recorded_event_ids"
            const val RECORDS_KEY = "dictation_records"
        }
    }
}
