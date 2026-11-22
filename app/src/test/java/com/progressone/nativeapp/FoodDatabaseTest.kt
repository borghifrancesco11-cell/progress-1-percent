package com.progressone.nativeapp

import com.progressone.nativeapp.foodvision.FoodDatabase
import org.junit.Assert.*
import org.junit.Test

class FoodDatabaseTest {

    @Test
    fun testAll101DishesExist() {
        val all = FoodDatabase.allDishes()
        assertEquals(101, all.size)
    }

    @Test
    fun testLookupDish() {
        val pizza = FoodDatabase.getDish("pizza")
        assertNotNull(pizza)
        assertEquals("Pizza", pizza?.displayName)
        assertTrue(pizza!!.kcalPer100g > 0)

        val salmon = FoodDatabase.getDish("grilled_salmon")
        assertNotNull(salmon)
        assertEquals("Grilled Salmon", salmon?.displayName)
    }

    @Test
    fun testIntelligentScoreHealthyDish() {
        // Salmon: 182 kcal, 25g protein, 0g carb, 9g fat per 100g portion
        val score = FoodDatabase.calculateIntelligentScore(
            kcal = 182.0,
            protein = 25.0,
            carbs = 0.0,
            fat = 9.0,
            portionGrams = 100.0,
            baseScore = 96
        )
        assertTrue("Expected score >= 80, got ${score.score}", score.score >= 80)
        assertTrue(score.grade in listOf("A+", "A"))
        assertEquals("High Protein", score.proteinQuality)
    }

    @Test
    fun testIntelligentScoreSweetDish() {
        // Donuts: 420 kcal, 5g protein, 50g carb, 22g fat per 100g portion
        val score = FoodDatabase.calculateIntelligentScore(
            kcal = 420.0,
            protein = 5.0,
            carbs = 50.0,
            fat = 22.0,
            portionGrams = 100.0,
            baseScore = 28
        )
        assertTrue("Expected score <= 50, got ${score.score}", score.score <= 50)
        assertTrue(score.grade in listOf("C", "D"))
    }
}
