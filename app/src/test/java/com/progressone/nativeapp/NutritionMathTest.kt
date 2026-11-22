package com.progressone.nativeapp

import com.progressone.nativeapp.data.NutritionMath
import org.junit.Assert.*
import org.junit.Test

class NutritionMathTest {
    @Test fun scalesPerHundredGrams() {
        assertEquals(450.0, NutritionMath.forPortion(300.0, 150.0), 0.001)
        assertEquals(18.75, NutritionMath.forPortion(12.5, 150.0), 0.001)
    }
    @Test fun decimalServingIsStable() { assertEquals(3.75,
        NutritionMath.forPortion(2.5, 150.0), 0.000000001) }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeServing() { NutritionMath.forPortion(10.0, -3.0) }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInfinite() { NutritionMath.forPortion(Double.POSITIVE_INFINITY, 100.0) }
    @Test fun checksGramsPerHundred() {
        assertTrue(NutritionMath.checkedMacroPer100g(45.5))
        assertFalse(NutritionMath.checkedMacroPer100g(102.0))
    }
    @Test fun exactLabelTotalsForDecimalPortion() {
        val totals = NutritionMath.totalsForLabel(252.5, 13.2, 20.5, 12.0, 75.5)
        assertEquals(190.6375, totals.kcal, 0.000001)
        assertEquals(9.966, totals.protein, 0.000001)
        assertEquals(15.4775, totals.carbs, 0.000001)
        assertEquals(9.06, totals.fat, 0.000001)
    }
    @Test fun checkRoundedLabelAndRejectImpossibleMacroSum() {
        assertTrue(NutritionMath.validLabel(410.0, 20.0, 60.0, 21.0))
        assertFalse(NutritionMath.validLabel(500.0, 60.0, 60.0, 10.0))
        assertFalse(NutritionMath.validLabel(Double.NaN, 10.0, 10.0, 10.0))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectUnmeasuredPortion() { NutritionMath.totalsForLabel(200.0, 10.0, 20.0, 4.0, 0.0) }
}
