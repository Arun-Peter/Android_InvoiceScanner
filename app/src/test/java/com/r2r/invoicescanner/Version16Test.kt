package com.r2r.invoicescanner

import com.r2r.invoicescanner.model.Business
import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.model.FieldValues
import com.r2r.invoicescanner.model.InvoiceRecord
import com.r2r.invoicescanner.model.RegisterFilter
import com.r2r.invoicescanner.security.Base32
import com.r2r.invoicescanner.security.Totp
import com.r2r.invoicescanner.storage.CsvFormat
import com.r2r.invoicescanner.storage.FileNamer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Version16Test {

    // ---------------------------------------------------------- TOTP (RFC 6238 appendix B, SHA-1)

    private val rfcSecret = "12345678901234567890".toByteArray()

    @Test fun totpMatchesRfcVectors() {
        val vectors = mapOf(
            59L to "94287082", 1111111109L to "07081804", 1111111111L to "14050471",
            1234567890L to "89005924", 2000000000L to "69279037", 20000000000L to "65353130",
        )
        vectors.forEach { (t, expected) -> assertEquals(expected, Totp.code(rfcSecret, t * 1000, digits = 8)) }
        assertEquals("287082", Totp.code(rfcSecret, 59_000)) // 6-digit form used by the app
    }

    @Test fun verifyAllowsOneStepDriftOnly() {
        val now = 1_700_000_000_000L
        val code = Totp.code(rfcSecret, now)
        assertTrue(Totp.verify(rfcSecret, code, now))
        assertTrue(Totp.verify(rfcSecret, code, now + 30_000))
        assertFalse(Totp.verify(rfcSecret, code, now + 90_000))
        assertFalse(Totp.verify(rfcSecret, "12345", now))
    }

    @Test fun base32RoundTripAndUri() {
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
        assertEquals("foobar", String(Base32.decode("mzxw 6ytb oi")))
        val secret = Totp.newSecret()
        assertEquals(20, secret.size)
        assertTrue(Base32.decode(Base32.grouped(secret)).contentEquals(secret))
        val uri = Totp.uri(secret, "App lock")
        assertTrue(uri, uri.startsWith("otpauth://totp/Invoice%20Scanner%3AApp%20lock?secret=${Base32.encode(secret)}"))
    }

    @Test fun recoveryCodesAreUniqueAndHashIgnoresFormatting() {
        val codes = Totp.newRecoveryCodes()
        assertEquals(8, codes.toSet().size)
        assertTrue(codes.all { Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(it) })
        assertEquals(Totp.hashRecoveryCode("K7RM-2XQP"), Totp.hashRecoveryCode(" k7rm2xqp "))
    }

    // ---------------------------------------------------------- businesses

    @Test fun businessCodeValidation() {
        assertEquals("R2R", Business.cleanCode(" r2-r9 "))
        val existing = listOf(Business("XYZ", "Example Business"))
        assertNull(Business.validate("abc", "ABC Pvt Ltd", existing))
        assertTrue(Business.validate("ab", "Name", existing)!!.contains("3"))
        assertTrue(Business.validate("xyz", "Again", existing)!!.contains("already"))
        assertTrue(Business.validate("QWE", " ", existing)!!.contains("name"))
    }

    @Test fun fileNameIncludesBusinessCode() {
        val d = LocalDate.of(2026, 10, 21)
        assertEquals(
            "20261021_XYZ_Fuel_Transport_Example-Fuel_1520.jpeg",
            FileNamer.build(d, ExpenseCategory.Fuel_Transport, "Example Fuel", 1520.99, "XYZ"),
        )
        assertEquals("20261021_Fuel_Transport_HP_10.jpeg", FileNamer.build(d, ExpenseCategory.Fuel_Transport, "HP", 10.0))
    }

    private fun rec(id: String, code: String, cat: ExpenseCategory = ExpenseCategory.Office_Supplies) = InvoiceRecord(
        id = id, fileName = "$id.jpeg", imageUri = "", invoiceNo = "", date = "2026-10-21", vendorName = "V",
        currency = "INR", subtotal = null, tax = null, amount = 100.0, read = FieldValues(), category = cat,
        aiCategory = null, categoryReason = "", amountConfidence = 90, findings = emptyList(), comment = "",
        engine = "", scannedAt = id.hashCode().toLong(), businessCode = code,
    )

    @Test fun filterByBusiness() {
        val list = listOf(rec("a", "XYZ"), rec("b", "ABC"), rec("c", ""))
        assertEquals(listOf("a"), RegisterFilter(business = "XYZ").apply(list).map { it.id })
        assertEquals(listOf("c"), RegisterFilter(business = "").apply(list).map { it.id })
        assertEquals(3, RegisterFilter().apply(list).size)
        assertTrue(RegisterFilter(business = "ABC").isActive)
    }

    @Test fun csvHasBusinessAndImagePath() {
        val r = rec("a", "XYZ", ExpenseCategory.Fuel_Transport)
        assertEquals("XYZ/Fuel_Transport/a.jpeg", CsvFormat.imagePath(r))
        assertEquals("Fuel_Transport/c.jpeg", CsvFormat.imagePath(rec("c", "", ExpenseCategory.Fuel_Transport)))
        val header = CsvFormat.HEADER
        assertEquals(listOf("BusinessCode", "BusinessName", "ImagePath"), header.subList(16, 19))
        val row = CsvFormat.toCsv(listOf(r), mapOf("a" to "XYZ/Fuel_Transport/a.jpeg"), mapOf("XYZ" to "Example Business"))
            .lines()[1].split(",")
        assertEquals("XYZ", row[header.indexOf("BusinessCode")])
        assertEquals("Example Business", row[header.indexOf("BusinessName")])
        assertEquals("XYZ/Fuel_Transport/a.jpeg", row[header.indexOf("ImagePath")])
    }
}
