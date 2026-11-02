package com.r2r.invoicescanner

import com.r2r.invoicescanner.extract.HeuristicExtractor
import com.r2r.invoicescanner.extract.OcrLine
import com.r2r.invoicescanner.extract.RowBuilder
import com.r2r.invoicescanner.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HeuristicExtractorTest {
    private val today = LocalDate.of(2026, 10, 24)

    @Test fun restaurantBillWithGst() {
        val rows = listOf(
            "EXAMPLE RESTAURANT",
            "12 Example Street",
            "GSTIN: 00AAAAA0000A0A0",
            "TAX INVOICE",
            "Bill No: INV/2026/00001      Date: 21/10/2026",
            "Table No 7   Covers 4",
            "Meals x2   640.00",
            "Coffee x4   360.00",
            "Sub Total   1,000.00",
            "CGST @ 2.5%   25.00",
            "SGST @ 2.5%   25.00",
            "Grand Total   Rs.1,050.00",
        )
        val r = HeuristicExtractor.extract(rows, today)
        assertEquals(1050.0, r.fields.amount!!, 0.001)
        assertEquals(1000.0, r.fields.subtotal!!, 0.001)
        assertEquals(50.0, r.fields.tax!!, 0.001)
        assertEquals(LocalDate.of(2026, 10, 21), r.fields.date)
        assertEquals("INV/2026/00001", r.fields.invoiceNo)
        assertEquals("EXAMPLE RESTAURANT", r.fields.vendorName)
        assertEquals("INR", r.fields.currency)
        assertEquals(ExpenseCategory.Business_Promotion, r.category)
        assertEquals(80, r.amountBaseConfidence)
    }

    @Test fun fuelReceipt() {
        val rows = listOf(
            "WELCOME",
            "EXAMPLE PETROL PUMP",
            "Receipt No. 004471",
            "Date 05-Oct-2026   Time 18:42",
            "Product: Petrol   Rate 102.63",
            "Volume (Ltr) 14.62",
            "Amount (Rs)   1500.00",
            "Total   1500.00",
        )
        val r = HeuristicExtractor.extract(rows, today)
        assertEquals(1500.0, r.fields.amount!!, 0.001)
        assertEquals(LocalDate.of(2026, 10, 5), r.fields.date)
        assertEquals("004471", r.fields.invoiceNo)
        assertEquals("EXAMPLE PETROL PUMP", r.fields.vendorName)
        assertEquals(ExpenseCategory.Fuel_Transport, r.category)
    }

    @Test fun mobileBillMonthFirstImpossibleDayFirst() {
        val rows = listOf(
            "Example Mobile Services",
            "Postpaid Bill   Invoice No. MH1234567890",
            "Bill Date 2026-09-30",
            "Taxable Value  847.46",
            "IGST 18%  152.54",
            "Amount Payable  1,000.00",
        )
        val r = HeuristicExtractor.extract(rows, today)
        assertEquals(1000.0, r.fields.amount!!, 0.001)
        assertEquals(847.46, r.fields.subtotal!!, 0.001)
        assertEquals(152.54, r.fields.tax!!, 0.001)
        assertEquals("MH1234567890", r.fields.invoiceNo)
        assertEquals(LocalDate.of(2026, 9, 30), r.fields.date)
        assertEquals(ExpenseCategory.Mobile_Internet, r.category)
    }

    @Test fun rowBuilderJoinsLabelAndValue() {
        val rows = RowBuilder.buildRows(
            listOf(
                OcrLine("TOTAL", 10, 100, 80, 120),
                OcrLine("1,520.00", 300, 102, 380, 121),
                OcrLine("Thank you", 10, 150, 120, 170),
            )
        )
        assertEquals(listOf("TOTAL   1,520.00", "Thank you"), rows)
    }
}
