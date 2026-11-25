package com.nexus.mobile.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.nexus.mobile.security.BiometricHelper

/**
 * Reusable Page Auth Gate for sensitive routes (e.g. Node Terminal, Security Controls, Settings).
 * Requires fresh biometric or PIN verification before rendering [content].
 */
@Composable
fun PageAuthGate(
    requireAuth: Boolean,
    activity: FragmentActivity,
    verifyPin: (String) -> Boolean,
    content: @Composable () -> Unit,
) {
    var pageUnlocked by remember(requireAuth) { mutableStateOf(!requireAuth) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val bioAvailable = remember { BiometricHelper.canAuthenticate(activity) }

    LaunchedEffect(requireAuth) {
        if (requireAuth && !pageUnlocked && bioAvailable) {
            BiometricHelper.prompt(
                activity = activity,
                title = "Authenticate to Open Page",
                onSuccess = { pageUnlocked = true },
                onError = { errorMsg = it },
            )
        }
    }

    if (pageUnlocked) {
        content()
    } else {
        LockScreen(
            title = "Authentication Required",
            errorMessage = errorMsg,
            isBiometricAvailable = bioAvailable,
            onBiometricClick = {
                BiometricHelper.prompt(
                    activity = activity,
                    title = "Authenticate to Open Page",
                    onSuccess = { pageUnlocked = true },
                    onError = { errorMsg = it },
                )
            },
            onPinSubmitted = { pin ->
                if (verifyPin(pin)) {
                    pageUnlocked = true
                    errorMsg = null
                } else {
                    errorMsg = "Incorrect PIN"
                }
            },
        )
    }
}
