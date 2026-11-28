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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.mobile.data.network.NexusChatMessage
import com.nexus.mobile.data.network.NexusMeshClient
import com.nexus.mobile.data.network.NexusNetworkClient
import com.nexus.mobile.security.SecureStorage
import com.nexus.mobile.ui.components.FzLimeAccent
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.NexusGlassCard
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

data class NexusChatUiState(
    val messages: List<NexusChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NexusChatViewModel @Inject constructor(
    private val networkClient: NexusNetworkClient,
    private val meshClient: NexusMeshClient,
    private val storage: SecureStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NexusChatUiState())
    val uiState: StateFlow<NexusChatUiState> = _uiState.asStateFlow()

    val currentDeviceId: String
        get() = storage.getOrCreateDeviceId()

    init {
        meshClient.start()
        viewModelScope.launch {
            meshClient.incomingChat.collect { incoming ->
                _uiState.update { state ->
                    if (state.messages.any { it.id == incoming.id }) state
                    else state.copy(messages = state.messages + incoming)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            networkClient.listNexusChatMessages()
                .onSuccess { list ->
                    _uiState.update { it.copy(messages = list, isLoading = false, error = null) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun send(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val senderName = storage.userDisplayName ?: "Android Operator"
        val senderId = storage.getOrCreateDeviceId()
        viewModelScope.launch(Dispatchers.IO) {
            networkClient.sendNexusChatMessage(
                content = trimmed,
                senderName = senderName,
                senderId = senderId,
            ).onSuccess {
                refresh()
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message) }
            }
        }
    }
}

@Composable
fun NexusChatScreen(
    onBack: () -> Unit,
    viewModel: NexusChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val palette = LocalNexusPalette.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        NexusModalTopBar(
            title = "Nexus Chat",
            subtitle = "Global Fleet Channel",
            onBack = onBack,
            trailingContent = {
                NexusCircularButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh messages",
                    onClick = viewModel::refresh,
                )
            },
        )

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
            if (state.messages.isEmpty() && !state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No fleet chat messages yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.messages, key = { it.id }) { msg ->
                NexusGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = msg.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (msg.senderId == viewModel.currentDeviceId) FzLimeAccent else MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = msg.createdAt.takeLast(8),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

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
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (input.isEmpty()) {
                        Text(
                            text = "Broadcast to Nexus Fleet...",
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
                        viewModel.send(toSend)
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
