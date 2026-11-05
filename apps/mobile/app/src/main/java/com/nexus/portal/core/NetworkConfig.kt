package com.nexus.portal.core

object NetworkConfig {
    // Default production backend URL (can be customized dynamically in the UI for debugging)
    var backendUrl: String = "https://nexus.faizal.uk"

    val updateCheckUrl: String
        get() = "$backendUrl/update/check"

    val profilesUrl: String
        get() = "$backendUrl/api/profiles"

    val notificationsUrl: String
        get() = "$backendUrl/api/notifications"

    val sandboxUrl: String
        get() = "$backendUrl/sandbox/"

    val errorsUrl: String
        get() = "$backendUrl/api/errors"

    val rideTrackerSyncUrl: String
        get() = "$backendUrl/api/ridetracker/sync"

    val electricityBillSyncUrl: String
        get() = "$backendUrl/api/electricitybill/sync"

    val timelineSyncUrl: String
        get() = "$backendUrl/api/timeline/sync"

    val timelinePreferenceUrl: String
        get() = "$backendUrl/api/timeline/preference"

    val timelineCommandUrl: String
        get() = "$backendUrl/api/timeline/command"

    val timelineUploadAudioUrl: String
        get() = "$backendUrl/api/timeline/upload-audio"

    val leaderboardUrl: String
        get() = "$backendUrl/api/ridetracker/leaderboard"

    val performanceLeaderboardUrl: String
        get() = "$backendUrl/api/performance/leaderboard"

    val timelineTelemetryStatusUrl: String
        get() = "$backendUrl/api/timeline/telemetry_status"

    val timelineStreamAudioUrl: String
        get() = "$backendUrl/api/timeline/stream_audio"
}
