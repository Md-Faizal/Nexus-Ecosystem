package com.nexus.mobile.data.network

import android.util.Log
import com.nexus.mobile.security.SecureStorage
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CloudNodeStats(
    val id: String,
    val shortLabel: String,
    val hostname: String,
    val role: String,
    val ip: String,
    val os: String,
    val status: String,
    val uptimeSecs: Long,
    val cpuLoadPercent: Int,
    val memoryUsedPercent: Int,
    val memoryUsedGb: Double,
    val memoryTotalGb: Double,
    val diskUsedPercent: Int,
    val diskAvailGb: Double,
    val diskTotalGb: Double,
)

data class NexusServiceInfo(
    val id: String,
    val name: String,
    val status: String,
    val node: String,
    val port: Int,
    val servingState: String,
    val trafficRoute: String,
)

data class FleetNode(
    val nodeId: String,
    val hostname: String,
    val os: String,
    val osVersion: String,
    val arch: String,
    val agentVersion: String,
    val status: String,
    val lastSeenSecs: Long,
    val models: List<String>,
    val cpuLoadPercent: Int?,
    val memoryUsedPercent: Int?,
    val memoryUsedGb: Double?,
    val memoryTotalGb: Double?,
    val diskUsedPercent: Int?,
    val diskFreeGb: Double?,
    val diskTotalGb: Double?,
    val uptimeSecs: Long?,
    val tempCelsius: Int?,
)

data class HubStats(
    val node: String,
    val role: String,
    val ip: String,
    val status: String,
    val os: String,
    val uptimeSecs: Long,
    val cpuLoadPercent: Int,
    val cpuCores: Int,
    val memoryUsedPercent: Int,
    val memoryUsedBytes: Long,
    val memoryTotalBytes: Long,
    val diskUsedPercent: Int,
    val diskAvailBytes: Long,
    val diskTotalBytes: Long,
)

data class CommandResponse(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionMs: Long,
)

data class AiConversation(
    val id: String,
    val title: String,
    val targetNodeId: String,
    val model: String,
    val messageCount: Int,
    val updatedAt: String,
)

data class AiMessage(
    val id: Long,
    val conversationId: String,
    val role: String,
    val content: String,
    val nodeId: String,
    val createdAt: String,
)

data class NexusChatMessage(
    val id: Long,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String?,
    val content: String,
    val messageType: String,
    val createdAt: String,
)

