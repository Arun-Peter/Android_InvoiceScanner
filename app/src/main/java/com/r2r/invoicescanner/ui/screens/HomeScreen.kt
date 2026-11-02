@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.r2r.invoicescanner.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.r2r.invoicescanner.extract.AmountParser
import com.r2r.invoicescanner.model.Business
import com.r2r.invoicescanner.model.DatePreset
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.InvoiceRecord
import com.r2r.invoicescanner.model.RegisterFilter
import com.r2r.invoicescanner.model.SortField
import com.r2r.invoicescanner.model.labelDate
import com.r2r.invoicescanner.storage.ImageStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun HomeScreen(
    records: List<InvoiceRecord>,
    shown: List<InvoiceRecord>,
    filter: RegisterFilter,
    categories: List<ExpenseCategory>,
    businesses: List<Business>,
    imageStatus: Map<String, ImageStore.Status>,
    showAiBanner: Boolean,
    exporting: Boolean,
    onDismissAiBanner: () -> Unit,
    onScan: () -> Unit,
    onOpen: (InvoiceRecord) -> Unit,
    onExport: (withImages: Boolean) -> Unit,
    onSettings: () -> Unit,
    onFilterChange: ((RegisterFilter) -> RegisterFilter) -> Unit,
    onDelete: (Set<String>) -> Unit,
) {
    // Selection for bulk delete. Only invoices shown by the current filters can be selected or deleted.
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    val visibleSelected = shown.filter { it.id in selectedIds }
    val allSelected = shown.isNotEmpty() && visibleSelected.size == shown.size
    val endSelection = { selecting = false; selectedIds = emptySet() }
    val toggle: (InvoiceRecord) -> Unit = { r ->
        selectedIds = if (r.id in selectedIds) selectedIds - r.id else selectedIds + r.id
    }
    BackHandler(enabled = selecting) { endSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = endSelection) { Icon(Icons.Default.Close, contentDescription = "Cancel selection") }
                    },
                    title = { Text("${visibleSelected.size} selected") },
                    actions = {
                        TextButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else shown.map { it.id }.toSet()
                        }) { Text(if (allSelected) "Select none" else "Select all (${shown.size})") }
                        IconButton(onClick = { confirmDelete = true }, enabled = visibleSelected.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected invoices")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Invoice Scanner") },
                    actions = {
                        if (shown.isNotEmpty()) {
                            TextButton(onClick = { selecting = true }) { Text("Select") }
                        }
                        Box {
                            if (exporting) {
                                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { exportMenu = true }) {
                                    Icon(Icons.Default.Share, contentDescription = "Export register")
                                }
                            }
                            DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                                Text(
                                    when {
                                        !filter.isActive -> "Exports all ${shown.size} invoices"
                                        shown.size == 1 -> "Exports the 1 invoice shown by the filters"
                                        else -> "Exports the ${shown.size} invoices shown by the filters"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Register + images (ZIP)")
                                            Text(
                                                "CSV and every invoice image in Business/Expense-type folders",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = { exportMenu = false; onExport(true) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Register only (CSV)") },
                                    onClick = { exportMenu = false; onExport(false) },
                                )
                            }
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Scan invoice") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showAiBanner) {
                item { AiBanner(onSettings, onDismissAiBanner) }
            }
            if (records.isNotEmpty()) {
                item { FilterBar(records, filter, categories, businesses, onFilterChange) }
            }
            item { SummaryCard(records, shown, filter.isActive) }
            if (records.isEmpty()) {
                item {
                    Text(
                        "No invoices yet.\n\nTap \"Scan invoice\" to photograph a receipt or invoice. " +
                            "It is cropped automatically, read, scored and classified — you review it and save it to your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp, start = 8.dp, end = 8.dp),
                    )
                }
            } else if (shown.isEmpty()) {
                item {
                    Text(
                        "No invoices match these filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { r ->
                RecordCard(
                    r = r,
                    status = imageStatus[r.id] ?: ImageStore.Status.PRESENT,
                    selected = if (selecting) r.id in selectedIds else null,
                    onClick = { if (selecting) toggle(r) else onOpen(r) },
                    onLongClick = {
                        if (!selecting) selecting = true
                        toggle(r)
                    },
                )
            }
        }
    }

    if (confirmDelete) {
        val n = visibleSelected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $n invoice${if (n == 1) "" else "s"}?") },
            text = {
                Text(
                    "Total ${byCurrency(visibleSelected) { it.amount }}. " +
                        (if (n == 1) "Its image will be deleted from the phone and the row removed from the register. "
                        else "Their images will be deleted from the phone and the rows removed from the register. ") +
                        "This cannot be undone — export the register first if you need a copy."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(visibleSelected.map { it.id }.toSet())
                    confirmDelete = false
                    endSelection()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AiBanner(onSettings: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "Invoices are read on the device only. Tap to connect your Azure OpenAI service for AI extraction, scoring comments and classification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun FilterBar(
    records: List<InvoiceRecord>,
    filter: RegisterFilter,
    categories: List<ExpenseCategory>,
    businesses: List<Business>,
    onFilterChange: ((RegisterFilter) -> RegisterFilter) -> Unit,
) {
    var businessMenu by remember { mutableStateOf(false) }
    // Known businesses plus codes found on saved invoices; "" = invoices saved without a business.
    val businessCodes = (businesses.map { it.code } + records.map { it.businessCode }).distinct()
        .sortedWith(compareBy({ it.isBlank() }, { it }))
    var dateMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var rangePicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    // Every known category, plus any one-off categories used in saved invoices.
    val categoryOptions = (categories + records.map { it.category }).distinct()

    Column {
        EditField(
            value = filter.query,
            onValueChange = { q -> onFilterChange { it.copy(query = q) } },
            label = "Search vendor, invoice no., amount…",
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Box {
                FilterChip(
                    selected = filter.datePreset != DatePreset.ALL,
                    onClick = { dateMenu = true },
                    label = { Text(filter.dateLabel(today), maxLines = 1, softWrap = false) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                    Text(
                        "Filter by invoice date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    DatePreset.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            onClick = {
                                dateMenu = false
                                if (p == DatePreset.CUSTOM) rangePicker = true
                                else onFilterChange { it.copy(datePreset = p) }
                            },
                        )
                    }
                }
            }
            if (businessCodes.any { it.isNotBlank() }) {
                Box {
                    FilterChip(
                        selected = filter.business != null,
                        onClick = { businessMenu = true },
                        label = {
                            Text(
                                when (filter.business) {
                                    null -> "All businesses"
                                    "" -> "No business"
                                    else -> filter.business
                                },
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(expanded = businessMenu, onDismissRequest = { businessMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All businesses") },
                            onClick = { businessMenu = false; onFilterChange { it.copy(business = null) } },
                        )
                        businessCodes.forEach { code ->
                            val count = records.count { it.businessCode == code }
                            val name = businesses.firstOrNull { it.code == code }?.label ?: code.ifBlank { "No business" }
                            DropdownMenuItem(
                                text = { Text(if (count > 0) "$name  ($count)" else name) },
                                onClick = { businessMenu = false; onFilterChange { it.copy(business = code) } },
                            )
                        }
                    }
                }
            }
            Box {
                FilterChip(
                    selected = filter.category != null,
                    onClick = { categoryMenu = true },
                    label = { Text(filter.category?.name ?: "All expense types", maxLines = 1, softWrap = false) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("All expense types") },
                        onClick = {
                            categoryMenu = false
                            onFilterChange { it.copy(category = null) }
                        },
                    )
                    categoryOptions.forEach { c ->
                        val count = records.count { it.category == c }
                        DropdownMenuItem(
                            text = { Text(if (count > 0) "${c.name}  ($count)" else c.name) },
                            onClick = {
                                categoryMenu = false
                                onFilterChange { it.copy(category = c) }
                            },
                        )
                    }
                }
            }
            Box {
                val arrow = if (filter.descending) "↓" else "↑"
                FilterChip(
                    selected = filter.sort != SortField.PROCESSED || !filter.descending,
                    onClick = { sortMenu = true },
                    label = { Text("Sort: ${filter.sort.label} $arrow", maxLines = 1, softWrap = false) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    Text(
                        "Tap the current choice again to reverse the order",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    SortField.entries.forEach { f ->
                        val current = f == filter.sort
                        DropdownMenuItem(
                            text = { Text(f.label + if (current) "  $arrow" else "") },
                            leadingIcon = if (current) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                sortMenu = false
                                onFilterChange {
                                    // Same field: reverse. New field: newest / largest / A–Z first.
                                    if (current) it.copy(descending = !it.descending)
                                    else it.copy(sort = f, descending = f != SortField.VENDOR)
                                }
                            },
                        )
                    }
                }
            }
            if (filter.isActive) {
                TextButton(onClick = { onFilterChange { it.copy(query = "", datePreset = DatePreset.ALL, category = null, business = null) } }) {
                    Text("Clear filters")
                }
            }
        }
    }

    if (rangePicker) {
        val utc: (LocalDate?) -> Long? = { d -> d?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() }
        val toDate: (Long?) -> LocalDate? = { ms -> ms?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } }
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = utc(filter.customFrom),
            initialSelectedEndDateMillis = utc(filter.customTo),
        )
        DatePickerDialog(
            onDismissRequest = { rangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val from = toDate(state.selectedStartDateMillis)
                        val to = toDate(state.selectedEndDateMillis) ?: from
                        onFilterChange { it.copy(datePreset = DatePreset.CUSTOM, customFrom = from, customTo = to) }
                        rangePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { rangePicker = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(
                state = state,
                title = { Text("Invoice dates", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                // One line, same style for both ends (the default headline wraps the end date);
                // the text shrinks a little if it doesn't fit, e.g. with a large phone font size.
                headline = {
                    val from = toDate(state.selectedStartDateMillis)?.let(::labelDate) ?: "Start date"
                    val to = toDate(state.selectedEndDateMillis)?.let(::labelDate) ?: "End date"
                    val base = MaterialTheme.typography.titleLarge
                    var style by remember { mutableStateOf(base) }
                    Text(
                        "$from – $to",
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { r ->
                            if (r.didOverflowWidth && style.fontSize.value > 12f) style = style.copy(fontSize = style.fontSize * 0.92f)
                        },
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "INR 1,050.00 · USD 20.00" — amounts are never added across currencies. */
private fun byCurrency(list: List<InvoiceRecord>, value: (InvoiceRecord) -> Double?): String =
    list.groupBy { it.currency }
        .map { (cur, rs) -> "$cur ${AmountParser.pretty(rs.mapNotNull(value).sum())}".trim() }
        .joinToString("  ·  ")

@Composable
private fun SummaryCard(records: List<InvoiceRecord>, shown: List<InvoiceRecord>, filtered: Boolean) {
    val needReview = shown.count { it.amountConfidence < 60 }
    val byType = shown.groupBy { it.category }.toList().sortedByDescending { (_, rs) -> rs.sumOf { it.amount } }
    var showBreakdown by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (filtered) "${shown.size} of ${records.size} invoices"
                else "${records.size} invoice${if (records.size == 1) "" else "s"} saved",
                style = MaterialTheme.typography.titleMedium,
            )
            if (shown.isNotEmpty()) {
                Text(byCurrency(shown) { it.amount }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (shown.any { it.subtotal != null || it.tax != null }) Text(
                    "Subtotal ${byCurrency(shown) { it.subtotal }}   ·   Tax ${byCurrency(shown) { it.tax }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (byType.size > 1) {
                TextButton(
                    onClick = { showBreakdown = !showBreakdown },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(32.dp),
                ) { Text(if (showBreakdown) "Hide totals by expense type ▴" else "Totals by expense type ▾") }
            }
            if (byType.size > 1 && showBreakdown) {
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                byType.forEach { (cat, rs) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(
                            "${cat.name} (${rs.size})",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(byCurrency(rs) { it.amount }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (needReview > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "$needReview with low amount confidence — worth a second look",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** [selected] is null outside selection mode. Long-press starts selecting. */
@Composable
private fun RecordCard(
    r: InvoiceRecord,
    status: ImageStore.Status,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        colors = if (selected == true) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 4.dp))
            }
            val thumb = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
            if (status == ImageStore.Status.PRESENT) {
                AsyncImage(
                    model = Uri.parse(r.imageUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = thumb,
                )
            } else {
                Box(thumb.background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            if (status == ImageStore.Status.TRASHED) "In trash" else "Missing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.vendorName.ifBlank { "Unknown vendor" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(r.date, r.businessCode.ifBlank { null }, r.category.name).joinToString("  ·  ") +
                        if (r.reclassified) "  (re-classified)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    r.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${r.currency} ${AmountParser.pretty(r.amount)}".trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                ConfidenceChip(r.amountConfidence)
            }
        }
    }
}
