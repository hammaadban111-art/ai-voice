package com.dictate.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dictate.app.asDictateApp
import com.dictate.app.core.PermissionState
import com.dictate.app.data.settings.DictateSettings
import com.dictate.app.overlay.BubbleOverlayService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onRedoOnboarding: () -> Unit) {
    val context = LocalContext.current
    val app = context.asDictateApp()
    val settings by app.settingsRepository.settings.collectAsState(initial = DictateSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictate") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Floating mic bubble", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (settings.bubbleEnabled) "On — appears over text fields" else "Off",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.bubbleEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { app.settingsRepository.setBubbleEnabled(enabled) }
                            if (enabled) BubbleOverlayService.start(context) else BubbleOverlayService.stop(context)
                        },
                    )
                }
            }

            val hasKey = app.secureKeyStore.hasApiKey()
            StatusCard(title = "Gemini API key", ok = hasKey, okText = "Saved", badText = "Not set — add it in Settings")
            StatusCard(
                title = "Microphone",
                ok = PermissionState.hasMicrophone(context),
                okText = "Granted",
                badText = "Not granted",
            )
            StatusCard(
                title = "Accessibility service",
                ok = PermissionState.isAccessibilityServiceEnabled(context),
                okText = "Enabled",
                badText = "Disabled — needed to insert text",
            )
            StatusCard(
                title = "Display over other apps",
                ok = PermissionState.canDrawOverlays(context),
                okText = "Allowed",
                badText = "Not allowed",
            )

            TextButton(onClick = onRedoOnboarding) { Text("Re-run setup") }
        }
    }
}

@Composable
private fun StatusCard(title: String, ok: Boolean, okText: String, badText: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (ok) okText else badText,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
