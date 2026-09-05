package com.dictate.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dictate.app.core.PermissionState
import com.dictate.app.overlay.BubbleOverlayService

private data class Step(val title: String, val description: String, val isDone: () -> Boolean)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

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
    var bubbleEnabledLocally by remember { mutableStateOf(false) }
    var testFieldValue by remember { mutableStateOf("") }

    val steps = remember(refreshTick, bubbleEnabledLocally) {
        listOf(
            Step("Microphone", "Dictate needs your microphone to hear what you say.") {
                PermissionState.hasMicrophone(context)
            },
            Step("Accessibility service", "Lets Dictate see which text field is focused and type into it.") {
                PermissionState.isAccessibilityServiceEnabled(context)
            },
            Step("Display over other apps", "Lets the mic bubble float on top of WhatsApp, Chrome, and everything else.") {
                PermissionState.canDrawOverlays(context)
            },
            Step("Turn on the bubble", "Enable the floating mic bubble.") { bubbleEnabledLocally },
            Step("Test dictation", "Tap the text field below, then tap the bubble and try speaking.") { true },
        )
    }

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
                1 -> Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open Accessibility settings") }
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
                    BubbleOverlayService.start(context)
                    bubbleEnabledLocally = true
                    refreshTick++
                }) { Text("Enable the bubble") }
                4 -> androidx.compose.material3.OutlinedTextField(
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
private fun StepRow(step: Step, isCurrent: Boolean) {
    val done = step.isDone()
    androidx.compose.foundation.layout.Row(
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
