package com.progressone.nativeapp.foodvision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.exp

/**
 * On-device Food-101 image classifier using the official Swin Transformer FP32 ONNX model.
 * Model size: ~336 MB. Resolves ConvInteger(10) operator issues from quantized INT8 models.
 *
 * Input: "pixel_values" [1, 3, 224, 224] RGB, normalized with ImageNet mean/std.
 * Output: "logits" [1, 101] float logits.
 *
 * The model ships inside the APK as an uncompressed asset. ONNX Runtime needs a real
 * file on disk, so the asset is materialised once into the app's private storage and
 * reused afterwards. Preparation is retryable: a transient failure (for example a full
 * disk) never poisons the session for the rest of the process lifetime.
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

        // Create session via file path to utilize memory-mapped file access
        val modelFile = materializeModel(context)
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
        const val MODEL_SIZE_BYTES: Long = 352_389_545L
        const val MODEL_SHA256: String = "f79aefaa7af43b7a11ae47267a8f733cb1152dec223d4d3db8fc95ab5dc44a83"
        private const val TAG = "FoodVision"

        @Volatile
        private var instance: Food101Classifier? = null

        private val lock = Mutex()

        /**
         * Returns a ready classifier, preparing the model on first use.
         * Safe to call from any thread; concurrent callers share one session.
         * Failures are reported instead of being cached forever.
         */
        suspend fun prepare(context: Context): Result<Food101Classifier> =
            withContext(Dispatchers.IO) {
                instance?.let { return@withContext Result.success(it) }
                lock.withLock {
                    instance?.let { return@withLock Result.success(it) }
                    runCatching {
                        Food101Classifier(context.applicationContext).also { instance = it }
                    }.onFailure { Log.e(TAG, "Food vision model unavailable", it) }
                }
            }

        /** Drops the cached session so the next [prepare] call starts from scratch. */
        suspend fun reset() = withContext(Dispatchers.IO) {
            lock.withLock {
                instance?.let {
                    runCatching { it.close() }
                    instance = null
                }
            }
        }

        /** Copies the asset model to private storage so ONNX Runtime can mmap it. */
        private fun materializeModel(context: Context): File {
            val directory = context.noBackupFilesDir
            if (!directory.exists()) directory.mkdirs()
            val target = File(directory, MODEL_FILE)

            val expected = assetLength(context)
            if (target.isFile && expected > 0 && target.length() == expected && sha256(target) == MODEL_SHA256) {
                return target
            }

            val usable = directory.usableSpace
            if (expected > 0 && usable < expected + MIN_FREE_BYTES) {
                throw IllegalStateException(
                    "Not enough free space for the AI model (need ${expected / 1024 / 1024} MB, " +
                        "available ${usable / 1024 / 1024} MB)"
                )
            }

            val temporary = File(directory, "$MODEL_FILE.part")
            temporary.delete()
            try {
                context.assets.open(MODEL_FILE).use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output, bufferSize = 256 * 1024)
                        output.flush()
                    }
                }
                if (expected > 0 && temporary.length() != expected) {
                    throw IllegalStateException(
                        "Incomplete AI model download (${temporary.length()} of $expected bytes)"
                    )
                }
                if (temporary.length() != MODEL_SIZE_BYTES || sha256(temporary) != MODEL_SHA256) {
                    throw IllegalStateException("AI model integrity verification failed")
                }
                if (!temporary.renameTo(target)) {
                    target.delete()
                    if (!temporary.renameTo(target)) {
                        throw IllegalStateException("Could not install the AI model file")
                    }
                }
            } catch (e: Throwable) {
                temporary.delete()
                throw e
            }
            Log.i(TAG, "Food vision model installed in ${target.length() / 1024 / 1024} MB")
            return target
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun assetLength(context: Context): Long = runCatching {
            context.assets.openFd(MODEL_FILE).use { it.length }
        }.getOrElse {
            runCatching { context.assets.open(MODEL_FILE).use { it.available().toLong() } }.getOrDefault(0L)
        }

        private const val MIN_FREE_BYTES = 64L * 1024 * 1024
    }
}
