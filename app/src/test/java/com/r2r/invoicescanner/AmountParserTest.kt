package com.r2r.invoicescanner

import com.r2r.invoicescanner.extract.AmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountParserTest {

    @Test fun parsesIndianAndWesternGrouping() {
        assertEquals(123456.78, AmountParser.parse("1,23,456.78")!!, 0.001)
        assertEquals(123456.78, AmountParser.parse("123,456.78")!!, 0.001)
        assertEquals(1520.0, AmountParser.parse("₹ 1,520.00")!!, 0.001)
        assertEquals(1520.0, AmountParser.parse("Rs.1520")!!, 0.001)
    }

    @Test fun parsesDecimalComma() {
        assertEquals(1520.5, AmountParser.parse("1.520,50")!!, 0.001)
    }

    @Test fun blankIsNull() {
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("abc"))
    }

    @Test fun findsTokensAndPercentages() {
        val t = AmountParser.tokens("CGST @ 9%   45.00")
        assertEquals(2, t.size)
        assertTrue(t[0].isPercent)
        assertFalse(t[1].isPercent)
        assertEquals(45.0, t[1].value, 0.001)
    }

    @Test fun rupeePrefixWithDot() {
        val t = AmountParser.moneyTokens("Grand Total Rs.1,520.00")
        assertEquals(1520.0, t.last().value, 0.001)
    }
}
