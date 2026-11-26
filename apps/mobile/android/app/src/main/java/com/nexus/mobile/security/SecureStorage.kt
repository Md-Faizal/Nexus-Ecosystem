package com.nexus.mobile.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "nexus_secure_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = "nexus-android-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun getOrCreatePinSalt(): String {
        val existing = prefs.getString(KEY_PIN_SALT, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_PIN_SALT, created).apply()
        return created
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userDisplayName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userUsername: String?
        get() = prefs.getString(KEY_USER_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USER_USERNAME, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userRole: String?
        get() = prefs.getString(KEY_USER_ROLE, null)
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value).apply()

    var userAvatarUrl: String?
        get() = prefs.getString(KEY_USER_AVATAR, null)
        set(value) = prefs.edit().putString(KEY_USER_AVATAR, value).apply()

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var biometricPromptMode: String
        get() = prefs.getString(KEY_BIOMETRIC_PROMPT_MODE, "ask_next_time") ?: "ask_next_time"
        set(value) = prefs.edit().putString(KEY_BIOMETRIC_PROMPT_MODE, value).apply()

    var hasCentralPin: Boolean
        get() = prefs.getBoolean(KEY_HAS_CENTRAL_PIN, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_CENTRAL_PIN, value).apply()

    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, value).apply()

    var lockoutUntilEpochMs: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKOUT_UNTIL, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var useDynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    var isPageAuthEnabled: Boolean
        get() = prefs.getBoolean(KEY_PAGE_AUTH_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PAGE_AUTH_ENABLED, value).apply()

    fun hasPinConfigured(): Boolean = !pinHash.isNullOrBlank()
    fun isAuthenticated(): Boolean = !accessToken.isNullOrBlank()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_USERNAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_AVATAR)
            .remove(KEY_HAS_CENTRAL_PIN)
            .apply()
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_USERNAME = "user_username"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_ROLE = "user_role"
        const val KEY_USER_AVATAR = "user_avatar"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_BIOMETRIC_PROMPT_MODE = "biometric_prompt_mode"
        const val KEY_HAS_CENTRAL_PIN = "has_central_pin"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until_ms"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_PAGE_AUTH_ENABLED = "page_auth_enabled"
    }
}
