package com.hammaad.voiceappv4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.hammaad.voiceappv4.audio.PcmAudioRecorder
import com.hammaad.voiceappv4.data.AppPreferences
import com.hammaad.voiceappv4.network.GeminiApi
import com.hammaad.voiceappv4.security.SecureApiKeyStore
import com.hammaad.voiceappv4.ui.VoiceTheme
import kotlinx.coroutines.launch

class TestDictationActivity : ComponentActivity() {
    private var recorder: PcmAudioRecorder? = null
    private var recording by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var level by mutableFloatStateOf(0f)
    private var result by mutableStateOf("This optional test records only between Start and Stop.")
    private val api = GeminiApi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoiceTheme { TestScreen() } }
    }

    override fun onDestroy() {
        recorder?.cancel()
        recorder = null
        super.onDestroy()
    }

    private fun toggle() {
        if (busy) return
        if (!recording) {
            val capture = PcmAudioRecorder(this)
            val started = capture.start(
                onChunk = {},
                onLevel = { value -> runOnUiThread { level = value } },
                onError = { message -> runOnUiThread { result = message; recording = false } },
            )
            if (started) {
                recorder = capture
                recording = true
                result = "Listening… speak naturally, then tap Stop."
            }
        } else {
            val capture = recorder?.stop()
            recorder = null
            recording = false
            busy = true
            result = "Transcribing with Gemini…"
            lifecycleScope.launch {
                runCatching {
                    api.batchTranscribe(
                        SecureApiKeyStore(this@TestDictationActivity).get(),
                        capture?.pcm ?: byteArrayOf(),
                        AppPreferences(this@TestDictationActivity).read(),
                    )
                }.onSuccess { result = it }.onFailure { result = "Test failed: ${it.message.orEmpty()}" }
                busy = false
                level = 0f
            }
        }
    }

    @Composable
    private fun TestScreen() {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Optional dictation test", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.size(104.dp)) {
                drawCircle(primary.copy(alpha = .18f + level.coerceIn(0f, 1f) * .35f), radius = size.minDimension * (.42f + level * .08f))
                drawCircle(if (recording) Color(0xFFBA1A1A) else primary, radius = size.minDimension * .31f)
                drawLine(Color.White, Offset(center.x, center.y - 17.dp.toPx()), Offset(center.x, center.y + 10.dp.toPx()), 8.dp.toPx())
            }
            Spacer(Modifier.height(18.dp))
            Card(Modifier.fillMaxWidth()) { Text(result, Modifier.padding(18.dp)) }
            Spacer(Modifier.height(18.dp))
            Button(onClick = ::toggle, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (recording) "Stop and transcribe" else if (busy) "Transcribing…" else "Start test")
            }
            OutlinedButton(onClick = { finish() }, enabled = !recording, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
    }
}
