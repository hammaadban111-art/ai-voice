package com.hammaad.voiceappv4.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val transcriptData = TranscriptDataStore(appContext)

    init {
        transcriptData.migrate(prefs.getString("history", null))
    }

    fun read(): AppSettings = AppSettings(
        onboardingComplete = prefs.getBoolean("onboarding_complete", false),
        bubbleEnabled = prefs.getBoolean("bubble_enabled", false),
        mode = runCatching { TranscriptionMode.valueOf(prefs.getString("mode", "SMART")!!) }
            .getOrDefault(TranscriptionMode.SMART),
        languageCode = prefs.getString("language", "") ?: "",
        customVocabulary = decodeArray(prefs.getString("vocabulary", "[]")),
        bubbleSizeDp = prefs.getInt("bubble_size", 58).coerceIn(48, 76),
        bubbleOpacity = prefs.getFloat("bubble_opacity", 0.96f).coerceIn(0.55f, 1f),
        bubbleX = prefs.getInt("bubble_x", -1),
        bubbleY = prefs.getInt("bubble_y", -1),
        haptics = prefs.getBoolean("haptics", true),
        snoozeMinutes = prefs.getInt("snooze_minutes", 30).coerceIn(5, 1440),
        snoozedUntil = prefs.getLong("snoozed_until", 0L),
        excludedPackages = prefs.getStringSet("excluded_packages", emptySet())?.toSet() ?: emptySet(),
        historyEnabled = prefs.getBoolean("history_enabled", false),
        diagnosticsEnabled = prefs.getBoolean("diagnostics_enabled", true),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(read())
        prefs.edit()
            .putBoolean("onboarding_complete", s.onboardingComplete)
            .putBoolean("bubble_enabled", s.bubbleEnabled)
            .putString("mode", s.mode.name)
            .putString("language", s.languageCode)
            .putString("vocabulary", JSONArray(s.customVocabulary.take(1000)).toString())
            .putInt("bubble_size", s.bubbleSizeDp)
            .putFloat("bubble_opacity", s.bubbleOpacity)
            .putInt("bubble_x", s.bubbleX)
            .putInt("bubble_y", s.bubbleY)
            .putBoolean("haptics", s.haptics)
            .putInt("snooze_minutes", s.snoozeMinutes)
            .putLong("snoozed_until", s.snoozedUntil)
            .putStringSet("excluded_packages", s.excludedPackages)
            .putBoolean("history_enabled", s.historyEnabled)
            .putBoolean("diagnostics_enabled", s.diagnosticsEnabled)
            .apply()
    }

    fun addHistory(
        text: String,
        appPackage: String,
        timestampMs: Long = System.currentTimeMillis(),
        stableId: String? = null,
    ) {
        transcriptData.add(
            text = text,
            appPackage = appPackage,
            timestampMs = timestampMs,
            stableId = stableId,
            keepVisible = read().historyEnabled,
        )
    }

    fun history(): List<TranscriptEntry> = transcriptData.history()

    fun statistics(): DictationStatistics = transcriptData.statistics()

    fun clearHistory() = transcriptData.clearVisibleHistory()

    fun log(message: String) {
        if (!read().diagnosticsEnabled) return
        val old = prefs.getStringSet("diagnostics", emptySet()).orEmpty().toMutableList()
        old += "${System.currentTimeMillis()}|${message.take(220)}"
        prefs.edit().putStringSet("diagnostics", old.takeLast(30).toSet()).apply()
    }

    fun diagnostics(): List<String> = prefs.getStringSet("diagnostics", emptySet()).orEmpty()
        .sortedByDescending { it.substringBefore('|').toLongOrNull() ?: 0 }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun decodeArray(raw: String?): List<String> = runCatching {
        val array = JSONArray(raw ?: "[]")
        List(array.length()) { array.getString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    companion object { const val FILE = "voice_app_settings" }
}
