package com.nexus.mobile.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.mobile.ui.components.NexusCircularBackButton
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import com.nexus.mobile.ui.theme.nexusSpecularSheen

private enum class AuthMode(val label: String) {
    PASSWORD("Password"),
    OTP("Email OTP"),
    MAGIC_LINK("Magic Link"),
    DEVICE_CODE("Device Code"),
    SIGN_UP("Create Account"),
    FORGOT("Reset Password"),
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onLoginWithPassword: (String, String) -> Unit,
    onSendOtp: (String) -> Unit,
    onVerifyOtp: (String, String) -> Unit,
    onSendMagicLink: (String) -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onRegisterAccount: (email: String, fullName: String, password: String?, username: String?, secQuestion: String?, secAnswer: String?) -> Unit,
    onSocialTokenLogin: (provider: String, token: String) -> Unit,
    onStartDevicePairing: () -> Unit,
    onCancelPairing: () -> Unit,
    onClearMessage: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(24.dp)

    var mode by remember { mutableStateOf(AuthMode.PASSWORD) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secAnswer by remember { mutableStateOf("") }

    var activeSocialProvider by remember { mutableStateOf<String?>(null) }
    var socialTokenInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        if (onBackClick != null) {
            NexusCircularBackButton(
                onClick = {
                    onCancelPairing()
                    onBackClick()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onBackClick != null) Spacer(modifier = Modifier.height(44.dp))

            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = colors.primaryContainer,
                border = nexusSpecularBorder(palette.isDark, 0.75f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = colors.secondary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Nexus Identity",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = "Central auth across Web, macOS & Mobile",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    AuthMode.PASSWORD,
                    AuthMode.OTP,
                    AuthMode.MAGIC_LINK,
                    AuthMode.DEVICE_CODE,
                    AuthMode.SIGN_UP,
                ).forEach { tab ->
                    FilterChip(
                        selected = mode == tab,
                        onClick = {
                            mode = tab
                            onClearMessage()
                            if (tab != AuthMode.DEVICE_CODE) onCancelPairing()
                        },
                        label = { Text(tab.label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                color = palette.cardSurface,
                border = nexusSpecularBorder(palette.isDark, 0.75f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .nexusSpecularSheen(palette.isDark)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (mode) {
                        AuthMode.PASSWORD -> {
                            OutlinedTextField(
                                value = identifier,
                                onValueChange = { identifier = it },
                                label = { Text("Email or username") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = "Toggle password visibility",
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { onLoginWithPassword(identifier, password) },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = {
                                    email = if (identifier.contains("@")) identifier else email
                                    mode = AuthMode.FORGOT
                                    onClearMessage()
                                }) {
                                    Text("Forgot Password?", color = colors.secondary, fontSize = 13.sp)
                                }
                            }
                            PrimaryAuthButton(
                                text = "Sign In",
                                isLoading = state is AuthUiState.Loading,
                                onClick = { onLoginWithPassword(identifier, password) },
                            )
                        }

                        AuthMode.OTP -> {
                            val awaitingOtp = (state as? AuthUiState.InfoMessage)?.otpAwaitingCode == true
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Account Email") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            if (awaitingOtp) {
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otpCode = it },
                                    label = { Text("6-Digit Verification Code") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                )
                                PrimaryAuthButton(
                                    text = "Verify 6-Digit Code",
                                    isLoading = state is AuthUiState.Loading,
                                    onClick = { onVerifyOtp(email, otpCode) },
                                )
                                TextButton(onClick = { onSendOtp(email) }) {
                                    Text("Resend Code", color = colors.onSurfaceVariant)
                                }
                            } else {
                                PrimaryAuthButton(
                                    text = "Send 6-Digit Code",
                                    isLoading = state is AuthUiState.Loading,
                                    onClick = { onSendOtp(email) },
                                )
                            }
                        }

                        AuthMode.MAGIC_LINK -> {
                            Text(
                                text = "We'll send a one-tap Magic Link to your email. Tapping 'Open in Nexus Mobile' signs you straight in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Account Email") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            PrimaryAuthButton(
                                text = "Send Magic Link",
                                isLoading = state is AuthUiState.Loading,
                                onClick = { onSendMagicLink(email) },
                            )
                        }

                        AuthMode.DEVICE_CODE -> {
                            when (state) {
                                is AuthUiState.WaitingDeviceApproval -> {
                                    Text(
                                        text = "Approve this device in Nexus Admin → Security → Devices, or open the approval page:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                    Text(
                                        text = state.userCode,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 4.sp,
                                        color = colors.secondary,
                                    )
                                    PrimaryAuthButton(
                                        text = "Open Approval Page",
                                        isLoading = false,
                                        onClick = {
                                            val url = "${state.verificationUri}?code=${Uri.encode(state.userCode)}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        },
                                    )
                                    OutlinedButton(
                                        onClick = onCancelPairing,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                                else -> {
                                    Text(
                                        text = "Onboard this device using an 8-character Nexus Device Code approved from your browser.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                    PrimaryAuthButton(
                                        text = "Generate Onboarding Code",
                                        isLoading = state is AuthUiState.Loading,
                                        onClick = onStartDevicePairing,
                                    )
                                }
                            }
                        }

                        AuthMode.SIGN_UP -> {
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password (optional for passwordless)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            OutlinedTextField(
                                value = secAnswer,
                                onValueChange = { secAnswer = it },
                                label = { Text("Recovery Answer: First pet / city? (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            PrimaryAuthButton(
                                text = "Create Nexus Account",
                                isLoading = state is AuthUiState.Loading,
                                onClick = {
                                    onRegisterAccount(
                                        email,
                                        fullName,
                                        password.takeIf { it.isNotBlank() },
                                        username.takeIf { it.isNotBlank() },
                                        if (secAnswer.isNotBlank()) "pet" else null,
                                        secAnswer.takeIf { it.isNotBlank() },
                                    )
                                },
                            )
                        }

                        AuthMode.FORGOT -> {
                            Text(
                                text = "We'll email you a secure one-time link to set a new Nexus password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Account Email") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            PrimaryAuthButton(
                                text = "Send Password Reset Link",
                                isLoading = state is AuthUiState.Loading,
                                onClick = { onRequestPasswordReset(email) },
                            )
                            TextButton(onClick = { mode = AuthMode.PASSWORD }) {
                                Text("Back to Password Sign In", color = colors.onSurfaceVariant)
                            }
                        }
                    }

                    when (state) {
                        is AuthUiState.InfoMessage -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.onlineDot,
                            )
                        }
                        is AuthUiState.AccountNotFound -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(palette.dangerAccent.copy(alpha = 0.12f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.dangerAccent,
                                )
                                Button(
                                    onClick = {
                                        email = state.email
                                        mode = AuthMode.SIGN_UP
                                        onClearMessage()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.primary,
                                        contentColor = colors.onPrimary,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Create Account for ${state.email}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is AuthUiState.Error -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.dangerAccent,
                            )
                        }
                        else -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "OR CONTINUE WITH",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.4.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf("Google", "Meta", "X").forEach { provider ->
                    OutlinedButton(
                        onClick = {
                            activeSocialProvider = if (activeSocialProvider == provider) null else provider
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(provider, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                    }
                }
            }

            if (activeSocialProvider != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape,
                    color = palette.cardSurface,
                    border = nexusSpecularBorder(palette.isDark, 0.75f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "$activeSocialProvider OAuth Token",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                        )
                        OutlinedTextField(
                            value = socialTokenInput,
                            onValueChange = { socialTokenInput = it },
                            label = { Text("Paste $activeSocialProvider ID / Access Token") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        )
                        PrimaryAuthButton(
                            text = "Continue with $activeSocialProvider",
                            isLoading = state is AuthUiState.Loading,
                            onClick = {
                                activeSocialProvider?.let { p -> onSocialTokenLogin(p, socialTokenInput) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryAuthButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.onPrimary,
            )
        } else {
            Text(text = text, fontWeight = FontWeight.Bold)
        }
    }
}
