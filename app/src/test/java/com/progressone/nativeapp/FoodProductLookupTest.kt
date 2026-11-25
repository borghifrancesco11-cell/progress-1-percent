package com.progressone.nativeapp

import com.progressone.nativeapp.data.FoodProductLookup
import org.junit.Assert.*
import org.junit.Test

class FoodProductLookupTest {
    @Test fun knownEANChecksum() { assertTrue(FoodProductLookup.validBarcode("3017624010701")) }
    @Test fun rejectTypoBarcode() { assertFalse(FoodProductLookup.validBarcode("3017624010702")) }
    @Test fun rejectNonDigits() { assertFalse(FoodProductLookup.validBarcode("ABCD12345678")) }

    @Test fun validUpcEIsAcceptedAndExpanded() {
        assertTrue(FoodProductLookup.validBarcode("02345673", upcE = true))
        assertEquals("023456000073", FoodProductLookup.canonicalBarcode("02345673", upcE = true))
    }

    @Test fun invalidUpcECheckDigitIsRejected() {
        assertFalse(FoodProductLookup.validBarcode("02345674", upcE = true))
    }
}
