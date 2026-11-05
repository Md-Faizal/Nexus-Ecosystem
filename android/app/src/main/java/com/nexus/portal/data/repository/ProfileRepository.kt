package com.nexus.portal.data.repository

import android.content.Context
import com.nexus.portal.core.NetworkConfig
import com.nexus.portal.data.model.UserProfile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

class ProfileRepository {

    companion object {
        private val defaultProfiles = listOf(
            UserProfile("guest_auth", "Unauthenticated Node", "U", "#FF3366", "Awaiting OAuth identity")
        )

        private val _profiles = MutableStateFlow<List<UserProfile>>(defaultProfiles)
        val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

        private val _activeProfile = MutableStateFlow<UserProfile>(defaultProfiles[0])
        val activeProfile: StateFlow<UserProfile> = _activeProfile.asStateFlow()

        private val _isInitialized = MutableStateFlow(false)
        val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

        private val _isSessionUnlocked = MutableStateFlow(false)
        val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()
        
        private val _failedLoginAttempts = MutableStateFlow(0)
        val failedLoginAttempts: StateFlow<Int> = _failedLoginAttempts.asStateFlow()

        private val _lockoutEndTime = MutableStateFlow(0L)
        val lockoutEndTime: StateFlow<Long> = _lockoutEndTime.asStateFlow()

        var pendingDeepLink: String? = null

        private val _authenticatedProfileIds = MutableStateFlow<Set<String>>(emptySet())
        val authenticatedProfileIds: StateFlow<Set<String>> = _authenticatedProfileIds.asStateFlow()

        private val _authenticatedProfiles = MutableStateFlow<List<UserProfile>>(defaultProfiles)
        val authenticatedProfiles: StateFlow<List<UserProfile>> = _authenticatedProfiles.asStateFlow()

        private var prefs: android.content.SharedPreferences? = null

        var deviceId: String = ""
            private set
        var sessionId: String = ""
            private set

        fun init(context: Context) {
            sessionId = UUID.randomUUID().toString()

            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                prefs = EncryptedSharedPreferences.create(
                    context,
                    "nexus_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                // Manage Device ID
                val savedDeviceId = prefs?.getString("device_id", null)
                if (savedDeviceId != null) {
                    deviceId = savedDeviceId
                } else {
                    deviceId = UUID.randomUUID().toString()
                    prefs?.edit()?.putString("device_id", deviceId)?.apply()
                }

                // Load local profiles
                val savedProfilesJson = prefs?.getString("saved_profiles", null)
                if (savedProfilesJson != null) {
                    try {
                        val jsonArr = JSONArray(savedProfilesJson)
                        val localList = mutableListOf<UserProfile>()
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            val enabledAppsArr = obj.optJSONArray("enabledApps")
                            val enabledApps = mutableListOf<String>()
                            if (enabledAppsArr != null) {
                                for (j in 0 until enabledAppsArr.length()) {
                                    enabledApps.add(enabledAppsArr.getString(j))
                                }
                            }
                            localList.add(
                                UserProfile(
                                    id = obj.getString("id"),
                                    name = obj.getString("name"),
                                    avatarInitial = obj.optString("avatarInitial", "U"),
                                    themeColorHex = obj.getString("themeColorHex"),
                                    statusText = obj.optString("statusText", "Active Node"),
                                    enabledApps = enabledApps,
                                    hasPin = obj.optBoolean("hasPin", false),
                                    hasFingerprint = obj.optBoolean("hasFingerprint", false),
                                    profilePictureUrl = if (obj.isNull("profilePictureUrl")) null else obj.optString("profilePictureUrl"),
                                    lastDeviceId = obj.optString("lastDeviceId", null),
                                    lastSessionId = obj.optString("lastSessionId", null)
                                )
                            )
                        }
                        
                        val savedAuthIds = prefs?.getStringSet("authenticated_profile_ids", emptySet()) ?: emptySet()
                        _authenticatedProfileIds.value = savedAuthIds
                        
                        if (localList.isNotEmpty()) {
                            _profiles.value = localList
                            updateAuthenticatedProfiles()
                            val lastActiveId = prefs?.getString("last_active_id", null) ?: _activeProfile.value.id
                            val matched = localList.find { it.id == lastActiveId }
                            if (matched != null) {
                                _activeProfile.value = matched
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Failsafe: auto-fetch profile picture from Google if missing
                try {
                    val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null && account.photoUrl != null) {
                        val googlePhotoUrl = account.photoUrl.toString()
                        val currentProfiles = _profiles.value.toMutableList()
                        var updated = false
                        for (i in currentProfiles.indices) {
                            val p = currentProfiles[i]
                            if (p.profilePictureUrl == null && (p.id == account.email || p.name == account.displayName || _activeProfile.value.id == p.id)) {
                                currentProfiles[i] = p.copy(profilePictureUrl = googlePhotoUrl)
                                updated = true
                                if (_activeProfile.value.id == p.id) {
                                    _activeProfile.value = currentProfiles[i]
                                }
                            }
                        }
                        if (updated) {
                            _profiles.value = currentProfiles
                            saveProfilesLocally()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
            syncWithBackend()
        }

        private fun updateAuthenticatedProfiles() {
            val authIds = _authenticatedProfileIds.value
            val profilesList = _profiles.value
            val filtered = profilesList.filter { it.id in authIds || it.id == "guest_auth" }
            _authenticatedProfiles.value = if (filtered.isEmpty()) defaultProfiles else filtered
        }

        private fun addAuthenticatedProfile(id: String) {
            val current = _authenticatedProfileIds.value.toMutableSet()
            current.add(id)
            _authenticatedProfileIds.value = current
            prefs?.edit()?.putStringSet("authenticated_profile_ids", current)?.apply()
            updateAuthenticatedProfiles()
        }

        private fun removeAuthenticatedProfile(id: String) {
            val current = _authenticatedProfileIds.value.toMutableSet()
            current.remove(id)
            _authenticatedProfileIds.value = current
            prefs?.edit()?.putStringSet("authenticated_profile_ids", current)?.apply()
            updateAuthenticatedProfiles()
        }

        private fun saveProfilesLocally() {
            try {
                val jsonArr = JSONArray()
                _profiles.value.forEach { profile ->
                    val obj = JSONObject().apply {
                        put("id", profile.id)
                        put("name", profile.name)
                        put("avatarInitial", profile.avatarInitial)
                        put("themeColorHex", profile.themeColorHex)
                        put("statusText", profile.statusText)
                        put("enabledApps", JSONArray(profile.enabledApps))
                        put("hasPin", profile.hasPin)
                        put("hasFingerprint", profile.hasFingerprint)
                        put("profilePictureUrl", profile.profilePictureUrl)
                        put("lastDeviceId", profile.lastDeviceId)
                        put("lastSessionId", profile.lastSessionId)
                    }
                    jsonArr.put(obj)
                }
                prefs?.edit()?.putString("saved_profiles", jsonArr.toString())?.apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun hashPinLocally(pin: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        suspend fun setPin(pin: String): Boolean = withContext(Dispatchers.IO) {
            val id = _activeProfile.value.id
            if (id == "guest_auth") return@withContext false

            try {
                prefs?.edit()?.putString("pin_$id", hashPinLocally(pin))?.apply()

                val url = URL(NetworkConfig.profilesUrl.replace("/profiles", "/auth/set_pin"))
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                
                val reqObj = JSONObject().apply {
                    put("id", id)
                    put("pin", pin)
                }
                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(reqObj.toString())
                    writer.flush()
                }

                val success = conn.responseCode == HttpURLConnection.HTTP_OK
                conn.disconnect()

                if (success) {
                    val updated = _activeProfile.value.copy(hasPin = true)
                    _activeProfile.value = updated
                    val currentProfiles = _profiles.value.toMutableList()
                    val idx = currentProfiles.indexOfFirst { it.id == id }
                    if (idx != -1) {
                        currentProfiles[idx] = updated
                        _profiles.value = currentProfiles
                        saveProfilesLocally()
                    }
                    addAuthenticatedProfile(id)
                    _isSessionUnlocked.value = true
                }

                // Load any active lockout
                _lockoutEndTime.value = prefs?.getLong("lockoutEndTime", 0L) ?: 0L

                return@withContext success
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }

        suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
            val id = _activeProfile.value.id
            if (id == "guest_auth") return@withContext false

            if (System.currentTimeMillis() < _lockoutEndTime.value) {
                return@withContext false
            }

            val localHash = prefs?.getString("pin_$id", null)
            if (localHash != null && localHash == hashPinLocally(pin)) {
                _isSessionUnlocked.value = true
                _failedLoginAttempts.value = 0
                prefs?.edit()?.putLong("lockoutEndTime", 0L)?.apply()
                _lockoutEndTime.value = 0L
                addAuthenticatedProfile(id)
                return@withContext true
            }

            try {
                val url = URL(NetworkConfig.profilesUrl.replace("/profiles", "/auth/verify_pin"))
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                
                val reqObj = JSONObject().apply {
                    put("id", id)
                    put("pin", pin)
                }
                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(reqObj.toString())
                    writer.flush()
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    val json = JSONObject(response.toString())
                    val success = json.optBoolean("success", false)
                    
                    if (success) {
                        prefs?.edit()?.putString("pin_$id", hashPinLocally(pin))?.apply()
                        _isSessionUnlocked.value = true
                        _failedLoginAttempts.value = 0
                        prefs?.edit()?.putLong("lockoutEndTime", 0L)?.apply()
                        _lockoutEndTime.value = 0L
                        addAuthenticatedProfile(id)
                    } else {
                        handleFailedAttempt()
                    }
                    return@withContext success
                }
                
                handleFailedAttempt()
                return@withContext false
            } catch (e: Exception) {
                e.printStackTrace()
                handleFailedAttempt()
                return@withContext false
            }
        }
        
        private fun handleFailedAttempt() {
            val attempts = _failedLoginAttempts.value + 1
            _failedLoginAttempts.value = attempts
            if (attempts >= 5) {
                val lockoutEnd = System.currentTimeMillis() + 30000L // 30 seconds
                _lockoutEndTime.value = lockoutEnd
                prefs?.edit()?.putLong("lockoutEndTime", lockoutEnd)?.apply()
                _failedLoginAttempts.value = 0 // Reset attempts after locking out
            }
        }

        fun selectProfile(profileId: String) {
            val found = _profiles.value.find { it.id == profileId }
            if (found != null) {
                _activeProfile.value = found
                prefs?.edit()?.putString("last_active_id", profileId)?.apply()
                uploadProfilesToBackend()
            }
        }
        
        fun lockSession() {
            _isSessionUnlocked.value = false
        }
        
        fun logout(context: Context) {
            lockSession()
            // Just clear active ID, user can still see other authenticated profiles
            prefs?.edit()?.remove("last_active_id")?.apply()
            _activeProfile.value = defaultProfiles[0]
        }

        fun removeProfile(context: Context, profileId: String) {
            if (_activeProfile.value.id == profileId) {
                logout(context)
            }
            removeAuthenticatedProfile(profileId)
            prefs?.edit()?.remove("pin_$profileId")?.apply()
            
            // Sign out of Google to clear the default account
            try {
                val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                client.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Disable fingerprint locally & update backend
            val currentProfiles = _profiles.value.toMutableList()
            val idx = currentProfiles.indexOfFirst { it.id == profileId }
            if (idx != -1) {
                currentProfiles[idx] = currentProfiles[idx].copy(hasFingerprint = false, hasPin = false)
                _profiles.value = currentProfiles
                saveProfilesLocally()
                uploadProfilesToBackend()
            }
        }

        fun updateFingerprintPreference(enabled: Boolean) {
            val id = _activeProfile.value.id
            if (id == "guest_auth") return
            
            val updated = _activeProfile.value.copy(hasFingerprint = enabled)
            _activeProfile.value = updated
            val currentProfiles = _profiles.value.toMutableList()
            val idx = currentProfiles.indexOfFirst { it.id == id }
            if (idx != -1) {
                currentProfiles[idx] = updated
                _profiles.value = currentProfiles
                saveProfilesLocally()
                uploadProfilesToBackend()
            }
        }

        fun addProfile(name: String, themeHex: String, enabledApps: List<String>, oauthToken: String? = null, email: String? = null, photoUrl: String? = null) {
            val id = email ?: (name.lowercase().replace(" ", "_") + "_" + System.currentTimeMillis())
            val initial = if (name.isNotEmpty()) name[0].uppercase().toString() else "U"
            val newProfile = UserProfile(
                id = id,
                name = name,
                avatarInitial = initial,
                themeColorHex = themeHex,
                statusText = "Google Authenticated Profile",
                enabledApps = enabledApps,
                hasPin = false,
                profilePictureUrl = photoUrl,
                lastDeviceId = deviceId,
                lastSessionId = sessionId
            )
            val currentProfiles = _profiles.value.toMutableList()
            currentProfiles.removeAll { it.id == "guest_auth" }
            
            val existingIdx = currentProfiles.indexOfFirst { it.id == id }
            if (existingIdx != -1) {
                val merged = newProfile.copy(hasPin = currentProfiles[existingIdx].hasPin)
                currentProfiles[existingIdx] = merged
                _activeProfile.value = merged
            } else {
                currentProfiles.add(newProfile)
                _activeProfile.value = newProfile
            }
            
            _profiles.value = currentProfiles
            saveProfilesLocally()
            updateAuthenticatedProfiles()
            prefs?.edit()?.putString("last_active_id", id)?.apply()
            uploadProfilesToBackend()
        }

        fun syncWithBackend() {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL(NetworkConfig.profilesUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()
                        
                        val jsonArr = JSONArray(response.toString())
                        val remoteList = mutableListOf<UserProfile>()
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            
                            val enabledAppsArr = obj.optJSONArray("enabledSubApps") ?: obj.optJSONArray("enabledApps")
                            val enabledApps = mutableListOf<String>()
                            if (enabledAppsArr != null) {
                                for (j in 0 until enabledAppsArr.length()) {
                                    val app = enabledAppsArr.getString(j)
                                    val mappedApp = when (app) {
                                        "updates" -> "update"
                                        else -> app
                                    }
                                    enabledApps.add(mappedApp)
                                }
                            } else {
                                enabledApps.addAll(listOf("permissions", "update", "profiles", "sandbox"))
                            }
                            
                            remoteList.add(
                                UserProfile(
                                    id = obj.getString("id"),
                                    name = obj.getString("name"),
                                    avatarInitial = obj.optString("avatarInitial", "U"),
                                    themeColorHex = obj.getString("themeColorHex"),
                                    statusText = obj.optString("statusText", "Active Node"),
                                    enabledApps = enabledApps,
                                    hasPin = obj.optBoolean("hasPin", false),
                                    hasFingerprint = obj.optBoolean("hasFingerprint", false),
                                    profilePictureUrl = if (obj.isNull("profilePictureUrl")) null else obj.optString("profilePictureUrl"),
                                    lastDeviceId = obj.optString("lastDeviceId", null),
                                    lastSessionId = obj.optString("lastSessionId", null)
                                )
                            )
                        }
                        
                        if (remoteList.isNotEmpty()) {
                            // Merge with existing to preserve local-only fields like profilePictureUrl if backend drops it
                            val mergedList = remoteList.map { remote ->
                                val local = _profiles.value.find { it.id == remote.id }
                                if (remote.profilePictureUrl == null && local?.profilePictureUrl != null) {
                                    remote.copy(profilePictureUrl = local.profilePictureUrl)
                                } else {
                                    remote
                                }
                            }
                            _profiles.value = mergedList
                            saveProfilesLocally()
                            updateAuthenticatedProfiles()
                            val lastActiveId = prefs?.getString("last_active_id", null) ?: _activeProfile.value.id
                            val matched = mergedList.find { it.id == lastActiveId }
                            if (matched != null) {
                                _activeProfile.value = matched
                            } else {
                                // Don't force login to first one if last_active_id isn't found. Remain guest_auth.
                                // Actually, if we have profiles, we should prompt the user to pick one, or default to guest.
                                // But if it was already guest, it stays guest.
                            }
                        }
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isInitialized.value = true
                }
            }
        }

        private fun uploadProfilesToBackend() {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL(NetworkConfig.profilesUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    
                    val jsonArr = JSONArray()
                    _profiles.value.forEach { profile ->
                        val obj = JSONObject().apply {
                            put("id", profile.id)
                            put("name", profile.name)
                            put("avatarInitial", profile.avatarInitial)
                            put("themeColorHex", profile.themeColorHex)
                            put("statusText", profile.statusText)
                            
                            val remoteApps = profile.enabledApps.map {
                                when (it) {
                                    "update" -> "updates"
                                    else -> it
                                }
                            }
                            put("enabledSubApps", JSONArray(remoteApps))
                            put("profileNote", "Synced Android Profile node.")
                            put("hasPin", profile.hasPin)
                            put("hasFingerprint", profile.hasFingerprint)
                            put("profilePictureUrl", profile.profilePictureUrl)
                            put("lastDeviceId", deviceId)
                            put("lastSessionId", sessionId)
                        }
                        jsonArr.put(obj)
                    }
                    
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonArr.toString())
                        writer.flush()
                    }
                    
                    connection.responseCode
                    connection.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
