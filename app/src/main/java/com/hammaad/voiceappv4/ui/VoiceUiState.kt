package com.hammaad.voiceappv4.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.hammaad.voiceappv4.data.AppPreferences
import com.hammaad.voiceappv4.data.AppSettings
import com.hammaad.voiceappv4.data.TranscriptEntry
import com.hammaad.voiceappv4.security.SecureApiKeyStore

data class PermissionSnapshot(
    val microphoneGranted: Boolean,
    val overlayGranted: Boolean,
    val accessibilityGranted: Boolean,
) {
    val allGranted: Boolean
        get() = microphoneGranted && overlayGranted && accessibilityGranted
}

data class VoiceUiSnapshot(
    val settings: AppSettings,
    val keyConfigured: Boolean,
    val permissions: PermissionSnapshot,
    val isSnoozed: Boolean,
    val history: List<TranscriptEntry>,
    val diagnostics: List<String>,
    val totalWords: Long = 0L,
    val dictationsToday: Int = 0,
) {
    val baseReady: Boolean
        get() = keyConfigured && permissions.allGranted

    val isReady: Boolean
        get() = baseReady && settings.bubbleEnabled && !isSnoozed
}

/**
 * Presentation-facing bridge to the existing preference and secure-key stores.
 * It deliberately contains no service, audio, or network behavior.
 */
class VoiceUiState(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = AppPreferences(appContext)
    private val keyStore = SecureApiKeyStore(appContext)

    var snapshot by mutableStateOf(readSnapshot())
        private set

    val totalWords: Long
        get() = snapshot.totalWords

    val dictationsToday: Int
        get() = snapshot.dictationsToday

    fun refresh() {
        snapshot = readSnapshot()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        preferences.update(transform)
        refresh()
    }

    fun saveApiKey(value: String): Boolean = runCatching {
        keyStore.save(value)
        refresh()
    }.isSuccess

    fun clearApiKey() {
        keyStore.clear()
        refresh()
    }

    fun clearHistory() {
        preferences.clearHistory()
        refresh()
    }

    private fun readSnapshot(): VoiceUiSnapshot {
        val settings = preferences.read()
        val statistics = preferences.statistics()
        val permissions = PermissionSnapshot(
            microphoneGranted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
            overlayGranted = Settings.canDrawOverlays(appContext),
            accessibilityGranted = isDictationAccessibilityEnabled(),
        )
        return VoiceUiSnapshot(
            settings = settings,
            keyConfigured = keyStore.hasKey(),
            permissions = permissions,
            isSnoozed = settings.snoozedUntil > System.currentTimeMillis(),
            history = preferences.history(),
            diagnostics = preferences.diagnostics(),
            totalWords = statistics.totalWords,
            dictationsToday = statistics.dictationsToday,
        )
    }

    private fun isDictationAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val expected = ComponentName(
            appContext.packageName,
            "${appContext.packageName}.service.DictationAccessibilityService",
        )
        return enabledServices.split(':').any { raw ->
            val component = ComponentName.unflattenFromString(raw) ?: return@any false
            component.packageName == expected.packageName && component.className == expected.className
        }
    }
}
