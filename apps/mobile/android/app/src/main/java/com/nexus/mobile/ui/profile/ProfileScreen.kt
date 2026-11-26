package com.nexus.mobile.ui.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.mobile.ui.components.LocalNexusEdgeFadeConfig
import com.nexus.mobile.ui.components.NexusCircularBackButton
import com.nexus.mobile.ui.components.NexusCircularButton
import com.nexus.mobile.ui.components.nexusEdgeFadeBrush
import com.nexus.mobile.ui.components.rememberEdgeFadeAlpha
import com.nexus.mobile.ui.components.resolveColor
import com.nexus.mobile.ui.theme.LocalNexusPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun ProfileScreen(
    displayName: String,
    username: String?,
    email: String,
    role: String,
    avatarUrl: String?,
    deviceId: String,
    hasPin: Boolean,
    onBackClick: () -> Unit,
    onRefreshProfile: () -> Unit,
    onOpenSecurityPin: () -> Unit,
    onOpenUpdates: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val localHaze = remember { HazeState() }
    val listState = rememberLazyListState()
    val edgeConfig = LocalNexusEdgeFadeConfig.current
    val topColor = edgeConfig.topColor.resolveColor()
    val topBlurAlpha = if (topColor != null) rememberEdgeFadeAlpha(edgeConfig.topReaction, listState) else 0f
    var avatarBitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNullOrBlank()) {
            avatarBitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = when {
                    avatarUrl.startsWith("http") -> avatarUrl
                    avatarUrl.startsWith("/") -> "https://faizal.uk$avatarUrl"
                    else -> "https://faizal.uk/$avatarUrl"
                }
                URL(resolved).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.onSuccess { bmp -> avatarBitmap = bmp }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(localHaze),
            contentPadding = PaddingValues(top = 96.dp, bottom = 40.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Identity Hero
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Surface(
                            shape = CircleShape,
                            color = palette.cardElevated,
                            border = BorderStroke(1.5.dp, palette.hairlineBorder),
                            modifier = Modifier.size(88.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (avatarBitmap != null) {
                                    Image(
                                        bitmap = avatarBitmap!!,
                                        contentDescription = displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text(
                                        text = displayName.trim().take(1).uppercase().ifEmpty { "N" },
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(palette.onlineDot),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!username.isNullOrBlank()) {
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (email.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.cardSurface,
                        border = BorderStroke(1.dp, palette.hairlineBorder),
                    ) {
                        Text(
                            text = role.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Session & Device Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.cardSurface,
                    border = BorderStroke(1.dp, palette.hairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        ProfileInfoRow(
                            icon = Icons.Rounded.Shield,
                            label = "Nexus Auth",
                            value = "Verified JWT • faizal.uk",
                        )
                        HorizontalDivider(color = palette.hairlineBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileInfoRow(
                            icon = Icons.Rounded.Devices,
                            label = "Device ID",
                            value = deviceId,
                            mono = true,
                        )
                    }
                }
            }

            // Quick Actions Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.cardSurface,
                    border = BorderStroke(1.dp, palette.hairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        ProfileActionRow(
                            icon = Icons.Rounded.Lock,
                            title = if (hasPin) "Change App & Page PIN" else "Configure App PIN",
                            subtitle = if (hasPin) "4-digit lock active" else "Add biometric or PIN protection",
                            onClick = onOpenSecurityPin,
                        )
                        HorizontalDivider(color = palette.hairlineBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        ProfileActionRow(
                            icon = Icons.Rounded.SystemUpdate,
                            title = "Software Updates",
                            subtitle = "Check faizal.uk for new APK releases",
                            onClick = onOpenUpdates,
                        )
                    }
                }
            }

            // Sign Out
            item {
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.dangerAccent.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.dangerAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Sign Out of Nexus", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Scroll-linked top gradient mask + Apple Music circular floating back & refresh orbs
        Box(modifier = Modifier.fillMaxWidth()) {
            if (topColor != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = topBlurAlpha }
                        .nexusEdgeFadeBrush(
                            color = topColor,
                            topToBottom = true,
                            maxAlpha = edgeConfig.topMaxAlpha,
                        ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                NexusCircularBackButton(
                    onClick = onBackClick,
                    hazeState = localHaze,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                NexusCircularButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh profile",
                    onClick = onRefreshProfile,
                    hazeState = localHaze,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    mono: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
