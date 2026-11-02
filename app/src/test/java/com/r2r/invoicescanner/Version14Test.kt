package com.r2r.invoicescanner

import com.r2r.invoicescanner.extract.AiPrompt
import com.r2r.invoicescanner.extract.CategoryClassifier
import com.r2r.invoicescanner.model.CategoryPick
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.FieldValues
import com.r2r.invoicescanner.model.InvoiceRecord
import com.r2r.invoicescanner.model.RegisterFilter
import com.r2r.invoicescanner.model.ReviewDraft
import com.r2r.invoicescanner.model.SortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Version14Test {

    private fun rec(id: String, date: String, vendor: String, amount: Double, scannedAt: Long) = InvoiceRecord(
        id = id, fileName = "$id.jpeg", imageUri = "", invoiceNo = "", date = date, vendorName = vendor,
        currency = "INR", subtotal = null, tax = null, amount = amount, read = FieldValues(),
        category = ExpenseCategory.Office_Supplies, aiCategory = null, categoryReason = "", amountConfidence = 90,
        findings = emptyList(), comment = "", engine = "", scannedAt = scannedAt,
    )

    private val list = listOf(
        rec("a", "2026-10-02", "zeta stores", 300.0, 1),
        rec("b", "2026-09-15", "Alpha Traders", 1050.0, 3),
        rec("c", "2026-10-20", "Midway", 99.0, 2),
    )

    private fun ids(f: RegisterFilter) = f.apply(list).map { it.id }

    @Test fun sortsByEachField() {
        assertEquals(listOf("b", "c", "a"), ids(RegisterFilter()))                                   // newest processed first
        assertEquals(listOf("a", "c", "b"), ids(RegisterFilter(descending = false)))
        assertEquals(listOf("c", "a", "b"), ids(RegisterFilter(sort = SortField.INVOICE_DATE)))
        assertEquals(listOf("b", "a", "c"), ids(RegisterFilter(sort = SortField.AMOUNT)))
        assertEquals(listOf("b", "c", "a"), ids(RegisterFilter(sort = SortField.VENDOR, descending = false))) // case-insensitive A–Z
    }

    @Test fun sortIsNotAFilter() {
        assertFalse(RegisterFilter(sort = SortField.AMOUNT).isActive)
    }

    @Test fun comboTextPicksExistingOrCreatesNew() {
        val known = ExpenseCategory.BUILT_IN
        val pickExisting = CategoryPick.resolve("fuel transport", known, known)
        assertEquals(ExpenseCategory.Fuel_Transport, pickExisting.category)
        assertNull(pickExisting.newCategory)

        val created = CategoryPick.resolve("  staff  welfare & gifts ", known, known)
        assertEquals("Staff_Welfare_Gifts", created.category!!.name)
        assertEquals(created.category, created.newCategory)

        assertNull(CategoryPick.resolve("   ", known, known).category)
    }

    @Test fun deletedBuiltInIsNotUsedForClassificationOrPrompt() {
        val active = ExpenseCategory.BUILT_IN - ExpenseCategory.Fuel_Transport
        val text = "HP PETROL PUMP\nPetrol 10 litre\nTotal 1000.00"
        assertEquals(ExpenseCategory.Fuel_Transport, CategoryClassifier.classify(text).category)
        assertTrue(CategoryClassifier.classify(text, active).category != ExpenseCategory.Fuel_Transport)
        assertFalse(AiPrompt.system(active).contains("Fuel_Transport"))
    }

    @Test fun draftValuesDetectEdits() {
        val read = FieldValues(vendorName = "HP", amount = "100.00")
        val d = ReviewDraft(imageUri = "x", vendorName = "HP", amount = "100.00", read = read)
        assertTrue(d.values.changedFrom(read).isEmpty())
        assertEquals(listOf("Tax"), d.withTax("5").values.changedFrom(read).filter { it == "Tax" })
    }
}

class Version15Test {
    @Test fun builtInCategoriesUpdated() {
        val names = ExpenseCategory.BUILT_IN.map { it.name }
        assertFalse("Vehicle_Insurance" in names)
        assertTrue(names.containsAll(listOf("Capital_Expenditure", "Capital_Drawings")))
        // Saved invoices with the old category still load (as a one-off type).
        assertNull(ExpenseCategory.fromName("Vehicle_Insurance"))
    }

    @Test fun capitalPurchasesClassified() {
        val text = "ABC Computers\nDell Laptop i7 16GB\nSerial No 5CD1234\nWarranty 1 year\nTotal 85,000.00"
        assertEquals(ExpenseCategory.Capital_Expenditure, CategoryClassifier.classify(text).category)
        assertTrue(AiPrompt.system().contains("Capital_Expenditure"))
    }

    @Test fun newTypeNameIsTidied() {
        assertEquals("New_Cost", CategoryPick.resolve("new cost", ExpenseCategory.BUILT_IN, ExpenseCategory.BUILT_IN).category!!.name)
    }
}
