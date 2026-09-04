package com.aivoice.flow.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * The only component that can put text into another app's input field.
 *
 * It does no event processing of its own; it exists so [TextInjector] has a
 * handle with the privileges needed to read the focused node and write to it.
 */
class FlowAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FlowA11yService"

        @Volatile
        var instance: FlowAccessibilityService? = null
            private set

        /**
         * Whether the user has turned the service on in Settings.
         *
         * [instance] alone is not enough: it is null until Android binds the
         * service, so the setup screen reads the system setting directly.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, FlowAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            for (entry in splitter) {
                val component = ComponentName.unflattenFromString(entry) ?: continue
                if (component == expected) return true
            }
            return false
        }
    }
}
