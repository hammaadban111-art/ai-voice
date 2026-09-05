package com.dictate.app.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dictate.app.DictateApplication
import com.dictate.app.asDictateApp
import com.dictate.app.core.DictationTestState
import com.dictate.app.core.PermissionState
import com.dictate.app.core.SetupStatus
import com.dictate.app.data.settings.DictateSettings
import com.dictate.app.gemini.GeminiRestClient
import com.dictate.app.overlay.BubbleOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Step(val title: String, val description: String, val isDone: () -> Boolean)

private const val STEP_MIC = 0
private const val STEP_ACCESSIBILITY = 1
private const val STEP_OVERLAY = 2
private const val STEP_API_KEY = 3
private const val STEP_BUBBLE = 4
private const val STEP_TEST = 5

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.asDictateApp()
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settings.collectAsState(initial = DictateSettings())
    val testSucceeded by DictationTestState.succeeded.collectAsState()

    var refreshTick by remember { mutableIntStateOf(0) }
    var accessibilitySettingsOpened by remember { mutableStateOf(false) }
    var apiKeyField by remember { mutableStateOf(app.secureKeyStore.getApiKey().orEmpty()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }

    var stepIndex by remember { mutableIntStateOf(0) }
    var testFieldValue by remember { mutableStateOf("") }

    // Re-read live system state (not a cached/local flag) whenever the
    // screen resumes, so a checklist item is never checked off based on a
    // stale snapshot or a value the user merely tapped a button for.
    val micGranted = remember(refreshTick) { PermissionState.hasMicrophone(context) }
    val accessibilityEnabled = remember(refreshTick) { PermissionState.isAccessibilityServiceEnabled(context) }
    val accessibilityInstalled = remember(refreshTick) { PermissionState.isAccessibilityServiceInstalled(context) }
    val overlayGranted = remember(refreshTick) { PermissionState.canDrawOverlays(context) }
    val accessibilityBlocked = accessibilitySettingsOpened && accessibilityInstalled && !accessibilityEnabled
    val apiKeyPresent = app.secureKeyStore.hasApiKey()

    val setupStatus = SetupStatus(
        microphoneGranted = micGranted,
        accessibilityEnabled = accessibilityEnabled,
        overlayGranted = overlayGranted,
        apiKeyPresent = apiKeyPresent,
        bubbleFeatureEnabled = settings.bubbleEnabled,
    )

    fun finish() {
        if (setupStatus.requiredComplete) {
            scope.launch { app.settingsRepository.setOnboardingComplete(true) }
        }
        onFinished()
    }

    val steps = listOf(
        Step("Microphone", "Dictate needs your microphone to hear what you say.") { micGranted },
        Step("Accessibility service", "Lets Dictate see which text field is focused and type into it.") {
            accessibilityEnabled
        },
        Step("Display over other apps", "Lets the mic bubble float on top of WhatsApp, Chrome, and everything else.") {
            overlayGranted
        },
        Step("Gemini API key", "Bring your own key so Dictate can actually transcribe speech.") { apiKeyPresent },
        Step("Turn on the bubble", "Enable the floating mic bubble.") { settings.bubbleEnabled },
        Step("Test dictation (optional)", "Tap the text field below, then tap the bubble and try speaking.") {
            testSucceeded
        },
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1) / (steps.size + 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Set up Dictate", style = MaterialTheme.typography.headlineSmall)

            steps.forEachIndexed { index, step ->
                StepRow(step, isCurrent = index == stepIndex)
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (stepIndex) {
                STEP_MIC -> Button(onClick = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant microphone access")
                }
                STEP_ACCESSIBILITY -> {
                    Button(onClick = {
                        accessibilitySettingsOpened = true
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Open Accessibility settings") }

                    if (accessibilityBlocked) {
                        Spacer(modifier = Modifier.height(12.dp))
                        RestrictedSettingsNotice(
                            onOpenAppInfo = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            },
                            onOpenAccessibility = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                        )
                    }
                }
                STEP_OVERLAY -> Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Allow display over apps") }
                STEP_API_KEY -> ApiKeyStep(
                    app = app,
                    scope = scope,
                    apiKeyField = apiKeyField,
                    onApiKeyFieldChange = { apiKeyField = it },
                )
                STEP_BUBBLE -> Button(onClick = {
                    if (!PermissionState.hasNotifications(context) &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    ) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    scope.launch { app.settingsRepository.setBubbleEnabled(true) }
                    BubbleOverlayService.start(context)
                }) { Text("Enable the bubble") }
                STEP_TEST -> {
                    OutlinedTextField(
                        value = testFieldValue,
                        onValueChange = { testFieldValue = it },
                        label = { Text("Tap here, then tap the bubble") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!apiKeyPresent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Add a Gemini API key first — go back to the API key step above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (stepIndex == STEP_TEST) {
                // Test dictation is genuinely optional: this button is
                // always enabled once the required steps are behind it —
                // a successful test is never a precondition for finishing.
                Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
                    Text("Finish setup")
                }
            } else {
                val currentDone = steps.getOrNull(stepIndex)?.isDone?.invoke() == true
                Button(
                    onClick = { if (stepIndex < steps.lastIndex) stepIndex++ },
                    enabled = currentDone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Next")
                }
            }
            TextButton(onClick = ::finish, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun ApiKeyStep(
    app: DictateApplication,
    scope: CoroutineScope,
    apiKeyField: String,
    onApiKeyFieldChange: (String) -> Unit,
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Get a free key from Google AI Studio, then paste it below. It's encrypted on-device and never leaves your phone except to call Gemini directly.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = apiKeyField,
            onValueChange = { onApiKeyFieldChange(it); status = null },
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
                clipboard.primaryClip?.getItemAt(0)?.text?.let { onApiKeyFieldChange(it.toString()) }
            }) { Icon(Icons.Filled.ContentPaste, null); Text(" Paste") }

            Button(onClick = {
                app.secureKeyStore.saveApiKey(apiKeyField.trim())
                status = "Saved"
            }, enabled = apiKeyField.isNotBlank()) { Text("Save") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = apiKeyField.isNotBlank() && !testing,
                onClick = {
                    testing = true
                    status = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { GeminiRestClient().testConnection(apiKeyField.trim()) }
                        status = result.fold({ "Connection OK" }, { "Failed: ${it.message}" })
                        testing = false
                    }
                },
            ) { Text(if (testing) "Testing…" else "Test connection") }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (apiKeyField.isBlank()) {
            Text(
                "Add Gemini API key first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RestrictedSettingsNotice(onOpenAppInfo: () -> Unit, onOpenAccessibility: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "Accessibility is blocked by Android",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                "Android sees the Dictate service, but the toggle is greyed out. This is Android's " +
                    "\"restricted settings\" protection for apps installed outside an app store (sideloaded " +
                    "APKs, including this one) — it isn't a bug in Dictate, and it can't be bypassed from " +
                    "inside the app.\n\n" +
                    "On OnePlus / OxygenOS:\n" +
                    "1. Tap \"Open App Info\" below.\n" +
                    "2. Tap the ⋮ menu in the top-right corner.\n" +
                    "3. Tap \"Allow restricted settings\" and confirm.\n" +
                    "4. Tap \"Open Accessibility settings\", find Dictate under Downloaded apps, and turn it on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenAppInfo) { Text("Open App Info") }
                OutlinedButton(onClick = onOpenAccessibility) { Text("Open Accessibility") }
            }
        }
    }
}

@Composable
private fun StepRow(step: Step, isCurrent: Boolean) {
    val done = step.isDone()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                step.title,
                style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            )
            if (isCurrent) {
                Text(step.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
