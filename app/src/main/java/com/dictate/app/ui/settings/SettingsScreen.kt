package com.dictate.app.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dictate.app.asDictateApp
import com.dictate.app.core.LanguageMode
import com.dictate.app.core.TranscriptionMode
import com.dictate.app.data.settings.DictateSettings
import com.dictate.app.gemini.GeminiRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.asDictateApp()
    val settings by app.settingsRepository.settings.collectAsState(initial = DictateSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            GeminiSection(settings, app, scope)
            HorizontalDivider()
            BubbleSection(settings, app, scope)
            HorizontalDivider()
            PrivacySection(settings, app, scope, context)
            HorizontalDivider()
            AdvancedSection(settings, app, scope)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

// --------------------------------------------------------------- Gemini

@Composable
private fun GeminiSection(
    settings: DictateSettings,
    app: com.dictate.app.DictateApplication,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val context = LocalContext.current
    var apiKeyField by remember { mutableStateOf(app.secureKeyStore.getApiKey().orEmpty()) }
    var visible by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Gemini")

        OutlinedTextField(
            value = apiKeyField,
            onValueChange = { apiKeyField = it; testStatus = null },
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Show/hide")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip?.getItemAt(0)?.text?.let { apiKeyField = it.toString() }
            }) { Icon(Icons.Filled.ContentPaste, null); Text(" Paste") }

            Button(onClick = { app.secureKeyStore.saveApiKey(apiKeyField.trim()); testStatus = "Saved" }) { Text("Save") }
            TextButton(onClick = { app.secureKeyStore.removeApiKey(); apiKeyField = ""; testStatus = "Removed" }) { Text("Remove") }
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = apiKeyField.isNotBlank() && !testing,
                onClick = {
                    testing = true
                    testStatus = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { GeminiRestClient().testConnection(apiKeyField.trim()) }
                        testStatus = result.fold({ "Connection OK" }, { "Failed: ${it.message}" })
                        testing = false
                    }
                },
            ) { Text(if (testing) "Testing…" else "Test connection") }
            testStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        SectionTitle("Transcription style")
        RadioRow("Smart (clean, punctuated)", settings.transcriptionMode == TranscriptionMode.SMART) {
            scope.launch { app.settingsRepository.setTranscriptionMode(TranscriptionMode.SMART) }
        }
        RadioRow("Verbatim (exact words)", settings.transcriptionMode == TranscriptionMode.VERBATIM) {
            scope.launch { app.settingsRepository.setTranscriptionMode(TranscriptionMode.VERBATIM) }
        }

        SectionTitle("Language")
        RadioRow("Auto-detect", settings.languageMode == LanguageMode.AUTO) {
            scope.launch { app.settingsRepository.setLanguageMode(LanguageMode.AUTO) }
        }
        RadioRow("Manual: ${settings.manualLanguageCode}", settings.languageMode == LanguageMode.MANUAL) {
            scope.launch { app.settingsRepository.setLanguageMode(LanguageMode.MANUAL) }
        }
        if (settings.languageMode == LanguageMode.MANUAL) {
            var code by remember(settings.manualLanguageCode) { mutableStateOf(settings.manualLanguageCode) }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it; scope.launch { app.settingsRepository.setManualLanguage(it) } },
                label = { Text("BCP-47 language code, e.g. en-US") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        var vocab by remember(settings.customVocabulary) { mutableStateOf(settings.customVocabulary) }
        OutlinedTextField(
            value = vocab,
            onValueChange = { vocab = it; scope.launch { app.settingsRepository.setCustomVocabulary(it) } },
            label = { Text("Custom vocabulary (comma separated)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --------------------------------------------------------------- Bubble

@Composable
private fun BubbleSection(
    settings: DictateSettings,
    app: com.dictate.app.DictateApplication,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Bubble")
        SwitchRow("Enabled", settings.bubbleEnabled) { scope.launch { app.settingsRepository.setBubbleEnabled(it) } }

        Text("Size: ${settings.bubbleSizeDp}dp", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = settings.bubbleSizeDp.toFloat(),
            valueRange = 40f..80f,
            onValueChange = { scope.launch { app.settingsRepository.setBubbleSize(it.toInt()) } },
        )

        Text("Opacity: ${settings.bubbleOpacityPercent}%", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = settings.bubbleOpacityPercent.toFloat(),
            valueRange = 30f..100f,
            onValueChange = { scope.launch { app.settingsRepository.setBubbleOpacity(it.toInt()) } },
        )

        SwitchRow("Haptics", settings.hapticsEnabled) { scope.launch { app.settingsRepository.setHapticsEnabled(it) } }

        Text("Snooze duration: ${settings.snoozeMinutes} min", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = settings.snoozeMinutes.toFloat(),
            valueRange = 5f..120f,
            onValueChange = { scope.launch { app.settingsRepository.setSnoozeMinutes(it.toInt()) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scope.launch { app.settingsRepository.snoozeNow(settings.snoozeMinutes) } }) {
                Text("Snooze now")
            }
            if (settings.isSnoozed) {
                TextButton(onClick = { scope.launch { app.settingsRepository.clearSnooze() } }) { Text("Clear snooze") }
            }
        }
    }
}

// -------------------------------------------------------------- Privacy

@Composable
private fun PrivacySection(
    settings: DictateSettings,
    app: com.dictate.app.DictateApplication,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Privacy")
        SwitchRow("Save transcript history on this device", settings.saveHistory) {
            scope.launch { app.settingsRepository.setSaveHistory(it) }
        }
        TextButton(onClick = { showClearConfirm = true }) { Text("Delete history") }
        TextButton(onClick = { showAppPicker = true }) {
            Text("Excluded apps (${settings.excludedApps.size})")
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                TextButton(onClick = { app.historyStore.clear(); showClearConfirm = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
            title = { Text("Delete transcript history?") },
            text = { Text("This removes every saved transcript from this device. This cannot be undone.") },
        )
    }

    if (showAppPicker) {
        ExcludedAppsDialog(
            context = context,
            excluded = settings.excludedApps,
            onDismiss = { showAppPicker = false },
            onToggle = { pkg, exclude ->
                val updated = if (exclude) settings.excludedApps + pkg else settings.excludedApps - pkg
                scope.launch { app.settingsRepository.setExcludedApps(updated) }
            },
        )
    }
}

private data class InstalledApp(val packageName: String, val label: String)

@Composable
private fun ExcludedAppsDialog(
    context: Context,
    excluded: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Excluded apps") },
        text = {
            LazyColumn {
                items(apps) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = app.packageName in excluded,
                            onCheckedChange = { checked -> onToggle(app.packageName, checked) },
                        )
                        Text(app.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

// ------------------------------------------------------------- Advanced

@Composable
private fun AdvancedSection(
    settings: DictateSettings,
    app: com.dictate.app.DictateApplication,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var showResetConfirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Advanced")

        var liveModel by remember(settings.liveModelOverride) { mutableStateOf(settings.liveModelOverride) }
        OutlinedTextField(
            value = liveModel,
            onValueChange = { liveModel = it; scope.launch { app.settingsRepository.setLiveModelOverride(it) } },
            label = { Text("Live model override") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        var restModel by remember(settings.restModelOverride) { mutableStateOf(settings.restModelOverride) }
        OutlinedTextField(
            value = restModel,
            onValueChange = { restModel = it; scope.launch { app.settingsRepository.setRestModelOverride(it) } },
            label = { Text("Fallback model override") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SwitchRow("Diagnostics logging", settings.diagnosticsEnabled) {
            scope.launch { app.settingsRepository.setDiagnosticsEnabled(it) }
        }

        TextButton(onClick = { showResetConfirm = true }) { Text("Reset all settings") }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            confirmButton = {
                TextButton(onClick = { scope.launch { app.settingsRepository.resetToDefaults() }; showResetConfirm = false }) {
                    Text("Reset")
                }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } },
            title = { Text("Reset all settings?") },
            text = { Text("Bubble, privacy, and advanced settings return to their defaults. Your API key is not affected.") },
        )
    }
}

// --------------------------------------------------------------- shared

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
