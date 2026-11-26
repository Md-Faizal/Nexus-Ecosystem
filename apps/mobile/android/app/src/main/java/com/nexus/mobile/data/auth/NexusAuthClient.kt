package com.nexus.mobile.data.auth

import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class NexusUser(
    val id: String,
    val email: String,
    val username: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val role: String,
    val hasPin: Boolean = false,
    val hasPassword: Boolean = false,
    val hasSecurityQuestion: Boolean = false,
) {
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: email
}

data class AuthResult(
    val token: String,
    val user: NexusUser,
)

data class DeviceCodeInfo(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSecs: Long,
    val intervalSecs: Long,
)

class AccountNotFoundException(message: String) : IllegalStateException(message)

@Singleton
class NexusAuthClient @Inject constructor() {

    private val baseUrl = "https://faizal.uk"

    fun loginWithPassword(identifier: String, password: String): Result<AuthResult> {
        return postAuthResult(
            path = "/api/auth/login",
            body = JSONObject().apply {
                put("identifier", identifier.trim())
                put("password", password)
                put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
            },
            defaultError = "Invalid username/email or password",
        )
    }

    fun register(
        email: String,
        fullName: String,
        password: String?,
        username: String?,
    ): Result<AuthResult> {
        return postAuthResult(
            path = "/api/auth/register",
            body = JSONObject().apply {
                put("email", email.trim())
                put("full_name", fullName.trim())
                if (!password.isNullOrBlank()) put("password", password)
                if (!username.isNullOrBlank()) put("username", username.trim())
            },
            defaultError = "Could not create Nexus account",
        )
    }

    fun sendOtp(email: String, allowCreate: Boolean = false): Result<String> {
        return postJson("$baseUrl/api/auth/otp/send", JSONObject().apply {
            put("email", email.trim())
            put("allow_create", allowCreate)
        }).map { json ->
            json.optString("message", "6-digit code sent to your email.")
        }
    }

    fun verifyOtp(email: String, code: String): Result<AuthResult> {
        return postAuthResult(
            path = "/api/auth/otp/verify",
            body = JSONObject().apply {
                put("email", email.trim())
                put("code", code.trim())
                put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
            },
            defaultError = "Invalid or expired 6-digit code",
        )
    }

    fun sendMagicLink(email: String, allowCreate: Boolean = false): Result<String> {
        return postJson("$baseUrl/api/auth/magic-link", JSONObject().apply {
            put("email", email.trim())
            put("allow_create", allowCreate)
        }).map { json ->
            json.optString("message", "Magic link sent to your email.")
        }
    }

    fun requestPasswordReset(email: String): Result<String> {
        return postJson("$baseUrl/api/auth/password-reset/request", JSONObject().apply {
            put("email", email.trim())
        }).map { json ->
            json.optString("message", "Password reset link sent to your email.")
        }
    }

    fun loginWithGoogle(idToken: String): Result<AuthResult> {
        return postAuthResult(
            path = "/api/auth/google",
            body = JSONObject().apply {
                put("credential", idToken.trim())
                put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
            },
            defaultError = "Google sign-in failed",
        )
    }

    fun loginWithMeta(accessToken: String): Result<AuthResult> {
        return postAuthResult(
            path = "/api/auth/meta",
            body = JSONObject().apply {
                put("credential", accessToken.trim())
                put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
            },
            defaultError = "Meta sign-in failed",
        )
    }

    fun loginWithX(accessToken: String): Result<AuthResult> {
        return postAuthResult(
            path = "/api/auth/x",
            body = JSONObject().apply {
                put("credential", accessToken.trim())
                put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
            },
            defaultError = "X sign-in failed",
        )
    }

    fun setupCentralPin(token: String, pin: String): Result<Boolean> {
        return postJson(
            urlStr = "$baseUrl/api/auth/pin/setup",
            body = JSONObject().apply { put("pin", pin.trim()) },
            bearerToken = token,
        ).map { true }
    }

    fun verifyCentralPin(token: String, pin: String): Result<Boolean> {
        return postJson(
            urlStr = "$baseUrl/api/auth/pin/verify",
            body = JSONObject().apply { put("pin", pin.trim()) },
            bearerToken = token,
        ).map { true }
    }

    fun updateCentralPin(
        token: String,
        newPin: String?,
        remove: Boolean,
        password: String?,
        otpCode: String?,
        securityAnswer: String?,
    ): Result<Boolean> {
        val body = JSONObject().apply {
            put("remove", remove)
            if (!newPin.isNullOrBlank()) put("new_pin", newPin.trim())
            if (!password.isNullOrBlank()) put("password", password)
            if (!otpCode.isNullOrBlank()) put("otp_code", otpCode.trim())
            if (!securityAnswer.isNullOrBlank()) put("security_answer", securityAnswer.trim())
        }
        return postJson(
            urlStr = "$baseUrl/api/auth/pin/update",
            body = body,
            bearerToken = token,
        ).map { it.optBoolean("has_pin", !remove) }
    }