@Singleton
class NexusNetworkClient @Inject constructor(
    private val storage: SecureStorage,
) {
    @Volatile
    var lanOverrideBaseUrl: String? = null

    private val primaryBaseUrl = "https://faizal.uk"

    private fun activeBaseUrl(): String = lanOverrideBaseUrl?.trimEnd('/') ?: primaryBaseUrl

    fun sharedHttpClient(): OkHttpClient = httpClient

    fun wsMeshUrl(): String {
        val base = activeBaseUrl()
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> base
        }
        return "$wsBase/api/mesh/ws"
    }

    fun fetchClusterSystemStats(): Result<List<CloudNodeStats>> = runCatching {
        val raw = getString("${activeBaseUrl()}/api/system-stats")
        val root = JSONObject(raw)
        val cluster = root.optJSONObject("stats")?.optJSONObject("cluster")
            ?: throw IllegalStateException("Missing cluster telemetry")

        listOf(
            Triple("my-mac", "my-mac", cluster.optJSONObject("master") ?: cluster.optJSONObject("primary")),
            Triple("cos", "cos", cluster.optJSONObject("slave") ?: cluster.optJSONObject("backup")),
            Triple("pro", "pro", cluster.optJSONObject("gcp")),
        ).mapNotNull { (id, shortLabel, obj) ->
            obj ?: return@mapNotNull null
            val cpu = obj.optJSONObject("cpu") ?: JSONObject()
            val mem = obj.optJSONObject("memory") ?: JSONObject()
            val disk = obj.optJSONObject("disk") ?: JSONObject()
            val memTotal = mem.optLong("totalBytes", 0L)
            val memUsed = mem.optLong("usedBytes", 0L)
            val diskTotal = disk.optLong("totalBytes", 0L)
            val diskAvail = disk.optLong("availBytes", 0L)
            CloudNodeStats(
                id = id,
                shortLabel = shortLabel,
                hostname = obj.optString("node", id),
                role = obj.optString("role", "Cluster Node"),
                ip = obj.optString("ip", ""),
                os = obj.optString("os", "Ubuntu 24.04"),
                status = obj.optString("status", "offline"),
                uptimeSecs = obj.optLong("uptime", 0L),
                cpuLoadPercent = cpu.optDouble("loadPercent", 0.0).toInt(),
                memoryUsedPercent = mem.optDouble("usedPercent", 0.0).toInt(),
                memoryUsedGb = memUsed / 1_073_741_824.0,
                memoryTotalGb = (memTotal / 1_073_741_824.0).coerceAtLeast(0.1),
                diskUsedPercent = disk.optDouble("usedPercent", 0.0).toInt(),
                diskAvailGb = diskAvail / 1_073_741_824.0,
                diskTotalGb = (diskTotal / 1_073_741_824.0).coerceAtLeast(0.1),
            )
        }
    }

    fun fetchServices(): Result<List<NexusServiceInfo>> = runCatching {
        val raw = getString("${activeBaseUrl()}/api/services")
        val arr = JSONObject(raw).optJSONArray("services") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    NexusServiceInfo(
                        id = obj.optString("id"),
                        name = obj.optString("name", obj.optString("id")),
                        status = obj.optString("status", "unknown"),
                        node = obj.optString("node", "my-mac"),
                        port = obj.optInt("port", 0),
                        servingState = obj.optString("serving_state", ""),
                        trafficRoute = obj.optString("traffic_route", ""),
                    )
                )
            }
        }
    }

    fun controlService(serviceId: String, action: String): Result<String> = runCatching {
        val body = JSONObject().apply { put("action", action) }
        val raw = postJson("${activeBaseUrl()}/api/services/$serviceId/action", body)
        JSONObject(raw).optString("message", "$action sent to $serviceId")
    }

    fun fetchFleetNodes(): Result<List<FleetNode>> = runCatching {
        val raw = getString("${activeBaseUrl()}/api/fleet/nodes")
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tel = obj.optJSONObject("telemetry")
                val cpu = tel?.optJSONObject("cpu")
                val mem = tel?.optJSONObject("memory")
                val disk = tel?.optJSONObject("disk")
                val modelsArr = obj.optJSONArray("models")
                val models = buildList {
                    if (modelsArr != null) {
                        for (m in 0 until modelsArr.length()) {
                            add(modelsArr.optString(m))
                        }
                    }
                }
                val memTotalBytes = mem?.optLong("totalBytes", 0L) ?: 0L
                val memUsedBytes = mem?.optLong("usedBytes", 0L) ?: 0L
                val diskTotalBytes = disk?.optLong("totalBytes", 0L) ?: 0L
                val diskAvailBytes = disk?.optLong("availBytes", 0L) ?: 0L
                add(
                    FleetNode(
                        nodeId = obj.optString("node_id"),
                        hostname = obj.optString("hostname", obj.optString("node_id")),
                        os = obj.optString("os", "linux"),
                        osVersion = obj.optString("os_version", ""),
                        arch = obj.optString("arch", ""),
                        agentVersion = obj.optString("agent_version", ""),
                        status = obj.optString("status", "offline"),
                        lastSeenSecs = obj.optLong("last_seen_secs", 0L),
                        models = models,
                        cpuLoadPercent = cpu?.optDouble("loadPercent")?.takeIf { !it.isNaN() }?.toInt(),
                        memoryUsedPercent = mem?.optInt("usedPercent"),
                        memoryUsedGb = if (memTotalBytes > 0) memUsedBytes / 1_073_741_824.0 else null,
                        memoryTotalGb = if (memTotalBytes > 0) memTotalBytes / 1_073_741_824.0 else null,
                        diskUsedPercent = disk?.optInt("usedPercent"),
                        diskFreeGb = if (diskTotalBytes > 0) diskAvailBytes / 1_073_741_824.0 else null,
                        diskTotalGb = if (diskTotalBytes > 0) diskTotalBytes / 1_073_741_824.0 else null,
                        uptimeSecs = tel?.optLong("uptime")?.takeIf { it > 0L },
                        tempCelsius = tel?.optInt("temperature")?.takeIf { it > 0 },
                    )
                )
            }
        }
    }

    fun fetchHubStats(): Result<HubStats> = runCatching {
        val raw = getString("${activeBaseUrl()}/api/stats")
        val obj = JSONObject(raw)
        val cpu = obj.optJSONObject("cpu") ?: JSONObject()
        val mem = obj.optJSONObject("memory") ?: JSONObject()
        val disk = obj.optJSONObject("disk") ?: JSONObject()
        HubStats(
            node = obj.optString("node", "nexus-hub"),
            role = obj.optString("role", "Core Fabric"),
            ip = obj.optString("ip", ""),
            status = obj.optString("status", "online"),
            os = obj.optString("os", "Linux"),
            uptimeSecs = obj.optLong("uptime", 0L),
            cpuLoadPercent = cpu.optInt("loadPercent", 0),
            cpuCores = cpu.optInt("cores", 1),
            memoryUsedPercent = mem.optInt("usedPercent", 0),
            memoryUsedBytes = mem.optLong("usedBytes", 0L),
            memoryTotalBytes = mem.optLong("totalBytes", 0L),
            diskUsedPercent = disk.optInt("usedPercent", 0),
            diskAvailBytes = disk.optLong("availBytes", 0L),
            diskTotalBytes = disk.optLong("totalBytes", 0L),
        )
    }

    fun dispatchCommand(
        nodeId: String,
        action: String,
        args: List<String> = emptyList(),
    ): Result<CommandResponse> = runCatching {
        val body = JSONObject().apply {
            put("action", action)
            put("args", JSONArray(args))
        }
        val raw = postJson("${activeBaseUrl()}/api/fleet/nodes/$nodeId/command", body)
        val obj = JSONObject(raw)
        CommandResponse(
            success = obj.optBoolean("success", false),
            exitCode = obj.optInt("exit_code", -1),
            stdout = obj.optString("stdout", ""),
            stderr = obj.optString("stderr", ""),
            executionMs = obj.optLong("execution_ms", 0L),
        )
    }

    fun loadModel(nodeId: String, model: String): Result<CommandResponse> = runCatching {
        val body = JSONObject().apply {
            put("model", model)
            put("keep_alive", "15m")
        }
        val raw = postJson("${activeBaseUrl()}/api/fleet/nodes/$nodeId/ai/load", body)
        val obj = JSONObject(raw)
        CommandResponse(
            success = obj.optBoolean("success", true),
            exitCode = obj.optInt("exit_code", 0),
            stdout = obj.optString("stdout", ""),
            stderr = obj.optString("stderr", ""),
            executionMs = obj.optLong("execution_ms", 0L),
        )
    }

    fun unloadModel(nodeId: String, model: String): Result<CommandResponse> = runCatching {
        val body = JSONObject().apply { put("model", model) }
        val raw = postJson("${activeBaseUrl()}/api/fleet/nodes/$nodeId/ai/unload", body)
        val obj = JSONObject(raw)
        CommandResponse(
            success = obj.optBoolean("success", true),
            exitCode = obj.optInt("exit_code", 0),
            stdout = obj.optString("stdout", ""),
            stderr = obj.optString("stderr", ""),
            executionMs = obj.optLong("execution_ms", 0L),
        )
    }

    fun listAiConversations(): Result<List<AiConversation>> = runCatching {
        val raw = getString("${activeBaseUrl()}/api/fleet/ai/conversations")
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    AiConversation(
                        id = obj.optString("id"),
                        title = obj.optString("title", "Conversation"),
                        targetNodeId = obj.optString("target_node_id", "my-mac"),
                        model = obj.optString("model", "qwen3.5:4b"),
                        messageCount = obj.optInt("message_count", 0),
                        updatedAt = obj.optString("updated_at", ""),
                    )
                )
            }
        }
    }

    fun createAiConversation(
        title: String,
        targetNodeId: String,
        model: String,
    ): Result<AiConversation> = runCatching {
        val body = JSONObject().apply {
            put("title", title)
            put("target_node_id", targetNodeId)
            put("model", model)
        }
        val raw = postJson("${activeBaseUrl()}/api/fleet/ai/conversations", body)
        val obj = JSONObject(raw)
        AiConversation(
            id = obj.optString("id"),
            title = obj.optString("title", title),
            targetNodeId = obj.optString("target_node_id", targetNodeId),
            model = obj.optString("model", model),
            messageCount = 0,
            updatedAt = "",
        )
    }

    fun getAiMessages(conversationId: String): Result<List<AiMessage>> = runCatching {
        val raw = getString("${activeBaseUrl()}/api/fleet/ai/conversations/$conversationId/messages")
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    AiMessage(
                        id = obj.optLong("id"),
                        conversationId = obj.optString("conversation_id", conversationId),
                        role = obj.optString("role", "assistant"),
                        content = obj.optString("content", ""),
                        nodeId = obj.optString("node_id", ""),
                        createdAt = obj.optString("created_at", ""),
                    )
                )
            }
        }
    }

    fun streamAiMessage(
        conversationId: String,
        content: String,
        model: String?,
        onToken: (String) -> Unit,
    ): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("content", content)
            if (!model.isNullOrBlank()) put("model", model)
        }
        val req = authRequestBuilder("${activeBaseUrl()}/api/fleet/ai/conversations/$conversationId/messages")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw IllegalStateException(err.ifBlank { "AI stream error (${resp.code})" })
            }
            val stream = resp.body?.byteStream() ?: return@use
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line ?: continue
                    if (trimmed.startsWith("data:")) {
                        val payload = trimmed.removePrefix("data:").trimStart()
                        if (payload == "[DONE]") break
                        if (payload.isNotEmpty()) {
                            onToken(payload)
                        }
                    }
                }
            }
        }
    }

    fun deleteAiConversation(conversationId: String): Result<Unit> = runCatching {
        val req = authRequestBuilder("${activeBaseUrl()}/api/fleet/ai/conversations/$conversationId")
            .delete()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Delete failed (${resp.code})")
        }
    }

    fun listNexusChatMessages(chatId: String = "global"): Result<List<NexusChatMessage>> = runCatching {
        val raw = getString("${activeBaseUrl()}/api/fleet/chat/messages?chat_id=$chatId")
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    NexusChatMessage(
                        id = obj.optLong("id"),
                        chatId = obj.optString("chat_id", chatId),
                        senderId = obj.optString("sender_id", ""),
                        senderName = obj.optString("sender_name", "Operator"),
                        recipientId = obj.optString("recipient_id").takeIf { it.isNotBlank() && it != "null" },
                        content = obj.optString("content", ""),
                        messageType = obj.optString("message_type", "text"),
                        createdAt = obj.optString("created_at", ""),
                    )
                )
            }
        }
    }

    fun sendNexusChatMessage(
        content: String,
        senderName: String,
        senderId: String,
        chatId: String = "global",
    ): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("chat_id", chatId)
            put("sender_id", senderId)
            put("sender_name", senderName)
            put("content", content)
            put("message_type", "text")
        }
        postJson("${activeBaseUrl()}/api/fleet/chat/messages", body)
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    InetAddress.getAllByName(hostname).sortedByDescending { it is Inet4Address }
            })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun authRequestBuilder(urlStr: String): Request.Builder {
        val builder = Request.Builder().url(urlStr)
        storage.accessToken?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return builder
    }

    private fun getString(urlStr: String): String {
        val start = System.currentTimeMillis()
        Log.d("NexusNetwork", "GET $urlStr")
        return try {
            val req = authRequestBuilder(urlStr)
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                Log.d("NexusNetwork", "GET $urlStr -> ${resp.code} in ${System.currentTimeMillis() - start}ms (${text.length}B)")
                if (!resp.isSuccessful) {
                    throw IllegalStateException(text.ifBlank { "HTTP ${resp.code}" })
                }
                text
            }
        } catch (e: Exception) {
            Log.w("NexusNetwork", "GET $urlStr FAILED in ${System.currentTimeMillis() - start}ms: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private fun postJson(urlStr: String, body: JSONObject): String {
        val start = System.currentTimeMillis()
        val reqBody = body.toString().toRequestBody(jsonMediaType)
        val req = authRequestBuilder(urlStr)
            .header("Accept", "application/json")
            .post(reqBody)
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            Log.d("NexusNetwork", "POST $urlStr -> ${resp.code} in ${System.currentTimeMillis() - start}ms")
            if (!resp.isSuccessful) {
                throw IllegalStateException(text.ifBlank { "HTTP ${resp.code}" })
            }
            text
        }
    }
}
