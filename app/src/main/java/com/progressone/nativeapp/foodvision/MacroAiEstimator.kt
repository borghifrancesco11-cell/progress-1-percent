package com.progressone.nativeapp.foodvision

import com.google.ai.client.generativeai.GenerativeModel
import com.progressone.nativeapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Second AI path of the app: estimate calories/macros for a dish recognised by
 * the on-device Food-101 vision model.
 *
 * - If GEMINI_API_KEY is configured in local.properties, the estimate comes from
 *   Google Gemini 1.5 Flash (cloud).
 * - Without a key (or on any network/API error) it falls back to the local
 *   FoodDatabase values. That fallback is deterministic arithmetic, not an AI model.
 */
data class MacroEstimate(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val portionGrams: Double,
    val portionLabel: String,
    val source: String
)

object MacroAiEstimator {
    const val VISION_MODEL_LABEL = "Swin Transformer Food-101 · ONNX (on-device)"
    const val CLOUD_MACRO_MODEL_LABEL = "Google Gemini 1.5 Flash (cloud)"
    const val OFFLINE_MACRO_MODEL_LABEL = "Database nutrizionale offline (nessuna API key)"

    fun isCloudConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    fun macroModelLabel(): String =
        if (isCloudConfigured()) CLOUD_MACRO_MODEL_LABEL else OFFLINE_MACRO_MODEL_LABEL

    fun macroModelActive(): Boolean = isCloudConfigured()

    suspend fun estimate(foodName: String, dish: FoodDishInfo?): MacroEstimate =
        withContext(Dispatchers.IO) {
            val cleanName = foodName.trim().ifBlank { dish?.displayName ?: "meal" }
            if (isCloudConfigured()) {
                runCatching { fromGemini(cleanName, dish) }
                    .getOrElse { offline(cleanName, dish, "Gemini non disponibile · fallback offline") }
            } else {
                offline(cleanName, dish, "Database nutrizionale offline")
            }
        }

    private suspend fun fromGemini(foodName: String, dish: FoodDishInfo?): MacroEstimate {
        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
        val prompt = """
            Sei un nutrizionista. Stima i valori nutrizionali di una porzione tipica di: "$foodName".
            Considera la cucina italiana quando il nome è ambiguo.
            Rispondi SOLO con queste sei righe, senza markdown e senza spiegazioni:
            CALORIE: <numero>
            PROTEINE: <numero in grammi>
            CARBOIDRATI: <numero in grammi>
            GRASSI: <numero in grammi>
            GRAMMI: <peso della porzione in grammi>
            PORZIONE: <descrizione breve della porzione>
        """.trimIndent()

        val text = model.generateContent(prompt).text
            ?: error("Risposta Gemini vuota")
        return parseGemini(text, dish)
            ?: error("Formato Gemini non riconosciuto")
    }

    private fun parseGemini(text: String, dish: FoodDishInfo?): MacroEstimate? {
        val calories = numberAfter(text, "CALORIE", "CALORIES", "KCAL") ?: return null
        val protein = numberAfter(text, "PROTEINE", "PROTEIN") ?: return null
        val carbs = numberAfter(text, "CARBOIDRATI", "CARBS", "CARBOHYDRATES") ?: return null
        val fat = numberAfter(text, "GRASSI", "FAT") ?: return null
        val grams = numberAfter(text, "GRAMMI", "GRAMS", "PORZIONE_GRAMMI")
            ?: dish?.defaultServingGrams
            ?: 250.0
        val label = Regex("(?:PORZIONE|PORTION)\\s*:\\s*([^\\n\\r]+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: dish?.displayName
            ?: "1 porzione"

        if (calories <= 0.0 || protein < 0.0 || carbs < 0.0 || fat < 0.0) return null
        if (grams < 5.0 || grams > 3000.0) return null

        // Convert to per-100g so the existing NutritionMath validation applies.
        val p100 = protein / grams * 100.0
        val c100 = carbs / grams * 100.0
        val f100 = fat / grams * 100.0
        val k100 = calories / grams * 100.0
        if (!k100.isFinite() || k100 > 1000.0) return null
        if (p100 > 100.0 || c100 > 100.0 || f100 > 100.0 || p100 + c100 + f100 > 102.0) return null

        return MacroEstimate(
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            portionGrams = grams,
            portionLabel = label,
            source = CLOUD_MACRO_MODEL_LABEL
        )
    }

    private fun numberAfter(text: String, vararg keys: String): Double? {
        for (key in keys) {
            val match = Regex("$key\\s*:\\s*([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
                .find(text) ?: continue
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (value != null) return value
        }
        return null
    }

    fun offline(foodName: String, dish: FoodDishInfo?, reason: String): MacroEstimate {
        val grams = dish?.defaultServingGrams?.takeIf { it > 0 } ?: 150.0
        val kcal = if (dish != null) dish.kcalPer100g * grams / 100.0 else 350.0
        val protein = if (dish != null) dish.proteinPer100g * grams / 100.0 else 18.0
        val carbs = if (dish != null) dish.carbsPer100g * grams / 100.0 else 40.0
        val fat = if (dish != null) dish.fatPer100g * grams / 100.0 else 12.0
        return MacroEstimate(
            calories = kcal,
            protein = protein,
            carbs = carbs,
            fat = fat,
            portionGrams = grams,
            portionLabel = dish?.displayName ?: foodName,
            source = "$reason · ${grams.roundToInt()} g"
        )
    }
}
