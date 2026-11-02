package com.r2r.invoicescanner.model

import com.r2r.invoicescanner.extract.AmountParser
import java.time.LocalDate

/** The data points extracted from one invoice / receipt. */
data class InvoiceFields(
    val invoiceNo: String? = null,
    val date: LocalDate? = null,
    val vendorName: String? = null,
    val currency: String? = null,
    val subtotal: Double? = null,
    val tax: Double? = null,
    val amount: Double? = null,
)

/** One reason that pushed the amount confidence up (positive) or down. */
data class Finding(val positive: Boolean, val text: String) {
    fun encode(): String = (if (positive) "+" else "-") + text

    companion object {
        fun decode(s: String): Finding = Finding(s.startsWith("+"), s.drop(1))
    }
}

enum class ConfidenceBand(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }

fun bandOf(score: Int): ConfidenceBand = when {
    score >= 85 -> ConfidenceBand.HIGH
    score >= 60 -> ConfidenceBand.MEDIUM
    else -> ConfidenceBand.LOW
}

/**
 * The editable values of an invoice as text. Used to remember what the app
 * first filled in (OCR / AI) so the register can show which ones the user changed.
 */
data class FieldValues(
    val invoiceNo: String = "",
    val date: String = "",
    val vendorName: String = "",
    val currency: String = "",
    val subtotal: String = "",
    val tax: String = "",
    val amount: String = "",
) {
    /** Names of the fields that differ from [read]; a blank value that was filled in counts as a change. */
    fun changedFrom(read: FieldValues): List<String> = buildList {
        if (!sameText(invoiceNo, read.invoiceNo)) add("InvoiceNo")
        if (!sameText(date, read.date)) add("Date")
        if (!sameText(vendorName, read.vendorName)) add("VendorName")
        if (!sameText(currency, read.currency)) add("Currency")
        if (!sameMoney(subtotal, read.subtotal)) add("Subtotal")
        if (!sameMoney(tax, read.tax)) add("Tax")
        if (!sameMoney(amount, read.amount)) add("Amount")
    }

    companion object {
        val AMOUNT_FIELDS = setOf("Subtotal", "Tax", "Amount")

        private fun sameText(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

        private fun sameMoney(a: String, b: String): Boolean {
            val x = AmountParser.parse(a)
            val y = AmountParser.parse(b)
            return if (x == null || y == null) x == null && y == null else AmountParser.same(x, y)
        }
    }
}

/** A saved invoice (one row of the register). */
data class InvoiceRecord(
    val id: String,
    val fileName: String,
    val imageUri: String,
    val invoiceNo: String,
    val date: String,              // yyyy-MM-dd
    val vendorName: String,
    val currency: String,
    val subtotal: Double?,
    val tax: Double?,
    val amount: Double,
    val read: FieldValues,         // values as originally read from the document
    val category: ExpenseCategory,
    val aiCategory: ExpenseCategory?,
    val categoryReason: String,
    val amountConfidence: Int,
    val findings: List<Finding>,
    val comment: String,
    val engine: String,
    val scannedAt: Long,           // when the invoice was processed (scanned)
    val updatedAt: Long? = null,   // last time it was saved after editing
    val businessCode: String = "", // e.g. "XYZ"; blank for invoices saved before businesses existed
    val narration: String = "",    // the user's own note about the invoice
) {
    val values: FieldValues
        get() = FieldValues(
            invoiceNo, date, vendorName, currency,
            AmountParser.plain(subtotal), AmountParser.plain(tax), AmountParser.plain(amount),
        )
    val reclassified: Boolean get() = aiCategory != null && aiCategory != category
    val editedFields: List<String> get() = values.changedFrom(read)
    val amountEditedByUser: Boolean get() = editedFields.any { it in FieldValues.AMOUNT_FIELDS }
}

/** What text typed into the expense-type combo box means. */
object CategoryPick {
    data class Result(val category: ExpenseCategory?, val newCategory: ExpenseCategory?)

    /**
     * Text matching an existing type (ignoring case, spaces and "_") picks it; any other text becomes
     * a new type named like the built-in ones ("staff welfare" -> "Staff_Welfare"); blank clears it.
     */
    fun resolve(text: String, options: List<ExpenseCategory>, known: List<ExpenseCategory>): Result {
        val cat = ExpenseCategory.of(text, options) ?: return Result(null, null)
        return Result(cat, cat.takeIf { it !in known })
    }
}

/** Editable state behind the Review screen (new scan or an existing record). */
data class ReviewDraft(
    val recordId: String? = null,
    val imageUri: String,
    val pendingFilePath: String? = null,
    val savedFileName: String? = null,
    val invoiceNo: String = "",
    val date: String = "",
    val vendorName: String = "",
    val currency: String = "",
    val subtotal: String = "",
    val tax: String = "",
    val amount: String = "",
    val read: FieldValues = FieldValues(),
    val category: ExpenseCategory? = null,
    val aiCategory: ExpenseCategory? = null,
    val categoryReason: String = "",
    val newCategory: ExpenseCategory? = null,    // typed in by the user on this screen
    val keepNewCategory: Boolean = false,        // add it to the regular categories on save
    val amountConfidence: Int = 0,
    val findings: List<Finding> = emptyList(),
    val comment: String = "",
    val engine: String = "",
    val scannedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long? = null,
    val businessCode: String = "",
    val narration: String = "",
) {
    val isNew: Boolean get() = recordId == null

    /** The values currently on screen, comparable with [read]. */
    val values: FieldValues get() = FieldValues(invoiceNo, date, vendorName, currency, subtotal, tax, amount)

    /** Changing the tax re-balances the subtotal so that Subtotal + Tax = Amount; the amount never moves. */
    fun withTax(value: String): ReviewDraft =
        copy(tax = value, subtotal = remainder(amount, value) ?: subtotal)

    /** Changing the subtotal re-balances the tax so that Subtotal + Tax = Amount; the amount never moves. */
    fun withSubtotal(value: String): ReviewDraft =
        copy(subtotal = value, tax = remainder(amount, value) ?: tax)

    private fun remainder(total: String, part: String): String? {
        val t = AmountParser.parse(total) ?: return null
        val p = AmountParser.parse(part) ?: return null
        val rest = Math.round((t - p) * 100) / 100.0
        return if (rest < 0) null else AmountParser.plain(rest)
    }
}
