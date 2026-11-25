package com.nexus.mobile.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionLockManager @Inject constructor(
    private val storage: SecureStorage,
) {
    private val _isAppUnlocked = MutableStateFlow(!storage.hasPinConfigured())
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _lockoutUntilMs = MutableStateFlow(storage.lockoutUntilEpochMs)
    val lockoutUntilMs: StateFlow<Long> = _lockoutUntilMs.asStateFlow()

    private var lastBackgroundAtMs: Long = 0L

    fun onAppBackgrounded() {
        lastBackgroundAtMs = System.currentTimeMillis()
    }

    fun onAppForegrounded(timeoutMs: Long = 60_000L) {
        if (!storage.hasPinConfigured()) {
            _isAppUnlocked.value = true
            return
        }
        if (lastBackgroundAtMs > 0L && System.currentTimeMillis() - lastBackgroundAtMs > timeoutMs) {
            _isAppUnlocked.value = false
        }
    }

    fun markUnlocked() {
        storage.failedAttempts = 0
        storage.lockoutUntilEpochMs = 0L
        _lockoutUntilMs.value = 0L
        _isAppUnlocked.value = true
    }

    fun lockNow() {
        if (storage.hasPinConfigured()) {
            _isAppUnlocked.value = false
        }
    }

    fun recordFailedAttempt(): Long {
        val attempts = storage.failedAttempts + 1
        storage.failedAttempts = attempts
        if (attempts >= 5) {
            val penaltyMs = (1L shl (attempts - 5).coerceAtMost(6)) * 15_000L
            val until = System.currentTimeMillis() + penaltyMs
            storage.lockoutUntilEpochMs = until
            _lockoutUntilMs.value = until
            return penaltyMs
        }
        return 0L
    }
}
