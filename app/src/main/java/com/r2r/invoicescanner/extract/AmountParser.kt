package com.r2r.invoicescanner.extract

import java.util.Locale
import kotlin.math.abs

/** Finds and parses money values such as 1,520.00 / 1,23,456.78 / Rs.1520 / ₹ 99.5 */
object AmountParser {

    data class NumberToken(
        val value: Double,
        val raw: String,
        val start: Int,
        val hasDecimals: Boolean,
        val isPercent: Boolean,
    )

    // Western (123,456.78) and Indian (1,23,456.78) grouping, or plain digits,
    // with optional 1-2 decimals. Not preceded by a digit/comma or "digit." so
    // we don't start in the middle of another number; "Rs.1520" still matches.
    private val NUMBER = Regex("""(?<![\d,])(?<!\d\.)(\d{1,3}(?:,\d{2,3})+|\d+)(\.\d{1,2})?(?!\d)""")

    private val CURRENCY_PREFIX = Regex("""(?i)\b(rs|inr)\.?""")

    fun tokens(text: String): List<NumberToken> = NUMBER.findAll(text).mapNotNull { m ->
        val raw = m.value
        val value = raw.replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
        var i = m.range.last + 1
        while (i < text.length && text[i] == ' ') i++
        val isPercent = i < text.length && text[i] == '%'
        NumberToken(value, raw, m.range.first, raw.contains('.'), isPercent)
    }.toList()

    /** Money-like tokens on a line (percentages removed). */
    fun moneyTokens(text: String): List<NumberToken> = tokens(text).filter { !it.isPercent }

    /** Parses a single user / AI supplied value: "₹ 1,520.00", "1520", "1.520,50" -> Double. */
    fun parse(input: String?): Double? {
        if (input == null) return null
        var s = input.trim()
            .replace(CURRENCY_PREFIX, "")
            .replace(Regex("[^0-9.,\\-]"), "")
        if (s.isEmpty() || s == "-" ) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = if (lastComma > lastDot && (s.length - lastComma - 1) in 1..2) {
            // decimal comma, e.g. 1.520,50 or 12,5
            s.replace(".", "").replace(',', '.')
        } else {
            s.replace(",", "")
        }
        return s.toDoubleOrNull()
    }

    /** Value as a plain editable string: 1520.0 -> "1520.00" */
    fun plain(value: Double?): String =
        if (value == null) "" else String.format(Locale.US, "%.2f", value)

    /** Value for display: 1520.0 -> "1,520.00" */
    fun pretty(value: Double?): String =
        if (value == null) "—" else String.format(Locale.US, "%,.2f", value)

    fun same(a: Double?, b: Double?, tolerance: Double = 0.005): Boolean =
        a != null && b != null && abs(a - b) <= tolerance
}
