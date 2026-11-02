package com.r2r.invoicescanner.storage

import com.r2r.invoicescanner.model.ExpenseCategory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** yyyymmdd_BusinessCode_expenseType_Vendor_rounddownamount.jpeg (the business part only when one is set) */
object FileNamer {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    const val EXTENSION = ".jpeg"

    fun build(date: LocalDate, category: ExpenseCategory, vendor: String, amount: Double, businessCode: String = ""): String =
        listOfNotNull(date.format(DATE), businessCode.ifBlank { null }, category.name, sanitizeVendor(vendor), floorAmount(amount))
            .joinToString("_") + EXTENSION

    /** Letters/digits kept; spaces, underscores and symbols become "-" (underscore is the field separator). */
    fun sanitizeVendor(vendor: String): String {
        val cleaned = vendor.trim()
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), "-")
            .trim('-')
            .take(40)
            .trimEnd('-')
        return cleaned.ifEmpty { "UnknownVendor" }
    }

    /** Round DOWN to a whole number: 1520.99 -> "1520". */
    fun floorAmount(amount: Double): String =
        BigDecimal.valueOf(amount).setScale(0, RoundingMode.FLOOR).toPlainString()

    /** Adds _2, _3 ... when the name is already used. */
    fun uniquify(name: String, existing: Set<String>): String {
        val taken = existing.map { it.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        val stem = name.removeSuffix(EXTENSION)
        var i = 2
        while ("${stem}_$i$EXTENSION".lowercase() in taken) i++
        return "${stem}_$i$EXTENSION"
    }
}
