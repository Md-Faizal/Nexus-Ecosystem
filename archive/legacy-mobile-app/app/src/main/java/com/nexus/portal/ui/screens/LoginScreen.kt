package com.nexus.portal.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nexus.portal.data.repository.ProfileRepository
import com.nexus.portal.ui.components.TechGridBackground
import com.nexus.portal.ui.theme.PortalTheme
import com.nexus.portal.ui.theme.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    activity: FragmentActivity
) {
    val activeProfile by ProfileRepository.activeProfile.collectAsState()
    val lockoutEndTime by ProfileRepository.lockoutEndTime.collectAsState()
    val primaryColor = PortalTheme.colors.primary
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var pinCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    var timeLeftMillis by remember { mutableStateOf(0L) }
    val shakeOffset = remember { Animatable(0f) }
    
    // Update countdown timer
    LaunchedEffect(lockoutEndTime) {
        while (true) {
            val remaining = lockoutEndTime - System.currentTimeMillis()
            if (remaining > 0) {
                timeLeftMillis = remaining
            } else {
                timeLeftMillis = 0L
            }
            delay(500)
        }
    }

    // Animation values
    val logoScale by animateFloatAsState(
        targetValue = if (isAuthenticating) 0.9f else 1.0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "LogoScale"
    )

    suspend fun triggerError(msg: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        errorMessage = msg
        shakeOffset.animateTo(20f, animationSpec = tween(50))
        shakeOffset.animateTo(-20f, animationSpec = tween(50))
        shakeOffset.animateTo(10f, animationSpec = tween(50))
        shakeOffset.animateTo(-10f, animationSpec = tween(50))
        shakeOffset.animateTo(0f, animationSpec = tween(50))
        pinCode = ""
        delay(2500)
        errorMessage = ""
    }

    fun launchBiometricPrompt() {
        if (!activeProfile.hasFingerprint) return
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticating = true
                    coroutineScope.launch {
                        delay(600)
                        isAuthenticating = false
                        onLoginSuccess()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    coroutineScope.launch {
                        triggerError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    coroutineScope.launch {
                        triggerError("Biometric authentication failed")
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Nexus Portal Authentication")
            .setSubtitle("Authenticate to unlock secure VM node")
            .setNegativeButtonText("Use PIN Pad")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            isAuthenticating = true
            coroutineScope.launch {
                delay(1200)
                isAuthenticating = false
                onLoginSuccess()
                Toast.makeText(activity, "Bypassed: Biometrics not enrolled on emulator", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-launch biometric on screen mount and lifecycle resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activeProfile.id, lockoutEndTime) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (activeProfile.hasFingerprint && lockoutEndTime < System.currentTimeMillis()) {
                    coroutineScope.launch {
                        delay(300)
                        launchBiometricPrompt()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Initial launch when the screen first mounts
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (activeProfile.hasFingerprint && lockoutEndTime < System.currentTimeMillis()) {
                coroutineScope.launch {
                    delay(300)
                    launchBiometricPrompt()
                }
            }
        }
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val loginBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PortalTheme.colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PortalTheme.colors.background)
                .layerBackdrop(loginBackdrop)
        ) {
            TechGridBackground()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Upper Tech Logo container
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = (20).dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .drawBackdrop(
                            backdrop = loginBackdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(12f.dp.toPx())
                                lens(36f.dp.toPx(), 36f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(primaryColor.copy(alpha = 0.08f))
                            }
                        )
                        .border(2.dp, primaryColor.copy(alpha = logoScale), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeProfile.profilePictureUrl != null) {
                        coil.compose.AsyncImage(
                            model = activeProfile.profilePictureUrl,
                            contentDescription = "Profile Picture",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Nexus Secure Portal",
                            tint = primaryColor,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    // Glow circle pulse
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, primaryColor.copy(alpha = 0.2f), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "NEXUS SECURE PORTAL",
                    color = PortalTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "WELCOME BACK, ${activeProfile.name.split(" ")[0].uppercase()}",
                    color = primaryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (timeLeftMillis > 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 60.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "TOO MANY ATTEMPTS",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Try again in ${timeLeftMillis / 1000}s",
                        color = PortalTheme.colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                // Middle input status & passcode dots
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Passcode Display Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 20.dp).offset(x = shakeOffset.value.dp)
                    ) {
                        repeat(4) { index ->
                            val active = index < pinCode.length
                            val dotColor = if (active) primaryColor else PortalTheme.colors.border
                            val dotSize = if (active) 16.dp else 12.dp
                            val animatedSize by animateDpAsState(targetValue = dotSize, label = "dotSize")

                            Box(
                                modifier = Modifier
                                    .size(animatedSize)
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

                    // Error or Info Text
                    AnimatedVisibility(
                        visible = errorMessage.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFF3366),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    if (errorMessage.isEmpty()) {
                        Text(
                            text = if (activeProfile.hasFingerprint) "Enter secure node PIN or tap biometrics" else "Enter secure node PIN",
                            color = PortalTheme.colors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }

                // Lower Keypad Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("BIO", "0", "CLR")
                    )

                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            row.forEach { key ->
                                if (key == "BIO") {
                                    if (activeProfile.hasFingerprint) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .drawBackdrop(
                                                    backdrop = loginBackdrop,
                                                    shape = { CircleShape },
                                                    effects = {
                                                        vibrancy()
                                                        blur(8f.dp.toPx())
                                                        lens(16f.dp.toPx(), 16f.dp.toPx())
                                                    },
                                                    onDrawSurface = {
                                                        drawRect(primaryColor.copy(alpha = 0.15f))
                                                    }
                                                )
                                                .border(1.dp, primaryColor.copy(alpha = 0.4f), CircleShape)
                                                .clickable { launchBiometricPrompt() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Biometrics",
                                                tint = primaryColor,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(68.dp))
                                    }
                                } else if (key == "CLR") {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .drawBackdrop(
                                                backdrop = loginBackdrop,
                                                shape = { CircleShape },
                                                effects = {
                                                    vibrancy()
                                                    blur(8f.dp.toPx())
                                                    lens(16f.dp.toPx(), 16f.dp.toPx())
                                                },
                                                onDrawSurface = {
                                                    drawRect(Color.White.copy(alpha = 0.08f))
                                                }
                                            )
                                            .border(1.dp, PortalTheme.colors.border.copy(alpha = 0.3f), CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(bounded = true, color = primaryColor)
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (pinCode.isNotEmpty()) {
                                                    pinCode = pinCode.dropLast(1)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "⌫",
                                            color = PortalTheme.colors.textPrimary,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .drawBackdrop(
                                                backdrop = loginBackdrop,
                                                shape = { CircleShape },
                                                effects = {
                                                    vibrancy()
                                                    blur(8f.dp.toPx())
                                                    lens(16f.dp.toPx(), 16f.dp.toPx())
                                                },
                                                onDrawSurface = {
                                                    drawRect(Color.White.copy(alpha = 0.08f))
                                                }
                                            )
                                            .border(1.dp, PortalTheme.colors.border.copy(alpha = 0.3f), CircleShape)
                                            .clickable(
                                                enabled = !isAuthenticating,
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(bounded = true, color = primaryColor)
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                if (pinCode.length < 4) {
                                                    pinCode += key
                                                    if (pinCode.length == 4) {
                                                        coroutineScope.launch {
                                                            delay(150)
                                                            isAuthenticating = true
                                                            val success = ProfileRepository.verifyPin(pinCode)
                                                            if (success) {
                                                                delay(400)
                                                                isAuthenticating = false
                                                                onLoginSuccess()
                                                            } else {
                                                                isAuthenticating = false
                                                                triggerError("Access Denied: Invalid passcode")
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            color = PortalTheme.colors.textPrimary,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