    fun setupSecurityQuestion(token: String, questionKey: String, answer: String): Result<Boolean> {
        return postJson(
            urlStr = "$baseUrl/api/auth/security-questions/setup",
            body = JSONObject().apply {
                put("question_key", questionKey)
                put("answer", answer.trim())
            },
            bearerToken = token,
        ).map { true }
    }

    fun requestDeviceCode(deviceId: String): Result<DeviceCodeInfo> {
        return runCatching {
            val conn = openJsonPost("$baseUrl/api/auth/device/code")
            val body = JSONObject().apply {
                put("client_id", "nexus_mobile_companion")
                put("device_id", deviceId)
                put("scope", "profile:read fleet:read")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Failed to request pairing code (HTTP $code)")
            }
            val json = JSONObject(text)
            DeviceCodeInfo(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUri = json.optString("verification_uri", "$baseUrl/auth/device"),
                expiresInSecs = json.optLong("expires_in", 600),
                intervalSecs = json.optLong("interval", 5),
            )
        }
    }

    /**
     * Returns null while authorization is pending; throws on denial/expiry/error.
     */
    fun pollDeviceToken(deviceCode: String): Result<AuthResult?> {
        return runCatching {
            val conn = openJsonPost("$baseUrl/api/auth/device/token")
            val body = JSONObject().apply {
                put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                put("device_code", deviceCode)
                put("client_id", "nexus_mobile_companion")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = JSONObject(text)

            if (code in 200..299 && json.optBoolean("success", false)) {
                val token = json.optString("token", "")
                val userObj = json.optJSONObject("user")
                if (token.isNotBlank() && userObj != null) {
                    return@runCatching AuthResult(token = token, user = parseUser(userObj))
                }
                return@runCatching null
            }

            val errCode = json.optString("error", "")
            if (errCode == "authorization_pending" || errCode == "slow_down") {
                return@runCatching null
            }
            throw IllegalStateException(json.optString("error_description", "Pairing failed ($errCode)"))
        }
    }

    fun fetchCurrentUser(token: String): Result<NexusUser> {
        return runCatching {
            val conn = (URL("$baseUrl/api/auth/me").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Session expired (HTTP $code)")
            }
            val json = JSONObject(text)
            val userObj = json.optJSONObject("user") ?: json
            parseUser(userObj)
        }
    }

    fun logout(token: String) {
        runCatching {
            val conn = (URL("$baseUrl/api/auth/logout").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            conn.responseCode
        }
    }

    private fun postAuthResult(
        path: String,
        body: JSONObject,
        defaultError: String,
    ): Result<AuthResult> {
        return postJson("$baseUrl$path", body).map { json ->
            val token = json.optString("token", "")
            val userObj = json.optJSONObject("user")
            if (token.isBlank() || userObj == null) {
                throw IllegalStateException(json.optString("error", defaultError))
            }
            AuthResult(token = token, user = parseUser(userObj))
        }
    }

    private fun postJson(
        urlStr: String,
        body: JSONObject,
        bearerToken: String? = null,
    ): Result<JSONObject> {
        return runCatching {
            val conn = openJsonPost(urlStr).apply {
                if (!bearerToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                }
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isNotBlank()) JSONObject(text) else JSONObject()

            if (code !in 200..299 || !json.optBoolean("success", true)) {
                val err = json.optString("error", "Request failed (HTTP $code)")
                if (json.has("account_exists") && !json.optBoolean("account_exists", true)) {
                    throw AccountNotFoundException(err)
                }
                throw IllegalStateException(err)
            }
            json
        }
    }

    private fun openJsonPost(urlStr: String): HttpURLConnection {
        return (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
        }
    }

    private fun parseUser(obj: JSONObject): NexusUser {
        return NexusUser(
            id = obj.optString("id", ""),
            email = obj.optString("email", ""),
            username = obj.optString("username", "").takeIf { it.isNotBlank() && it != "null" },
            fullName = obj.optString("full_name", "").takeIf { it.isNotBlank() && it != "null" },
            avatarUrl = obj.optString("avatar_url", "").takeIf { it.isNotBlank() && it != "null" },
            role = obj.optString("role", "user"),
            hasPin = obj.optBoolean("has_pin", false),
            hasPassword = obj.optBoolean("has_password", false),
            hasSecurityQuestion = obj.optBoolean("has_security_question", false),
        )
    }
}
