package com.banglu.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID

data class AuthSession(
    val userId: String,
    val name: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String
)

class AuthSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("banglu_prefs", Context.MODE_PRIVATE)

    init {
        BangluProcessGuards.requireUiProcess(appContext, "AuthSessionStore")
    }

    fun current(): AuthSession? {
        val userId = prefs.getString("auth_user_id", null).orEmpty()
        val email = prefs.getString("auth_email", null).orEmpty()
        val accessToken = getSecureString("auth_access_token")
        val refreshToken = getSecureString("auth_refresh_token")
        if (userId.isBlank() || email.isBlank() || accessToken.isBlank() || refreshToken.isBlank()) return null
        migrateLegacyTokenIfNeeded("auth_access_token", accessToken)
        migrateLegacyTokenIfNeeded("auth_refresh_token", refreshToken)
        return AuthSession(
            userId = userId,
            name = prefs.getString("auth_name", null).orEmpty(),
            email = email,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    fun save(session: AuthSession) {
        prefs.edit()
            .putString("auth_user_id", session.userId)
            .putString("auth_name", session.name)
            .putString("auth_email", session.email)
            .putEncryptedString("auth_access_token", session.accessToken)
            .putEncryptedString("auth_refresh_token", session.refreshToken)
            .apply()
    }

    fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putEncryptedString("auth_access_token", accessToken)
            .putEncryptedString("auth_refresh_token", refreshToken)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove("auth_user_id")
            .remove("auth_name")
            .remove("auth_email")
            .remove("auth_access_token")
            .remove("auth_refresh_token")
            .putString("subscription_plan", "free")
            .apply()
    }

    fun deviceId(): String {
        val existing = prefs.getString("auth_device_id", null).orEmpty()
        if (existing.isNotBlank() && !LEGACY_ANDROID_ID_PATTERN.matches(existing)) return existing

        val generated = "android_${UUID.randomUUID()}"
        prefs.edit().putString("auth_device_id", generated).apply()
        return generated
    }

    fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun SharedPreferences.Editor.putEncryptedString(key: String, value: String): SharedPreferences.Editor {
        return putString(key, encrypt(value))
    }

    private fun getSecureString(key: String): String {
        val stored = prefs.getString(key, null).orEmpty()
        if (stored.isBlank()) return ""
        if (!stored.startsWith(ENCRYPTED_PREFIX)) return stored
        return decrypt(stored).orEmpty()
    }

    private fun migrateLegacyTokenIfNeeded(key: String, plaintext: String) {
        val stored = prefs.getString(key, null).orEmpty()
        if (stored.isBlank() || stored.startsWith(ENCRYPTED_PREFIX)) return
        runCatching {
            prefs.edit().putEncryptedString(key, plaintext).apply()
        }.onFailure { error ->
            Log.w(TAG, "Failed to migrate auth token to encrypted storage", error)
        }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return ENCRYPTED_PREFIX +
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? {
        return runCatching {
            val parts = stored.removePrefix(ENCRYPTED_PREFIX).split(":", limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.onFailure { error ->
            Log.w(TAG, "Failed to decrypt auth token", error)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val TAG = "AuthSessionStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "banglu_auth_session_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val ENCRYPTED_PREFIX = "enc:v1:"
        private val LEGACY_ANDROID_ID_PATTERN = Regex("^android_[0-9a-fA-F]{16}$")
    }
}
