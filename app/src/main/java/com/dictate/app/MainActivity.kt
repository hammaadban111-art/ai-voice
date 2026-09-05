package com.dictate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dictate.app.core.PermissionState
import com.dictate.app.ui.home.HomeScreen
import com.dictate.app.ui.onboarding.OnboardingScreen
import com.dictate.app.ui.settings.SettingsScreen
import com.dictate.app.ui.theme.DictateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DictateTheme {
                val navController = rememberNavController()
                var onboardingDone by remember {
                    mutableStateOf(PermissionState.allGranted(this))
                }
                NavHost(navController = navController, startDestination = if (onboardingDone) "home" else "onboarding") {
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinished = {
                                onboardingDone = true
                                navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                            },
                        )
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
