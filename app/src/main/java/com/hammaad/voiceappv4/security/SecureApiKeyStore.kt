package com.hammaad.voiceappv4.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_key", Context.MODE_PRIVATE)

    fun hasKey(): Boolean = get().isNotBlank()

    fun get(): String {
        val payload = prefs.getString("gemini_api_key", null) ?: return ""
        return runCatching {
            val all = Base64.decode(payload, Base64.NO_WRAP)
            val ivLength = all[0].toInt() and 0xff
            val iv = all.copyOfRange(1, 1 + ivLength)
            val encrypted = all.copyOfRange(1 + ivLength, all.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun save(value: String) {
        val trimmed = value.trim()
        if (trimmed.isBlank()) { clear(); return }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val packed = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + encrypted
        prefs.edit().putString("gemini_api_key", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun clear() = prefs.edit().remove("gemini_api_key").apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    companion object {
        private const val ALIAS = "voice_app_v4_gemini_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

