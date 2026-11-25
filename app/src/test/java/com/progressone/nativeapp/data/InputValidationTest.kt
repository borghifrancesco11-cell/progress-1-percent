package com.progressone.nativeapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {
    @Test
    fun `strict dates reject impossible and out of range calendar dates`() {
        assertTrue(InputValidation.isValidDate("2024-02-29"))
        assertFalse(InputValidation.isValidDate("2023-02-29"))
        assertFalse(InputValidation.isValidDate("2024-02-31"))
        assertFalse(InputValidation.isValidDate("2024-13-01"))
        assertFalse(InputValidation.isValidDate("1899-12-31"))
        assertFalse(InputValidation.isValidDate("2101-01-01"))
        assertFalse(InputValidation.isValidDate("2024-2-01"))
    }

    @Test
    fun `strict times reject impossible clock values`() {
        assertTrue(InputValidation.isValidTime("00:00"))
        assertTrue(InputValidation.isValidTime("23:59"))
        assertFalse(InputValidation.isValidTime("24:00"))
        assertFalse(InputValidation.isValidTime("12:60"))
        assertFalse(InputValidation.isValidTime("1:30"))
    }

    @Test
    fun `amounts reject overflow infinity and malformed separators`() {
        assertEquals(12.5, InputValidation.parseAmount("12.50")!!, 0.000_001)
        assertEquals(12.5, InputValidation.parseAmount("12,50")!!, 0.000_001)
        assertEquals(1234.0, InputValidation.parseAmount("1,234")!!, 0.000_001)
        assertEquals(1234567.0, InputValidation.parseAmount("1,234,567")!!, 0.000_001)
        assertEquals(1234.56, InputValidation.parseAmount("1,234.56")!!, 0.000_001)
        assertEquals(1234.56, InputValidation.parseAmount("1.234,56")!!, 0.000_001)
        assertNull(InputValidation.parseAmount("1e309"))
        assertNull(InputValidation.parseAmount("Infinity"))
        assertNull(InputValidation.parseAmount("NaN"))
        assertNull(InputValidation.parseAmount("-1"))
        assertNull(InputValidation.parseAmount("1.2.3"))
        assertNull(InputValidation.parseAmount("1,23,456"))
        assertNull(InputValidation.parseAmount("12,34.56"))
    }

    @Test
    fun `profile values use plausible human ranges`() {
        assertEquals(70.0, InputValidation.parseWeightKg("70")!!, 0.000_001)
        assertNull(InputValidation.parseWeightKg("1e309"))
        assertNull(InputValidation.parseWeightKg("500"))
        assertEquals(175.0, InputValidation.parseHeightCm("175")!!, 0.000_001)
        assertNull(InputValidation.parseHeightCm("20"))
        assertEquals(30.0, InputValidation.parseAgeYears("30")!!, 0.000_001)
        assertNull(InputValidation.parseAgeYears("0"))
    }
}
