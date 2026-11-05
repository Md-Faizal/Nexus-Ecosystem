package com.nexus.portal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(onBackClick: () -> Unit, onLogout: () -> Unit) {
    val activeProfile by ProfileRepository.activeProfile.collectAsState()
    val primaryColor = PortalTheme.colors.primary
    val context = LocalContext.current
    var showRemoveDialog by remember { mutableStateOf(false) }

    val profileBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalTheme.colors.background)
                .layerBackdrop(profileBackdrop)
        ) {
            TechGridBackground()
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PortalTheme.colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PROFILE PAGE",
                        color = PortalTheme.colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))

            val isLightTheme = !isSystemInDarkTheme()
            val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.6f) else Color(0xFF121212).copy(alpha = 0.4f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = profileBackdrop,
                        shape = { RoundedCornerShape(24.dp) },
                        effects = {
                            colorControls(
                                brightness = if (isLightTheme) 0.2f else 0f,
                                saturation = 1.5f
                            )
                            blur(if (isLightTheme) 16f.dp.toPx() else 8f.dp.toPx())
                            lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                        },
                        highlight = { com.kyant.backdrop.highlight.Highlight.Plain },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeProfile.profilePictureUrl != null) {
                        AsyncImage(
                            model = activeProfile.profilePictureUrl,
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(activeProfile.themeColorHex))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeProfile.avatarInitial,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = activeProfile.name,
                            color = PortalTheme.colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeProfile.statusText,
                            color = primaryColor,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = PortalTheme.colors.border)
                Spacer(modifier = Modifier.height(16.dp))

                // Telemetry Data
                Text("SESSION TELEMETRY", color = PortalTheme.colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                TelemetryRow("User ID", activeProfile.id)
                TelemetryRow("Device ID", ProfileRepository.deviceId)
                TelemetryRow("Session ID", ProfileRepository.sessionId)
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = PortalTheme.colors.border)
                Spacer(modifier = Modifier.height(16.dp))

                // Security Settings
                Text("SECURITY", color = PortalTheme.colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Fingerprint Authentication", color = PortalTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Use biometrics for faster unlocking", color = PortalTheme.colors.textSecondary, fontSize = 11.sp)
                    }
                    LiquidToggle(
                        selected = { activeProfile.hasFingerprint },
                        onSelect = { isChecked ->
                            ProfileRepository.updateFingerprintPreference(isChecked)
                        },
                        backdrop = profileBackdrop
                    )
                }
            }
        }

            Spacer(modifier = Modifier.weight(1f))

            // Switch Profile Button
            LiquidButton(
                onClick = {
                    ProfileRepository.lockSession()
                    onLogout()
                },
                backdrop = profileBackdrop,
                modifier = Modifier.fillMaxWidth(),
                surfaceColor = Color.White.copy(alpha = 0.12f)
            ) {
                androidx.compose.foundation.text.BasicText(
                    "SWITCH PROFILE / LOCK APP",
                    style = androidx.compose.ui.text.TextStyle(
                        color = PortalTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Remove Profile Button
            LiquidButton(
                onClick = {
                    showRemoveDialog = true
                },
                backdrop = profileBackdrop,
                modifier = Modifier.fillMaxWidth(),
                surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            ) {
                androidx.compose.foundation.text.BasicText(
                    "REMOVE IDENTITY NODE",
                    style = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        // Liquid Glass Dialog overlay — drawn within the same Box sharing profileBackdrop
        if (showRemoveDialog) {
            val isLightThemeDialog = !isSystemInDarkTheme()
            val contentColor = if (isLightThemeDialog) Color.Black else Color.White
            val accentColor = if (isLightThemeDialog) Color(0xFF0088FF) else Color(0xFF0091FF)
            val containerColor = if (isLightThemeDialog) Color(0xFFFAFAFA).copy(0.6f) else Color(0xFF121212).copy(0.4f)
            val dimColor = if (isLightThemeDialog) Color(0xFF29293A).copy(0.23f) else Color(0xFF121212).copy(0.56f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(dimColor)
                    }
                    .clickable { showRemoveDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(20f.dp)
                        .drawBackdrop(
                            backdrop = profileBackdrop,
                            shape = { RoundedCornerShape(40.dp) },
                            effects = {
                                colorControls(
                                    brightness = if (isLightThemeDialog) 0.2f else 0f,
                                    saturation = 1.8f
                                )
                                blur(if (isLightThemeDialog) 24f.dp.toPx() else 16f.dp.toPx())
                                lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                            },
                            highlight = { com.kyant.backdrop.highlight.Highlight.Plain },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .border(1.dp, Color.White.copy(alpha = if (isLightThemeDialog) 0.5f else 0.15f), RoundedCornerShape(40.dp))
                        .fillMaxWidth()
                        .clickable(enabled = false) {}
                ) {
                    BasicText(
                        "Remove Identity Node?",
                        Modifier.padding(28f.dp, 28f.dp, 28f.dp, 12f.dp),
                        style = TextStyle(MaterialTheme.colorScheme.error, 26f.sp, FontWeight.SemiBold)
                    )

                    BasicText(
                        "This action will permanently delete this identity node from the device. All biometric and PIN settings will be wiped. You will need to sign in with Google again to restore access.",
                        Modifier.padding(28f.dp, 12f.dp, 28f.dp, 20f.dp),
                        style = TextStyle(contentColor.copy(0.72f), 15f.sp)
                    )

                    Row(
                        Modifier
                            .padding(20f.dp, 4f.dp, 20f.dp, 28f.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12f.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel — liquid glass button
                        LiquidButton(
                            onClick = { showRemoveDialog = false },
                            backdrop = profileBackdrop,
                            modifier = Modifier.weight(1f).height(56f.dp),
                            surfaceColor = containerColor.copy(0.25f)
                        ) {
                            BasicText("Cancel", style = TextStyle(contentColor, 16f.sp, FontWeight.Medium))
                        }
                        // Remove — liquid glass button with error tint
                        LiquidButton(
                            onClick = {
                                showRemoveDialog = false
                                ProfileRepository.removeProfile(context, activeProfile.id)
                                onLogout()
                            },
                            backdrop = profileBackdrop,
                            modifier = Modifier.weight(1f).height(56f.dp),
                            surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                        ) {
                            BasicText("Remove", style = TextStyle(Color.White, 16f.sp, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, color = PortalTheme.colors.textSecondary, fontSize = 12.sp)
        Text(value, color = PortalTheme.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
