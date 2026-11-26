package com.progressone.nativeapp.foodvision

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Second AI path of the app: the on-device macro estimation model.
 *
 * The model is a small multilayer perceptron (3 hidden layers) exported to ONNX and
 * bundled inside the APK. It receives the hashed text features of the food name plus
 * the one-hot Food-101 class (when the vision model recognised the dish) and returns
 * calories / protein / carbs / fat per 100 g together with a typical portion weight.
 *
 * Training data: 29 700+ real food entries (Open Food Facts), the USDA-style reference
 * values of the 101 Food-101 dishes, and a standardised nutrition dataset.
 * Holdout error: ~54 kcal per 100 g (a constant predictor would be off by ~149 kcal);
 * for the 101 Food-101 dishes the error is ~2.5 kcal per 100 g.
 *
 * Everything runs locally: no API key, no network, no Google services.
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

object MacroModel {
    const val MODEL_FILE = "macro_estimator.onnx"
    const val LABELS_FILE = "food101_labels.txt"

    /** Hashed word + bigram + character features. */
    const val TEXT_DIM = 2048
    private const val WORD_BUCKETS = 512
    private const val BIGRAM_BUCKETS = 512
    private const val CHAR_BUCKETS = 1024

    /** Text features + one-hot Food-101 class. */
    const val FEATURE_DIM = TEXT_DIM + 101

    const val MODEL_LABEL = "Macro estimator · on-device neural network (ONNX)"
    const val VISION_MODEL_LABEL = "Swin Transformer Food-101 · ONNX (on-device)"

    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private val mutex = Mutex()

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var labels: List<String> = emptyList()

    @Volatile
    private var labelIndex: Map<String, Int> = emptyMap()

    /** True when the macro model is ready to run. */
    fun isReady(): Boolean = session != null

    /** Loads the ONNX model from the APK assets (idempotent, ~7 MB). */
    suspend fun prepare(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (session != null) return@withContext true
        mutex.withLock {
            if (session != null) return@withLock true
            runCatching {
                val loaded = context.assets.open(LABELS_FILE)
                    .bufferedReader(Charsets.UTF_8).use { it.readLines() }
                    .filter { it.isNotBlank() }
                if (loaded.size != 101) error("Expected 101 Food-101 labels, found ${loaded.size}")
                val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
                try {
                    val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
                    val created = environment.createSession(bytes, options)
                    labels = loaded
                    labelIndex = loaded.withIndex().associate { (index, label) ->
                        normalize(label.replace('_', ' ')) to index
                    }
                    session = created
                } finally {
                    runCatching { options.close() }
                }
                true
            }.getOrElse { false }
        }
    }

    /**
     * Estimates the nutrition of a portion.
     *
     * @param foodName name of the food, e.g. "chicken wings"
     * @param dish optional Food-101 dish used as a sanity reference
     * @param requestedGrams portion size typed by the user, if any
     */
    suspend fun estimate(
        foodName: String,
        dish: FoodDishInfo?,
        requestedGrams: Double? = null
    ): MacroEstimate = withContext(Dispatchers.IO) {
        val cleanName = foodName.trim().ifBlank { dish?.displayName ?: "meal" }
        val classIdx = classIndexFor(cleanName).takeIf { it >= 0 }
            ?: dish?.let { classIndexFor(it.id).takeIf { idx -> idx >= 0 } ?: classIndexFor(it.displayName) }
            ?: -1
        val prediction = predict(cleanName, classIdx)
        if (prediction == null) return@withContext fallback(cleanName, dish)

        val grams = requestedGrams?.takeIf { it > 0 }
            ?: dish?.defaultServingGrams?.takeIf { it > 0 }
            ?: prediction.portionGrams
        val factor = grams / 100.0
        MacroEstimate(
            calories = prediction.kcal100 * factor,
            protein = prediction.protein100 * factor,
            carbs = prediction.carbs100 * factor,
            fat = prediction.fat100 * factor,
            portionGrams = grams,
            portionLabel = portionLabel(dish, grams),
            source = MODEL_LABEL
        )
    }

