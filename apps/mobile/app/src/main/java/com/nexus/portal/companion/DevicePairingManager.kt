package com.nexus.portal.companion

import android.util.Log
import com.nexus.portal.core.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class PairingState {
    object Idle : PairingState()
    data class CodeReceived(
        val userCode: String,
        val verificationUri: String,
        val expiresInSeconds: Long
    ) : PairingState()
    object Polling : PairingState()
    object Success : PairingState()
    data class Error(val message: String) : PairingState()
}

/**
 * Implements RFC 8628 (OAuth 2.0 Device Authorization Grant) pairing flow
 * for linking the Nexus Mobile Companion app to Central Hub and Desktop peers.
 */
@Singleton
class DevicePairingManager @Inject constructor(
    private val authBridge: NexusAuthBridge
) {
    private val tag = "DevicePairingManager"

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private var activeDeviceCode: String? = null
    private var isPolling = false

    /**
     * Request a user verification code from Central Hub.
     */
    suspend fun initiateDevicePairing(): Result<PairingState.CodeReceived> = withContext(Dispatchers.IO) {
        try {
            val deviceId = authBridge.getDeviceId()
            val url = URL("${NetworkConfig.backendUrl}/api/auth/device/code")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val body = JSONObject().apply {
                put("client_id", "nexus_mobile_companion")
                put("device_id", deviceId)
                put("scope", "companion.telemetry companion.control")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(responseStr)

                val deviceCode = json.getString("device_code")
                val userCode = json.getString("user_code")
                val verificationUri = json.optString("verification_uri", "${NetworkConfig.backendUrl}/device")
                val expiresIn = json.optLong("expires_in", 300)

                activeDeviceCode = deviceCode
                val result = PairingState.CodeReceived(userCode, verificationUri, expiresIn)
                _pairingState.value = result
                Log.i(tag, "Device pairing code received: $userCode")
                Result.success(result)
            } else {
                val errorMsg = "Hub returned HTTP $responseCode during pairing initiation"
                Log.e(tag, errorMsg)
                _pairingState.value = PairingState.Error(errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val err = "Network error during pairing initiation: ${e.message}"
            Log.e(tag, err, e)
            _pairingState.value = PairingState.Error(err)
            Result.failure(e)
        }
    }

    /**
     * Poll the Central Hub token endpoint until user confirms the code on web/desktop.
     */
    suspend fun pollForAuthorization(intervalSeconds: Long = 5, timeoutSeconds: Long = 300) = withContext(Dispatchers.IO) {
        val code = activeDeviceCode
        if (code == null) {
            _pairingState.value = PairingState.Error("No active pairing session")
            return@withContext
        }

        isPolling = true
        _pairingState.value = PairingState.Polling
        val startTime = System.currentTimeMillis()

        while (isPolling && (System.currentTimeMillis() - startTime) < (timeoutSeconds * 1000)) {
            delay(intervalSeconds * 1000)
            if (!isPolling) break

            try {
                val url = URL("${NetworkConfig.backendUrl}/api/auth/device/token")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val body = JSONObject().apply {
                    put("device_code", code)
                    put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    put("client_id", "nexus_mobile_companion")
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val status = conn.responseCode
                if (status == 200) {
                    val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val json = JSONObject(responseStr)

                    val accessToken = json.getString("access_token")
                    val refreshToken = json.optString("refresh_token", null)
                    val email = json.optString("user_email", "user@nexus.local")
                    val name = json.optString("display_name", "Nexus User")

                    authBridge.saveSession(accessToken, refreshToken, email, name)
                    _pairingState.value = PairingState.Success
                    isPolling = false
                    Log.i(tag, "Device authorization successful for: $email")
                    return@withContext
                } else if (status == 400 || status == 428) {
                    // authorization_pending, continue loop
                    Log.d(tag, "Authorization still pending on Central Hub...")
                } else {
                    _pairingState.value = PairingState.Error("Hub error during pairing poll: HTTP $status")
                    isPolling = false
                    return@withContext
                }
            } catch (e: Exception) {
                Log.w(tag, "Transient network error during poll: ${e.message}")
            }
        }

        if (isPolling) {
            _pairingState.value = PairingState.Error("Device pairing timed out")
            isPolling = false
        }
    }

    fun cancelPairing() {
        isPolling = false
        activeDeviceCode = null
        _pairingState.value = PairingState.Idle
    }
}
