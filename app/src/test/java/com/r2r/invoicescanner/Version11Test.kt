package com.r2r.invoicescanner

import com.r2r.invoicescanner.extract.AiPrompt
import com.r2r.invoicescanner.extract.AiResponseParser
import com.r2r.invoicescanner.extract.CategoryClassifier
import com.r2r.invoicescanner.model.DatePreset
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.FieldValues
import com.r2r.invoicescanner.model.InvoiceRecord
import com.r2r.invoicescanner.model.RegisterFilter
import com.r2r.invoicescanner.model.ReviewDraft
import com.r2r.invoicescanner.storage.CsvFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Version11Test {

    // ---------------------------------------------------------- subtotal / tax balancing

    private val draft = ReviewDraft(imageUri = "x", subtotal = "1000.00", tax = "50.00", amount = "1050.00")

    @Test fun editingTaxRebalancesSubtotal() {
        val d = draft.withTax("80")
        assertEquals("80", d.tax)
        assertEquals("970.00", d.subtotal)
        assertEquals("1050.00", d.amount)
    }

    @Test fun editingSubtotalRebalancesTax() {
        val d = draft.withSubtotal("1,000.50")
        assertEquals("49.50", d.tax)
        assertEquals("1050.00", d.amount)
    }

    @Test fun editingAmountChangesNothingElse() {
        val d = draft.copy(amount = "2000")
        assertEquals("1000.00", d.subtotal)
        assertEquals("50.00", d.tax)
    }

    @Test fun noRebalanceWhenBlankInvalidOrNegative() {
        assertEquals("1000.00", draft.withTax("").subtotal)
        assertEquals("1000.00", draft.withTax("abc").subtotal)
        assertEquals("1000.00", draft.withTax("5000").subtotal)
        assertEquals("50.00", draft.copy(amount = "").withSubtotal("10").tax)
    }

    // ---------------------------------------------------------- edited fields

    private fun record(subtotal: Double?, tax: Double?, read: FieldValues) = InvoiceRecord(
        id = "1", fileName = "f.jpeg", imageUri = "", invoiceNo = "INV1", date = "2026-10-21",
        vendorName = "Krishna Fuels", currency = "INR", subtotal = subtotal, tax = tax, amount = 1050.0,
        read = read, category = ExpenseCategory.Fuel_Transport, aiCategory = ExpenseCategory.Fuel_Transport,
        categoryReason = "", amountConfidence = 90, findings = emptyList(), comment = "", engine = "",
        scannedAt = 0L,
    )

    private val asRead = FieldValues("INV1", "2026-10-21", "Krishna Fuels", "INR", "", "", "1050.00")

    @Test fun fillingBlankSubtotalAndTaxCountsAsAmountEdit() {
        val r = record(1000.0, 50.0, asRead)
        assertEquals(listOf("Subtotal", "Tax"), r.editedFields)
        assertTrue(r.amountEditedByUser)
        val row = CsvFormat.toCsv(listOf(r)).lines()[1]
        assertTrue(row, row.contains(",Yes,") && row.contains(",Subtotal; Tax,"))
    }

    @Test fun untouchedRecordIsNotEdited() {
        val r = record(null, null, asRead.copy(vendorName = "krishna fuels "))
        assertTrue(r.editedFields.isEmpty())
        assertFalse(r.amountEditedByUser)
    }

    @Test fun csvExportsCurrency() {
        val header = CsvFormat.HEADER
        val row = CsvFormat.toCsv(listOf(record(1000.0, 50.0, asRead))).lines()[1].split(",")
        assertEquals("INR", row[header.indexOf("Currency")])
    }

    @Test fun csvKeepsEarlierColumnPositions() {
        assertEquals("EditedFields", CsvFormat.HEADER[15])
        assertEquals("AmountEditedByUser", CsvFormat.HEADER[13])
    }

    // ---------------------------------------------------------- categories

    @Test fun miscellaneousIsBuiltIn() {
        assertEquals(ExpenseCategory.Miscellaneous, ExpenseCategory.fromName("miscellaneous"))
        assertTrue(AiPrompt.system().contains("\"Miscellaneous\""))
    }

    @Test fun customCategoryNamesAreSanitisedAndMatchExisting() {
        assertEquals("Staff_Welfare", ExpenseCategory.of("  Staff welfare! ")!!.name)
        assertEquals(ExpenseCategory.Fuel_Transport, ExpenseCategory.of("fuel transport"))
        assertNull(ExpenseCategory.of(" *** "))
        assertEquals(ExpenseCategory("Staff_Welfare"), ExpenseCategory("staff welfare"))
    }

    @Test fun regularCustomCategoryIsUsedForClassification() {
        val welfare = ExpenseCategory("Staff_Welfare")
        val cats = ExpenseCategory.BUILT_IN + welfare
        assertEquals(welfare, CategoryClassifier.classify("Diwali staff welfare kit\nTotal 500.00", cats).category)
        assertTrue(AiPrompt.system(cats).contains("\"Staff_Welfare\""))
        val ai = AiResponseParser.parse("""{"amount": 5, "expense_type": "staff welfare"}""", cats)
        assertEquals(welfare, ai.category)
    }

    // ---------------------------------------------------------- register filters

    private val today = LocalDate.of(2026, 10, 24)

    private fun rec(id: String, date: String, cat: ExpenseCategory, vendor: String = "V", amount: Double = 100.0) =
        record(null, null, asRead).copy(id = id, date = date, category = cat, vendorName = vendor, amount = amount)

    private val list = listOf(
        rec("a", "2026-10-02", ExpenseCategory.Fuel_Transport, "Example Fuel", 1500.0),
        rec("b", "2026-09-15", ExpenseCategory.Business_Promotion, "Example Restaurant", 1050.0),
        rec("c", "2026-03-31", ExpenseCategory.Fuel_Transport, "HP", 999.0),
        rec("d", "2026-04-01", ExpenseCategory.Office_Supplies, "Stationery World", 250.0),
    )

    private fun ids(f: RegisterFilter) = f.apply(list, today).map { it.id }

    @Test fun datePresets() {
        assertEquals(listOf("a"), ids(RegisterFilter(datePreset = DatePreset.THIS_MONTH)))
        assertEquals(listOf("b"), ids(RegisterFilter(datePreset = DatePreset.LAST_MONTH)))
        assertEquals(listOf("a", "b", "d"), ids(RegisterFilter(datePreset = DatePreset.THIS_FY)))
        assertEquals(listOf("c"), ids(RegisterFilter(datePreset = DatePreset.LAST_FY)))
        val custom = RegisterFilter(
            datePreset = DatePreset.CUSTOM,
            customFrom = LocalDate.of(2026, 3, 31),
            customTo = LocalDate.of(2026, 4, 1),
        )
        assertEquals(listOf("c", "d"), ids(custom))
    }

    @Test fun categoryAndSearch() {
        assertEquals(listOf("a", "c"), ids(RegisterFilter(category = ExpenseCategory.Fuel_Transport)))
        assertEquals(listOf("b"), ids(RegisterFilter(query = "restaurant")))
        assertEquals(listOf("b"), ids(RegisterFilter(query = "1,050")))
        assertEquals(listOf("a"), ids(RegisterFilter(query = "fuel", category = ExpenseCategory.Fuel_Transport)))
        assertFalse(RegisterFilter().isActive)
    }
}
