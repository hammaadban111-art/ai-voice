package com.hammaad.voiceappv4.accessibility

import android.text.InputType

object FieldPolicy {
    private val builtInExcluded = setOf(
        "com.hammaad.voiceappv4",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    fun isAllowed(
        packageName: String,
        editable: Boolean,
        password: Boolean,
        inputType: Int,
        userExcluded: Set<String>,
    ): Boolean {
        if (!editable || password || packageName in builtInExcluded || packageName in userExcluded) return false
        val variation = inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION)
        val sensitive = setOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )
        return variation !in sensitive
    }
}
