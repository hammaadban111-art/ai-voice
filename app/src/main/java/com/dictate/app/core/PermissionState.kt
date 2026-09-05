package com.dictate.app.core

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.dictate.app.accessibility.DictationAccessibilityService

/**
 * Every permission/feature status check in the app goes through here, and
 * every one of them re-derives the answer from the actual Android/system
 * API on each call — nothing is cached, and nothing is inferred from
 * whether the user merely opened a Settings screen. This is deliberate:
 * an earlier version short-circuited the accessibility check with an
 * in-process "is my Service object currently alive" flag, which could
 * disagree with Settings' own answer for a window after the user toggles
 * the service off (the OS doesn't always tear the Service down the
 * instant the toggle flips), producing exactly the kind of "onboarding
 * says enabled, Home says disabled" inconsistency this object exists to
 * prevent. [AccessibilityManager.getEnabledAccessibilityServiceList] is
 * the OS's own authoritative answer, so that's the only thing consulted.
 */
object PermissionState {

    private fun expectedComponent(context: Context) =
        ComponentName(context.packageName, DictationAccessibilityService::class.java.name)

    private fun componentOf(info: AccessibilityServiceInfo): ComponentName? =
        info.resolveInfo?.serviceInfo?.let { ComponentName(it.packageName, it.name) }

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
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = expectedComponent(context)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { componentOf(it) == expected }
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
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = expectedComponent(context)
        return manager.installedAccessibilityServiceList.any { componentOf(it) == expected }
    }

    fun allGranted(context: Context): Boolean =
        hasMicrophone(context) && hasNotifications(context) && canDrawOverlays(context) && isAccessibilityServiceEnabled(context)
}
