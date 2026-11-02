package com.r2r.invoicescanner.extract

import com.r2r.invoicescanner.model.ExpenseCategory

/** Instructions sent to the AI model. */
object AiPrompt {

    fun system(categories: List<ExpenseCategory> = ExpenseCategory.BUILT_IN): String = buildString {
        val categoryList = categories.joinToString(", ") { "\"" + it.name + "\"" }
        appendLine("You are an expert accounts-payable assistant. You read photos of invoices, bills and receipts (often from India, but any country is possible) and return structured data as JSON.")
        appendLine()
        appendLine("Return ONLY one JSON object with exactly these keys:")
        appendLine("""{
  "is_invoice": true or false,
  "invoice_no": string or null,
  "date": "YYYY-MM-DD" or null,
  "vendor_name": string or null,
  "currency": ISO 4217 code such as "INR" or null,
  "subtotal": number or null,
  "tax": number or null,
  "amount": number or null,
  "amount_confidence": integer 0-100,
  "expense_type": one of $categoryList,
  "category_reason": string,
  "comment": string
}""")
        appendLine()
        appendLine("Rules:")
        appendLine("- is_invoice: false if the image is not an invoice, bill or receipt (then explain in comment).")
        appendLine("- invoice_no: the invoice / bill / receipt number exactly as printed.")
        appendLine("- date: the invoice date, not the due date. Indian and European documents are day-first (DD/MM/YYYY); use month-first only if the document is clearly American or the day-first reading is impossible.")
        appendLine("- vendor_name: the seller / supplier that issued the document, not the customer. Use the trading name, without addresses.")
        appendLine("- Numbers are plain JSON numbers without currency symbols or thousands separators, e.g. 123456.78.")
        appendLine("- amount: the final total payable including taxes (Grand Total / Net Payable / Amount Due / Total). If a rounded final total is printed, use it. Ignore cash tendered and change returned.")
        appendLine("- subtotal: the pre-tax total as printed (Sub Total, Taxable Value). If prices are tax-inclusive and no pre-tax figure is printed, use null. Do not calculate it.")
        appendLine("- tax: the sum of all tax lines (CGST + SGST, IGST, UTGST, cess, VAT, sales/service tax). Do not count a tax twice when both component lines and a tax total are printed. Use null if no tax is shown.")
        appendLine("- Do not guess. If a value is missing or illegible, use null and say so in comment.")
        appendLine("- amount_confidence: how certain you are that amount is exactly right. 90-100: clearly printed, labelled total that is consistent with subtotal + tax. 70-89: readable but some ambiguity (several totals, faint print, unclear label). 40-69: partly obscured, cut off, handwritten or inconsistent figures. Below 40: largely guessed. 0 if there is no amount.")
        appendLine("- expense_type: choose the single best head of expense:")
        categories.forEach { c ->
            appendLine("    ${c.name}: ${c.description.ifBlank { "user-defined head of expense; use it when the invoice clearly matches this name" }}")
        }
        val names = categories.map { it.name }.toSet()
        if (names.containsAll(listOf("Fuel_Transport", "Vehicle_Maintenance", "Repair_Maintenance"))) {
            appendLine("  Fuel for a vehicle is Fuel_Transport; servicing or spare parts for a vehicle is Vehicle_Maintenance; repairs to anything that is not a vehicle is Repair_Maintenance.")
        }
        if ("Capital_Expenditure" in names) {
            appendLine("  Buying a long-lived asset (computer, laptop, phone, furniture, machinery, equipment) is Capital_Expenditure, not Office_Supplies or Repair_Maintenance.")
        }
        if ("Capital_Drawings" in names) {
            appendLine("  Capital_Drawings is for the owner's personal spending paid by the business; choose it only when the document clearly shows a personal purchase.")
        }
        if ("Miscellaneous" in names) appendLine("  Use Miscellaneous only when no other head of expense fits.")
        appendLine("- category_reason: one short sentence explaining the expense_type.")
        appendLine("- comment: one or two short sentences for a finance reviewer. If the document could not be processed, say why (not an invoice, blurred, cut off, glare, handwritten, several receipts in one photo, etc.). If amount_confidence is below 85, explain what makes the amount uncertain. If everything is clear, return an empty string.")
    }

    fun userText(ocrText: String): String = buildString {
        appendLine("Extract the data from this invoice / receipt image and return the JSON object.")
        if (ocrText.isBlank()) {
            appendLine("On-device OCR found no text.")
        } else {
            appendLine("On-device OCR text (rows are approximate and may contain errors; the image is the source of truth):")
            appendLine("-----")
            appendLine(ocrText.take(6000))
            appendLine("-----")
        }
    }
}
