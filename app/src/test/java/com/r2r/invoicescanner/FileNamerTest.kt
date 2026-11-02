package com.r2r.invoicescanner

import com.r2r.invoicescanner.model.ExpenseCategory
import com.r2r.invoicescanner.storage.FileNamer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FileNamerTest {

    @Test fun buildsRequestedPattern() {
        val name = FileNamer.build(LocalDate.of(2026, 10, 24), ExpenseCategory.Fuel_Transport, "Example Fuel Co. Ltd", 1520.99)
        assertEquals("20261024_Fuel_Transport_Example-Fuel-Co-Ltd_1520.jpeg", name)
    }

    @Test fun roundsDown() {
        assertEquals("99", FileNamer.floorAmount(99.999))
        assertEquals("1520", FileNamer.floorAmount(1520.0))
    }

    @Test fun vendorFallbackAndUnderscores() {
        assertEquals("UnknownVendor", FileNamer.sanitizeVendor("  ***  "))
        assertEquals("A-B-C", FileNamer.sanitizeVendor("A_B / C"))
    }

    @Test fun uniquifies() {
        val existing = setOf("20261024_Fuel_Transport_HP_100.jpeg", "20261024_Fuel_Transport_HP_100_2.jpeg")
        assertEquals("20261024_Fuel_Transport_HP_100_3.jpeg", FileNamer.uniquify("20261024_Fuel_Transport_HP_100.jpeg", existing))
    }
}
