package com.nexus.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.mobile.data.network.AiConversation
import com.nexus.mobile.data.network.AiMessage
import com.nexus.mobile.data.network.FleetNode
import com.nexus.mobile.data.network.NexusNetworkClient
import com.nexus.mobile.ui.components.FzLimeAccent
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.NexusModalTopBar
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiChatUiState(
    val conversations: List<AiConversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<AiMessage> = emptyList(),
    val nodes: List<FleetNode> = emptyList(),
    val selectedNodeId: String = "my-mac",
    val selectedModel: String = "qwen3.5:4b",
    val streamingReply: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val networkClient: NexusNetworkClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch(Dispatchers.IO) {
            val nodes = networkClient.fetchFleetNodes().getOrDefault(emptyList())
            val firstOnline = nodes.firstOrNull { it.status.equals("online", ignoreCase = true) }
            val defaultNode = firstOnline?.nodeId ?: nodes.firstOrNull()?.nodeId ?: "my-mac"
            val defaultModel = firstOnline?.models?.firstOrNull() ?: "qwen3.5:4b"

            val convs = networkClient.listAiConversations().getOrDefault(emptyList())
            val activeId = convs.firstOrNull()?.id
            val msgs = if (activeId != null) {
                networkClient.getAiMessages(activeId).getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    nodes = nodes,
                    selectedNodeId = defaultNode,
                    selectedModel = defaultModel,
                    conversations = convs,
                    activeConversationId = activeId,
                    messages = msgs,
                )
            }
        }
    }

    fun selectConversation(id: String) {
        _uiState.update { it.copy(activeConversationId = id, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val msgs = networkClient.getAiMessages(id).getOrDefault(emptyList())
            _uiState.update { it.copy(messages = msgs) }
        }
    }

    fun createNewConversation() {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            networkClient.createAiConversation(
                title = "Mobile Chat",
                targetNodeId = state.selectedNodeId,
                model = state.selectedModel,
            ).onSuccess { created ->
                _uiState.update {
                    it.copy(
                        conversations = listOf(created) + it.conversations,
                        activeConversationId = created.id,
                        messages = emptyList(),
                        error = null,
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message) }
            }
        }
    }

    fun deleteActiveConversation() {
        val id = _uiState.value.activeConversationId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            networkClient.deleteAiConversation(id)
            val remaining = _uiState.value.conversations.filterNot { it.id == id }
            val nextId = remaining.firstOrNull()?.id
            val msgs = if (nextId != null) networkClient.getAiMessages(nextId).getOrDefault(emptyList()) else emptyList()
            _uiState.update {
                it.copy(
                    conversations = remaining,
                    activeConversationId = nextId,
                    messages = msgs,
                )
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isStreaming) return

        viewModelScope.launch(Dispatchers.IO) {
            var convId = _uiState.value.activeConversationId
            if (convId == null) {
                val created = networkClient.createAiConversation(
                    title = trimmed.take(36),
                    targetNodeId = _uiState.value.selectedNodeId,
                    model = _uiState.value.selectedModel,
                ).getOrElse { err ->
                    _uiState.update { it.copy(error = err.message ?: "Failed to create chat") }
                    return@launch
                }
                convId = created.id
                _uiState.update {
                    it.copy(
                        conversations = listOf(created) + it.conversations,
                        activeConversationId = convId,
                    )
                }
            }

            val optimisticUser = AiMessage(
                id = System.nanoTime(),
                conversationId = convId,
                role = "user",
                content = trimmed,
                nodeId = _uiState.value.selectedNodeId,
                createdAt = "",
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + optimisticUser,
                    isStreaming = true,
                    streamingReply = "",
                    error = null,
                )
            }

            val acc = StringBuilder()
            networkClient.streamAiMessage(
                conversationId = convId,
                content = trimmed,
                model = _uiState.value.selectedModel,
                onToken = { token ->
                    acc.append(token)
                    _uiState.update { it.copy(streamingReply = acc.toString()) }
                },
            ).onSuccess {
                val reply = acc.toString().ifBlank { "(Done)" }
                val assistantMsg = AiMessage(
                    id = System.nanoTime(),
                    conversationId = convId,
                    role = "assistant",
                    content = reply,
                    nodeId = _uiState.value.selectedNodeId,
                    createdAt = "",
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMsg,
                        isStreaming = false,
                        streamingReply = "",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingReply = "",
                        error = err.message ?: "AI stream failed",
                    )
                }
            }
        }
    }
}

@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val palette = LocalNexusPalette.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(state.messages.size, state.streamingReply) {
        val total = state.messages.size + if (state.streamingReply.isNotEmpty()) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        NexusModalTopBar(
            title = "AI Chat",
            subtitle = "${state.selectedNodeId} • ${state.selectedModel}",
            onBack = onBack,
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.activeConversationId != null) {
                        NexusCircularButton(
                            icon = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete conversation",
                            onClick = viewModel::deleteActiveConversation,
                        )
                    }
                    NexusCircularButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "New conversation",
                        onClick = viewModel::createNewConversation,
                    )
                }
            },
        )

        if (state.conversations.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.conversations, key = { it.id }) { conv ->
                    val selected = conv.id == state.activeConversationId
                    Surface(
                        onClick = { viewModel.selectConversation(conv.id) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) palette.cardElevated else palette.cardSurface,
                        border = nexusSpecularBorder(palette.isDark, if (selected) 1f else 0.75f),
                    ) {
                        Text(
                            text = conv.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (!state.error.isNullOrBlank()) {
            Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = palette.dangerAccent,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty() && state.streamingReply.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Ask anything to your Nexus AI node.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.messages, key = { it.id }) { msg ->
                ChatBubble(
                    text = msg.content,
                    isUser = msg.role == "user",
                )
            }

            if (state.streamingReply.isNotEmpty()) {
                item {
                    ChatBubble(
                        text = state.streamingReply,
                        isUser = false,
                    )
                }
            }
        }

        // Composer bar
        Surface(
            color = palette.cardSurface,
            border = nexusSpecularBorder(palette.isDark, 0.75f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = onOpenCamera,
                    shape = CircleShape,
                    color = palette.cardElevated,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = "Camera", modifier = Modifier.size(18.dp))
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (input.isEmpty()) {
                        Text(
                            text = "Message AI...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                        ),
                        cursorBrush = SolidColor(FzLimeAccent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Surface(
                    onClick = {
                        val toSend = input
                        input = ""
                        viewModel.sendMessage(toSend)
                    },
                    shape = CircleShape,
                    color = FzLimeAccent,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, isUser: Boolean) {
    val palette = LocalNexusPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isUser) FzLimeAccent.copy(alpha = 0.16f) else palette.cardSurface,
            border = nexusSpecularBorder(palette.isDark, 0.75f),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
