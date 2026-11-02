@file:OptIn(ExperimentalMaterial3Api::class)

package com.r2r.invoicescanner.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.r2r.invoicescanner.security.Base32
import com.r2r.invoicescanner.security.Totp

private const val AUTHENTICATOR_PACKAGE = "com.azure.authenticator"

/**
 * Step-by-step setup of the app lock with Microsoft Authenticator (free; any TOTP app works).
 * [onEnable] stores the secret and recovery codes; it is called only after a correct code.
 */
@Composable
fun MfaSetupScreen(
    onEnable: (ByteArray, List<String>) -> Boolean,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val secret = remember { Totp.newSecret() }
    val recovery = remember { Totp.newRecoveryCodes() }
    val key = remember { Base32.grouped(secret) }
    val uri = remember { Totp.uri(secret, "App lock") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    // The recovery codes are shown only once, so leaving that page needs a confirmation.
    val back: () -> Unit = { if (enabled) confirmLeave = true else onDone() }
    BackHandler(onBack = back)

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    val open: (Intent, String) -> Unit = { intent, fallback ->
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast(fallback)
        }
    }
    val verify: () -> Unit = {
        if (Totp.verify(secret, code)) {
            if (onEnable(secret, recovery)) enabled = true
        } else {
            error = "That code doesn't match. Check the phone's date & time are automatic and try the newest code."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (enabled) "Save your recovery codes" else "Set up app lock") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!enabled) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "Experimental. Once on, Invoice Scanner asks for a 6-digit code from Microsoft Authenticator " +
                            "when it opens and after 5 minutes in the background. Microsoft Authenticator is free and " +
                            "needs no Microsoft account for this.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Step("1", "Install Microsoft Authenticator") {
                    Text("Free from the Play Store. Skip this if you already have it.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        open(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$AUTHENTICATOR_PACKAGE")),
                            "Play Store not available — search for Microsoft Authenticator",
                        )
                    }) { Text("Open in Play Store") }
                }

                Step("2", "Add Invoice Scanner to Authenticator") {
                    Text(
                        "In Authenticator tap +  →  Other account (Google, Facebook, etc.)  →  Or enter code manually. " +
                            "Type the account name and paste the secret key below, then tap Finish.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Account name", style = MaterialTheme.typography.labelMedium)
                    Text(Totp.ISSUER, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Text("Secret key", style = MaterialTheme.typography.labelMedium)
                    Text(key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            clipboard.setText(AnnotatedString(key.replace(" ", "")))
                            toast("Secret key copied")
                        }) { Text("Copy key") }
                        OutlinedButton(onClick = {
                            open(Intent(Intent.ACTION_VIEW, Uri.parse(uri)), "No app opened the link — enter the key manually")
                        }) { Text("Open in app") }
                    }
                    Text(
                        "Authenticator on a different phone? Tap + → Other account and scan this code instead:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QrCode(uri, Modifier.size(180.dp))
                }

                Step("3", "Enter the code to check it works") {
                    Text(
                        "Authenticator now shows a 6-digit code for Invoice Scanner that changes every 30 seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EditField(
                        value = code,
                        onValueChange = { v -> code = v.filter { it.isDigit() }.take(6); error = null },
                        label = "6-digit code",
                        clearable = false,
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { verify() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = verify, enabled = code.length == 6) { Text("Verify and turn on") }
                }
            } else {
                Text("✓ App lock is on.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "If you lose your phone or Authenticator, these codes are the only way back into the app. Each works " +
                        "once. Save them now somewhere safe outside this phone (a password manager, or on paper). " +
                        "They will not be shown again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recovery.chunked(2).forEach { pair ->
                            Row {
                                pair.forEach {
                                    Text(it, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(140.dp))
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString("Invoice Scanner recovery codes\n" + recovery.joinToString("\n")))
                        toast("Recovery codes copied")
                    }) { Text("Copy codes") }
                    Button(onClick = onDone) { Text("I've saved them") }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Saved your recovery codes?") },
            text = {
                Text(
                    "They will not be shown again. Without them, losing Authenticator means you can't open the app " +
                        "(you would have to clear the app's data, which deletes the invoice register)."
                )
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Yes, leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Show codes") } },
        )
    }
}

@Composable
private fun Step(number: String, title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$number.  $title", style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** Draws [text] as a QR code (black on white, with a quiet zone). */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0) }
    Canvas(modifier.background(Color.White)) {
        val quiet = 2
        val cells = matrix.width + quiet * 2
        val cell = size.minDimension / cells
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) {
                drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell, cell))
            }
        }
    }
}
