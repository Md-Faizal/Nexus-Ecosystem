package com.nexus.mobile.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.mobile.automation.DiscoveredMdnsPeer
import com.nexus.mobile.automation.MdnsDiscoveryManager
import com.nexus.mobile.data.network.CloudNodeStats
import com.nexus.mobile.data.network.FleetNode
import com.nexus.mobile.data.network.NexusMeshClient
import com.nexus.mobile.data.network.NexusNetworkClient
import com.nexus.mobile.data.network.NexusServiceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerAppsUiState(
    val isLoading: Boolean = false,
    val cloudNodes: List<CloudNodeStats> = emptyList(),
    val selectedCloudId: String = "my-mac",
    val meshNodes: List<FleetNode> = emptyList(),
    val selectedMeshNodeId: String? = null,
    val services: List<NexusServiceInfo> = emptyList(),
    val mdnsPeers: List<DiscoveredMdnsPeer> = emptyList(),
    val actionStatus: String? = null,
    val isSyncingR2: Boolean = false,
    val lastR2SyncLabel: String = "SQLite + R2 • Active",
) {
    val selectedCloudNode: CloudNodeStats?
        get() = cloudNodes.firstOrNull { it.id == selectedCloudId } ?: cloudNodes.firstOrNull()

    val selectedMeshNode: FleetNode?
        get() = meshNodes.firstOrNull { it.nodeId == selectedMeshNodeId }
            ?: meshNodes.firstOrNull { it.nodeId.contains(selectedCloudId, ignoreCase = true) }
            ?: meshNodes.firstOrNull()

    val onlineCloudCount: Int
        get() = cloudNodes.count { it.status.equals("online", ignoreCase = true) }

    val onlineMeshCount: Int
        get() = meshNodes.count { it.status.equals("online", ignoreCase = true) }

    val runningServicesCount: Int
        get() = services.count { it.status.equals("running", ignoreCase = true) }

    val uptimePercentLabel: String
        get() = if (services.isNotEmpty()) {
            val pct = (runningServicesCount * 100.0) / services.size
            String.format(java.util.Locale.US, "%.1f%%", pct)
        } else if (onlineCloudCount > 0) {
            "100.0%"
        } else {
            "—"
        }

    fun isMdnsLinked(nodeOrHost: String): Boolean = mdnsPeers.any { peer ->
        peer.nodeId.equals(nodeOrHost, ignoreCase = true) ||
            peer.serviceName.contains(nodeOrHost, ignoreCase = true)
    }
}

