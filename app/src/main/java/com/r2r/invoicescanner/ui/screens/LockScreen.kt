package com.r2r.invoicescanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.r2r.invoicescanner.security.Biometric
import kotlinx.coroutines.delay

/**
 * Shown instead of the app while it is locked. With [biometric] on, the system fingerprint / face /
 * screen-lock prompt opens straight away; with [authenticator] on, the Microsoft Authenticator code
 * (or a recovery code) can be entered. When both are on, either one unlocks the app.
 */
@Composable
fun LockScreen(
    biometric: Boolean,
    authenticator: Boolean,
    onBiometricUnlocked: () -> Unit,
    onBiometricUnavailable: () -> Unit,
    onUnlock: (String) -> Boolean,
) {
    val context = LocalContext.current
    var biometricError by remember { mutableStateOf<String?>(null) }
    val askBiometric: () -> Unit = {
        if (!Biometric.available(context)) {
            onBiometricUnavailable()
        } else {
            biometricError = null
            Biometric.prompt(context, "Unlock Invoice Scanner") { error ->
                if (error == null) onBiometricUnlocked() else biometricError = error
            }
        }
    }
    LaunchedEffect(Unit) { if (biometric) askBiometric() }

    var code by remember { mutableStateOf("") }
    var useRecovery by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val waiting = now < waitUntil
    val submit: () -> Unit = {
        if (!waiting && code.isNotBlank()) {
            if (onUnlock(code)) {
                code = ""
            } else {
                failures++
                code = ""
                // Slow down guessing: after 5 wrong codes, wait 30 seconds between tries.
                if (failures >= 5) waitUntil = System.currentTimeMillis() + 30_000
                error = if (useRecovery) "That recovery code is not valid (each works only once)." else "Wrong code — check the Invoice Scanner entry in Microsoft Authenticator."
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Invoice Scanner is locked", style = MaterialTheme.typography.headlineSmall)
            if (biometric) {
                OutlinedButton(onClick = askBiometric, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock with fingerprint / face")
                }
                biometricError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
            if (!authenticator) return@Column
            Text(
                when {
                    useRecovery -> "Enter one of the recovery codes you saved when you set up the app lock."
                    biometric -> "Or open Microsoft Authenticator and enter the 6-digit code shown for Invoice Scanner."
                    else -> "Open Microsoft Authenticator and enter the 6-digit code shown for Invoice Scanner."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EditField(
                value = code,
                onValueChange = { v -> code = if (useRecovery) v.uppercase().take(12) else v.filter { it.isDigit() }.take(6); error = null },
                label = if (useRecovery) "Recovery code" else "6-digit code",
                placeholder = if (useRecovery) "XXXX-XXXX" else "123456",
                clearable = false,
                isError = error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (useRecovery) KeyboardType.Ascii else KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (waiting) {
                Text(
                    "Too many wrong codes. Try again in ${((waitUntil - now) / 1000) + 1} s.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = submit, enabled = !waiting && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Unlock")
            }
            TextButton(onClick = { useRecovery = !useRecovery; code = ""; error = null }) {
                Text(if (useRecovery) "Use the authenticator code" else "Lost your phone's authenticator? Use a recovery code")
            }
        }
    }
}
