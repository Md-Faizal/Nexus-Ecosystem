package com.nexus.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LockScreen(
    title: String = "Unlock Nexus",
    errorMessage: String? = null,
    isBiometricAvailable: Boolean,
    onBiometricClick: () -> Unit,
    onPinSubmitted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { idx ->
                val filled = idx < pin.length
                Surface(
                    shape = CircleShape,
                    color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(16.dp),
                ) {}
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(28.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit ->
                    PinDigitButton(digit) {
                        if (pin.length < 4) {
                            val next = pin + digit
                            pin = next
                            if (next.length == 4) {
                                onPinSubmitted(next)
                                pin = ""
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (isBiometricAvailable) {
                    IconButton(onClick = onBiometricClick) {
                        Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Unlock")
                    }
                }
            }
            PinDigitButton("0") {
                if (pin.length < 4) {
                    val next = pin + "0"
                    pin = next
                    if (next.length == 4) {
                        onPinSubmitted(next)
                        pin = ""
                    }
                }
            }
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                }
            }
        }
    }
}

@Composable
private fun PinDigitButton(digit: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(72.dp),
    ) {
        Text(text = digit, style = MaterialTheme.typography.titleLarge)
    }
}
