package com.nexus.mobile.data.network

import android.os.Build
import android.util.Log
import com.nexus.mobile.security.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent Nexus Mesh WebSocket client (`wss://faizal.uk/api/mesh/ws`),
 * matching the `MeshEnvelope` protocol used by `apps/desktop` and `rust-core/src/mesh.rs`.
 */
@Singleton
class NexusMeshClient @Inject constructor(
    private val networkClient: NexusNetworkClient,
    private val storage: SecureStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val frameCounter = AtomicLong(1L)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingChat = MutableSharedFlow<NexusChatMessage>(extraBufferCapacity = 32)
    val incomingChat: SharedFlow<NexusChatMessage> = _incomingChat.asSharedFlow()

    @Volatile
    private var activeSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        connectLoop()
    }

    fun stop() {
        started = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeSocket?.close(1000, "App stopped")
        activeSocket = null
        _isConnected.value = false
    }

    private fun connectLoop() {
        scope.launch {
            var backoffMs = 2_000L
            while (started && isActive) {
                if (activeSocket == null) {
                    val wsUrl = networkClient.wsMeshUrl()
                    Log.d("NexusMesh", "Connecting to $wsUrl")
                    val reqBuilder = Request.Builder().url(wsUrl)
                    storage.accessToken?.takeIf { it.isNotBlank() }?.let {
                        reqBuilder.header("Authorization", "Bearer $it")
                    }
                    val client: OkHttpClient = networkClient.sharedHttpClient()
                    activeSocket = client.newWebSocket(reqBuilder.build(), socketListener)
                }
                delay(backoffMs)
                backoffMs = if (_isConnected.value) 15_000L else (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d("NexusMesh", "Mesh WS connected")
            _isConnected.value = true
            sendNodeHello(webSocket)
            startHeartbeat(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val env = JSONObject(text)
                val msg = env.optJSONObject("msg") ?: return
                val type = msg.optString("type")
                val payload = msg.optJSONObject("payload") ?: return

                if (type == "fleet_chat_message") {
                    _incomingChat.tryEmit(
                        NexusChatMessage(
                            id = payload.optLong("id"),
                            chatId = payload.optString("chat_id", "global"),
                            senderId = payload.optString("sender_id", ""),
                            senderName = payload.optString("sender_name", "Operator"),
                            recipientId = payload.optString("recipient_id").takeIf { it.isNotBlank() && it != "null" },
                            content = payload.optString("content", ""),
                            messageType = payload.optString("message_type", "text"),
                            createdAt = "",
                        )
                    )
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("NexusMesh", "Mesh WS closed: $code $reason")
            handleDisconnect(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w("NexusMesh", "Mesh WS failure: ${t.javaClass.simpleName}: ${t.message}")
            handleDisconnect(webSocket)
        }
    }

    private fun handleDisconnect(webSocket: WebSocket) {
        if (activeSocket === webSocket) {
            activeSocket = null
        }
        _isConnected.value = false
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendNodeHello(webSocket: WebSocket) {
        val nodeId = storage.getOrCreateDeviceId()
        val hostname = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val hello = JSONObject().apply {
            put("frame_id", frameCounter.getAndIncrement())
            put("node_id", nodeId)
            put("timestamp", System.currentTimeMillis())
            put(
                "msg",
                JSONObject().apply {
                    put("type", "node_hello")
                    put(
                        "payload",
                        JSONObject().apply {
                            put("hostname", hostname)
                            put("os", "android")
                            put("os_version", Build.VERSION.RELEASE ?: "15")
                            put("arch", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a")
                            put("agent_version", "2.0.0")
                            put(
                                "features",
                                JSONObject().apply {
                                    put("mesh_node", true)
                                    put("mobile", true)
                                },
                            )
                        },
                    )
                },
            )
        }
        webSocket.send(hello.toString())
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        val startedAtMs = System.currentTimeMillis()
        heartbeatJob = scope.launch {
            while (isActive && activeSocket === webSocket) {
                delay(20_000L)
                val uptimeSecs = (System.currentTimeMillis() - startedAtMs) / 1000L
                val hb = JSONObject().apply {
                    put("frame_id", frameCounter.getAndIncrement())
                    put("node_id", storage.getOrCreateDeviceId())
                    put("timestamp", System.currentTimeMillis())
                    put(
                        "msg",
                        JSONObject().apply {
                            put("type", "heartbeat")
                            put(
                                "payload",
                                JSONObject().apply {
                                    put("uptime_secs", uptimeSecs)
                                },
                            )
                        },
                    )
                }
                if (!webSocket.send(hb.toString())) break
            }
        }
    }
}
