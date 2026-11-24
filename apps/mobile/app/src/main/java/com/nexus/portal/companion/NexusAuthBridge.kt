package com.nexus.portal.companion

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AuthSession(
    val isAuthenticated: Boolean,
    val userEmail: String?,
    val displayName: String?,
    val accessToken: String?,
    val deviceId: String
)

/**
 * NexusAuthBridge provides hardware-backed encrypted storage for Nexus Fleet credentials,
 * JWT access tokens, and device identifiers on Android using EncryptedSharedPreferences.
 */
@Singleton
class NexusAuthBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "NexusAuthBridge"
    private val prefsFile = "nexus_secure_auth_store"

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                prefsFile,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize EncryptedSharedPreferences, falling back to private mode: ${e.message}")
            context.getSharedPreferences(prefsFile, Context.MODE_PRIVATE)
        }
    }

    private val _sessionState = MutableStateFlow(loadCurrentSession())
    val sessionState: StateFlow<AuthSession> = _sessionState.asStateFlow()

    private fun loadCurrentSession(): AuthSession {
        val deviceId = prefs.getString("device_id", null) ?: run {
            val newId = "android_" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString("device_id", newId).apply()
            newId
        }

        val token = prefs.getString("access_token", null)
        val email = prefs.getString("user_email", null)
        val name = prefs.getString("display_name", null)

        return AuthSession(
            isAuthenticated = !token.isNullOrBlank(),
            userEmail = email,
            displayName = name,
            accessToken = token,
            deviceId = deviceId
        )
    }

    fun getDeviceId(): String {
        return sessionState.value.deviceId
    }

    fun getAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun saveSession(
        accessToken: String,
        refreshToken: String?,
        userEmail: String,
        displayName: String?
    ) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_email", userEmail)
            .putString("display_name", displayName)
            .apply()

        _sessionState.value = AuthSession(
            isAuthenticated = true,
            userEmail = userEmail,
            displayName = displayName,
            accessToken = accessToken,
            deviceId = getDeviceId()
        )
        Log.i(tag, "Saved authenticated session for user: $userEmail")
    }

    fun clearSession() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_email")
            .remove("display_name")
            .apply()

        _sessionState.value = AuthSession(
            isAuthenticated = false,
            userEmail = null,
            displayName = null,
            accessToken = null,
            deviceId = getDeviceId()
        )
        Log.i(tag, "Cleared authentication session.")
    }
}
