package com.dictate.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dictate.app.core.GeminiModels
import com.dictate.app.core.LanguageMode
import com.dictate.app.core.TranscriptionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dictate_settings")

data class DictateSettings(
    // Gemini
    val transcriptionMode: TranscriptionMode = TranscriptionMode.SMART,
    val languageMode: LanguageMode = LanguageMode.AUTO,
    val manualLanguageCode: String = "en-US",
    val customVocabulary: String = "",
    // Bubble
    val bubbleEnabled: Boolean = true,
    val bubbleSizeDp: Int = 56,
    val bubbleOpacityPercent: Int = 100,
    val hapticsEnabled: Boolean = true,
    val snoozeMinutes: Int = 30,
    val snoozeUntilEpochMs: Long = 0L,
    // Privacy
    val saveHistory: Boolean = false,
    val excludedApps: Set<String> = emptySet(),
    // Advanced
    val liveModelOverride: String = GeminiModels.LIVE_TRANSCRIBE,
    val restModelOverride: String = GeminiModels.REST_TRANSCRIBE,
    val diagnosticsEnabled: Boolean = false,
) {
    val isSnoozed: Boolean get() = System.currentTimeMillis() < snoozeUntilEpochMs

    val vocabularyTerms: List<String>
        get() = customVocabulary.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(1000)
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<DictateSettings> = context.dataStore.data.map { prefs ->
        DictateSettings(
            transcriptionMode = prefs[Keys.TRANSCRIPTION_MODE]?.let(TranscriptionMode::valueOf)
                ?: TranscriptionMode.SMART,
            languageMode = prefs[Keys.LANGUAGE_MODE]?.let(LanguageMode::valueOf) ?: LanguageMode.AUTO,
            manualLanguageCode = prefs[Keys.MANUAL_LANGUAGE] ?: "en-US",
            customVocabulary = prefs[Keys.CUSTOM_VOCAB] ?: "",
            bubbleEnabled = prefs[Keys.BUBBLE_ENABLED] ?: true,
            bubbleSizeDp = prefs[Keys.BUBBLE_SIZE] ?: 56,
            bubbleOpacityPercent = prefs[Keys.BUBBLE_OPACITY] ?: 100,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            snoozeMinutes = prefs[Keys.SNOOZE_MINUTES] ?: 30,
            snoozeUntilEpochMs = prefs[Keys.SNOOZE_UNTIL] ?: 0L,
            saveHistory = prefs[Keys.SAVE_HISTORY] ?: false,
            excludedApps = prefs[Keys.EXCLUDED_APPS] ?: emptySet(),
            liveModelOverride = prefs[Keys.LIVE_MODEL] ?: GeminiModels.LIVE_TRANSCRIBE,
            restModelOverride = prefs[Keys.REST_MODEL] ?: GeminiModels.REST_TRANSCRIBE,
            diagnosticsEnabled = prefs[Keys.DIAGNOSTICS] ?: false,
        )
    }

    suspend fun setTranscriptionMode(mode: TranscriptionMode) = edit { it[Keys.TRANSCRIPTION_MODE] = mode.name }
    suspend fun setLanguageMode(mode: LanguageMode) = edit { it[Keys.LANGUAGE_MODE] = mode.name }
    suspend fun setManualLanguage(code: String) = edit { it[Keys.MANUAL_LANGUAGE] = code }
    suspend fun setCustomVocabulary(text: String) = edit { it[Keys.CUSTOM_VOCAB] = text }

    suspend fun setBubbleEnabled(enabled: Boolean) = edit { it[Keys.BUBBLE_ENABLED] = enabled }
    suspend fun setBubbleSize(dp: Int) = edit { it[Keys.BUBBLE_SIZE] = dp }
    suspend fun setBubbleOpacity(percent: Int) = edit { it[Keys.BUBBLE_OPACITY] = percent }
    suspend fun setHapticsEnabled(enabled: Boolean) = edit { it[Keys.HAPTICS_ENABLED] = enabled }
    suspend fun setSnoozeMinutes(minutes: Int) = edit { it[Keys.SNOOZE_MINUTES] = minutes }
    suspend fun snoozeNow(minutes: Int) = edit {
        it[Keys.SNOOZE_UNTIL] = System.currentTimeMillis() + minutes * 60_000L
    }
    suspend fun clearSnooze() = edit { it[Keys.SNOOZE_UNTIL] = 0L }

    suspend fun setSaveHistory(enabled: Boolean) = edit { it[Keys.SAVE_HISTORY] = enabled }
    suspend fun setExcludedApps(packages: Set<String>) = edit { it[Keys.EXCLUDED_APPS] = packages }

    suspend fun setLiveModelOverride(model: String) = edit { it[Keys.LIVE_MODEL] = model }
    suspend fun setRestModelOverride(model: String) = edit { it[Keys.REST_MODEL] = model }
    suspend fun setDiagnosticsEnabled(enabled: Boolean) = edit { it[Keys.DIAGNOSTICS] = enabled }

    suspend fun resetToDefaults() = context.dataStore.edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private object Keys {
        val TRANSCRIPTION_MODE = stringPreferencesKey("transcription_mode")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val MANUAL_LANGUAGE = stringPreferencesKey("manual_language")
        val CUSTOM_VOCAB = stringPreferencesKey("custom_vocabulary")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val BUBBLE_SIZE = intPreferencesKey("bubble_size_dp")
        val BUBBLE_OPACITY = intPreferencesKey("bubble_opacity_percent")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val SNOOZE_UNTIL = androidx.datastore.preferences.core.longPreferencesKey("snooze_until")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
        val LIVE_MODEL = stringPreferencesKey("live_model_override")
        val REST_MODEL = stringPreferencesKey("rest_model_override")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics_enabled")
    }
}
