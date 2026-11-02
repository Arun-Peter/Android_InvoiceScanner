package com.r2r.invoicescanner.extract

import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.InvoiceFields
import java.time.LocalDate

/**
 * Rule-based reader that works fully offline on the OCR rows. It is used on its
 * own when no AI service is configured, and as an independent "second
 * opinion" on the amount when AI is used.
 */
object HeuristicExtractor {

    data class Result(
        val fields: InvoiceFields,
        val amountBaseConfidence: Int,
        val amountEvidence: String,
        val category: ExpenseCategory?,
        val categoryReason: String,
    )

    private val IC = RegexOption.IGNORE_CASE

    private data class TotalLabel(val regex: Regex, val confidence: Int, val name: String)

    private val TOTAL_LABELS = listOf(
        TotalLabel(Regex("""grand\s*total""", IC), 80, "Grand Total"),
        TotalLabel(Regex("""net\s*(amount\s*)?payable|amount\s*payable|total\s*payable|payable\s*amount""", IC), 80, "Amount Payable"),
        TotalLabel(Regex("""(amount|balance|total)\s*due""", IC), 78, "Amount Due"),
        TotalLabel(Regex("""invoice\s*(total|amount|value)|bill\s*(total|amount|value)|total\s*(invoice\s*)?(amount|value)|net\s*(total|amount)""", IC), 75, "Total Amount"),
        TotalLabel(Regex("""\btotal\b""", IC), 65, "Total"),
    )
    private val TOTAL_EXCLUDE = Regex(
        """sub\s*-?\s*total|total\s*(qty|quantity|items?|tax|gst|vat|discount|saving|saved|weight|pcs|units)|taxable|before\s*tax|excl""",
        IC
    )

    private val SUBTOTAL = Regex(
        """sub\s*-?\s*total|taxable\s*(value|amount|amt)|(total|amount)\s*before\s*tax|net\s*value|basic\s*(amount|value)|total\s*excl""",
        IC
    )

    private val TAX_TOTAL = Regex("""total\s*(tax|gst|vat)(\s*amount)?|(tax|gst|vat)\s*amount""", IC)
    private val TAX_PART = Regex("""\b(c\s?gst|s\s?gst|i\s?gst|ut\s?gst|cess|vat|gst|service\s*tax|sales\s*tax|tax)\b""", IC)
    private val TAX_EXCLUDE = Regex(
        """gstin|gst\s*(no|number|reg|#)|tax\s*invoice|taxable|\bincl|inclusive|before\s*tax|\bsub|\bpan\b|\bhsn\b|\bsac\b""",
        IC
    )

    private val INVOICE_NO = Regex(
        """\b(invoice|inv|bill|receipt|rcpt|voucher|folio|txn|transaction|order|ref)\b\.?\s*(?:(number|num|nbr|no|id)\b|#)?\.?\s*[:#\-]?\s*(?=[A-Za-z/\-]*\d)([A-Za-z0-9][A-Za-z0-9/\-]{0,29})""",
        IC
    )
    private val LOOKS_LIKE_DATE = Regex("""^\d{1,4}[/\-.]\d{1,2}[/\-.]\d{2,4}$""")

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MON = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?"
    private val DATE_NUM = Regex("""(?<!\d)(\d{1,4})[/\-.](\d{1,2})[/\-.](\d{2,4})(?!\d)""")
    private val DATE_DMY_TEXT = Regex("""(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]*$MON[\s\-/.,']*(\d{4}|\d{2})(?!\d)""", IC)
    private val DATE_MDY_TEXT = Regex("""$MON\s*(\d{1,2})(?:st|nd|rd|th)?,?\s*(\d{4})(?!\d)""", IC)

    private val VENDOR_SKIP = Regex(
        """tax\s*invoice|invoice|receipt|bill\s*of\s*supply|cash\s*memo|cash\s*bill|estimate|welcome|thank|gstin|gst\s*no|\bph(one)?\b|\bmob(ile)?\b|\btel\b|e-?mail|www\.|http|\bdate\b|\btime\b|original|duplicate|customer|\bfssai\b|\bcin\b|\bpan\b|\btotal\b|\bqty\b|\bamount\b|\bcash\b|\bchange\b""",
        IC
    )

