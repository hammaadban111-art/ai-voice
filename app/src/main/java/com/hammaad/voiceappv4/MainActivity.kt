package com.hammaad.voiceappv4

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hammaad.voiceappv4.network.GeminiApi
import com.hammaad.voiceappv4.security.SecureApiKeyStore
import com.hammaad.voiceappv4.ui.VoiceApp
import com.hammaad.voiceappv4.ui.VoiceIntegrationHooks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyStore = SecureApiKeyStore(this)
        val gemini = GeminiApi()
        setContent {
            VoiceApp(integrationHooks = VoiceIntegrationHooks(
                onOptionalDictationTest = { startActivity(Intent(this, TestDictationActivity::class.java)) },
                onTestConnection = {
                    gemini.testKey(keyStore.get()).map { "Gemini connection succeeded." }
                },
            ))
        }
    }
}
