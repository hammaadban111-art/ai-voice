package com.dictate.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.dictate.app.accessibility.DictationAccessibilityService

object PermissionState {

    private fun expectedServiceId(context: Context) =
        "${context.packageName}/${DictationAccessibilityService::class.java.name}"

    fun hasMicrophone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (DictationAccessibilityService.isEnabled) return true
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = expectedServiceId(context)
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    /**
     * True once Android knows about the service at all (it shows up under
     * Accessibility > Downloaded apps), regardless of whether it's toggled
     * on. Used to tell "not enabled yet" apart from "enabled but blocked by
     * Android's restricted-settings guard for sideloaded apps" — a state
     * Android exposes no direct API to query, so we infer it from this plus
     * the user having already visited Accessibility settings once.
     */
    fun isAccessibilityServiceInstalled(context: Context): Boolean {
        if (DictationAccessibilityService.isEnabled) return true
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = expectedServiceId(context)
        return manager.installedAccessibilityServiceList.any { it.id.equals(expected, ignoreCase = true) }
    }

    fun allGranted(context: Context): Boolean =
        hasMicrophone(context) && hasNotifications(context) && canDrawOverlays(context) && isAccessibilityServiceEnabled(context)
}
