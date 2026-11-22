package com.progressone.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Optional on-demand product lookup. Never sends user photos or personal entries. */
data class ProductNutrition(val name: String, val barcode: String,
    val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

object FoodProductLookup {
    fun validBarcode(code: String): Boolean {
        if (code.length !in listOf(8, 12, 13, 14) || code.any { !it.isDigit() }) return false
        val total = code.dropLast(1).reversed().mapIndexed { index, c ->
            (c - '0') * if (index % 2 == 0) 3 else 1
        }.sum()
        return (10 - total % 10) % 10 == (code.last() - '0')
    }

    suspend fun byBarcode(raw: String): ProductNutrition = withContext(Dispatchers.IO) {
        val code = raw.filterNot { it == ' ' || it == '-' }
        require(validBarcode(code)) { "Invalid EAN/UPC barcode" }
        val conn = URL("https://world.openfoodfacts.org/api/v2/product/$code?fields=code,product_name,nutriments")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Progress1Percent/1.1 (Android personal nutrition journal)")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8500
            conn.readTimeout = 8500
            require(conn.responseCode == 200) { "Food database unavailable (${conn.responseCode})" }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            require(json.optInt("status") == 1) { "Product not found" }
            val product = json.getJSONObject("product")
            val nutrient = product.optJSONObject("nutriments") ?: error("No nutrition label available")
            fun required(key: String): Double {
                val v = nutrient.optDouble(key, Double.NaN)
                require(NutritionMath.checkedMacroPer100g(v)) { "Missing or invalid $key value" }
                return v
            }
            val kcal = nutrient.optDouble("energy-kcal_100g", Double.NaN)
            require(kcal.isFinite() && kcal >= 0 && kcal <= 1000) {
                "Product has no reliable kcal / 100g entry"
            }
            ProductNutrition(product.optString("product_name").ifBlank { "Scanned food" }, code,
                kcal, required("proteins_100g"), required("carbohydrates_100g"), required("fat_100g"))
        } finally { conn.disconnect() }
    }
}
