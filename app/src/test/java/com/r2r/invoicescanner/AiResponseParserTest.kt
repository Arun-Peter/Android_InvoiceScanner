package com.r2r.invoicescanner

import com.r2r.invoicescanner.extract.AiResponseParser
import com.r2r.invoicescanner.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AiResponseParserTest {

    @Test fun parsesFencedAndStringNumbers() {
        val content = """
            ```json
            {"is_invoice": true, "invoice_no": "INV-42", "date": "2026-10-21",
             "vendor_name": "Krishna Fuels", "currency": "inr", "subtotal": null,
             "tax": "0", "amount": "1,500.00", "amount_confidence": 0.93,
             "expense_type": "fuel transport", "category_reason": "Petrol purchase", "comment": ""}
            ```
        """.trimIndent()
        val r = AiResponseParser.parse(content)
        assertEquals("INV-42", r.fields.invoiceNo)
        assertEquals(LocalDate.of(2026, 10, 21), r.fields.date)
        assertEquals("INR", r.fields.currency)
        assertEquals(1500.0, r.fields.amount!!, 0.001)
        assertEquals(93, r.amountConfidence)
        assertEquals(ExpenseCategory.Fuel_Transport, r.category)
    }
}
