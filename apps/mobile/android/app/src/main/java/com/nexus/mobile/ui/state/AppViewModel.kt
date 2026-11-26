package com.nexus.mobile.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.mobile.data.auth.NexusAuthClient
import com.nexus.mobile.security.PinCrypto
import com.nexus.mobile.security.SecureStorage
import com.nexus.mobile.security.SessionLockManager
import com.nexus.mobile.ui.theme.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppSettingsState(
    val deviceId: String,
    val isAuthenticated: Boolean,
    val displayName: String,
    val username: String?,
    val email: String,
    val role: String,
    val avatarUrl: String?,
    val hasPin: Boolean,
    val hasCentralPin: Boolean,
    val isBiometricEnabled: Boolean,
    val biometricPromptMode: String,
    val isPageAuthEnabled: Boolean,
    val themePreference: ThemePreference,
    val useDynamicColor: Boolean,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val storage: SecureStorage,
    private val authClient: NexusAuthClient,
    val lockManager: SessionLockManager,
) : ViewModel() {

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    fun reloadFromStorage() {
        _settings.value = readSettings()
    }

    private fun readSettings(): AppSettingsState = AppSettingsState(
        deviceId = storage.getOrCreateDeviceId(),
        isAuthenticated = storage.isAuthenticated(),
        displayName = storage.userDisplayName ?: "Nexus User",
        username = storage.userUsername,
        email = storage.userEmail ?: "",
        role = storage.userRole ?: "user",
        avatarUrl = storage.userAvatarUrl,
        hasPin = storage.hasPinConfigured(),
        hasCentralPin = storage.hasCentralPin,
        isBiometricEnabled = storage.isBiometricEnabled,
        biometricPromptMode = storage.biometricPromptMode,
        isPageAuthEnabled = storage.isPageAuthEnabled,
        themePreference = ThemePreference.fromKey(storage.themeMode),
        useDynamicColor = storage.useDynamicColor,
    )

    fun saveNewPin(pin: String) {
        val salt = storage.getOrCreatePinSalt()
        storage.pinHash = PinCrypto.hashPin(pin, salt)
        storage.hasCentralPin = true
        storage.failedAttempts = 0
        storage.lockoutUntilEpochMs = 0L
        lockManager.markUnlocked()
        _pinError.value = null
        _settings.update { it.copy(hasPin = true, hasCentralPin = true) }

        val token = storage.accessToken
        if (!token.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                authClient.setupCentralPin(token, pin)
            }
        }
    }

    fun verifyCentralPinAndCache(pin: String, onResult: (Boolean, String?) -> Unit) {
        val token = storage.accessToken
        if (token.isNullOrBlank()) {
            onResult(false, "Session expired. Please sign in again.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            authClient.verifyCentralPin(token, pin)
                .onSuccess {
                    val salt = storage.getOrCreatePinSalt()
                    storage.pinHash = PinCrypto.hashPin(pin, salt)
                    storage.hasCentralPin = true
                    storage.failedAttempts = 0
                    storage.lockoutUntilEpochMs = 0L
                    lockManager.markUnlocked()
                    _pinError.value = null
                    _settings.update { it.copy(hasPin = true, hasCentralPin = true) }
                    launch(Dispatchers.Main) { onResult(true, null) }
                }
                .onFailure { err ->
                    val msg = err.message ?: "Incorrect central PIN"
                    _pinError.value = msg
                    launch(Dispatchers.Main) { onResult(false, msg) }
                }
        }
    }

    fun updateOrRemovePinWithReAuth(
        newPin: String?,
        remove: Boolean,
        password: String?,
        otpCode: String?,
        securityAnswer: String?,
        onResult: (Boolean, String?) -> Unit,
    ) {
        val token = storage.accessToken
        if (token.isNullOrBlank()) {
            onResult(false, "Session expired. Please sign in again.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            authClient.updateCentralPin(
                token = token,
                newPin = newPin,
                remove = remove,
                password = password,
                otpCode = otpCode,
                securityAnswer = securityAnswer,
            ).onSuccess {
                if (remove) {
                    storage.pinHash = null
                    storage.hasCentralPin = false
                    storage.isBiometricEnabled = false
                    _settings.update {
                        it.copy(hasPin = false, hasCentralPin = false, isBiometricEnabled = false)
                    }
                } else if (!newPin.isNullOrBlank()) {
                    val salt = storage.getOrCreatePinSalt()
                    storage.pinHash = PinCrypto.hashPin(newPin, salt)
                    storage.hasCentralPin = true
                    _settings.update { it.copy(hasPin = true, hasCentralPin = true) }
                }
                _pinError.value = null
                launch(Dispatchers.Main) { onResult(true, null) }
            }.onFailure { err ->
                launch(Dispatchers.Main) { onResult(false, err.message ?: "Verification failed") }
            }
        }
    }

    fun sendReAuthOtp(onResult: (Boolean, String?) -> Unit) {
        val email = storage.userEmail
        if (email.isNullOrBlank()) {
            onResult(false, "No account email found.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            authClient.sendOtp(email, allowCreate = true)
                .onSuccess { msg -> launch(Dispatchers.Main) { onResult(true, msg) } }
                .onFailure { err -> launch(Dispatchers.Main) { onResult(false, err.message) } }
        }
    }

    fun setBiometricPromptMode(mode: String) {
        storage.biometricPromptMode = mode
        val enabled = mode == "enabled"
        storage.isBiometricEnabled = enabled
        _settings.update { it.copy(isBiometricEnabled = enabled, biometricPromptMode = mode) }
    }

    fun removePin() {
        storage.pinHash = null
        storage.isBiometricEnabled = false
        lockManager.markUnlocked()
        _pinError.value = null
        _settings.update { it.copy(hasPin = false, isBiometricEnabled = false) }
    }

    fun verifyPin(pin: String): Boolean {
        if (System.currentTimeMillis() < storage.lockoutUntilEpochMs) {
            _pinError.value = "Locked out temporarily"
            return false
        }
        val expected = storage.pinHash ?: return true
        val salt = storage.getOrCreatePinSalt()
        return if (PinCrypto.verifyPin(pin, salt, expected)) {
            _pinError.value = null
            lockManager.markUnlocked()
            true
        } else {
            val penaltyMs = lockManager.recordFailedAttempt()
            _pinError.value = if (penaltyMs > 0L) {
                "Locked for ${penaltyMs / 1000L}s"
            } else {
                "Incorrect PIN"
            }
            false
        }
    }

    fun clearPinError() {
        _pinError.value = null
    }

    fun setBiometricEnabled(enabled: Boolean) {
        storage.isBiometricEnabled = enabled
        _settings.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun setPageAuthEnabled(enabled: Boolean) {
        storage.isPageAuthEnabled = enabled
        _settings.update { it.copy(isPageAuthEnabled = enabled) }
    }

    fun setThemePreference(pref: ThemePreference) {
        storage.themeMode = pref.key
        _settings.update { it.copy(themePreference = pref) }
    }

    fun setDynamicColor(enabled: Boolean) {
        storage.useDynamicColor = enabled
        _settings.update { it.copy(useDynamicColor = enabled) }
    }
}
