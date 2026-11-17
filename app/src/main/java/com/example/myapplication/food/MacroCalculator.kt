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
    val portion: String
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
            } catch (e: Exception) {
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
            MacroInfo(calories, protein, carbs, fat, portion)
        } catch (e: Exception) {
            estimateOffline(fallbackName)
        }
    }

    fun estimateOffline(foodName: String): MacroInfo {
        val lower = foodName.lowercase()
        return when {
            lower.contains("pizza") -> MacroInfo(750, 28f, 95f, 25f, "1 pizza (300g)")
            lower.contains("pasta") || lower.contains("spaghetti") || lower.contains("ragù") -> MacroInfo(480, 16f, 75f, 12f, "1 piatto (100g pasta cruda)")
            lower.contains("insalata") || lower.contains("salad") -> MacroInfo(220, 8f, 12f, 15f, "1 piatto grande (250g)")
            lower.contains("pollo") || lower.contains("chicken") -> MacroInfo(320, 38f, 2f, 14f, "1 petto di pollo (200g)")
            lower.contains("carne") || lower.contains("bistecca") || lower.contains("steak") -> MacroInfo(450, 45f, 0f, 28f, "1 fetta (220g)")
            lower.contains("uova") || lower.contains("omelette") -> MacroInfo(280, 20f, 3f, 20f, "2 uova con formaggio")
            lower.contains("riso") || lower.contains("risotto") -> MacroInfo(420, 9f, 78f, 8f, "1 piatto (90g crudo)")
            lower.contains("pesce") || lower.contains("salmone") || lower.contains("sushi") -> MacroInfo(380, 32f, 25f, 14f, "8 pezzi / 1 filetto")
            lower.contains("mela") || lower.contains("frutta") || lower.contains("banana") -> MacroInfo(95, 1f, 24f, 0.3f, "1 frutto medio")
            lower.contains("pane") || lower.contains("panino") || lower.contains("toast") -> MacroInfo(340, 14f, 48f, 10f, "1 panino farcito")
            lower.contains("torta") || lower.contains("dolce") || lower.contains("cioccolato") -> MacroInfo(420, 6f, 55f, 20f, "1 fetta media (120g)")
            else -> MacroInfo(350, 18f, 40f, 12f, "1 porzione media standard")
        }
    }
}
