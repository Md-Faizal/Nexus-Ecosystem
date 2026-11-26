package com.nexus.mobile.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nexus.mobile.ui.components.NexusCircularBackButton
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import com.nexus.mobile.ui.theme.nexusSpecularSheen

@Composable
fun ReAuthScreen(
    title: String,
    subtitle: String,
    email: String,
    isLoading: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onSendOtp: () -> Unit,
    onSubmitProof: (password: String?, otpCode: String?, securityAnswer: String?) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val colors = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(24.dp)
    var method by remember { mutableIntStateOf(0) } // 0 = Email OTP, 1 = Password, 2 = Security Question
    var otpCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        NexusCircularBackButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Email OTP", "Password", "Sec. Question").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = method == idx,
                        onClick = { method = idx },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
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
                    when (method) {
                        0 -> {
                            Text(
                                text = "Verify via 6-digit code sent to $email",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onSendOtp,
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Send 6-Digit Email OTP")
                            }
                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otpCode = it },
                                label = { Text("6-Digit OTP Code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                        }

                        1 -> {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Account Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                        }

                        else -> {
                            OutlinedTextField(
                                value = securityAnswer,
                                onValueChange = { securityAnswer = it },
                                label = { Text("Security Question Answer") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                        }
                    }

                    if (!infoMessage.isNullOrBlank()) {
                        Text(
                            text = infoMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onlineDot,
                        )
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.dangerAccent,
                        )
                    }

                    Button(
                        onClick = {
                            when (method) {
                                0 -> onSubmitProof(null, otpCode, null)
                                1 -> onSubmitProof(password, null, null)
                                else -> onSubmitProof(null, null, securityAnswer)
                            }
                        },
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
                            Text("Verify & Continue", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
