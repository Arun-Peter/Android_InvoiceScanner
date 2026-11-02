@file:OptIn(ExperimentalMaterial3Api::class)

package com.r2r.invoicescanner.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.r2r.invoicescanner.extract.AmountParser
import com.r2r.invoicescanner.model.Business
import com.r2r.invoicescanner.model.CategoryPick
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.ReviewDraft
import com.r2r.invoicescanner.model.bandOf
import com.r2r.invoicescanner.storage.ImageStore
import com.r2r.invoicescanner.ui.MainViewModel
import com.r2r.invoicescanner.ui.theme.NegativeRed
import com.r2r.invoicescanner.ui.theme.PositiveGreen
import com.r2r.invoicescanner.ui.theme.WarningAmber
import com.r2r.invoicescanner.ui.theme.bandColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(
    draft: ReviewDraft,
    validation: MainViewModel.Validation,
    categories: List<ExpenseCategory>,
    businesses: List<Business>,
    imageStatus: ImageStore.Status,
    saving: Boolean,
    retrying: Boolean,
    onChange: ((ReviewDraft) -> ReviewDraft) -> Unit,
    onSave: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmRetry by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var imageExpanded by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = {
        if (draft.isNew) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }

    BackHandler { requestClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "Review invoice" else "Edit invoice") },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!draft.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete invoice")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (draft.isNew) {
                        OutlinedButton(onClick = onRetake, enabled = !saving, modifier = Modifier.weight(1f)) {
                            Text("Retake")
                        }
                    }
                    Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(2f)) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (draft.isNew) "Save to phone" else "Update")
                        }
                    }
                }
            }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!draft.isNew) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Processed on ${stamp(draft.scannedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    draft.updatedAt?.let {
                        Text(
                            "Last updated ${stamp(it)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (imageStatus == ImageStore.Status.PRESENT) {
                AsyncImage(
                    model = Uri.parse(draft.imageUri),
                    contentDescription = "Cropped invoice — tap to enlarge",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (imageExpanded) 1200.dp else 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { imageExpanded = !imageExpanded },
                )
            } else {
                MissingImageCard(imageStatus, draft.savedFileName.orEmpty())
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Read by: ${draft.engine}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (retrying) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = {
                                val edited = draft.values.changedFrom(draft.read).isNotEmpty()
                                if (edited) confirmRetry = true else onRetry()
                            },
                            enabled = imageStatus == ImageStore.Status.PRESENT && !saving,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Read the invoice again")
                        }
                    }
                }
                AssessmentCard(draft)
            }
            HorizontalDivider()
            CategorySection(draft, categories, onChange)
            FieldsSection(draft, businesses, onChange, onPickDate = { showDatePicker = true })
            FileNameCard(validation)
        }
    }

    if (showDatePicker) {
        val initial = runCatching { LocalDate.parse(draft.date.trim()) }.getOrNull()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange { it.copy(date = picked.toString()) }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (confirmRetry) {
        AlertDialog(
            onDismissRequest = { confirmRetry = false },
            title = { Text("Read the invoice again?") },
            text = { Text("The values you changed on this screen will be replaced by the new reading. Nothing is saved until you tap ${if (draft.isNew) "Save" else "Update"}.") },
            confirmButton = { TextButton(onClick = { confirmRetry = false; onRetry() }) { Text("Read again") } },
            dismissButton = { TextButton(onClick = { confirmRetry = false }) { Text("Cancel") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this scan?") },
            text = { Text("The image and the extracted details will not be saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this invoice?") },
            text = { Text("The image ${draft.savedFileName.orEmpty()} will be deleted from the phone and the row removed from the register.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MissingImageCard(status: ImageStore.Status, fileName: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (status == ImageStore.Status.TRASHED) "Image is in the phone's trash" else "Image file not found",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    if (status == ImageStore.Status.TRASHED) {
                        "$fileName was deleted in a file manager and sits in the trash / recycle bin. " +
                            "Restore it there to keep it; it will not be renamed while it is in the trash."
                    } else {
                        "$fileName was deleted or moved outside the app. You can still edit the details, " +
                            "or delete this row from the register."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Amount confidence with its reasons and the AI comment, collapsed behind "Why?". */
@Composable
private fun AssessmentCard(draft: ReviewDraft) {
    val band = bandOf(draft.amountConfidence)
    val color = bandColor(band)
    val original = AmountParser.parse(draft.read.amount)
    val edited = original != null && !AmountParser.same(AmountParser.parse(draft.amount), original)
    var expanded by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Amount confidence", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${draft.amountConfidence}%  ·  ${band.label}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Why?") }
            }
            LinearProgressIndicator(
                progress = { draft.amountConfidence / 100f },
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (edited) {
                Text(
                    "You changed the amount (read as ${AmountParser.pretty(original)}). The score refers to the value read from the document.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (expanded) {
                draft.findings.forEach { f ->
                    Row(Modifier.padding(top = 8.dp)) {
                        Text(
                            if (f.positive) "✓" else "!",
                            color = if (f.positive) PositiveGreen else NegativeRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(18.dp),
                        )
                        Text(f.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = color.copy(alpha = 0.3f))
                Text("AI comment", style = MaterialTheme.typography.labelLarge)
                Text(draft.comment, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Which business the invoice belongs to (by its 3-character code); the default business is preselected for new scans. */
@Composable
private fun BusinessPicker(
    draft: ReviewDraft,
    businesses: List<Business>,
    onChange: ((ReviewDraft) -> ReviewDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val code = draft.businessCode
    // A business deleted after this invoice was saved is still shown for it.
    val options = businesses.map { it.code }.sorted() +
        listOfNotNull(code.takeIf { it.isNotBlank() && businesses.none { b -> b.code == it } })
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = code,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Business", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            isError = code !in options,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { b ->
                DropdownMenuItem(
                    text = { Text(b) },
                    leadingIcon = if (b == code) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        onChange { it.copy(businessCode = b) }
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** "Fuel transport" / "fuel_tr" / "TRANSPORT" all match Fuel_Transport. */
private fun matchKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

private val STAMP = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", java.util.Locale.ENGLISH)

private fun stamp(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(STAMP)

@Composable
private fun CategorySection(
    draft: ReviewDraft,
    categories: List<ExpenseCategory>,
    onChange: ((ReviewDraft) -> ReviewDraft) -> Unit,
) {
    val selected = draft.category
    val suggested = draft.aiCategory
    val suggestTag = if (draft.engine.startsWith("On-device")) "suggested" else "AI"
    val options = (categories + listOfNotNull(suggested, selected.takeIf { it != draft.newCategory })).distinct()
    val isNew = selected != null && selected == draft.newCategory && selected !in categories

    // Combo box: starts with the chosen type (the AI's choice for a new scan). Typing filters the
    // list; text that matches no existing type becomes a new expense type for this invoice.
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(selected?.name.orEmpty()) }
    // Typing keeps the text and the chosen type in step; if the type changes from outside
    // (e.g. after "read again"), show the new type.
    LaunchedEffect(selected) {
        if (matchKey(query) != matchKey(selected?.name.orEmpty())) query = selected?.name.orEmpty()
    }
    val filtered = if (query == selected?.name.orEmpty() && !isNew) options
    else options.filter { matchKey(it.name).contains(matchKey(query)) }
    val choose: (ExpenseCategory) -> Unit = { cat ->
        onChange { it.copy(category = cat, newCategory = null, keepNewCategory = false) }
        query = cat.name
        expanded = false
    }
    val type: (String) -> Unit = { q ->
        query = q
        expanded = true
        val match = CategoryPick.resolve(q, options, categories)
        onChange { it.copy(category = match.category, newCategory = match.newCategory) }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f),
            ) {
                EditField(
                    value = query,
                    onValueChange = type,
                    label = "Expense type",
                    placeholder = "Pick or type a new one",
                    isError = selected == null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { expanded = false }),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    filtered.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(if (cat == suggested) "${cat.name}  ($suggestTag)" else cat.name) },
                            leadingIcon = if (cat == selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                            onClick = { choose(cat) },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                    if (isNew && selected != null) {
                        DropdownMenuItem(
                            text = { Text("New expense type: ${selected.name}", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            // Show the tidied name ("new cost" -> "New_Cost") in the box.
                            onClick = { query = selected.name; expanded = false },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            // Only meaningful for a new type: keep it for future scans and classification.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable(enabled = isNew) { onChange { it.copy(keepNewCategory = !it.keepNewCategory) } },
            ) {
                Checkbox(
                    checked = isNew && draft.keepNewCategory,
                    onCheckedChange = { c -> onChange { it.copy(keepNewCategory = c) } },
                    enabled = isNew,
                )
                Text(
                    "Regular",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isNew) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
        val note = when {
            selected == null -> "Pick an expense type, or type a new one."
            isNew -> if (draft.keepNewCategory) "New type ${selected.name} — kept as a regular category when you save."
            else "New type ${selected.name} — used for this invoice only. Tick Regular to keep it for future scans."
            suggested == null -> "Chosen by you" + if (selected.description.isBlank()) "." else ": ${selected.description}."
            selected != suggested -> "Re-classified by you (${if (suggestTag == "AI") "AI" else "the app"} suggested ${suggested.name})."
            else -> "${suggestTag.replaceFirstChar { it.uppercase() }}: ${draft.categoryReason.ifBlank { selected.description }}"
        }
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FieldsSection(
    draft: ReviewDraft,
    businesses: List<Business>,
    onChange: ((ReviewDraft) -> ReviewDraft) -> Unit,
    onPickDate: () -> Unit,
) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val dateInvalid = draft.date.isNotBlank() && runCatching { LocalDate.parse(draft.date.trim()) }.isFailure

    val tip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Invoice details", style = MaterialTheme.typography.titleSmall)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
                tooltip = {
                    RichTooltip(title = { Text("Tips") }) {
                        Text(
                            "• Editing the subtotal or tax adjusts the other so that Subtotal + Tax = Amount. " +
                                "Changing the amount leaves both as they are.\n" +
                                "• Double-tap a field to select all of it; × clears it."
                        )
                    }
                },
                state = tip,
            ) {
                IconButton(onClick = { scope.launch { tip.show() } }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Tips for editing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        EditField(
            value = draft.vendorName,
            onValueChange = { v -> onChange { it.copy(vendorName = v) } },
            label = "Vendor name",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditField(
                value = draft.invoiceNo,
                onValueChange = { v -> onChange { it.copy(invoiceNo = v) } },
                label = "Invoice no.",
                modifier = Modifier.weight(1f),
            )
            EditField(
                value = draft.date,
                onValueChange = { v -> onChange { it.copy(date = v) } },
                label = "Date",
                placeholder = "YYYY-MM-DD",
                clearable = false,
                isError = dateInvalid || draft.date.isBlank(),
                trailingIcon = {
                    IconButton(onClick = onPickDate) { Icon(Icons.Default.DateRange, contentDescription = "Pick date") }
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditField(
                value = draft.subtotal,
                onValueChange = { v -> onChange { it.withSubtotal(v) } },
                label = "Subtotal",
                keyboardOptions = decimal,
                isError = draft.subtotal.isNotBlank() && AmountParser.parse(draft.subtotal) == null,
                modifier = Modifier.weight(1f),
            )
            EditField(
                value = draft.tax,
                onValueChange = { v -> onChange { it.withTax(v) } },
                label = "Tax",
                keyboardOptions = decimal,
                isError = draft.tax.isNotBlank() && AmountParser.parse(draft.tax) == null,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditField(
                value = draft.currency,
                onValueChange = { v -> onChange { it.copy(currency = v.take(3).uppercase()) } },
                label = "Currency",
                clearable = false,
                modifier = Modifier.weight(1f),
            )
            EditField(
                value = draft.amount,
                onValueChange = { v -> onChange { it.copy(amount = v) } },
                label = "Amount (total)",
                keyboardOptions = decimal,
                isError = AmountParser.parse(draft.amount) == null,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(2f),
            )
        }
        val hasBusiness = businesses.isNotEmpty() || draft.businessCode.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasBusiness) BusinessPicker(draft, businesses, onChange, Modifier.weight(1f))
            EditField(
                value = draft.narration,
                onValueChange = { v -> onChange { it.copy(narration = v.take(NARRATION_MAX)) } },
                label = "Narration (optional)",
                placeholder = "Your note",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(2f),
            )
        }
        if (!hasBusiness) {
            Text(
                "Tip: add your business in Settings to put its code in the file name and folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val NARRATION_MAX = 200

@Composable
private fun FileNameCard(validation: MainViewModel.Validation) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("File name", style = MaterialTheme.typography.labelLarge)
            Text(
                validation.fileName ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Saved to phone storage › ${(validation.folder ?: ImageStore.RELATIVE_PATH).replace("/", " › ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            validation.duplicateWarning?.let {
                Text(
                    "Possible duplicate: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            validation.errors.forEach {
                Text(
                    "• $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
