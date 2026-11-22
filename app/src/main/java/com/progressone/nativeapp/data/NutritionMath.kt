package com.progressone.nativeapp.data

import java.math.BigDecimal
import java.math.MathContext

/** Exact decimal scaling of *supplied* nutrition data by the measured portion.
 * Photograph recognition alone cannot establish ingredients or their weights. */
data class NutritionTotals(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

object NutritionMath {
    fun forPortion(per100g: Double, portionGrams: Double): Double {
        require(per100g.isFinite() && portionGrams.isFinite() && per100g >= 0 && portionGrams >= 0)
        require(portionGrams <= 100_000 && per100g <= 100_000) { "Out-of-range nutrition value" }
        return BigDecimal.valueOf(per100g)
            .multiply(BigDecimal.valueOf(portionGrams), MathContext.DECIMAL64)
            .divide(BigDecimal.valueOf(100), MathContext.DECIMAL64)
            .toDouble()
    }

    /** Common per-100g validation. A small tolerance accounts for rounded label figures. */
    fun validLabel(kcal: Double, protein: Double, carbs: Double, fat: Double): Boolean =
        kcal.isFinite() && kcal in 0.0..1000.0 &&
        listOf(protein, carbs, fat).all(::checkedMacroPer100g) &&
        protein + carbs + fat <= 102.0

    fun totalsForLabel(kcal: Double, protein: Double, carbs: Double, fat: Double,
                       grams: Double): NutritionTotals {
        require(grams.isFinite() && grams > 0 && grams <= 100_000) { "Invalid portion" }
        require(validLabel(kcal, protein, carbs, fat)) { "Invalid per-100g values" }
        return NutritionTotals(forPortion(kcal, grams), forPortion(protein, grams),
            forPortion(carbs, grams), forPortion(fat, grams))
    }

    fun checkedMacroPer100g(value: Double): Boolean = value.isFinite() && value >= 0.0 && value <= 100.0
}
