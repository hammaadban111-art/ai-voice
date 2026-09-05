package com.dictate.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dictate.app.asDictateApp
import com.dictate.app.core.DictationTestState
import com.dictate.app.core.PermissionState
import com.dictate.app.data.settings.DictateSettings
import com.dictate.app.overlay.BubbleOverlayService
import kotlinx.coroutines.launch

private data class Step(val title: String, val description: String, val isDone: () -> Boolean)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.asDictateApp()
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settings.collectAsState(initial = DictateSettings())
    val testSucceeded by DictationTestState.succeeded.collectAsState()

    var refreshTick by remember { mutableIntStateOf(0) }
    var accessibilitySettingsOpened by remember { mutableStateOf(false) }

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
    val accessibilityEnabled = remember(refreshTick) { PermissionState.isAccessibilityServiceEnabled(context) }
    val accessibilityInstalled = remember(refreshTick) { PermissionState.isAccessibilityServiceInstalled(context) }
    val accessibilityBlocked = accessibilitySettingsOpened && accessibilityInstalled && !accessibilityEnabled

    val steps = listOf(
        Step("Microphone", "Dictate needs your microphone to hear what you say.") {
            PermissionState.hasMicrophone(context)
        },
        Step("Accessibility service", "Lets Dictate see which text field is focused and type into it.") {
            accessibilityEnabled
        },
        Step("Display over other apps", "Lets the mic bubble float on top of WhatsApp, Chrome, and everything else.") {
            PermissionState.canDrawOverlays(context)
        },
        Step("Turn on the bubble", "Enable the floating mic bubble.") {
            settings.bubbleEnabled
        },
        Step("Test dictation", "Tap the text field below, then tap the bubble and try speaking.") {
            testSucceeded
        },
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                0 -> Button(onClick = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant microphone access")
                }
                1 -> {
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
                2 -> Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Allow display over apps") }
                3 -> Button(onClick = {
                    if (!PermissionState.hasNotifications(context) &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    ) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    scope.launch { app.settingsRepository.setBubbleEnabled(true) }
                    BubbleOverlayService.start(context)
                }) { Text("Enable the bubble") }
                4 -> OutlinedTextField(
                    value = testFieldValue,
                    onValueChange = { testFieldValue = it },
                    label = { Text("Tap here, then tap the bubble") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val currentDone = steps.getOrNull(stepIndex)?.isDone?.invoke() == true
            Button(
                onClick = {
                    if (stepIndex < steps.lastIndex) stepIndex++ else onFinished()
                },
                enabled = currentDone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (stepIndex < steps.lastIndex) "Next" else "Finish")
            }
            TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
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
