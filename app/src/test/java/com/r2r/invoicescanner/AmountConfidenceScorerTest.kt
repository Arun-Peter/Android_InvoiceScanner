package com.r2r.invoicescanner

import com.r2r.invoicescanner.extract.AmountConfidenceScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountConfidenceScorerTest {

    private fun input(amount: Double?, sub: Double?, tax: Double?, base: Int = 90, ocr: String = "Grand Total 1,050.00", second: Double? = null) =
        AmountConfidenceScorer.Input(amount, sub, tax, base, "base", ocr, second)

    @Test fun consistentInvoiceScoresHigh() {
        val out = AmountConfidenceScorer.score(input(1050.0, 1000.0, 50.0, second = 1050.0))
        assertEquals(100, out.score)
    }

    @Test fun mismatchLowersScore() {
        val good = AmountConfidenceScorer.score(input(1050.0, 1000.0, 50.0)).score
        val bad = AmountConfidenceScorer.score(input(1050.0, 900.0, 50.0)).score
        assertTrue(bad < good)
        assertTrue(bad < 85)
    }

    @Test fun amountNotPrintedIsPenalised() {
        val out = AmountConfidenceScorer.score(input(1080.0, null, null, ocr = "Grand Total 1,050.00"))
        assertTrue(out.score < 60)
    }

    @Test fun missingAmountIsZero() {
        assertEquals(0, AmountConfidenceScorer.score(input(null, null, null)).score)
    }
}
