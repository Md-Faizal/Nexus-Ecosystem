package com.nexus.portal.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SetPinScreen(onPinSet: () -> Unit) {
    val primaryColor = PortalTheme.colors.primary
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    var step by remember { mutableStateOf(0) } // 0 = enter pin, 1 = confirm pin
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    val weakPins = listOf("0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999", "1234", "4321", "2580")

    suspend fun triggerError(msg: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        errorMessage = msg
        shakeOffset.animateTo(20f, animationSpec = tween(50))
        shakeOffset.animateTo(-20f, animationSpec = tween(50))
        shakeOffset.animateTo(10f, animationSpec = tween(50))
        shakeOffset.animateTo(-10f, animationSpec = tween(50))
        shakeOffset.animateTo(0f, animationSpec = tween(50))
        currentPin = ""
        if (step == 1 && msg == "PINs do not match") {
            firstPin = ""
            step = 0
        }
        delay(2000)
        errorMessage = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
    ) {
        TechGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (step == 0) "SECURE YOUR NODE" else "CONFIRM SECURE PIN",
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (step == 0) "Create a 4-digit PIN for offline access." else "Re-enter your 4-digit PIN.",
                    color = PortalTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // PIN Dots
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp).offset(x = shakeOffset.value.dp)
                ) {
                    repeat(4) { index ->
                        val active = index < currentPin.length
                        val dotColor = if (active) primaryColor else PortalTheme.colors.border
                        val dotSize = if (active) 16.dp else 12.dp

                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(
                                    1.dp,
                                    if (active) primaryColor.copy(alpha = 0.8f) else Color.Transparent,
                                    CircleShape
                                )
                        )
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "CLR")
                )

                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(68.dp))
                            } else if (key == "CLR") {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            enabled = !isProcessing,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(bounded = true, color = primaryColor)
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⌫", color = PortalTheme.colors.textPrimary, fontSize = 24.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            enabled = !isProcessing,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(bounded = true, color = primaryColor)
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (currentPin.length < 4) {
                                                currentPin += key
                                                if (currentPin.length == 4) {
                                                    coroutineScope.launch {
                                                        delay(100)
                                                        if (step == 0) {
                                                            if (weakPins.contains(currentPin)) {
                                                                triggerError("Weak PIN. Choose a stronger one.")
                                                            } else {
                                                                firstPin = currentPin
                                                                currentPin = ""
                                                                step = 1
                                                            }
                                                        } else {
                                                            if (currentPin == firstPin) {
                                                                isProcessing = true
                                                                val success = ProfileRepository.setPin(currentPin)
                                                                if (success) {
                                                                    onPinSet()
                                                                } else {
                                                                    triggerError("Network error. Try again.")
                                                                    isProcessing = false
                                                                }
                                                            } else {
                                                                triggerError("PINs do not match")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(key, color = PortalTheme.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