    /** Raw model output for a name, or null when the model is not available. */
    fun predict(foodName: String, classIndex: Int): Prediction? {
        val activeSession = session ?: return null
        val features = featurize(foodName, classIndex)
        return try {
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(features),
                longArrayOf(1, FEATURE_DIM.toLong())
            ).use { input ->
                activeSession.run(mapOf("features" to input)).use { output ->
                    @Suppress("UNCHECKED_CAST")
                    val nutrition = (output[0].value as Array<FloatArray>)[0]
                    @Suppress("UNCHECKED_CAST")
                    val portion = (output[1].value as Array<FloatArray>)[0][0]
                    if (nutrition.size < 4) return null
                    val prediction = Prediction(
                        kcal100 = nutrition[0].toDouble(),
                        protein100 = nutrition[1].toDouble(),
                        carbs100 = nutrition[2].toDouble(),
                        fat100 = nutrition[3].toDouble(),
                        portionGrams = portion.toDouble()
                    )
                    if (prediction.isPlausible()) prediction else null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    data class Prediction(
        val kcal100: Double,
        val protein100: Double,
        val carbs100: Double,
        val fat100: Double,
        val portionGrams: Double
    ) {
        fun isPlausible(): Boolean =
            kcal100 in 5.0..900.0 &&
                protein100 in 0.0..80.0 &&
                carbs100 in 0.0..95.0 &&
                fat100 in 0.0..75.0 &&
                (protein100 + carbs100 + fat100) <= 102.0 &&
                portionGrams in 10.0..900.0
    }

    /** Reference values from the local table, used when the model cannot answer. */
    fun fallback(foodName: String, dish: FoodDishInfo?): MacroEstimate {
        val grams = dish?.defaultServingGrams?.takeIf { it > 0 } ?: 150.0
        val factor = grams / 100.0
        return MacroEstimate(
            calories = (dish?.kcalPer100g ?: 180.0) * factor,
            protein = (dish?.proteinPer100g ?: 8.0) * factor,
            carbs = (dish?.carbsPer100g ?: 20.0) * factor,
            fat = (dish?.fatPer100g ?: 7.0) * factor,
            portionGrams = grams,
            portionLabel = dish?.displayName ?: foodName,
            source = "Reference nutrition table (offline)"
        )
    }

    private fun portionLabel(dish: FoodDishInfo?, grams: Double): String {
        val rounded = grams.roundToInt()
        return dish?.let { "${it.displayName} · ${rounded} g" } ?: "${rounded} g"
    }

    /**
     * Food-101 class of a free-text name, -1 when the food is not a Food-101 dish.
     * Exact match first, then containment ("chicken wings" in "hot chicken wings").
     */
    fun classIndexFor(name: String): Int {
        if (labelIndex.isEmpty()) return -1
        val normalized = normalize(name)
        if (normalized.isEmpty()) return -1
        labelIndex[normalized]?.let { return it }
        var best = -1
        var bestLength = 0
        for ((label, index) in labelIndex) {
            if (label.isEmpty()) continue
            val matches = normalized.contains(label) ||
                (normalized.length >= 4 && label.contains(normalized))
            if (matches && label.length > bestLength) {
                best = index
                bestLength = label.length
            }
        }
        return best
    }

    /** Hashes the food name exactly like the Python training pipeline. */
    fun featurize(name: String, classIndex: Int): FloatArray {
        val features = FloatArray(FEATURE_DIM)
        val normalized = normalize(name)
        if (normalized.isNotEmpty()) {
            val words = normalized.split(' ')
            for (word in words) {
                features[bucket(fnv1a(word), WORD_BUCKETS)] += 1f
            }
            normalizeBlock(features, 0, WORD_BUCKETS)

            val bigramStart = WORD_BUCKETS
            for (index in 0 until words.size - 1) {
                val key = "${words[index]} ${words[index + 1]}"
                features[bigramStart + bucket(fnv1a(key), BIGRAM_BUCKETS)] += 1f
            }
            normalizeBlock(features, bigramStart, bigramStart + BIGRAM_BUCKETS)

            val charStart = WORD_BUCKETS + BIGRAM_BUCKETS
            val padded = " $normalized "
            for (index in 0 until padded.length - 2) {
                val gram = padded.substring(index, index + 3)
                features[charStart + bucket(fnv1a(gram), CHAR_BUCKETS)] += 1f
            }
            normalizeBlock(features, charStart, charStart + CHAR_BUCKETS)
        }
        if (classIndex in 0 until 101) features[TEXT_DIM + classIndex] = 1f
        return features
    }

    private fun normalizeBlock(features: FloatArray, from: Int, to: Int) {
        var sum = 0f
        for (index in from until to) sum += features[index] * features[index]
        if (sum <= 0f) return
        val norm = Math.sqrt(sum.toDouble()).toFloat()
        for (index in from until to) features[index] /= norm
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase().replace("&", " and ")
        val builder = StringBuilder(lower.length)
        for (ch in lower) {
            builder.append(if (ch in 'a'..'z' || ch in '0'..'9') ch else ' ')
        }
        return builder.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** FNV-1a 32 bit, identical to the Python implementation. */
    private fun fnv1a(value: String): Int {
        var hash = -2128831035 // 2166136261 as signed Int
        for (ch in value) {
            hash = hash xor (ch.code and 0xFF)
            hash *= 16777619
        }
        return hash
    }

    /** Unsigned 32 bit modulo, matching Python's `hash % bucket`. */
    private fun bucket(hash: Int, size: Int): Int = ((hash.toLong() and 0xFFFFFFFFL) % size).toInt()
}
