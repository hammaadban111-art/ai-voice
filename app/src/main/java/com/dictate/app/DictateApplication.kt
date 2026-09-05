package com.dictate.app

import android.app.Application
import android.content.Context
import com.dictate.app.data.history.TranscriptHistoryStore
import com.dictate.app.data.security.SecureKeyStore
import com.dictate.app.data.settings.SettingsRepository

/** Simple manual service locator: no DI framework needed for this app's size. */
class DictateApplication : Application() {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore(this) }
    val historyStore: TranscriptHistoryStore by lazy { TranscriptHistoryStore(this) }
}

fun Context.asDictateApp(): DictateApplication = applicationContext as DictateApplication
