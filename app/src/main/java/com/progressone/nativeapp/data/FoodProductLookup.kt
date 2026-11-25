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
    /**
     * Validates GTIN-8/12/13/14 codes. When [upcE] is true, the value is
     * interpreted as UPC-E and expanded to UPC-A before its check digit is
     * verified. Without a scanner format, an eight-digit value may be either
     * EAN-8 or UPC-E.
     */
    fun validBarcode(code: String, upcE: Boolean = false): Boolean {
        val clean = code.trim()
        return if (upcE) validUpcE(clean) else validGtin(clean) || validUpcE(clean)
    }

    /** Returns the canonical UPC-A identifier for a valid UPC-E value. */
    fun canonicalBarcode(code: String, upcE: Boolean = false): String {
        val clean = code.trim()
        if (upcE && validUpcE(clean)) return expandUpcE(clean)
        return clean
    }

    private fun validGtin(code: String): Boolean {
        if (code.length !in listOf(8, 12, 13, 14) || code.any { !it.isDigit() }) return false
        val total = code.dropLast(1).reversed().mapIndexed { index, c ->
            (c - '0') * if (index % 2 == 0) 3 else 1
        }.sum()
        return (10 - total % 10) % 10 == (code.last() - '0')
    }

    private fun validUpcE(code: String): Boolean {
        if (code.length != 8 || code.any { !it.isDigit() }) return false
        if (code.first() !in listOf('0', '1')) return false
        return validGtin(expandUpcE(code))
    }

    /** Implements the standard UPC-E zero-suppression expansion rules. */
    private fun expandUpcE(code: String): String {
        require(code.length == 8 && code.all { it.isDigit() }) { "Invalid UPC-E barcode" }
        val numberSystem = code[0]
        val a = code[1]
        val b = code[2]
        val c = code[3]
        val d = code[4]
        val e = code[5]
        val last = code[6]
        val check = code[7]
        return when (last) {
            in '0'..'2' -> "$numberSystem$a$b$last" + "0000" + "$c$d$e$check"
            '3' -> "$numberSystem$a$b$c" + "00000" + "$d$e$check"
            '4' -> "$numberSystem$a$b$c$d" + "00000" + "$e$check"
            else -> "$numberSystem$a$b$c$d$e" + "0000" + "$last$check"
        }
    }

    suspend fun byBarcode(raw: String): ProductNutrition = withContext(Dispatchers.IO) {
        val code = raw.filterNot { it == ' ' || it == '-' }.trim()
        require(validBarcode(code)) { "Invalid EAN/UPC barcode" }

        // An eight-digit manual entry can be either EAN-8 or UPC-E. Try the
        // literal code first and then its canonical UPC-A expansion.
        val candidates = buildList {
            add(code)
            if (code.length == 8 && validUpcE(code)) {
                val expanded = expandUpcE(code)
                if (expanded != code) add(expanded)
            }
        }

        var notFound: Exception? = null
        for (candidate in candidates) {
            try {
                return@withContext fetchProduct(candidate)
            } catch (e: ProductNotFoundException) {
                notFound = e
            }
        }
        throw notFound ?: IllegalArgumentException("Product not found")
    }

    private class ProductNotFoundException : Exception("Product not found")

    private fun fetchProduct(code: String): ProductNutrition {
        val conn = URL("https://world.openfoodfacts.org/api/v2/product/$code?fields=code,product_name,nutriments")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty(
                "User-Agent",
                "Progress1Percent/1.4.0 (Android personal nutrition journal; " +
                    "+https://github.com/borghifrancesco11-cell/progress-1-percent)",
            )
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8500
            conn.readTimeout = 8500
            require(conn.responseCode == 200) { "Food database unavailable (${conn.responseCode})" }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("status") != 1) throw ProductNotFoundException()
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
            return ProductNutrition(product.optString("product_name").ifBlank { "Scanned food" }, code,
                kcal, required("proteins_100g"), required("carbohydrates_100g"), required("fat_100g"))
        } finally { conn.disconnect() }
    }
}
