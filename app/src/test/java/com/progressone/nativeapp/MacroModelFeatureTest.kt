package com.progressone.nativeapp

import com.progressone.nativeapp.foodvision.MacroModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hashing pipeline of the Kotlin inference code must stay identical to the one
 * used to train the ONNX model, otherwise the app would feed the network garbage.
 * The expected values below were produced by the Python training script.
 */
class MacroModelFeatureTest {

    private fun assertVector(expected: Map<Int, Float>, actual: FloatArray) {
        assertEquals(MacroModel.FEATURE_DIM, actual.size)
        val nonZero = actual.withIndex().filter { it.value != 0f }.associate { it.index to it.value }
        assertEquals("different number of active features", expected.size, nonZero.size)
        expected.forEach { (index, value) ->
            assertEquals(
                "feature $index differs",
                value.toDouble(),
                (nonZero[index] ?: 0f).toDouble(),
                1e-5
            )
        }
    }

    @Test
    fun applePieFeaturesMatchTrainingPipeline() {
        val expected = mapOf(
            181 to 0.707107f, 191 to 0.707107f, 861 to 1.0f,
            1052 to 0.333333f, 1150 to 0.333333f, 1267 to 0.333333f, 1492 to 0.333333f,
            1705 to 0.333333f, 1717 to 0.333333f, 1836 to 0.333333f, 1936 to 0.333333f,
            1982 to 0.333333f, 2048 to 1.0f
        )
        assertVector(expected, MacroModel.featurize("apple pie", 0))
    }

    @Test
    fun chickenWingsFeaturesMatchTrainingPipeline() {
        val expected = mapOf(
            203 to 0.707107f, 284 to 0.707107f, 636 to 1.0f,
            1033 to 0.258199f, 1146 to 0.258199f, 1372 to 0.258199f, 1437 to 0.258199f,
            1531 to 0.516398f, 1588 to 0.258199f, 1635 to 0.258199f, 1637 to 0.258199f,
            1674 to 0.258199f, 1694 to 0.258199f, 1881 to 0.258199f, 1987 to 0.258199f,
            2068 to 1.0f
        )
        assertVector(expected, MacroModel.featurize("chicken wings", 20))
    }

    @Test
    fun unknownFoodHasNoClassOneHot() {
        val features = MacroModel.featurize("pizza margherita", -1)
        val oneHot = (MacroModel.TEXT_DIM until MacroModel.FEATURE_DIM).sumOf { features[it].toDouble() }
        assertEquals(0.0, oneHot, 1e-6)
    }

    @Test
    fun featureVectorIsNormalisedPerBlock() {
        val features = MacroModel.featurize("apple pie", 0)
        fun norm(from: Int, to: Int): Double {
            var sum = 0.0
            for (index in from until to) sum += (features[index] * features[index]).toDouble()
            return Math.sqrt(sum)
        }
        assertEquals(1.0, norm(0, 512), 1e-5)
        assertEquals(1.0, norm(512, 1024), 1e-5)
        assertEquals(1.0, norm(1024, 2048), 1e-5)
    }

    @Test
    fun modelAssetIsPackaged() {
        // the ONNX model must be present in the assets folder of the application
        val asset = java.io.File("src/main/assets/${MacroModel.MODEL_FILE}")
        assertTrue("macro_estimator.onnx must be packaged in the APK", asset.isFile)
        assertTrue("macro model must not be empty", asset.length() > 100_000)
    }
}