    fun extract(
        rows: List<String>,
        today: LocalDate = LocalDate.now(),
        categories: List<ExpenseCategory> = ExpenseCategory.BUILT_IN,
    ): Result {
        val text = rows.joinToString("\n")

        val total = findTotal(rows)
        val amount: Double?
        val base: Int
        val evidence: String
        if (total != null) {
            amount = total.first
            base = total.second
            evidence = "On-device reading took the amount from the '${total.third}' line"
        } else {
            val fallback = rows.flatMap { AmountParser.moneyTokens(it) }
                .filter { it.hasDecimals && it.value > 0 && it.value < 100_000_000 }
                .maxByOrNull { it.value }
            amount = fallback?.value
            base = if (fallback != null) 35 else 0
            evidence = if (fallback != null) {
                "No 'Total' label found — the largest amount on the document was used"
            } else {
                "No amount could be found in the text"
            }
        }

        val classification = CategoryClassifier.classify(text, categories)
        val fields = InvoiceFields(
            invoiceNo = findInvoiceNo(rows),
            date = findDate(rows, today),
            vendorName = findVendor(rows),
            currency = findCurrency(text),
            subtotal = findSubtotal(rows),
            tax = findTax(rows),
            amount = amount,
        )
        return Result(fields, base, evidence, classification.category, classification.reason)
    }

    // ---------------------------------------------------------------- amounts

    private fun valueAfter(row: String, labelEnd: Int): Double? =
        AmountParser.moneyTokens(row.substring(labelEnd)).lastOrNull()?.value

    private fun findTotal(rows: List<String>): Triple<Double, Int, String>? {
        for (label in TOTAL_LABELS) {
            val candidates = mutableListOf<Double>()
            rows.forEachIndexed { i, row ->
                val m = label.regex.find(row) ?: return@forEachIndexed
                if (TOTAL_EXCLUDE.containsMatchIn(row)) return@forEachIndexed
                var value = valueAfter(row, m.range.last + 1)
                if (value == null && i + 1 < rows.size) {
                    val next = rows[i + 1]
                    if (next.count { it.isLetter() } < 4) {
                        value = AmountParser.moneyTokens(next).lastOrNull()?.value
                    }
                }
                if (value != null && value > 0) candidates += value
            }
            if (candidates.isNotEmpty()) return Triple(candidates.max(), label.confidence, label.name)
        }
        return null
    }

    private fun findSubtotal(rows: List<String>): Double? =
        rows.mapNotNull { row ->
            val m = SUBTOTAL.find(row) ?: return@mapNotNull null
            valueAfter(row, m.range.last + 1)
        }.maxOrNull()

    private fun findTax(rows: List<String>): Double? {
        // 1. An explicit tax total wins.
        for (row in rows) {
            val m = TAX_TOTAL.find(row) ?: continue
            val v = valueAfter(row, m.range.last + 1)
            if (v != null && v > 0) return v
        }
        // 2. Otherwise add up the individual tax lines (CGST + SGST, IGST, cess, VAT...).
        var components = 0.0
        var generic = 0.0
        for (row in rows) {
            if (TAX_EXCLUDE.containsMatchIn(row)) continue
            val matches = TAX_PART.findAll(row).toList()
            matches.forEachIndexed { idx, m ->
                val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else row.length
                val segment = row.substring(m.range.last + 1, end)
                val v = AmountParser.moneyTokens(segment).lastOrNull()?.value ?: return@forEachIndexed
                val key = m.value.lowercase().replace(" ", "")
                if (key == "gst" || key == "tax") generic += v else components += v
            }
        }
        val tax = if (components > 0) components else generic
        return if (tax > 0) Math.round(tax * 100) / 100.0 else null
    }

    // ------------------------------------------------------------ identifiers

