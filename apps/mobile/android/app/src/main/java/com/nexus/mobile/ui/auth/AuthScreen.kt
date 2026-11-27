package com.nexus.mobile.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.delay

private enum class AuthMode {
    PASSWORD,
    OTP,
    SIGN_UP,
    FORGOT,
}

private val FzLime = Color(0xFF55FF00)
private const val WING_DOWN_PATH =
    "M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z"
private const val WING_UP_PATH =
    "M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z"

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
    val cardShape = RoundedCornerShape(26.dp)

    var mode by remember { mutableStateOf(AuthMode.PASSWORD) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpCountdown by remember { mutableIntStateOf(0) }
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secAnswer by remember { mutableStateOf("") }

    var activeSocialProvider by remember { mutableStateOf<String?>(null) }
    var socialTokenInput by remember { mutableStateOf("") }

    LaunchedEffect(otpCountdown) {
        if (otpCountdown > 0) {
            delay(1000L)
            otpCountdown -= 1
        }
    }

    LaunchedEffect(state) {
        if (state is AuthUiState.InfoMessage && state.otpAwaitingCode && otpCountdown == 0) {
            otpCountdown = 60
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        // Subtle ambient lime halo behind the top monogram
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            FzLime.copy(alpha = if (palette.isDark) 0.12f else 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(if (onBackClick != null) 38.dp else 18.dp))

            // Full neon-green FZ monogram orb
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = palette.cardSurface,
                border = nexusSpecularBorder(palette.isDark, 0.75f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .nexusSpecularSheen(palette.isDark),
                    contentAlignment = Alignment.Center,
                ) {
                    FzMonogramMark(modifier = Modifier.size(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (mode) {
                    AuthMode.PASSWORD -> "Sign in"
                    AuthMode.OTP -> "Email OTP"
                    AuthMode.SIGN_UP -> "Create account"
                    AuthMode.FORGOT -> "Reset password"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )

            Spacer(modifier = Modifier.height(22.dp))

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
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (mode) {
                        AuthMode.PASSWORD -> {
                            NexusAuthInput(
                                value = identifier,
                                onValueChange = { identifier = it },
                                placeholder = "Username or email",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            NexusAuthInput(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = "Password",
                                isPassword = true,
                                passwordVisible = passwordVisible,
                                onTogglePassword = { passwordVisible = !passwordVisible },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { onLoginWithPassword(identifier, password) },
                                ),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = {
                                    if (identifier.contains("@")) email = identifier.trim()
                                    mode = AuthMode.FORGOT
                                    onClearMessage()
                                }) {
                                    Text(
                                        text = "Forgot password?",
                                        color = colors.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    )
                                }
                            }

                            PrimaryAuthButton(
                                text = "Sign In",
                                isLoading = state is AuthUiState.Loading,
                                onClick = { onLoginWithPassword(identifier, password) },
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = palette.hairlineBorder,
                                )
                                Text(
                                    text = "OR",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant,
                                    letterSpacing = 1.sp,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = palette.hairlineBorder,
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (identifier.contains("@")) email = identifier.trim()
                                    otpCode = ""
                                    mode = AuthMode.OTP
                                    onClearMessage()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = nexusSpecularBorder(palette.isDark, 0.75f),
                            ) {
                                Text(
                                    text = "Use Email OTP instead",
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                TextButton(onClick = {
                                    if (identifier.contains("@")) email = identifier.trim()
                                    mode = AuthMode.SIGN_UP
                                    onClearMessage()
                                }) {
                                    Text(
                                        text = "Create an account",
                                        color = FzLime,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }

                        AuthMode.OTP -> {
                            val awaitingOtp = (state as? AuthUiState.InfoMessage)?.otpAwaitingCode == true
                            NexusAuthInput(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = "user@mail.xyz",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = if (awaitingOtp) ImeAction.Next else ImeAction.Done,
                                ),
                            )
                            if (awaitingOtp) {
                                NexusAuthInput(
                                    value = otpCode,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(6)
                                        otpCode = digits
                                        if (digits.length == 6) {
                                            onVerifyOtp(email, digits)
                                        }
                                    },
                                    placeholder = "6-digit code",
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { onVerifyOtp(email, otpCode) },
                                    ),
                                )
                                PrimaryAuthButton(
                                    text = "Verify 6-Digit Code",
                                    isLoading = state is AuthUiState.Loading,
                                    onClick = { onVerifyOtp(email, otpCode) },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    TextButton(onClick = {
                                        mode = AuthMode.PASSWORD
                                        onClearMessage()
                                    }) {
                                        Text("Use Password", color = colors.onSurfaceVariant)
                                    }
                                    TextButton(
                                        enabled = otpCountdown == 0 && state !is AuthUiState.Loading,
                                        onClick = {
                                            otpCountdown = 60
                                            onSendOtp(email)
                                        },
                                    ) {
                                        Text(
                                            text = if (otpCountdown > 0) "Resend in ${otpCountdown}s" else "Resend Code",
                                            color = if (otpCountdown > 0) colors.onSurfaceVariant else FzLime,
                                        )
                                    }
                                }
                            } else {
                                PrimaryAuthButton(
                                    text = "Send 6-Digit Code",
                                    isLoading = state is AuthUiState.Loading,
                                    onClick = { onSendOtp(email) },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    TextButton(onClick = {
                                        mode = AuthMode.PASSWORD
                                        onClearMessage()
                                    }) {
                                        Text("Use Password instead", color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        AuthMode.SIGN_UP -> {
                            NexusAuthInput(
                                value = fullName,
                                onValueChange = { fullName = it },
                                placeholder = "Full Name",
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            NexusAuthInput(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = "user@mail.xyz",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            NexusAuthInput(
                                value = username,
                                onValueChange = { username = it },
                                placeholder = "Username (optional)",
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            NexusAuthInput(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = "Password (optional)",
                                isPassword = true,
                                passwordVisible = passwordVisible,
                                onTogglePassword = { passwordVisible = !passwordVisible },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            NexusAuthInput(
                                value = secAnswer,
                                onValueChange = { secAnswer = it },
                                placeholder = "Recovery answer: First pet / city? (optional)",
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            )
                            PrimaryAuthButton(
                                text = "Create Account",
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                TextButton(onClick = {
                                    mode = AuthMode.PASSWORD
                                    onClearMessage()
                                }) {
                                    Text("Already have an account? Sign in", color = colors.onSurfaceVariant)
                                }
                            }
                        }

                        AuthMode.FORGOT -> {
                            Text(
                                text = "We'll email you a link to sign in immediately or set a new password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            NexusAuthInput(
                                value = email,
                                onValueChange = { email = it },
                                placeholder = "user@mail.xyz",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { onRequestPasswordReset(email) },
                                ),
                            )
                            PrimaryAuthButton(
                                text = "Send Login & Reset Link",
                                isLoading = state is AuthUiState.Loading,
                                onClick = { onRequestPasswordReset(email) },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                TextButton(onClick = {
                                    mode = AuthMode.PASSWORD
                                    onClearMessage()
                                }) {
                                    Text("Back to Sign in", color = colors.onSurfaceVariant)
                                }
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
                                        containerColor = FzLime,
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Create Account", fontWeight = FontWeight.Bold)
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

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf("Google", "Meta").forEach { provider ->
                    OutlinedButton(
                        onClick = {
                            activeSocialProvider = if (activeSocialProvider == provider) null else provider
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = nexusSpecularBorder(palette.isDark, 0.75f),
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
                            .clip(cardShape)
                            .nexusSpecularSheen(palette.isDark)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "$activeSocialProvider Sign-In",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                        )
                        NexusAuthInput(
                            value = socialTokenInput,
                            onValueChange = { socialTokenInput = it },
                            placeholder = "Paste $activeSocialProvider ID / Access Token",
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

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(FzLime),
                )
                Text(
                    text = "Powered by Nexus Central Auth",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FzMonogramMark(modifier: Modifier = Modifier) {
    val wingDown = remember { PathParser().parsePathString(WING_DOWN_PATH).toPath() }
    val wingUp = remember { PathParser().parsePathString(WING_UP_PATH).toPath() }
    Canvas(modifier = modifier) {
        val sx = size.width / 171.16f
        val sy = size.height / 89.17f
        val scaleFactor = minOf(sx, sy)
        val dx = (size.width - 171.16f * scaleFactor) / 2f
        val dy = (size.height - 89.17f * scaleFactor) / 2f
        withTransform({
            translate(dx, dy)
            scale(scaleFactor, scaleFactor, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            drawPath(wingDown, color = FzLime)
            drawPath(wingUp, color = FzLime)
        }
    }
}

@Composable
private fun NexusAuthInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val palette = LocalNexusPalette.current
    val colors = MaterialTheme.colorScheme
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) FzLime.copy(alpha = 0.7f) else palette.hairlineBorder,
        animationSpec = tween(180),
        label = "AuthInputBorder",
    )
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(palette.cardElevated)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.onSurface,
                    fontSize = 15.sp,
                ),
                cursorBrush = SolidColor(FzLime),
                visualTransformation = if (isPassword && !passwordVisible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
            )
        }
        if (isPassword && onTogglePassword != null) {
            IconButton(
                onClick = onTogglePassword,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = "Toggle password visibility",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PrimaryAuthBounce",
    )

    Button(
        onClick = onClick,
        enabled = !isLoading,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FzLime,
            contentColor = Color.Black,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.Black,
            )
        } else {
            Text(text = text, fontWeight = FontWeight.Bold)
        }
    }
}
