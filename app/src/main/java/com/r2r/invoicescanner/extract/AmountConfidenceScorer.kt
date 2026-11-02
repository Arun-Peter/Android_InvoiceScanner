package com.r2r.invoicescanner.extract

import com.r2r.invoicescanner.model.Finding
import kotlin.math.abs
import kotlin.math.max

/**
 * Evidence-based confidence score (0-100) for the invoice AMOUNT.
 *
 * Starts from the reader's own certainty (AI self-assessment, or the strength
 * of the on-device label match) and then adjusts it with independent checks:
 *  1. Is the amount actually printed on the document (OCR text)?
 *  2. Does Subtotal + Tax add up to the Amount?
 *  3. Does an independent second reading arrive at the same amount?
 */
object AmountConfidenceScorer {

    data class Input(
        val amount: Double?,
        val subtotal: Double?,
        val tax: Double?,
        val baseConfidence: Int,
        val baseReason: String,
        val ocrText: String,
        val secondOpinionAmount: Double? = null,
        val secondOpinionLabel: String = "On-device reading",
    )

    data class Output(val score: Int, val findings: List<Finding>)

    fun score(input: Input): Output {
        val amount = input.amount
        if (amount == null || amount <= 0.0) {
            return Output(0, listOf(Finding(false, "No total amount could be identified on the document")))
        }
        val findings = mutableListOf<Finding>()
        var score = input.baseConfidence.coerceIn(0, 100)
        findings += Finding(input.baseConfidence >= 80, input.baseReason)

        // 1. Printed on the document?
        if (input.ocrText.isNotBlank()) {
            val printed = AmountParser.moneyTokens(input.ocrText).any { AmountParser.same(it.value, amount) }
            if (printed) {
                score += 5
                findings += Finding(true, "${fmt(amount)} is printed on the document")
            } else {
                score -= 25
                findings += Finding(
                    false,
                    "${fmt(amount)} could not be found in the text read from the image — it may be misread, handwritten or faint"
                )
            }
        }

        // 2. Arithmetic cross-check (allow 1% / 1.00 for round-off)
        val tolerance = max(1.0, amount * 0.01)
        val sub = input.subtotal
        val tax = input.tax
        when {
            sub != null && tax != null -> {
                val sum = sub + tax
                val diff = abs(sum - amount)
                if (diff <= tolerance) {
                    score += 10
                    findings += Finding(true, "Subtotal ${fmt(sub)} + Tax ${fmt(tax)} = ${fmt(sum)} matches the amount")
                } else if (abs(sub - amount) <= tolerance) {
                    score -= 3
                    findings += Finding(false, "Subtotal equals the amount, so tax appears to be included in the prices")
                } else {
                    score -= 15
                    findings += Finding(
                        false,
                        "Subtotal + Tax = ${fmt(sum)}, which differs from the amount by ${fmt(diff)} " +
                            "(service charge, tip, discount, round-off or a misread?)"
                    )
                }
            }
            sub != null -> {
                if (abs(sub - amount) <= tolerance) {
                    score += 3
                    findings += Finding(true, "No tax shown and the subtotal equals the amount")
                } else {
                    score -= 5
                    findings += Finding(false, "Tax not found, so the amount could only be partly cross-checked")
                }
            }
            tax != null -> {
                score -= 5
                findings += Finding(false, "Subtotal not printed, so the amount could only be partly cross-checked")
            }
            else -> {
                score -= 10
                findings += Finding(false, "No subtotal or tax shown, so the amount could not be cross-checked")
            }
        }

        // 3. Independent second reading
        val second = input.secondOpinionAmount
        if (second != null) {
            if (AmountParser.same(second, amount, 0.01)) {
                score += 5
                findings += Finding(true, "${input.secondOpinionLabel} found the same amount")
            } else {
                score -= 10
                findings += Finding(false, "${input.secondOpinionLabel} found a different amount: ${fmt(second)}")
            }
        }

        return Output(score.coerceIn(0, 100), findings)
    }

    private fun fmt(v: Double) = AmountParser.pretty(v)
}
