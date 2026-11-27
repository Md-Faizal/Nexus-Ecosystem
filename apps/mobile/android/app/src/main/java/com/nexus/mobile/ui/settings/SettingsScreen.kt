package com.nexus.mobile.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.mobile.ui.components.EdgeFadePlacement
import com.nexus.mobile.ui.components.LocalNexusEdgeFadeConfig
import com.nexus.mobile.ui.components.NexusEdgeFadeContainer
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.ThemePreference

@Composable
fun SettingsScreen(
    deviceId: String,
    isAuthenticated: Boolean,
    hasPin: Boolean,
    isBiometricAvailable: Boolean,
    isBiometricEnabled: Boolean,
    isPageAuthEnabled: Boolean,
    themePreference: ThemePreference,
    useDynamicColor: Boolean,
    currentVersionName: String,
    hasUpdateAvailable: Boolean,
    onSetupOrChangePin: () -> Unit,
    onRemovePin: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onTogglePageAuth: (Boolean) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val listState = rememberLazyListState()
    val edgeConfig = LocalNexusEdgeFadeConfig.current.copy(placement = EdgeFadePlacement.BOTH)

    NexusEdgeFadeContainer(
        modifier = modifier,
        listState = listState,
        config = edgeConfig,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 20.dp,
                bottom = 156.dp,
                start = 20.dp,
                end = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
        item {
            Column(modifier = Modifier.statusBarsPadding()) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Security, theme, and over-the-air updates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Nexus Auth & Hub card
        item {
            SectionLabel("NEXUS AUTH")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Key,
                    title = if (isAuthenticated) "Paired to Nexus Hub" else "Pair with Nexus Hub",
                    subtitle = "Device ID: $deviceId • faizal.uk",
                    onClick = onOpenPairing,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }

        // Security & Page Auth card
        item {
            SectionLabel("SECURITY & PAGE AUTH")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Lock,
                    title = if (hasPin) "Change 4-Digit PIN" else "Set App & Page PIN",
                    subtitle = if (hasPin) "Salted SHA-256 lock enabled" else "Protect app & sensitive routes",
                    onClick = onSetupOrChangePin,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                if (hasPin) {
                    CardDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Fingerprint,
                        title = "Biometric Unlock",
                        subtitle = if (isBiometricAvailable) "Use fingerprint / face unlock" else "Not enrolled on device",
                        trailing = {
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = onToggleBiometric,
                                enabled = isBiometricAvailable,
                            )
                        },
                    )
                    CardDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Security,
                        title = "Page Auth Guard",
                        subtitle = "Require PIN/Biometrics for sensitive pages",
                        trailing = {
                            Switch(
                                checked = isPageAuthEnabled,
                                onCheckedChange = onTogglePageAuth,
                            )
                        },
                    )
                    CardDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = "Lock App Now",
                        subtitle = "Immediately require authentication",
                        onClick = onLockNow,
                    )
                    CardDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = "Remove PIN",
                        subtitle = "Disable app and page lock",
                        titleColor = palette.dangerAccent,
                        onClick = onRemovePin,
                    )
                }
            }
        }

        // Appearance card (Light + Pitch-Black OLED Dark)
        item {
            SectionLabel("APPEARANCE")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemePreference.entries.forEachIndexed { idx, pref ->
                            SegmentedButton(
                                selected = themePreference == pref,
                                onClick = { onSelectTheme(pref) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = ThemePreference.entries.size,
                                ),
                            ) {
                                Text(pref.label, maxLines = 1)
                            }
                        }
                    }
                }
                CardDivider()
                SettingsRow(
                    icon = Icons.Rounded.Palette,
                    title = "Material You Dynamic Color",
                    subtitle = "Blend accent colors from wallpaper",
                    trailing = {
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = onToggleDynamicColor,
                        )
                    },
                )
            }
        }

        // OTA Updates card
        item {
            SectionLabel("SYSTEM & OTA")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Software Update",
                    subtitle = if (hasUpdateAvailable) {
                        "New version available • Installed v$currentVersionName"
                    } else {
                        "Installed v$currentVersionName • Self-hosted OTA"
                    },
                    onClick = onOpenUpdates,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (hasUpdateAvailable) palette.warningDot else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val palette = LocalNexusPalette.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.cardSurface,
        border = BorderStroke(1.dp, palette.hairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        color = LocalNexusPalette.current.hairlineBorder,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = titleColor.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}
