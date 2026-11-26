package com.nexus.mobile.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.mobile.ui.theme.LocalNexusPalette
import com.nexus.mobile.ui.theme.nexusSpecularBorder
import com.nexus.mobile.ui.theme.nexusSpecularSheen

@Composable
fun BiometricOptInScreen(
    displayName: String,
    onEnableBiometric: () -> Unit,
    onAskNextTime: () -> Unit,
    onDontAskAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNexusPalette.current
    val colors = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = colors.primaryContainer,
                    border = nexusSpecularBorder(palette.isDark, 0.75f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = "Fingerprint Unlock",
                            tint = colors.secondary,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Enable Fingerprint Unlock?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Unlock Nexus faster as $displayName using your device biometrics instead of typing your 4-digit PIN every time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onEnableBiometric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    Text(
                        text = "Enable Fingerprint Unlock",
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onAskNextTime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "Not Now · Ask Me Next Time",
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(onClick = onDontAskAgain) {
                    Text(
                        text = "Don't ask again on this device",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
