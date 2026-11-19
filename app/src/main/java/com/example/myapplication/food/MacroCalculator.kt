package com.example.myapplication.food

import com.example.myapplication.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MacroInfo(
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val portion: String,
    val sourceEngine: String = "Gemini AI"
)

object MacroCalculator {

    suspend fun estimateMacros(foodName: String): MacroInfo = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank()) {
            try {
                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )
                val prompt = "Fornisci una stima nutrizionale per: '$foodName'. Rispondi SOLO in formato: CALORIE: [num], PROTEINE: [num], CARBOIDRATI: [num], GRASSI: [num], PORZIONE: [string]"
                val response = model.generateContent(prompt)
                val text = response.text ?: ""
                parseAiResponse(text, foodName)
            } catch (_: Exception) {
                estimateOffline(foodName)
            }
        } else {
            estimateOffline(foodName)
        }
    }

    private fun parseAiResponse(text: String, fallbackName: String): MacroInfo {
        return try {
            val calories = Regex("CALORIE:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 350
            val protein = Regex("PROTEINE:\\s*([\\d.]+)").find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 15f
            val carbs = Regex("CARBOIDRATI:\\s*([\\d.]+)").find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 40f
            val fat = Regex("GRASSI:\\s*([\\d.]+)").find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 12f
            val portion = Regex("PORZIONE:\\s*([^\n\r]+)").find(text)?.groupValues?.get(1)?.trim() ?: "1 porzione media"
            MacroInfo(calories, protein, carbs, fat, portion, sourceEngine = "Google Gemini AI (Cloud)")
        } catch (_: Exception) {
            estimateOffline(fallbackName)
        }
    }

    private data class FoodEstimate(
        val calories: Int,
        val protein: Float,
        val carbs: Float,
        val fat: Float,
        val portion: String
    )

    fun estimateOffline(foodName: String): MacroInfo {
        val lower = foodName.lowercase()
        val est = when {
            lower.contains("pizza") -> FoodEstimate(750, 28f, 95f, 25f, "1 pizza tonda (300g)")
            lower.contains("pasta") || lower.contains("spaghetti") || lower.contains("ragù") -> FoodEstimate(480, 16f, 75f, 12f, "1 piatto (100g cruda)")
            lower.contains("lasagna") -> FoodEstimate(620, 26f, 55f, 32f, "1 porzione (280g)")
            lower.contains("insalata") || lower.contains("salad") -> FoodEstimate(180, 7f, 10f, 12f, "1 piatto grande (250g)")
            lower.contains("pollo") || lower.contains("chicken") -> FoodEstimate(310, 42f, 0f, 14f, "1 petto di pollo (200g)")
            lower.contains("carne") || lower.contains("bistecca") || lower.contains("steak") -> FoodEstimate(450, 46f, 0f, 28f, "1 fetta (220g)")
            lower.contains("uova") || lower.contains("omelette") -> FoodEstimate(260, 20f, 2f, 19f, "2 uova")
            lower.contains("riso") || lower.contains("risotto") -> FoodEstimate(420, 9f, 78f, 8f, "1 piatto (90g crudo)")
            lower.contains("pesce") || lower.contains("salmone") -> FoodEstimate(380, 34f, 0f, 24f, "1 trancio (200g)")
            lower.contains("sushi") -> FoodEstimate(400, 18f, 62f, 8f, "8 pezzi misti")
            lower.contains("hamburger") -> FoodEstimate(540, 30f, 42f, 26f, "1 panino completo")
            lower.contains("mela") || lower.contains("frutta") || lower.contains("banana") -> FoodEstimate(90, 1f, 23f, 0.3f, "1 frutto medio")
            lower.contains("torta") || lower.contains("dolce") || lower.contains("tiramisù") -> FoodEstimate(420, 6f, 54f, 20f, "1 porzione (120g)")
            lower.contains("yogurt") -> FoodEstimate(130, 10f, 15f, 3f, "1 vasetto (150g)")
            else -> FoodEstimate(350, 18f, 40f, 12f, "1 porzione standard (150g)")
        }
        return MacroInfo(
            calories = est.calories,
            protein = est.protein,
            carbs = est.carbs,
            fat = est.fat,
            portion = est.portion,
            sourceEngine = "Database Nutrizionale Offline"
        )
    }
}
