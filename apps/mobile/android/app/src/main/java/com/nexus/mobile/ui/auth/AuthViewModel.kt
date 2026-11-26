package com.nexus.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.mobile.data.auth.AccountNotFoundException
import com.nexus.mobile.data.auth.AuthResult
import com.nexus.mobile.data.auth.NexusAuthClient
import com.nexus.mobile.data.auth.NexusUser
import com.nexus.mobile.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class InfoMessage(val message: String, val otpAwaitingCode: Boolean = false) : AuthUiState
    data class AccountNotFound(val email: String, val message: String) : AuthUiState
    data class WaitingDeviceApproval(
        val userCode: String,
        val verificationUri: String,
    ) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authClient: NexusAuthClient,
    private val storage: SecureStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(storage.isAuthenticated())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private var pollingJob: Job? = null

    fun clearMessage() {
        if (_uiState.value is AuthUiState.Error ||
            _uiState.value is AuthUiState.InfoMessage ||
            _uiState.value is AuthUiState.AccountNotFound
        ) {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun loginWithPassword(
        identifier: String,
        password: String,
        onSuccess: () -> Unit,
    ) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Enter both email/username and password.")
            return
        }
        runAuthCall(onSuccess) { authClient.loginWithPassword(identifier, password) }
    }

    fun registerAccount(
        email: String,
        fullName: String,
        password: String?,
        username: String?,
        securityQuestionKey: String?,
        securityAnswer: String?,
        onSuccess: () -> Unit,
    ) {
        if (email.isBlank() || fullName.isBlank()) {
            _uiState.value = AuthUiState.Error("Full name and email are required.")
            return
        }
        pollingJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AuthUiState.Loading
            authClient.register(email, fullName, password, username)
                .onSuccess { result ->
                    if (!securityQuestionKey.isNullOrBlank() && !securityAnswer.isNullOrBlank()) {
                        authClient.setupSecurityQuestion(result.token, securityQuestionKey, securityAnswer)
                    }
                    persistSession(result)
                    _uiState.value = AuthUiState.Idle
                    launch(Dispatchers.Main) { onSuccess() }
                }
                .onFailure { err ->
                    _uiState.value = AuthUiState.Error(err.message ?: "Account creation failed")
                }
        }
    }

    fun sendOtp(email: String) {
        if (!email.contains("@")) {
            _uiState.value = AuthUiState.Error("Enter a valid email address.")
            return
        }
        pollingJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AuthUiState.Loading
            authClient.sendOtp(email, allowCreate = false)
                .onSuccess { msg ->
                    _uiState.value = AuthUiState.InfoMessage(msg, otpAwaitingCode = true)
                }
                .onFailure { err ->
                    _uiState.value = if (err is AccountNotFoundException) {
                        AuthUiState.AccountNotFound(email.trim(), err.message ?: "Account not found.")
                    } else {
                        AuthUiState.Error(err.message ?: "Failed to send verification code")
                    }
                }
        }
    }

    fun verifyOtp(email: String, code: String, onSuccess: () -> Unit) {
        if (code.trim().length != 6) {
            _uiState.value = AuthUiState.Error("Enter the 6-digit verification code.")
            return
        }
        runAuthCall(onSuccess) { authClient.verifyOtp(email, code) }
    }

    fun sendMagicLink(email: String) {
        if (!email.contains("@")) {
            _uiState.value = AuthUiState.Error("Enter a valid email address.")
            return
        }
        pollingJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AuthUiState.Loading
            authClient.sendMagicLink(email, allowCreate = false)
                .onSuccess { msg ->
                    _uiState.value = AuthUiState.InfoMessage(msg, otpAwaitingCode = false)
                }
                .onFailure { err ->
                    _uiState.value = if (err is AccountNotFoundException) {
                        AuthUiState.AccountNotFound(email.trim(), err.message ?: "Account not found.")
                    } else {
                        AuthUiState.Error(err.message ?: "Failed to send magic link")
                    }
                }
        }
    }

    fun requestPasswordReset(email: String) {
        if (!email.contains("@")) {
            _uiState.value = AuthUiState.Error("Enter your account email address.")
            return
        }
        pollingJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AuthUiState.Loading
            authClient.requestPasswordReset(email)
                .onSuccess { msg ->
                    _uiState.value = AuthUiState.InfoMessage(msg, otpAwaitingCode = false)
                }
                .onFailure { err ->
                    _uiState.value = AuthUiState.Error(err.message ?: "Failed to send password reset link")
                }
        }
    }

    fun loginWithSocialToken(
        provider: String,
        token: String,
        onSuccess: () -> Unit,
    ) {
        if (token.isBlank()) {
            _uiState.value = AuthUiState.Error("Paste or supply a valid $provider token.")
            return
        }
        runAuthCall(onSuccess) {
            when (provider.lowercase()) {
                "google" -> authClient.loginWithGoogle(token)
                "meta" -> authClient.loginWithMeta(token)
                "x" -> authClient.loginWithX(token)
                else -> Result.failure(IllegalArgumentException("Unsupported provider: $provider"))
            }
        }
    }

    fun handleDeepLinkToken(token: String, onSuccess: () -> Unit) {
        if (token.isBlank()) return
        pollingJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AuthUiState.Loading
            authClient.fetchCurrentUser(token)
                .onSuccess { user ->
                    persistSession(AuthResult(token = token, user = user))
                    _uiState.value = AuthUiState.Idle
                    launch(Dispatchers.Main) { onSuccess() }
                }
                .onFailure { err ->
                    _uiState.value = AuthUiState.Error(err.message ?: "Magic link session invalid")
                }
        }
    }

    fun startDevicePairing(onSuccess: () -> Unit) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AuthUiState.Loading
            val codeResult = authClient.requestDeviceCode(storage.getOrCreateDeviceId())
            codeResult.onFailure { err ->
                _uiState.value = AuthUiState.Error(err.message ?: "Unable to reach Nexus Auth")
                return@launch
            }
            val info = codeResult.getOrThrow()
            _uiState.value = AuthUiState.WaitingDeviceApproval(
                userCode = info.userCode,
                verificationUri = info.verificationUri,
            )

            val deadlineMs = System.currentTimeMillis() + info.expiresInSecs * 1000L
            val intervalMs = (info.intervalSecs.coerceAtLeast(3L)) * 1000L

            while (isActive && System.currentTimeMillis() < deadlineMs) {
                delay(intervalMs)
                val poll = authClient.pollDeviceToken(info.deviceCode)
                if (poll.isFailure) {
                    _uiState.value = AuthUiState.Error(poll.exceptionOrNull()?.message ?: "Pairing denied")
                    return@launch
                }
                val session = poll.getOrNull()
                if (session != null) {
                    persistSession(session)
                    _uiState.value = AuthUiState.Idle
                    launch(Dispatchers.Main) { onSuccess() }
                    return@launch
                }
            }
            if (isActive) {
                _uiState.value = AuthUiState.Error("Pairing code expired. Try again.")
            }
        }
    }

    fun cancelPairing() {
        pollingJob?.cancel()
        _uiState.value = AuthUiState.Idle
    }

    fun refreshProfile(onUpdated: () -> Unit = {}) {
        val token = storage.accessToken ?: return
        viewModelScope.launch(Dispatchers.IO) {
            authClient.fetchCurrentUser(token)
                .onSuccess { user ->
                    saveUserFields(user)
                    launch(Dispatchers.Main) { onUpdated() }
                }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        pollingJob?.cancel()
        val token = storage.accessToken
        storage.clearSession()
        _isAuthenticated.value = false
        _uiState.value = AuthUiState.Idle
        if (!token.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                authClient.logout(token)
            }
        }
        onComplete()
    }

    private fun runAuthCall(onSuccess: () -> Unit, call: () -> Result<AuthResult>) {
        pollingJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AuthUiState.Loading
            call()
                .onSuccess { result ->
                    persistSession(result)
                    _uiState.value = AuthUiState.Idle
                    launch(Dispatchers.Main) { onSuccess() }
                }
                .onFailure { err ->
                    _uiState.value = AuthUiState.Error(err.message ?: "Authentication failed")
                }
        }
    }

    private fun persistSession(result: AuthResult) {
        storage.accessToken = result.token
        saveUserFields(result.user)
        _isAuthenticated.value = true
    }

    private fun saveUserFields(user: NexusUser) {
        storage.userId = user.id
        storage.userEmail = user.email
        storage.userUsername = user.username
        storage.userDisplayName = user.displayName
        storage.userRole = user.role
        storage.userAvatarUrl = user.avatarUrl
        storage.hasCentralPin = user.hasPin
    }
}
