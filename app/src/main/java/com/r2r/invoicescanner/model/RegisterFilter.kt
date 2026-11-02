package com.r2r.invoicescanner.model

import com.r2r.invoicescanner.extract.AmountParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DatePreset(val label: String) {
    ALL("All dates"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_FY("This financial year"),
    LAST_FY("Last financial year"),
    CUSTOM("Custom range…"),
}

private val LABEL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

/** "01 Oct 2026" — used for custom date ranges in the filter chip and the range picker. */
fun labelDate(d: LocalDate): String = d.format(LABEL_DATE)

enum class SortField(val label: String) {
    PROCESSED("Processed time"),
    INVOICE_DATE("Invoice date"),
    AMOUNT("Total amount"),
    VENDOR("Vendor name"),
}

/** Filters (and sort order) for the register on the home screen. The date filter applies to the invoice date. */
data class RegisterFilter(
    val query: String = "",
    val datePreset: DatePreset = DatePreset.ALL,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val category: ExpenseCategory? = null,
    val business: String? = null,  // business code; "" = invoices without a business
    val sort: SortField = SortField.PROCESSED,
    val descending: Boolean = true,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || datePreset != DatePreset.ALL || category != null || business != null

    /** Inclusive date range; null ends are open. Financial year runs April to March. */
    fun range(today: LocalDate): Pair<LocalDate?, LocalDate?> {
        val monthStart = today.withDayOfMonth(1)
        val fyStart = LocalDate.of(if (today.monthValue >= 4) today.year else today.year - 1, 4, 1)
        return when (datePreset) {
            DatePreset.ALL -> null to null
            DatePreset.THIS_MONTH -> monthStart to monthStart.plusMonths(1).minusDays(1)
            DatePreset.LAST_MONTH -> monthStart.minusMonths(1) to monthStart.minusDays(1)
            DatePreset.LAST_3_MONTHS -> monthStart.minusMonths(2) to monthStart.plusMonths(1).minusDays(1)
            DatePreset.THIS_FY -> fyStart to fyStart.plusYears(1).minusDays(1)
            DatePreset.LAST_FY -> fyStart.minusYears(1) to fyStart.minusDays(1)
            DatePreset.CUSTOM -> customFrom to customTo
        }
    }

    /** Custom ranges read "01 Oct 2026 – 24 Oct 2026" (both ends in the same style). */
    fun dateLabel(today: LocalDate): String {
        if (datePreset != DatePreset.CUSTOM) return datePreset.label
        val (from, to) = range(today)
        return "${from?.let(::labelDate) ?: "…"} – ${to?.let(::labelDate) ?: "…"}"
    }

    fun apply(records: List<InvoiceRecord>, today: LocalDate = LocalDate.now()): List<InvoiceRecord> {
        val (from, to) = range(today)
        val q = query.trim()
        val matching = records.filter { r ->
            (category == null || r.category == category) &&
                (business == null || r.businessCode == business) &&
                (from == null && to == null || inRange(r.date, from, to)) &&
                (q.isEmpty() || matchesQuery(r, q))
        }
        val order: Comparator<InvoiceRecord> = when (sort) {
            SortField.PROCESSED -> compareBy { it.scannedAt }
            SortField.INVOICE_DATE -> compareBy<InvoiceRecord> { it.date }.thenBy { it.scannedAt }
            SortField.AMOUNT -> compareBy<InvoiceRecord> { it.amount }.thenBy { it.scannedAt }
            SortField.VENDOR -> compareBy<InvoiceRecord, String>(String.CASE_INSENSITIVE_ORDER) { it.vendorName.trim() }.thenBy { it.scannedAt }
        }
        return matching.sortedWith(if (descending) order.reversed() else order)
    }

    private fun inRange(date: String, from: LocalDate?, to: LocalDate?): Boolean {
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to))
    }

    /** Vendor, invoice no., file name, comment or narration; digits also match the amount (e.g. "1050"). */
    private fun matchesQuery(r: InvoiceRecord, q: String): Boolean {
        val text = listOf(r.vendorName, r.invoiceNo, r.fileName, r.comment, r.businessCode, r.narration)
        if (text.any { it.contains(q, ignoreCase = true) }) return true
        val digits = q.filter { it.isDigit() || it == '.' }
        return digits.isNotEmpty() && digits == q.replace(",", "") && AmountParser.plain(r.amount).contains(digits)
    }
}
