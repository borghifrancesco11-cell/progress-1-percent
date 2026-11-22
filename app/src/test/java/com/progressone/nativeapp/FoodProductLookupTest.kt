package com.progressone.nativeapp

import com.progressone.nativeapp.data.FoodProductLookup
import org.junit.Assert.*
import org.junit.Test

class FoodProductLookupTest {
    @Test fun knownEANChecksum() { assertTrue(FoodProductLookup.validBarcode("3017624010701")) }
    @Test fun rejectTypoBarcode() { assertFalse(FoodProductLookup.validBarcode("3017624010702")) }
    @Test fun rejectNonDigits() { assertFalse(FoodProductLookup.validBarcode("ABCD12345678")) }
}
