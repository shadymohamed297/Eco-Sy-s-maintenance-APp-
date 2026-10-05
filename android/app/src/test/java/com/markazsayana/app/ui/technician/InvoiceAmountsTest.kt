package com.markazsayana.app.ui.technician

import org.junit.Assert.*
import org.junit.Test

class InvoiceAmountsTest {
    @Test fun acceptsActualArabicAndEnglishAmountsIncludingFreeLabor() {
        assertEquals(0.0, parseMoney("٠")!!, 0.0)
        assertEquals(125.50, parseMoney("١٢٥٫٥٠")!!, 0.0)
        assertEquals(125.50, parseMoney("125,50")!!, 0.0)
        assertEquals(42.0, parseMoney(" ۴۲ ")!!, 0.0)
    }

    @Test fun rejectsBlankNegativeInfiniteAndAmbiguousMonetaryInputs() {
        listOf("", "-1", "NaN", "Infinity", "1e5", "12.345", "1,000.00", "1 2", ".50").forEach {
            assertNull("Should reject $it", parseMoney(it))
        }
    }

    @Test fun discountCannotExceedActualPartsAndLaborButFullDiscountIsAllowed() {
        assertNotNull(invoiceAmountError("100", "201", 100.0))
        assertNull(invoiceAmountError("100", "200", 100.0))
        assertNull(invoiceAmountError("0", "0", 0.0))
        assertNotNull(invoiceAmountError("", "0", 100.0))
    }
}
