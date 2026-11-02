package com.r2r.invoicescanner

import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.FieldValues
import com.r2r.invoicescanner.model.InvoiceRecord
import com.r2r.invoicescanner.model.RegisterFilter
import com.r2r.invoicescanner.security.AppLock
import com.r2r.invoicescanner.storage.CsvFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class Version17Test {

    private fun rec(id: String, narration: String) = InvoiceRecord(
        id = id, fileName = "$id.jpeg", imageUri = "", invoiceNo = "", date = "2026-10-21", vendorName = "V",
        currency = "INR", subtotal = null, tax = null, amount = 100.0, read = FieldValues(),
        category = ExpenseCategory.Office_Supplies, aiCategory = null, categoryReason = "", amountConfidence = 90,
        findings = emptyList(), comment = "", engine = "", scannedAt = 1L, businessCode = "XYZ", narration = narration,
    )

    @Test fun narrationIsTheLastCsvColumnAndIsEscaped() {
        assertEquals("Narration", CsvFormat.HEADER.last())
        val csv = CsvFormat.toCsv(listOf(rec("a", "Team lunch, client visit")))
        assertEquals(true, csv.lines()[1].endsWith(",\"Team lunch, client visit\""))
        assertEquals(true, CsvFormat.toCsv(listOf(rec("b", ""))).lines()[1].endsWith(","))
    }

    @Test fun searchMatchesNarration() {
        val list = listOf(rec("a", "Client visit Chennai"), rec("b", ""))
        assertEquals(listOf("a"), RegisterFilter(query = "chennai").apply(list).map { it.id })
    }

    @Test fun relockMinutesOnlyAllowsTheOfferedChoices() {
        assertEquals(listOf(5, 10, 15, AppLock.NEVER), AppLock.RELOCK_MINUTES)
        assertEquals(10, AppLock.parseMinutes(10))
        assertEquals(AppLock.NEVER, AppLock.parseMinutes(0))
        assertEquals(AppLock.DEFAULT_RELOCK_MINUTES, AppLock.parseMinutes(7))
    }
}
