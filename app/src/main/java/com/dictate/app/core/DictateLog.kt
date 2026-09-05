package com.dictate.app.core

import android.util.Log
import com.dictate.app.BuildConfig

/**
 * Debug-only logging for state transitions and gesture decisions. Never
 * pass API keys, audio bytes, authorization headers, or transcript text
 * to this — only class/state names and booleans, so a logcat capture is
 * never a privacy leak even by accident.
 */
object DictateLog {
    private const val TAG = "Dictate"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