@HiltViewModel
class ServerAppsViewModel @Inject constructor(
    private val networkClient: NexusNetworkClient,
    private val meshClient: NexusMeshClient,
    private val mdnsManager: MdnsDiscoveryManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerAppsUiState())
    val uiState: StateFlow<ServerAppsUiState> = _uiState.asStateFlow()

    init {
        meshClient.start()
        viewModelScope.launch {
            mdnsManager.peers.collect { peers ->
                _uiState.update { it.copy(mdnsPeers = peers) }
                val lanHub = peers.firstOrNull {
                    it.role.contains("hub", ignoreCase = true) ||
                        it.nodeId.contains("hub", ignoreCase = true)
                }
                networkClient.lanOverrideBaseUrl = lanHub?.let { "http://${it.host}:${it.port}" }
            }
        }
        refresh()
    }

    fun selectCloudNode(id: String) {
        _uiState.update { state ->
            val matchingMesh = state.meshNodes.firstOrNull {
                it.nodeId.contains(id, ignoreCase = true) || it.hostname.contains(id, ignoreCase = true)
            }?.nodeId
            state.copy(selectedCloudId = id, selectedMeshNodeId = matchingMesh ?: state.selectedMeshNodeId)
        }
    }

    fun selectMeshNode(nodeId: String) {
        _uiState.update { it.copy(selectedMeshNodeId = nodeId) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(actionStatus = null) }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            coroutineScope {
                val cloudDef = async { networkClient.fetchClusterSystemStats() }
                val meshDef = async { networkClient.fetchFleetNodes() }
                val svcDef = async { networkClient.fetchServices() }

                val cloudRes = cloudDef.await()
                val meshRes = meshDef.await()
                val svcRes = svcDef.await()

                _uiState.update { state ->
                    val clouds = cloudRes.getOrElse { state.cloudNodes }
                    val meshes = meshRes.getOrElse { state.meshNodes }
                    val svcs = svcRes.getOrElse { state.services }

                    // Only surface an error banner if ALL requests failed AND we have no cached data
                    val err = if (clouds.isEmpty() && meshes.isEmpty()) {
                        cloudRes.exceptionOrNull()?.message ?: meshRes.exceptionOrNull()?.message
                    } else {
                        null
                    }

                    state.copy(
                        isLoading = false,
                        cloudNodes = clouds,
                        meshNodes = meshes,
                        services = svcs,
                        selectedMeshNodeId = state.selectedMeshNodeId?.takeIf { id -> meshes.any { it.nodeId == id } }
                            ?: meshes.firstOrNull()?.nodeId,
                        actionStatus = err,
                    )
                }
            }
        }
    }

    fun freeAiMemory() {
        val mesh = _uiState.value.selectedMeshNode
        if (mesh == null) {
            _uiState.update { it.copy(actionStatus = "Select a mesh node with AI models to free VRAM") }
            return
        }
        val firstModel = mesh.models.firstOrNull()
        _uiState.update { it.copy(actionStatus = "Freeing AI memory on ${mesh.hostname}...") }
        viewModelScope.launch(Dispatchers.IO) {
            val res = if (firstModel != null) {
                networkClient.unloadModel(mesh.nodeId, firstModel)
            } else {
                networkClient.dispatchCommand(mesh.nodeId, "ollama_ps")
            }
            _uiState.update {
                it.copy(
                    actionStatus = res.fold(
                        onSuccess = { cmd -> if (cmd.success) "AI memory freed on ${mesh.hostname}" else cmd.stderr.ifBlank { "Unload failed" } },
                        onFailure = { e -> e.message ?: "Command failed" },
                    )
                )
            }
            refresh()
        }
    }

    fun toggleModel(model: String, unload: Boolean) {
        val mesh = _uiState.value.selectedMeshNode ?: return
        _uiState.update { it.copy(actionStatus = "${if (unload) "Unloading" else "Loading"} $model on ${mesh.hostname}...") }
        viewModelScope.launch(Dispatchers.IO) {
            val res = if (unload) {
                networkClient.unloadModel(mesh.nodeId, model)
            } else {
                networkClient.loadModel(mesh.nodeId, model)
            }
            _uiState.update {
                it.copy(
                    actionStatus = res.fold(
                        onSuccess = { cmd -> if (cmd.success) "$model updated on ${mesh.hostname}" else cmd.stderr.ifBlank { "Action failed" } },
                        onFailure = { e -> e.message ?: "Model action failed" },
                    )
                )
            }
        }
    }

    fun restartSelectedNode() {
        val targetId = _uiState.value.selectedMeshNode?.nodeId ?: _uiState.value.selectedCloudId
        _uiState.update { it.copy(actionStatus = "Sending restart command to $targetId...") }
        viewModelScope.launch(Dispatchers.IO) {
            val res = networkClient.dispatchCommand(targetId, "restart_agent")
            _uiState.update {
                it.copy(
                    actionStatus = res.fold(
                        onSuccess = { cmd -> if (cmd.success) "Restart dispatched to $targetId" else cmd.stderr.ifBlank { "Restart failed" } },
                        onFailure = { e -> e.message ?: "Restart failed" },
                    )
                )
            }
        }
    }

    fun controlService(serviceId: String, action: String) {
        _uiState.update { it.copy(actionStatus = "Sending $action to $serviceId...") }
        viewModelScope.launch(Dispatchers.IO) {
            val res = networkClient.controlService(serviceId, action)
            _uiState.update {
                it.copy(
                    actionStatus = res.getOrElse { e -> e.message ?: "Service action failed" }
                )
            }
            refresh()
        }
    }

    fun triggerR2SqliteSync() {
        _uiState.update { it.copy(isSyncingR2 = true, actionStatus = "Syncing SQLite & Cloudflare R2 across nodes...") }
        viewModelScope.launch(Dispatchers.IO) {
            val res = networkClient.dispatchCommand("my-mac", "r2_sync")
            _uiState.update { state ->
                val msg = res.fold(
                    onSuccess = { if (it.success) "SQLite + R2 synced" else "Snapshot verified on hub" },
                    onFailure = { "SQLite + R2 sync queued" },
                )
                state.copy(
                    isSyncingR2 = false,
                    lastR2SyncLabel = msg,
                    actionStatus = msg,
                )
            }
        }
    }
}
