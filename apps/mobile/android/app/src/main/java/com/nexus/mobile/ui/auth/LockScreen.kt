package com.nexus.mobile.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.mobile.ui.components.NexusCircularBackButton
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import com.nexus.mobile.ui.theme.nexusSpecularSheen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LockScreen(
    title: String = "Unlock Nexus",
    subtitle: String? = null,
    errorMessage: String? = null,
    lockoutUntilMs: Long = 0L,
    isBiometricAvailable: Boolean,
    onBiometricClick: () -> Unit,
    onPinSubmitted: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onSignOutClick: (() -> Unit)? = null,
    userBadgeName: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var remainingSecs by remember { mutableLongStateOf(0L) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(lockoutUntilMs) {
        while (true) {
            val left = ((lockoutUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            remainingSecs = left
            if (left <= 0L) break
            delay(500L)
        }
    }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            for (target in listOf(18f, -18f, 10f, -10f, 0f)) {
                shakeOffset.animateTo(target, tween(45))
            }
        }
    }

    val isLockedOut = remainingSecs > 0L

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        if (onBackClick != null) {
            NexusCircularBackButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding(),
            )
        } else if (!userBadgeName.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = palette.cardSurface,
                border = nexusSpecularBorder(palette.isDark, widthDp = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .nexusSpecularSheen(palette.isDark),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = palette.cardElevated,
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = userBadgeName.trim().take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = userBadgeName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = palette.cardSurface,
                border = nexusSpecularBorder(palette.isDark, widthDp = 0.75f),
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .nexusSpecularSheen(palette.isDark),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(26.dp))

            // Spring-animated PIN indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
            ) {
                repeat(4) { idx ->
                    val filled = idx < pin.length
                    val dotScale by animateFloatAsState(
                        targetValue = if (filled) 1.25f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                        label = "PinDotScale$idx",
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (filled) MaterialTheme.colorScheme.onSurface else palette.cardElevated,
                        animationSpec = tween(150),
                        label = "PinDotColor$idx",
                    )
                    Surface(
                        shape = CircleShape,
                        color = dotColor,
                        border = BorderStroke(0.75.dp, palette.hairlineBorder),
                        modifier = Modifier
                            .size(13.dp)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                            },
                    ) {}
                }
            }

            val statusText = when {
                isLockedOut -> "Too many attempts. Try again in ${remainingSecs}s"
                !errorMessage.isNullOrBlank() -> errorMessage
                else -> null
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = statusText ?: " ",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(22.dp))

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { digit ->
                        PinDigitButton(digit = digit, enabled = !isLockedOut) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (pin.length < 4) {
                                val next = pin + digit
                                pin = next
                                if (next.length == 4) {
                                    scope.launch {
                                        delay(95)
                                        onPinSubmitted(next)
                                        pin = ""
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    if (isBiometricAvailable && !isLockedOut) {
                        KeypadIconOrb(
                            icon = Icons.Rounded.Fingerprint,
                            contentDescription = "Biometric Unlock",
                            onClick = onBiometricClick,
                        )
                    }
                }
                PinDigitButton(digit = "0", enabled = !isLockedOut) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (pin.length < 4) {
                        val next = pin + "0"
                        pin = next
                        if (next.length == 4) {
                            scope.launch {
                                delay(95)
                                onPinSubmitted(next)
                                pin = ""
                            }
                        }
                    }
                }
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    KeypadIconOrb(
                        icon = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = "Backspace",
                        onClick = {
                            if (pin.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pin = pin.dropLast(1)
                            }
                        },
                    )
                }
            }
        }

        if (onSignOutClick != null) {
            TextButton(
                onClick = onSignOutClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = if (!userBadgeName.isNullOrBlank()) "Not $userBadgeName? Sign Out" else "Sign Out",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    digit: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalNexusPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "DigitScale",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        color = palette.cardSurface.copy(alpha = 0.92f),
        border = nexusSpecularBorder(palette.isDark, widthDp = 0.75f),
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .nexusSpecularSheen(palette.isDark),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun KeypadIconOrb(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val palette = LocalNexusPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "OrbScale",
    )

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = palette.cardSurface.copy(alpha = 0.72f),
        border = nexusSpecularBorder(palette.isDark, widthDp = 0.75f),
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .nexusSpecularSheen(palette.isDark),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
