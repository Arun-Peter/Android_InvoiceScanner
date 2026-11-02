@file:OptIn(ExperimentalMaterial3Api::class)

package com.r2r.invoicescanner.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.r2r.invoicescanner.BuildConfig
import com.r2r.invoicescanner.model.Business
import com.r2r.invoicescanner.security.AppLock
import com.r2r.invoicescanner.security.Biometric
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.settings.AiProvider
import com.r2r.invoicescanner.settings.AiSettings
import com.r2r.invoicescanner.settings.ImageStyle
import com.r2r.invoicescanner.storage.ImageStore
import com.r2r.invoicescanner.ui.theme.NegativeRed
import com.r2r.invoicescanner.ui.theme.PositiveGreen

@Composable
fun SettingsScreen(
    current: AiSettings,
    currentStyle: ImageStyle,
    categories: List<ExpenseCategory>,
    deletedCategories: List<ExpenseCategory>,
    testing: Boolean,
    connectionResult: Pair<Boolean, String>?,
    onSave: (AiSettings, ImageStyle) -> Unit,
    onTest: (AiSettings) -> Unit,
    onRemoveCategory: (ExpenseCategory) -> Unit,
    onRestoreCategory: (ExpenseCategory) -> Unit,
    onPurgeCategory: (ExpenseCategory) -> Unit,
    businesses: List<Business>,
    defaultBusiness: String?,
    onAddBusiness: (String, String) -> String?,
    onDeleteBusiness: (String) -> Unit,
    onSetDefaultBusiness: (String) -> Unit,
    biometricEnabled: Boolean,
    onSetBiometric: (Boolean) -> Unit,
    mfaEnabled: Boolean,
    recoveryCodesLeft: Int,
    onSetupMfa: () -> Unit,
    onDisableMfa: () -> Unit,
    mfaSetupKey: () -> Pair<String, String>?,
    relockMinutes: Int,
    onRelockMinutes: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var s by remember(current) { mutableStateOf(current) }
    var style by remember(currentStyle) { mutableStateOf(currentStyle) }
    var showKey by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ExpenseCategory?>(null) }
    var showDeleted by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf<ExpenseCategory?>(null) }
    var addingBusiness by remember { mutableStateOf(false) }
    var confirmDeleteBusiness by remember { mutableStateOf<Business?>(null) }
    var turningOffMfa by remember { mutableStateOf(false) }
    var setupKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(s, style) }) { Text("Save") }
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
            // 1. How invoices are read
            Text("Technology", style = MaterialTheme.typography.titleMedium)
            AiProvider.entries.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = s.provider == p, onClick = { s = s.copy(provider = p) }, role = Role.RadioButton)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.provider == p, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(p.label, style = MaterialTheme.typography.bodyLarge)
                        Text(p.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }


            val keyField: @Composable () -> Unit = {
                EditField(
                    value = s.apiKey,
                    onValueChange = { v -> s = s.copy(apiKey = v) },
                    label = "API key",
                    clearable = false,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (s.provider) {
                AiProvider.AZURE_OPENAI -> {
                    EditField(
                        value = s.azureEndpoint,
                        onValueChange = { v -> s = s.copy(azureEndpoint = v) },
                        label = "Endpoint",
                        placeholder = "https://your-resource.openai.azure.com",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EditField(
                        value = s.azureDeployment,
                        onValueChange = { v -> s = s.copy(azureDeployment = v) },
                        label = "Deployment name (vision model, e.g. gpt-4o)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EditField(
                        value = s.azureApiVersion,
                        onValueChange = { v -> s = s.copy(azureApiVersion = v) },
                        label = "API version",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    keyField()
                }
                AiProvider.OPENAI -> {
                    EditField(
                        value = s.openAiBaseUrl,
                        onValueChange = { v -> s = s.copy(openAiBaseUrl = v) },
                        label = "Base URL",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EditField(
                        value = s.openAiModel,
                        onValueChange = { v -> s = s.copy(openAiModel = v) },
                        label = "Model (must accept images)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    keyField()
                }
                AiProvider.NONE -> {
                    Text(
                        "Text is read with Google ML Kit on the phone and parsed with rules. Nothing leaves the device. " +
                            "Comments and classification are rule-based and less accurate than with AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (s.provider != AiProvider.NONE) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "Privacy: the cropped invoice image and its text are sent to the AI service above. " +
                            "Use only a service your company has approved for financial documents. " +
                            "The key is stored privately in this app and is never included in backups.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (s.provider != AiProvider.NONE) {
                OutlinedButton(onClick = { onTest(s) }, enabled = s.isConfigured && !testing) {
                    Text(if (testing) "Testing…" else "Test connection")
                }
                connectionResult?.let { (ok, text) ->
                    Text(
                        (if (ok) "✓ " else "✗ ") + text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) PositiveGreen else NegativeRed,
                    )
                }
            }

            // 2. Expense categories
            HorizontalDivider()
            Text("Expense Categories", style = MaterialTheme.typography.titleMedium)
            Text(
                "These are offered on every scan and used when classifying invoices. To add one, type a new " +
                    "expense type while reviewing a scan and tick Regular. Deleting one does not change invoices " +
                    "already saved with it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            categories.sortedBy { it.name.lowercase() }.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (c.builtIn) "Built-in" else "Added by you",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirmDelete = c }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${c.name}", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (deletedCategories.isNotEmpty()) {
                TextButton(onClick = { showDeleted = true }) {
                    Text("Deleted categories (${deletedCategories.size})…")
                }
            }

            // 3. Saved image
            HorizontalDivider()
            Text("Saved Image", style = MaterialTheme.typography.titleMedium)
            ImageStyle.entries.forEach { st ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = style == st, onClick = { style = st }, role = Role.RadioButton)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = style == st, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(st.label, style = MaterialTheme.typography.bodyLarge)
                        Text(st.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Applies to new scans. Images are saved in ${ImageStore.RELATIVE_PATH}/<business code>/<expense type>/.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )


            // 4. Businesses
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Businesses", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { addingBusiness = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add a business", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "Select the business you are processing invoices for; it is preselected on every new scan and can be " +
                    "changed per invoice. Its 3-character code is part of the file name " +
                    "(date_CODE_type_vendor_amount.jpeg) and of the folder: ${ImageStore.RELATIVE_PATH}/CODE/<expense type>/.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (businesses.isEmpty()) {
                Text("No businesses yet — tap + to add one.", style = MaterialTheme.typography.bodyMedium)
            }
            businesses.sortedBy { it.code }.forEach { b ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = b.code == defaultBusiness, onClick = { onSetDefaultBusiness(b.code) }, role = Role.RadioButton),
                ) {
                    RadioButton(selected = b.code == defaultBusiness, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            b.name + if (b.code == defaultBusiness) "  ·  default" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirmDeleteBusiness = b }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${b.code}", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 5. Security: app lock with biometrics and / or an authenticator app
            HorizontalDivider()
            Text("Security", style = MaterialTheme.typography.titleMedium)
            ToggleRow(
                title = "Biometric unlock",
                hint = "Open the app with your fingerprint or face (or the phone's PIN / pattern).",
                checked = biometricEnabled,
                onCheckedChange = { on ->
                    when {
                        !on -> onSetBiometric(false)
                        !Biometric.available(context) -> Toast.makeText(
                            context, "Set up a fingerprint, face or screen lock on the phone first", Toast.LENGTH_LONG,
                        ).show()
                        // Confirm it works before relying on it.
                        else -> Biometric.prompt(context, "Turn on biometric unlock") { error ->
                            if (error == null) onSetBiometric(true)
                        }
                    }
                },
            )
            ToggleRow(
                title = "Microsoft Authenticator (MFA)",
                hint = if (mfaEnabled) {
                    "Experimental. Asks for the 6-digit code from the free Microsoft Authenticator app. " +
                        "Recovery codes left: $recoveryCodesLeft."
                } else {
                    "Experimental. Asks for a 6-digit code from the free Microsoft Authenticator app (or any " +
                        "authenticator app). Guided setup, about a minute, no account needed."
                },
                checked = mfaEnabled,
                onCheckedChange = { on -> if (on) onSetupMfa() else turningOffMfa = true },
            )
            if (mfaEnabled) {
                TextButton(onClick = { setupKey = mfaSetupKey() }) { Text("Show setup key (add to Authenticator again)") }
            }
            if (biometricEnabled || mfaEnabled) {
                Text("Lock again after this long in the background", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLock.RELOCK_MINUTES.forEach { m ->
                        FilterChip(
                            selected = relockMinutes == m,
                            onClick = { onRelockMinutes(m) },
                            label = { Text(if (m == AppLock.NEVER) "Never" else "$m min") },
                        )
                    }
                }
                if (relockMinutes == AppLock.NEVER) {
                    Text(
                        "The app still locks each time it is opened afresh (e.g. after the phone restarts or the app is closed).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (biometricEnabled && mfaEnabled) {
                    Text(
                        "With both on, either your fingerprint / face or the authenticator code opens the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Version
            Spacer(Modifier.size(24.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME}\nCourtesy: Claude Code",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${c.name}?") },
            text = {
                Text(
                    "It will no longer be offered for new scans or used for classification. Invoices already saved " +
                        "with it keep it. You can restore it later from Deleted categories."
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemoveCategory(c); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    if (showDeleted) {
        AlertDialog(
            onDismissRequest = { showDeleted = false },
            title = { Text("Deleted categories") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (deletedCategories.isEmpty()) Text("Nothing left to restore.")
                    deletedCategories.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (c.builtIn) "Built-in" else "Added by you",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onRestoreCategory(c) }) { Text("Restore") }
                            IconButton(onClick = { confirmPurge = c }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete ${c.name} permanently",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDeleted = false }) { Text("Done") } },
        )
    }

    confirmPurge?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("Delete ${c.name} permanently?") },
            text = {
                Text(
                    "It will be removed from this list and can't be restored here. Invoices already saved with it " +
                        "keep it. You could still type it again as a new expense type later."
                )
            },
            confirmButton = {
                TextButton(onClick = { onPurgeCategory(c); confirmPurge = null }) {
                    Text("Delete permanently", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text("Cancel") } },
        )
    }

    if (addingBusiness) {
        var code by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { addingBusiness = false },
            title = { Text("Add a business") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditField(
                        value = code,
                        onValueChange = { code = Business.cleanCode(it); error = null },
                        label = "Short code (3 characters)",
                        placeholder = "e.g. XYZ",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EditField(
                        value = name,
                        onValueChange = { name = it.take(40); error = null },
                        label = "Short name",
                        placeholder = "e.g. Example Business",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    error = onAddBusiness(code, name)
                    if (error == null) addingBusiness = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingBusiness = false }) { Text("Cancel") } },
        )
    }

    confirmDeleteBusiness?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDeleteBusiness = null },
            title = { Text("Delete ${b.code}?") },
            text = { Text("${b.label} will no longer be offered. Invoices already saved for it keep the code ${b.code}.") },
            confirmButton = {
                TextButton(onClick = { onDeleteBusiness(b.code); confirmDeleteBusiness = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteBusiness = null }) { Text("Cancel") } },
        )
    }

    if (turningOffMfa) {
        AlertDialog(
            onDismissRequest = { turningOffMfa = false },
            title = { Text("Turn off the authenticator lock?") },
            text = { Text("You can then delete the Invoice Scanner entry in Microsoft Authenticator. Turning it on again creates a new key.") },
            confirmButton = {
                TextButton(onClick = { onDisableMfa(); turningOffMfa = false }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { turningOffMfa = false }) { Text("Cancel") } },
        )
    }

    setupKey?.let { (key, uri) ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { setupKey = null },
            title = { Text("Setup key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "In Microsoft Authenticator tap +  →  Other account  →  Or enter code manually, and use this key " +
                            "with the account name Invoice Scanner. Or scan the code from another phone. Keep the key private.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    QrCode(uri, Modifier.size(160.dp))
                }
            },
            confirmButton = { TextButton(onClick = { setupKey = null }) { Text("Done") } },
            dismissButton = {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(key.replace(" ", "")))
                    Toast.makeText(context, "Setup key copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy key") }
            },
        )
    }
}

/** A setting that is switched on or off straight away. */
@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, onClick = { onCheckedChange(!checked) }, role = Role.Switch),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
