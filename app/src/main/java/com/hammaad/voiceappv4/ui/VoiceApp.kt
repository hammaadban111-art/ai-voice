package com.hammaad.voiceappv4.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hammaad.voiceappv4.data.AppSettings
import com.hammaad.voiceappv4.data.TranscriptionMode
import kotlinx.coroutines.launch

enum class AppScreen { Onboarding, Home, Settings }

enum class OnboardingStep { Welcome, Permissions, Key, Bubble, Finish }

/** Optional hooks for the core worker. A null hook is shown as unavailable, never simulated as complete. */
data class VoiceIntegrationHooks(
    val onOptionalDictationTest: (() -> Unit)? = null,
    /** The core reads the encrypted key itself; the UI never passes raw credentials through callbacks. */
    val onTestConnection: (suspend () -> Result<String>)? = null,
)

@Composable
fun VoiceApp(
    context: Context = LocalContext.current.applicationContext,
    integrationHooks: VoiceIntegrationHooks = VoiceIntegrationHooks(),
) {
    val uiState = remember { VoiceUiState(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var screenName by rememberSaveable {
        mutableStateOf(
            if (uiState.snapshot.settings.onboardingComplete) AppScreen.Home.name
            else AppScreen.Onboarding.name,
        )
    }
    val screen = runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.Home)
    val microphoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { uiState.refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) uiState.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.snapshot.settings.onboardingComplete) {
        if (uiState.snapshot.settings.onboardingComplete && screen == AppScreen.Onboarding) {
            screenName = AppScreen.Home.name
        }
    }

    VoiceTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                AppScreen.Onboarding -> OnboardingFlow(
                    snapshot = uiState.snapshot,
                    onRequestMicrophone = { microphoneLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)) },
                    onOpenOverlaySettings = { openOverlaySettings(context) },
                    onOpenAccessibilitySettings = { openAccessibilitySettings(context) },
                    onSaveKey = { uiState.saveApiKey(it) },
                    onUpdateSettings = uiState::updateSettings,
                    onFinish = {
                        uiState.updateSettings { it.copy(onboardingComplete = true, bubbleEnabled = true) }
                        screenName = AppScreen.Home.name
                    },
                    integrationHooks = integrationHooks,
                )

                AppScreen.Home -> {
                    HomeScreen(
                        snapshot = uiState.snapshot,
                        totalWords = uiState.totalWords.toString(),
                        dictationsToday = uiState.dictationsToday.toString(),
                        onOpenSettings = { screenName = AppScreen.Settings.name },
                        onReviewSetup = { screenName = AppScreen.Onboarding.name },
                        onUpdateSettings = uiState::updateSettings,
                        onClearHistory = uiState::clearHistory,
                    )
                }

                AppScreen.Settings -> {
                    BackHandler { screenName = AppScreen.Home.name }
                    SettingsScreen(
                        snapshot = uiState.snapshot,
                        onBack = { screenName = AppScreen.Home.name },
                        onSaveKey = { uiState.saveApiKey(it) },
                        onClearKey = uiState::clearApiKey,
                        onUpdateSettings = uiState::updateSettings,
                        integrationHooks = integrationHooks,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingFlow(
    snapshot: VoiceUiSnapshot,
    onRequestMicrophone: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSaveKey: (String) -> Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onFinish: () -> Unit,
    integrationHooks: VoiceIntegrationHooks,
) {
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.Welcome.name) }
    val step = runCatching { OnboardingStep.valueOf(stepName) }.getOrDefault(OnboardingStep.Welcome)
    val stepNumber = when (step) {
        OnboardingStep.Welcome -> 1
        OnboardingStep.Permissions -> 2
        OnboardingStep.Key -> 3
        OnboardingStep.Bubble -> 4
        OnboardingStep.Finish -> 4
    }
    val goTo: (OnboardingStep) -> Unit = { stepName = it.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VoiceMark()
            Spacer(Modifier.width(12.dp))
            Text("Voxta", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(24.dp))
        SetupProgress(current = stepNumber, total = 4)
        Spacer(Modifier.height(28.dp))

        when (step) {
            OnboardingStep.Welcome -> WelcomeStep(onContinue = { goTo(OnboardingStep.Permissions) })
            OnboardingStep.Permissions -> PermissionStep(
                snapshot = snapshot,
                onRequestMicrophone = onRequestMicrophone,
                onBack = { goTo(OnboardingStep.Welcome) },
                onContinue = { goTo(OnboardingStep.Key) },
            )

            OnboardingStep.Key -> KeyStep(
                snapshot = snapshot,
                onSaveKey = onSaveKey,
                onBack = { goTo(OnboardingStep.Permissions) },
                onContinue = { goTo(OnboardingStep.Bubble) },
            )

            OnboardingStep.Bubble -> BubbleStep(
                snapshot = snapshot,
                enabled = snapshot.settings.bubbleEnabled,
                onEnabledChange = { enabled -> onUpdateSettings { it.copy(bubbleEnabled = enabled) } },
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onBack = { goTo(OnboardingStep.Key) },
                onContinue = { goTo(OnboardingStep.Finish) },
            )

            OnboardingStep.Finish -> FinishStep(
                snapshot = snapshot,
                onOptionalTest = integrationHooks.onOptionalDictationTest,
                onBack = { goTo(OnboardingStep.Bubble) },
                onFinish = onFinish,
            )
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    IntroBlock(
        eyebrow = "A quieter way to speak",
        title = "Words, right where you type.",
        body = "Voxta places a calm voice control beside eligible text fields. Speak once; your words land at the cursor.",
    )
    Spacer(Modifier.height(32.dp))
    VoiceSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            VoiceMark(modifier = Modifier.size(76.dp))
            Spacer(Modifier.height(18.dp))
            Text("Private by design", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Audio is used only while you dictate. Accessibility is used only to find a safe text field and place the result at your cursor.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(32.dp))
    PrimaryAction(text = "Set up Voxta", onClick = onContinue)
    Spacer(Modifier.height(10.dp))
    Text(
        "You can finish setup without running a transcription test.",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionStep(
    snapshot: VoiceUiSnapshot,
    onRequestMicrophone: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    IntroBlock(
        eyebrow = "Step 1 of 3",
        title = "Allow active dictation.",
        body = "Microphone permission is used only after you tap or hold the bubble. Android permission state is checked again whenever you return.",
    )
    Spacer(Modifier.height(20.dp))
    VoiceSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            RequirementRow(
                title = "Microphone",
                detail = if (snapshot.permissions.microphoneGranted) "Ready for active dictation only" else "Needed only while you are recording",
                granted = snapshot.permissions.microphoneGranted,
                icon = VoiceIcon.Mic,
                actionLabel = "Allow",
                onAction = onRequestMicrophone,
            )
        }
    }
    Spacer(Modifier.height(28.dp))
    PrimaryAction(
        text = "Continue to Gemini key",
        onClick = onContinue,
        enabled = snapshot.permissions.microphoneGranted,
    )
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

@Composable
private fun KeyStep(
    snapshot: VoiceUiSnapshot,
    onSaveKey: (String) -> Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    IntroBlock(
        eyebrow = "Step 2 of 3",
        title = "Bring your own Gemini key.",
        body = "The key stays encrypted on this device. Voxta will not ask for it again unless you replace it.",
    )
    Spacer(Modifier.height(20.dp))
    if (snapshot.keyConfigured && draft.isBlank()) {
        AccessNotice("A Gemini API key is already stored securely on this device.")
        Spacer(Modifier.height(16.dp))
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it; error = "" },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(if (snapshot.keyConfigured) "Replace API key (optional)" else "Gemini API key") },
        placeholder = { Text("Paste your key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        supportingText = { Text("Never included in the app. Never shown in diagnostics.") },
    )
    if (error.isNotBlank()) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(24.dp))
    PrimaryAction(
        text = if (snapshot.keyConfigured) "Continue to bubble" else "Save key and continue",
        onClick = {
            if (draft.isBlank() && snapshot.keyConfigured) {
                onContinue()
            } else if (draft.trim().isBlank()) {
                error = "Enter a Gemini API key before continuing."
            } else if (onSaveKey(draft)) {
                onContinue()
            } else {
                error = "The key could not be saved securely. Try again."
            }
        },
        enabled = snapshot.keyConfigured || draft.isNotBlank(),
    )
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

@Composable
private fun BubbleStep(
    snapshot: VoiceUiSnapshot,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    IntroBlock(
        eyebrow = "Step 3 of 3",
        title = "Make it contextual.",
        body = "Enabled means the feature is available—not that a bubble floats everywhere. It appears only while an eligible text field is focused and ready for typing.",
    )
    Spacer(Modifier.height(20.dp))
    VoiceSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            RequirementRow(
                title = "Appear over other apps",
                detail = if (snapshot.permissions.overlayGranted) "Contextual overlay is allowed" else "Needed to show the bubble above a text field",
                granted = snapshot.permissions.overlayGranted,
                icon = VoiceIcon.Bubble,
                actionLabel = "Open",
                onAction = onOpenOverlaySettings,
            )
            SettingDivider()
            RequirementRow(
                title = "Accessibility access",
                detail = if (snapshot.permissions.accessibilityGranted) "Focused-field detection and insertion are ready" else "Used only to detect safe editable fields and insert text",
                granted = snapshot.permissions.accessibilityGranted,
                icon = VoiceIcon.Shield,
                actionLabel = "Open",
                onAction = onOpenAccessibilitySettings,
            )
            SettingDivider()
            SettingRow(
                title = "Contextual bubble",
                detail = if (enabled) "Ready to appear in eligible fields" else "Currently disabled",
                icon = VoiceIcon.Bubble,
            ) {
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = snapshot.permissions.overlayGranted && snapshot.permissions.accessibilityGranted,
                    modifier = Modifier.semantics {
                        contentDescription = "Enable contextual dictation bubble"
                    },
                )
            }
            SettingDivider()
            Spacer(Modifier.height(12.dp))
            Text("What you can expect", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            BulletLine("No bubble on Home, empty screens, or random apps.")
            BulletLine("No bubble in passwords, PINs, or sensitive fields.")
            BulletLine("Tap to dictate, or long-press for push-to-talk.")
        }
    }
    if (!snapshot.permissions.accessibilityGranted) {
        Spacer(Modifier.height(12.dp))
        AccessNotice(
            text = "OxygenOS sideload recovery: Settings › Apps › Voxta › three-dot menu › Allow restricted settings. Then return to Accessibility, enable Voxta, and allow background activity if OxygenOS later stops it.",
            warning = true,
        )
    }
    Spacer(Modifier.height(28.dp))
    PrimaryAction(
        text = "Review and finish",
        onClick = onContinue,
        enabled = enabled && snapshot.permissions.overlayGranted && snapshot.permissions.accessibilityGranted,
    )
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

@Composable
private fun FinishStep(
    snapshot: VoiceUiSnapshot,
    onOptionalTest: (() -> Unit)?,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    IntroBlock(
        eyebrow = "Ready",
        title = "Your setup is clear.",
        body = "Restarting will take you straight to Home. A test dictation is optional and never blocks setup.",
    )
    Spacer(Modifier.height(20.dp))
    VoiceSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            FinishCheck("Permissions", snapshot.permissions.allGranted)
            FinishCheck("Gemini API key", snapshot.keyConfigured)
            FinishCheck("Contextual bubble", snapshot.settings.bubbleEnabled)
        }
    }
    Spacer(Modifier.height(16.dp))
    if (onOptionalTest != null) {
        SecondaryAction(text = "Try an optional test dictation", onClick = onOptionalTest)
    } else {
        SecondaryAction(
            text = "Optional test (core hook pending)",
            onClick = {},
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "The UI is ready for the lead’s dictation callback. You can finish without testing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(20.dp))
    PrimaryAction(text = "Finish setup", onClick = onFinish)
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

@Composable
private fun FinishCheck(label: String, complete: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoiceGlyph(
            if (complete) VoiceIcon.Check else VoiceIcon.Warning,
            tint = if (complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            size = 22.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HomeScreen(
    snapshot: VoiceUiSnapshot,
    totalWords: String,
    dictationsToday: String,
    onOpenSettings: () -> Unit,
    onReviewSetup: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onClearHistory: () -> Unit,
) {
    val snoozeLabel = if (snapshot.isSnoozed) "Resume bubble" else "Snooze for ${snapshot.settings.snoozeMinutes} min"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Voxta",
            actionLabel = "Open settings",
            actionIcon = VoiceIcon.Gear,
            onAction = onOpenSettings,
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text("Good to have you back.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("Speak naturally. Keep control.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your contextual dictation setup at a glance.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            ReadinessCard(snapshot = snapshot, onReviewSetup = onReviewSetup)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DashboardMetricCard(
                    label = "TOTAL WORDS",
                    value = totalWords,
                    icon = VoiceIcon.Wave,
                    modifier = Modifier.weight(1f),
                )
                DashboardMetricCard(
                    label = "DICTATIONS TODAY",
                    value = dictationsToday,
                    icon = VoiceIcon.History,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            VoiceSurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionLabel("Bubble behavior")
                    Spacer(Modifier.height(10.dp))
                    SettingRow(
                        title = "Contextual bubble",
                        detail = if (snapshot.settings.bubbleEnabled) "Only in eligible, focused text fields" else "Disabled until you turn it back on",
                        icon = VoiceIcon.Bubble,
                    ) {
                        Switch(
                            checked = snapshot.settings.bubbleEnabled,
                            onCheckedChange = { checked -> onUpdateSettings { it.copy(bubbleEnabled = checked) } },
                            modifier = Modifier.semantics { contentDescription = "Toggle contextual bubble" },
                        )
                    }
                    SettingDivider()
                    Spacer(Modifier.height(12.dp))
                    BulletLine("Tap a normal text field and wait for the keyboard.")
                    BulletLine("Tap the real bubble to record; hold it for push-to-talk.")
                    BulletLine("Cancel discards audio. Done transcribes and inserts at the cursor.")
                }
            }
            Spacer(Modifier.height(16.dp))
            if (snapshot.isSnoozed) {
                AccessNotice("Snoozed until ${formatSnoozeTime(snapshot.settings.snoozedUntil)}. The bubble remains hidden while snoozed.")
            } else {
                AccessNotice("No audio is continuously recorded. The microphone should be active only during a real dictation session.")
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (snapshot.isSnoozed) onUpdateSettings { it.copy(snoozedUntil = 0L) }
                        else onUpdateSettings { it.copy(snoozedUntil = System.currentTimeMillis() + it.snoozeMinutes * 60_000L) }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text(snoozeLabel) }
                TextButton(
                    onClick = { onUpdateSettings { it.copy(bubbleEnabled = false, snoozedUntil = 0L) } },
                    modifier = Modifier.weight(.75f).height(52.dp),
                ) { Text("Disable") }
            }
            Spacer(Modifier.height(22.dp))
            VoiceSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionLabel("The promise")
                    Spacer(Modifier.height(10.dp))
                    Text("Your text stays yours.", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Accessibility data is used for focused-field context only. It is never sent to Gemini, and recorded audio is not kept after transcription.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (snapshot.settings.historyEnabled) {
                Spacer(Modifier.height(16.dp))
                VoiceSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("On-device history", Modifier.weight(1f))
                            if (snapshot.history.isNotEmpty()) TextButton(onClick = onClearHistory) { Text("Clear") }
                        }
                        if (snapshot.history.isEmpty()) {
                            Text("No transcripts saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else snapshot.history.take(8).forEach { entry ->
                            SettingDivider()
                            Text(entry.text, modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge)
                            Text(entry.appPackage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingsScreen(
    snapshot: VoiceUiSnapshot,
    onBack: () -> Unit,
    onSaveKey: (String) -> Boolean,
    onClearKey: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    integrationHooks: VoiceIntegrationHooks,
) {
    var editingKey by rememberSaveable { mutableStateOf(!snapshot.keyConfigured) }
    var keyDraft by rememberSaveable { mutableStateOf("") }
    var keyError by rememberSaveable { mutableStateOf("") }
    var connectionNotice by rememberSaveable { mutableStateOf("") }
    var vocabularyDraft by rememberSaveable { mutableStateOf(snapshot.settings.customVocabulary.joinToString("\n")) }
    var excludedDraft by rememberSaveable { mutableStateOf(snapshot.settings.excludedPackages.joinToString("\n")) }
    var languageDraft by rememberSaveable { mutableStateOf(snapshot.settings.languageCode) }
    var manualLanguage by rememberSaveable { mutableStateOf(snapshot.settings.languageCode.isNotBlank()) }
    var showClearKeyDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showClearKeyDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeyDialog = false },
            title = { Text("Remove Gemini key?") },
            text = { Text("Dictation will not be ready until you save a new key. This does not remove transcript history." ) },
            confirmButton = {
                TextButton(onClick = { onClearKey(); editingKey = true; showClearKeyDialog = false }) { Text("Remove key") }
            },
            dismissButton = { TextButton(onClick = { showClearKeyDialog = false }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack, actionLabel = "Go back")
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            SectionLabel("Gemini connection")
            Spacer(Modifier.height(10.dp))
            VoiceSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SettingRow(
                        title = "API key",
                        detail = if (snapshot.keyConfigured) "Stored encrypted on this device" else "Required before any transcription test",
                        icon = VoiceIcon.Key,
                    ) { }
                    if (editingKey) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it; keyError = "" },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (snapshot.keyConfigured) "Replacement key" else "Gemini API key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            supportingText = { Text("The key is never printed in diagnostics.") },
                        )
                        if (keyError.isNotBlank()) {
                            Text(keyError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (keyDraft.isBlank()) keyError = "Enter a key or cancel."
                                    else if (onSaveKey(keyDraft)) { editingKey = false; keyDraft = "" }
                                    else keyError = "The key could not be saved securely. Try again."
                                },
                                modifier = Modifier.height(48.dp),
                            ) { Text("Save key") }
                            TextButton(onClick = { editingKey = false; keyDraft = "" }, modifier = Modifier.height(48.dp)) { Text("Cancel") }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            SecondaryAction(
                                text = "Replace key",
                                onClick = { editingKey = true },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { showClearKeyDialog = true },
                                modifier = Modifier.weight(.8f).height(50.dp),
                            ) { Text("Remove") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SecondaryAction(
                        text = "Test connection",
                        onClick = {
                            if (integrationHooks.onTestConnection == null) {
                                connectionNotice = "The core connection hook is not connected in this UI slice; no network request was made."
                            } else {
                                connectionNotice = "Checking Gemini…"
                                scope.launch {
                                    val result = integrationHooks.onTestConnection.invoke()
                                    connectionNotice = result.fold(
                                        onSuccess = { it.ifBlank { "Gemini connection succeeded." } },
                                        onFailure = { error -> error.message ?: "Gemini connection failed. Check the key, internet, and quota." },
                                    )
                                }
                            }
                        },
                        enabled = snapshot.keyConfigured && !editingKey,
                    )
                    if (connectionNotice.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            connectionNotice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (connectionNotice.startsWith("Gemini connection succeeded")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A connection test is optional. Setup can finish without it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Transcription")
            Spacer(Modifier.height(10.dp))
            VoiceSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Cleanup style", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            label = "Smart",
                            selected = snapshot.settings.mode == TranscriptionMode.SMART,
                            onClick = { onUpdateSettings { it.copy(mode = TranscriptionMode.SMART) } },
                            modifier = Modifier.weight(1f),
                        )
                        ChoiceChip(
                            label = "Verbatim",
                            selected = snapshot.settings.mode == TranscriptionMode.VERBATIM,
                            onClick = { onUpdateSettings { it.copy(mode = TranscriptionMode.VERBATIM) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (snapshot.settings.mode == TranscriptionMode.SMART) "Cleans filler words, corrections, punctuation, and capitalization naturally." else "Keeps the spoken wording as close to the captured transcript as possible.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    SettingRow(
                        title = "Language",
                        detail = if (manualLanguage && languageDraft.isNotBlank()) languageDraft else "Auto-detect",
                        icon = VoiceIcon.Globe,
                    ) {
                        Switch(
                            checked = manualLanguage,
                            onCheckedChange = { selected ->
                                manualLanguage = selected
                                if (!selected) {
                                    languageDraft = ""
                                    onUpdateSettings { it.copy(languageCode = "") }
                                }
                            },
                            modifier = Modifier.semantics { contentDescription = "Use a manual language" },
                        )
                    }
                    if (manualLanguage) {
                        OutlinedTextField(
                            value = languageDraft,
                            onValueChange = { languageDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Language code") },
                            placeholder = { Text("en-US") },
                            singleLine = true,
                            supportingText = { Text("Use a BCP-47 code, or leave Auto-detect on.") },
                        )
                        TextButton(
                            onClick = { onUpdateSettings { it.copy(languageCode = languageDraft.trim()) } },
                            modifier = Modifier.align(Alignment.End).height(48.dp),
                        ) { Text("Save language") }
                    }
                    SettingDivider()
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vocabularyDraft,
                        onValueChange = { vocabularyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom vocabulary") },
                        placeholder = { Text("Names or terms, one per line") },
                        minLines = 3,
                        supportingText = { Text("Optional. Up to 1,000 terms; audio is never stored.") },
                    )
                    TextButton(
                        onClick = {
                            onUpdateSettings {
                                it.copy(customVocabulary = parseList(vocabularyDraft))
                            }
                        },
                        modifier = Modifier.align(Alignment.End).height(48.dp),
                    ) { Text("Save vocabulary") }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Bubble")
            Spacer(Modifier.height(10.dp))
            VoiceSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SettingRow(
                        title = "Contextual bubble",
                        detail = if (snapshot.settings.bubbleEnabled) "Available in eligible fields" else "Disabled",
                        icon = VoiceIcon.Bubble,
                    ) {
                        Switch(
                            checked = snapshot.settings.bubbleEnabled,
                            onCheckedChange = { checked -> onUpdateSettings { it.copy(bubbleEnabled = checked) } },
                            modifier = Modifier.semantics { contentDescription = "Toggle contextual dictation bubble" },
                        )
                    }
                    SettingDivider()
                    SettingSlider(
                        label = "Bubble size",
                        valueLabel = "${snapshot.settings.bubbleSizeDp} dp",
                        value = snapshot.settings.bubbleSizeDp.toFloat(),
                        valueRange = 48f..76f,
                        onValueChangeFinished = { value -> onUpdateSettings { it.copy(bubbleSizeDp = value.toInt()) } },
                    )
                    SettingSlider(
                        label = "Bubble opacity",
                        valueLabel = "${(snapshot.settings.bubbleOpacity * 100).toInt()}%",
                        value = snapshot.settings.bubbleOpacity,
                        valueRange = .55f..1f,
                        onValueChangeFinished = { value -> onUpdateSettings { it.copy(bubbleOpacity = value) } },
                    )
                    SettingRow(
                        title = "Haptics",
                        detail = "Short feedback for start, stop, and errors",
                        icon = VoiceIcon.Hand,
                    ) {
                        Switch(
                            checked = snapshot.settings.haptics,
                            onCheckedChange = { checked -> onUpdateSettings { it.copy(haptics = checked) } },
                            modifier = Modifier.semantics { contentDescription = "Toggle dictation haptics" },
                        )
                    }
                    SettingDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("Default snooze duration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Snooze hides the contextual bubble temporarily; it does not revoke permissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SnoozeChoices(
                        selected = snapshot.settings.snoozeMinutes,
                        onSelected = { minutes -> onUpdateSettings { it.copy(snoozeMinutes = minutes) } },
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Privacy and diagnostics")
            Spacer(Modifier.height(10.dp))
            VoiceSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SettingRow(
                        title = "Transcript history",
                        detail = "On-device text only; audio is never saved",
                        icon = VoiceIcon.History,
                    ) {
                        Switch(
                            checked = snapshot.settings.historyEnabled,
                            onCheckedChange = { checked -> onUpdateSettings { it.copy(historyEnabled = checked) } },
                            modifier = Modifier.semantics { contentDescription = "Toggle on-device transcript history" },
                        )
                    }
                    SettingDivider()
                    SettingRow(
                        title = "Diagnostics",
                        detail = "Local troubleshooting events without API keys or screen contents",
                        icon = VoiceIcon.Sliders,
                    ) {
                        Switch(
                            checked = snapshot.settings.diagnosticsEnabled,
                            onCheckedChange = { checked -> onUpdateSettings { it.copy(diagnosticsEnabled = checked) } },
                            modifier = Modifier.semantics { contentDescription = "Toggle local diagnostics" },
                        )
                    }
                    SettingDivider()
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = excludedDraft,
                        onValueChange = { excludedDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Excluded apps") },
                        placeholder = { Text("Package names, one per line") },
                        minLines = 2,
                        supportingText = { Text("The bubble never appears in these packages.") },
                    )
                    TextButton(
                        onClick = { onUpdateSettings { it.copy(excludedPackages = parseList(excludedDraft).toSet()) } },
                        modifier = Modifier.align(Alignment.End).height(48.dp),
                    ) { Text("Save excluded apps") }
                    AccessNotice("Accessibility is limited to eligible editable fields and cursor insertion. Screen contents are not uploaded to Gemini.")
                    if (snapshot.settings.diagnosticsEnabled && snapshot.diagnostics.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Recent diagnostics", style = MaterialTheme.typography.titleMedium)
                        snapshot.diagnostics.take(6).forEach { line ->
                            Text("• ${line.substringAfter('|')}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ValuePill(valueLabel)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue) },
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$label, current value $valueLabel" },
        )
    }
}

@Composable
private fun SnoozeChoices(
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val options = listOf(5 to "5 min", 30 to "30 min", 60 to "1 hour", 1440 to "Until tomorrow")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowOptions.forEach { (minutes, label) ->
                    FilterChip(
                        selected = selected == minutes,
                        onClick = { onSelected(minutes) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    )
                }
                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VoiceMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        VoiceGlyph(VoiceIcon.VoxtaMark, MaterialTheme.colorScheme.primary, size = 25.dp)
    }
}

private fun parseList(raw: String): List<String> = raw
    .split(',', '\n')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .take(1000)

private fun formatSnoozeTime(until: Long): String = android.text.format.DateFormat.format("EEE, h:mm a", until).toString()

private fun openOverlaySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
