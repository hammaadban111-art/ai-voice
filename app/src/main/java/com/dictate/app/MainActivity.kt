package com.dictate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dictate.app.ui.home.HomeScreen
import com.dictate.app.ui.onboarding.OnboardingScreen
import com.dictate.app.ui.settings.SettingsScreen
import com.dictate.app.ui.theme.DictateTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DictateTheme {
                val app = asDictateApp()
                // Read the *persisted* completion flag, not a live permission
                // snapshot: once onboarding has genuinely finished, relaunching
                // the app must go straight to Home, never back to setup —
                // Home/Settings already show live status if something regresses.
                val onboardingComplete by produceState<Boolean?>(initialValue = null) {
                    value = app.settingsRepository.settings.first().onboardingComplete
                }

                if (onboardingComplete == null) {
                    Scaffold { padding -> Box(modifier = Modifier.fillMaxSize().padding(padding)) }
                    return@DictateTheme
                }

                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = if (onboardingComplete == true) "home" else "onboarding",
                ) {
                    composable("onboarding") {
                        OnboardingScreen(onFinished = {
                            navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        })
                    }
                    composable("home") {
                        HomeScreen(
                            onOpenSettings = { navController.navigate("settings") },
                            onRedoOnboarding = { navController.navigate("onboarding") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
