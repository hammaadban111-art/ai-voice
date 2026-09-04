package com.aivoice.flow

import android.content.Context

/** Small persisted settings bag (language choice, bubble position). */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("aivoice", Context.MODE_PRIVATE)

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, Language.AUTO.code) ?: Language.AUTO.code
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var bubbleX: Int
        get() = prefs.getInt(KEY_BUBBLE_X, -1)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_X, value).apply()

    var bubbleY: Int
        get() = prefs.getInt(KEY_BUBBLE_Y, -1)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_Y, value).apply()

    /**
     * Languages the app offers.
     *
     * whisper can auto-detect, but Hindi and Urdu share a lot of vocabulary
     * and only differ in script, so on short utterances detection flips
     * between them. Pinning the language is what makes those two reliable.
     */
    enum class Language(val code: String, val label: String) {
        AUTO("auto", "Auto-detect"),
        ENGLISH("en", "English"),
        HINDI("hi", "हिन्दी (Hindi)"),
        URDU("ur", "اردو (Urdu)");

        companion object {
            fun fromCode(code: String): Language =
                entries.firstOrNull { it.code == code } ?: AUTO
        }
    }

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
    }
}
