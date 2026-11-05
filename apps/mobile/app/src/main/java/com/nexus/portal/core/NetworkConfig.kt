package com.nexus.portal.core

object NetworkConfig {
    // Production Central Network Hub (Rust Axum on faizal.uk)
    var backendUrl: String = "https://faizal.uk"

    // CAN-Bus WebSocket Mesh Gateway
    val meshWsUrl: String
        get() = backendUrl.replace("https://", "wss://").replace("http://", "ws://") + "/api/mesh/ws"

    val updateCheckUrl: String
        get() = "$backendUrl/api/update/check"

    val profilesUrl: String
        get() = "$backendUrl/api/profiles"

    val notificationsUrl: String
        get() = "$backendUrl/api/notifications"

    val errorsUrl: String
        get() = "$backendUrl/api/errors"

    val rideTrackerSyncUrl: String
        get() = "$backendUrl/api/ridetracker/sync"

    val timelineSyncUrl: String
        get() = "$backendUrl/api/timeline/sync"

    val timelinePreferenceUrl: String
        get() = "$backendUrl/api/timeline/preference"

    val timelineCommandUrl: String
        get() = "$backendUrl/api/timeline/command"

    val leaderboardUrl: String
        get() = "$backendUrl/api/ridetracker/leaderboard"

    val performanceLeaderboardUrl: String
        get() = "$backendUrl/api/performance/leaderboard"
}
