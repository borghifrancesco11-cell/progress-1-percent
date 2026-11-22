package com.progressone.nativeapp.foodvision

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * On-device Food-101 image classifier using the official Swin Transformer FP32 ONNX model.
 * Model size: ~336 MB. Resolves ConvInteger(10) operator issues from quantized INT8 models.
 *
 * Input: "pixel_values" [1, 3, 224, 224] RGB, normalized with ImageNet mean/std.
 * Output: "logits" [1, 101] float logits.
 */
class Food101Classifier private constructor(context: Context) : Closeable {
    data class Prediction(val label: String, val confidence: Float)

    private val environment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
    }
    private val session: OrtSession
    private val inputName: String
    private val labels: List<String>

    init {
        labels = context.assets.open(LABELS_FILE)
            .bufferedReader(Charsets.UTF_8).use { it.readLines() }
            .filter { it.isNotBlank() }
        require(labels.size == 101) { "Expected 101 Food-101 English labels, found ${labels.size}" }

        // Stream from assets to internal storage to avoid large heap allocation
        val modelFile = File(context.filesDir, MODEL_FILE)
        if (!modelFile.exists() || modelFile.length() < 300_000_000L) {
            context.assets.open(MODEL_FILE).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output, bufferSize = 128 * 1024)
                }
            }
        }
        // Create session via file path to utilize memory-mapped file access
        session = environment.createSession(modelFile.absolutePath, options)
        inputName = session.inputNames.first()
    }

    fun classify(bitmap: Bitmap, topK: Int = 4): List<Prediction> {
        require(topK in 1..101)
        val inputFloats = imageToNchwFloat32(bitmap)
        val shape = longArrayOf(1, 3, 224, 224)
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(inputFloats), shape).use { input ->
            session.run(mapOf(inputName to input)).use { output ->
                @Suppress("UNCHECKED_CAST")
                val scores = (output[0].value as Array<FloatArray>)[0]
                require(scores.size == labels.size) {
                    "Output has ${scores.size} classes but labels have ${labels.size}"
                }
                val maxLogit = scores.maxOrNull() ?: error("Empty logits")
                val exponentials = scores.map { exp((it - maxLogit).toDouble()) }
                val denominator = exponentials.sum()
                return exponentials.indices
                    .sortedByDescending { exponentials[it] }
                    .take(topK)
                    .map { index ->
                        Prediction(
                            label = labels[index].replace('_', ' '),
                            confidence = (exponentials[index] / denominator).toFloat()
                        )
                    }
            }
        }
    }

    private fun imageToNchwFloat32(bitmap: Bitmap): FloatArray {
        // Preprocessor config: RGB, 224x224, [0,1] scaling and ImageNet mean/std normalization
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val pixels = IntArray(224 * 224)
        resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        if (resized !== bitmap) resized.recycle()

        val means = floatArrayOf(0.485f, 0.456f, 0.406f)
        val stds = floatArrayOf(0.229f, 0.224f, 0.225f)
        val plane = 224 * 224
        val out = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val px = pixels[i]
            val channels = intArrayOf((px shr 16) and 255, (px shr 8) and 255, px and 255)
            for (c in 0..2) {
                out[c * plane + i] = ((channels[c] / 255.0f) - means[c]) / stds[c]
            }
        }
        return out
    }

    override fun close() {
        session.close()
        options.close()
    }

    companion object {
        const val MODEL_FILE = "swin_food101_fp32.onnx"
        const val LABELS_FILE = "food101_labels.txt"

        @Volatile
        private var instance: Food101Classifier? = null

        @Volatile
        private var initError: Throwable? = null

        fun getInstance(context: Context): Food101Classifier? {
            if (initError != null) return null
            return instance ?: synchronized(this) {
                initError?.let { return null }
                instance ?: try {
                    Food101Classifier(context.applicationContext).also { instance = it }
                } catch (e: Throwable) {
                    initError = e
                    null
                }
            }
        }
    }
}
