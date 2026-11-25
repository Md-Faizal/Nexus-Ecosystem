package com.nexus.portal.data.model

data class UserProfile(
    val id: String,
    val name: String,
    val avatarInitial: String,
    val themeColorHex: String,
    val statusText: String = "Active Nexus node",
    val enabledApps: List<String> = listOf("permissions", "update", "profiles", "sandbox"),
    val hasPin: Boolean = false,
    val hasFingerprint: Boolean = false,
    val profilePictureUrl: String? = null,
    val lastDeviceId: String? = null,
    val lastSessionId: String? = null
)
