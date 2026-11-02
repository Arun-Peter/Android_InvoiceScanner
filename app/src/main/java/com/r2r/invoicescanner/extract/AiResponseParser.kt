package com.r2r.invoicescanner.extract

import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.InvoiceFields
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

data class AiResult(
    val isInvoice: Boolean,
    val fields: InvoiceFields,
    val amountConfidence: Int,
    val category: ExpenseCategory?,
    val categoryReason: String,
    val comment: String,
)

/** Turns the model's JSON answer into an [AiResult]; tolerant of small format deviations. */
object AiResponseParser {

    fun parse(content: String, categories: List<ExpenseCategory> = ExpenseCategory.BUILT_IN): AiResult {
        val json = JSONObject(extractJsonObject(content))
        val amount = json.number("amount")
        val rawConfidence = json.optDouble("amount_confidence", Double.NaN)
        val confidence = when {
            amount == null -> 0
            rawConfidence.isNaN() -> 50
            rawConfidence <= 1.0 -> (rawConfidence * 100).roundToInt()   // model answered 0.92
            else -> rawConfidence.roundToInt()
        }.coerceIn(0, 100)

        return AiResult(
            isInvoice = json.optBoolean("is_invoice", true),
            fields = InvoiceFields(
                invoiceNo = json.text("invoice_no"),
                date = json.text("date")?.let { parseDate(it) },
                vendorName = json.text("vendor_name"),
                currency = json.text("currency")?.uppercase()?.take(3),
                subtotal = json.number("subtotal"),
                tax = json.number("tax"),
                amount = amount,
            ),
            amountConfidence = confidence,
            category = ExpenseCategory.fromName(json.text("expense_type"), categories),
            categoryReason = json.text("category_reason").orEmpty(),
            comment = json.text("comment").orEmpty(),
        )
    }

    /** Strips ```json fences or any text around the first {...} block. */
    fun extractJsonObject(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        return if (start >= 0 && end > start) content.substring(start, end + 1) else content
    }

    private fun parseDate(s: String): LocalDate? =
        runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()
            ?: HeuristicExtractor.datesIn(s).firstOrNull()

    private fun JSONObject.text(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val s = opt(key)?.toString()?.trim()
        return if (s.isNullOrEmpty() || s.equals("null", ignoreCase = true)) null else s
    }

    private fun JSONObject.number(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val v = opt(key)) {
            is Number -> v.toDouble()
            is String -> AmountParser.parse(v)
            else -> null
        }
    }
}