    private fun findInvoiceNo(rows: List<String>): String? {
        var best: Pair<Int, String>? = null
        for (row in rows) {
            for (m in INVOICE_NO.findAll(row)) {
                val label = m.groupValues[1].lowercase()
                val value = m.groupValues[3].trim('-', '/')
                if (value.none { it.isDigit() } || LOOKS_LIKE_DATE.matches(value)) continue
                val rank = when (label) {
                    "invoice", "inv" -> 0
                    "bill" -> 1
                    "receipt", "rcpt" -> 2
                    "voucher", "folio" -> 3
                    else -> 4
                }
                if (best == null || rank < best.first) best = rank to value
            }
        }
        return best?.second
    }

    // ------------------------------------------------------------------ dates

    /** All valid dates in a piece of text. Day-first (DD/MM/YYYY) unless impossible. */
    fun datesIn(text: String, today: LocalDate = LocalDate.now()): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        DATE_NUM.findAll(text).forEach { m ->
            val (g1, g2, g3) = m.destructured
            val date = if (g1.length == 4) {
                safeDate(g1.toInt(), g2.toInt(), g3.toIntOrNull() ?: -1)
            } else if (g1.length <= 2) {
                val year = fullYear(g3)
                val a = g1.toInt()
                val b = g2.toInt()
                if (year == null) null
                else if (b > 12 && a <= 12) safeDate(year, a, b)   // clearly MM/DD
                else safeDate(year, b, a)                            // DD/MM (default)
            } else null
            if (date != null) out += date
        }
        DATE_DMY_TEXT.findAll(text).forEach { m ->
            val day = m.groupValues[1].toInt()
            val month = monthIndex(m.groupValues[2])
            val year = fullYear(m.groupValues[3])
            if (year != null) safeDate(year, month, day)?.let { out += it }
        }
        DATE_MDY_TEXT.findAll(text).forEach { m ->
            val month = monthIndex(m.groupValues[1])
            val day = m.groupValues[2].toInt()
            val year = fullYear(m.groupValues[3])
            if (year != null) safeDate(year, month, day)?.let { out += it }
        }
        return out.filter { it.year in 2000..(today.year + 1) }
    }

    private fun findDate(rows: List<String>, today: LocalDate): LocalDate? {
        val dateRows = rows.filter { it.contains("date", ignoreCase = true) && !it.contains("due", ignoreCase = true) }
        for (row in dateRows) datesIn(row, today).firstOrNull()?.let { return it }
        for (row in rows) {
            if (row.contains("due", ignoreCase = true)) continue
            datesIn(row, today).firstOrNull()?.let { return it }
        }
        return null
    }

    private fun monthIndex(s: String): Int = MONTHS.indexOfFirst { s.lowercase().startsWith(it) } + 1

    private fun fullYear(s: String): Int? = when (s.length) {
        2 -> 2000 + s.toInt()
        4 -> s.toInt()
        else -> null
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        try { LocalDate.of(y, m, d) } catch (e: Exception) { null }

    // ------------------------------------------------------------ vendor etc.

    private fun findVendor(rows: List<String>): String? =
        rows.take(8)
            .map { it.replace(Regex("""\s{2,}"""), " ").trim().trim('*', '-', '=', '.', ',', ':', '|', '#', ' ') }
            .firstOrNull { r ->
                val letters = r.count { it.isLetter() }
                val nonSpace = r.count { !it.isWhitespace() }
                letters >= 3 && nonSpace > 0 && letters.toDouble() / nonSpace >= 0.6 &&
                    r.length <= 60 && !VENDOR_SKIP.containsMatchIn(r)
            }

    private fun findCurrency(text: String): String? = when {
        text.contains('₹') || Regex("""\b(rs|inr)\b""", IC).containsMatchIn(text) ||
            Regex("""\b(gstin|cgst|sgst|igst)\b""", IC).containsMatchIn(text) -> "INR"
        text.contains('€') || Regex("""\beur\b""", IC).containsMatchIn(text) -> "EUR"
        text.contains('£') || Regex("""\bgbp\b""", IC).containsMatchIn(text) -> "GBP"
        Regex("""\baed\b""", IC).containsMatchIn(text) -> "AED"
        text.contains('$') || Regex("""\busd\b""", IC).containsMatchIn(text) -> "USD"
        else -> null
    }
}
