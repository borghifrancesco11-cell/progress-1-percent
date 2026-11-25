package com.progressone.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.progressone.nativeapp.data.InputValidation
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.NutritionMath
import com.progressone.nativeapp.data.FoodProductLookup
import com.progressone.nativeapp.data.ProductNutrition
import com.progressone.nativeapp.data.NutritionTotals
import com.progressone.nativeapp.data.LocalCoachEngine
import com.progressone.nativeapp.data.enabledModuleIds
import com.progressone.nativeapp.foodvision.Food101Classifier
import com.progressone.nativeapp.foodvision.FoodDatabase
import com.progressone.nativeapp.foodvision.FoodDishInfo
import com.progressone.nativeapp.foodvision.MacroEstimate
import com.progressone.nativeapp.foodvision.MacroModel
import com.progressone.nativeapp.foodvision.NutritionalScoreResult
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Estimate a sensible meal portion (grams) from the user's height/weight/BMI. */
fun estimatePortionGrams(h: Double, w: Double): Double {
    if (!h.isFinite() || h <= 0 || !w.isFinite() || w <= 0) return 300.0
    val bmi = w / Math.pow(h / 100.0, 2.0)
    return when {
        bmi < 18.5 -> 400.0
        bmi < 25.0 -> 300.0
        bmi < 30.0 -> 250.0
        else -> 200.0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(
    store: LocalStore,
    snapshot: AppSnapshot,
    onOpen: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision = store.revision
    val records by produceState<List<JSONObject>>(emptyList(), store, revision) {
        value = withContext(Dispatchers.IO) { store.all("food") }
    }

    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("Lunch") }
    var photo by rememberSaveable { mutableStateOf("") }
    var unsavedPhotos by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var portion by rememberSaveable { mutableStateOf("") }
    var autoSavePending by rememberSaveable { mutableStateOf(false) }
    var autoSaveDish by rememberSaveable { mutableStateOf<FoodDishInfo?>(null) }
    var autoSaveToast by remember { mutableStateOf("") }

    // AI Classification state
    var aiPredictions by remember { mutableStateOf<List<Food101Classifier.Prediction>>(emptyList()) }
    var aiStatusMessage by rememberSaveable { mutableStateOf("") }
    var aiRunning by remember { mutableStateOf(false) }
    var recognizedDishId by rememberSaveable { mutableStateOf("") }
    var aiAvailable by remember { mutableStateOf(true) }
    // "preparing" | "ready" | "failed" — the photo flow works in every state, it simply waits.
    var aiModelState by remember { mutableStateOf("preparing") }
    var aiMacros by remember { mutableStateOf<MacroEstimate?>(null) }
    var aiMacroBusy by remember { mutableStateOf(false) }
    var aiMacroStatus by rememberSaveable { mutableStateOf("") }
    var targetsEditor by remember { mutableStateOf(false) }

    // User profile for portion estimation
    val heightCm = store.setting("user_height_cm", "").cleanNumber()
    val weightKg = store.setting("user_weight_kg", "").cleanNumber()
    val estimatedPortionGrams = estimatePortionGrams(heightCm, weightKg)

    // Daily macro targets (ported from the GitHub v1.1 Food module).
    val targetKcal = store.setting("food_target_kcal", "2200").cleanNumber().takeIf { it > 0 } ?: 2200.0
    val targetProtein = store.setting("food_target_protein", "120").cleanNumber().takeIf { it > 0 } ?: 120.0
    val targetCarbs = store.setting("food_target_carbs", "260").cleanNumber().takeIf { it > 0 } ?: 260.0
    val targetFat = store.setting("food_target_fat", "70").cleanNumber().takeIf { it > 0 } ?: 70.0
    val enabledIds = remember(store.revision) { store.enabledModuleIds() }
    val sleepTarget = remember(store.revision) {
        (store.setting("sleep_target_hours", "8").toDoubleOrNull() ?: 8.0).coerceIn(4.0, 12.0)
    }
    val coachInsight = remember(snapshot, enabledIds, date, targetKcal, targetProtein, sleepTarget) {
        LocalCoachEngine.food(
            snapshot = snapshot,
            enabledModules = enabledIds,
            targetKcal = targetKcal,
            targetProtein = targetProtein,
            sleepTargetHours = sleepTarget,
            date = date,
        )
    }

    // Preload the ONNX model in the background when entering the Food screen.
    // The first launch copies the 336 MB model into private storage, so this can
    // take a while; recognition waits for it instead of failing.
    LaunchedEffect(Unit) {
        MacroModel.prepare(context)
        val result = Food101Classifier.prepare(context)
        aiAvailable = result.isSuccess
        aiModelState = if (result.isSuccess) "ready" else "failed"
        if (result.isFailure) {
            aiStatusMessage = "AI food recognition unavailable: ${result.exceptionOrNull()?.message ?: "model error"}"
        }
    }

    // One-tap auto-save: photo → vision AI → macro AI → saved meal, no typing
    LaunchedEffect(autoSavePending, autoSaveDish, photo, date, group, portion, recognizedDishId, aiMacros) {
        if (!autoSavePending || autoSaveDish == null || photo.isBlank()) return@LaunchedEffect
        scope.launch {
            try {
                val dish = autoSaveDish!!
                val ai = aiMacros
                val serving = ai?.portionGrams ?: numeric(portion)
                if (serving == null || serving <= 0) return@launch
                val per100 = if (ai != null && ai.portionGrams > 0) {
                    listOf(
                        ai.calories / ai.portionGrams * 100.0,
                        ai.protein / ai.portionGrams * 100.0,
                        ai.carbs / ai.portionGrams * 100.0,
                        ai.fat / ai.portionGrams * 100.0
                    )
                } else {
                    listOf(dish.kcalPer100g, dish.proteinPer100g, dish.carbsPer100g, dish.fatPer100g)
                }
                val totals = NutritionMath.totalsForLabel(
                    per100[0], per100[1], per100[2], per100[3], serving
                )
                val scoreRes = FoodDatabase.calculateIntelligentScore(
                    kcal = totals.kcal, protein = totals.protein, carbs = totals.carbs,
                    fat = totals.fat, portionGrams = serving, baseScore = dish.healthScore
                )
                val item = LocalStore.obj(
                    "id" to LocalStore.freshId(),
                    "date" to date,
                    "meal" to group,
                    "name" to dish.displayName,
                    "photo" to photo,
                    "portion" to (ai?.portionLabel ?: portion),
                    "notes" to "Auto-logged via AI photo",
                    "kcal" to totals.kcal,
                    "protein" to totals.protein,
                    "carbs" to totals.carbs,
                    "fat" to totals.fat,
                    "nutritionKnown" to true,
                    "foodScore" to scoreRes.score,
                    "foodGrade" to scoreRes.grade,
                    "dishId" to dish.id,
                    "portionGrams" to serving.toString(),
                    "per100kcal" to per100[0].toString(),
                    "per100protein" to per100[1].toString(),
                    "per100carbs" to per100[2].toString(),
                    "per100fat" to per100[3].toString(),
                    "barcode" to "",
                    "nutritionSource" to if (ai != null) "macro_model" else "ai_food101",
                    "macroSource" to (ai?.source ?: "FoodDatabase")
                )
                store.put("food", item)
                unsavedPhotos.forEach { store.cleanupUnreferencedPhoto(it) }
                unsavedPhotos = emptyList()
                autoSavePending = false
                autoSaveDish = null
                autoSaveToast = "Saved: ${dish.displayName} · ${totals.kcal.roundToInt()} kcal · ${scoreRes.grade}"
            } catch (e: Exception) {
                autoSavePending = false
                autoSaveDish = null
                autoSaveToast = "Auto-save failed: ${e.localizedMessage ?: "error"}"
            }
        }
    }

    // Camera / barcode states
    var pendingCameraName by rememberSaveable { mutableStateOf("") }
    var barcodeCameraName by rememberSaveable { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var barcodeStatus by remember { mutableStateOf("") }
    var barcodeBusy by remember { mutableStateOf(false) }
    var fromBarcode by remember { mutableStateOf(false) }
    var barcodeNutrition by remember { mutableStateOf<ProductNutrition?>(null) }

    fun runMacroAi(dish: FoodDishInfo?, label: String = name) {
        if (label.isBlank() || aiMacroBusy) return
        scope.launch {
            aiMacroBusy = true
            aiMacroStatus = "Estimating macros with the on-device model…"
            try {
                val estimate = MacroModel.estimate(label, dish, numeric(portion))
                aiMacros = estimate
                portion = estimate.portionGrams.roundToInt().toString()
                aiMacroStatus = "Macros from ${estimate.source} · ${estimate.portionLabel}"
            } catch (e: Exception) {
                aiMacroStatus = "Macro estimation failed: ${e.localizedMessage ?: "error"}"
            } finally {
                aiMacroBusy = false
            }
        }
    }

    fun applyPresetDish(dish: FoodDishInfo) {
        recognizedDishId = dish.id
        name = dish.displayName
        aiMacros = null
        aiMacroStatus = ""
        if (portion.isBlank()) portion = estimatedPortionGrams.roundToInt().toString()
    }

    fun runAiRecognition(photoPath: String) {
        if (photoPath.isBlank()) return
        if (aiRunning) return
        scope.launch {
            aiRunning = true
            try {
                // The model may still be preparing (first launch copies ~336 MB).
                // Wait for it instead of refusing the scan.
                if (!aiAvailable) aiStatusMessage = "Preparing on-device AI model…"
                val classifier = Food101Classifier.prepare(context).getOrElse { error ->
                    aiAvailable = false
                    aiModelState = "failed"
                    throw error
                }
                aiAvailable = true
                aiModelState = "ready"
                aiStatusMessage = "Analyzing your meal with on-device AI…"
                val bitmap = withContext(Dispatchers.IO) {
                    decodeFoodPhoto(store.photoFile(photoPath), 500)
                } ?: error("Could not load photo for AI analysis")

                val predictions = withContext(Dispatchers.Default) {
                    try {
                        classifier.classify(bitmap, topK = 4)
                    } finally {
                        bitmap.recycle()
                    }
                }
                aiPredictions = predictions
                if (predictions.isNotEmpty()) {
                    val top = predictions.first()
                    val confidencePct = (top.confidence * 100).roundToInt()
                    if (top.confidence >= 0.35f) {
                        aiStatusMessage = "AI identified: ${top.label.replaceFirstChar { it.uppercase() }} ($confidencePct% match)"
                    } else {
                        aiStatusMessage = "Low confidence: ${top.label.replaceFirstChar { it.uppercase() }} ($confidencePct% match). Tap a suggestion or correct the name."
                    }
                    val dish = FoodDatabase.getDish(top.label)
                    if (dish != null) {
                        recognizedDishId = dish.id
                        if (name.isBlank() || name == "Meal") name = dish.displayName
                        portion = estimatedPortionGrams.roundToInt().toString()
                        applyPresetDish(dish)
                        // Second model: estimate macros for the recognised dish.
                        aiMacroStatus = "Estimating macros with the on-device model…"
                        val estimate = MacroModel.estimate(dish.displayName, dish)
                        aiMacros = estimate
                        portion = estimate.portionGrams.roundToInt().toString()
                        aiMacroStatus = "Macros from ${estimate.source} · ${estimate.portionLabel}"
                        // One-tap auto-save when AI is confident — no typing needed
                        if (top.confidence >= 0.6f && !showEditor) {
                            autoSaveDish = dish
                            autoSavePending = true
                        }
                    } else {
                        if (name.isBlank() || name == "Meal") name = top.label.replaceFirstChar { it.uppercase() }
                        portion = estimatedPortionGrams.roundToInt().toString()
                        aiMacros = null
                        aiMacroStatus = ""
                    }
                } else {
                    aiStatusMessage = "No recognized food item detected in picture."
                    portion = estimatedPortionGrams.roundToInt().toString()
                }
            } catch (e: OutOfMemoryError) {
                aiStatusMessage = "The photo or AI model needs more memory. Close other apps and retry with a smaller photo."
                aiModelState = "failed"
                aiAvailable = false
            } catch (e: Exception) {
                aiStatusMessage = "AI analysis error: ${e.localizedMessage ?: "Processing error"}"
            } finally {
                aiRunning = false
            }
        }
    }

    fun restoreBarcodeNutrition(record: JSONObject): ProductNutrition? {
        fun finitePer100(key: String): Double? {
            if (!record.has(key) || record.isNull(key)) return null
            val value = record.optDouble(key, Double.NaN)
            return value.takeIf { it.isFinite() && it >= 0.0 && it <= 100_000.0 }
        }
        val kcal = finitePer100("per100kcal")
        val protein = finitePer100("per100protein")
        val carbs = finitePer100("per100carbs")
        val fat = finitePer100("per100fat")
        if (kcal != null && protein != null && carbs != null && fat != null) {
            return ProductNutrition(
                record.optString("name").ifBlank { "Scanned food" },
                record.optString("barcode"),
                kcal,
                protein,
                carbs,
                fat,
            )
        }

        // Older records may only contain totals. Reconstruct the per-100g
        // values from the saved portion instead of silently replacing them
        // with zeroes when the meal is edited.
        val grams = record.optString("portion").toDoubleOrNull()
        if (grams != null && grams > 0.0 && record.optBoolean("nutritionKnown")) {
            fun total(key: String): Double? {
                val value = record.optDouble(key, Double.NaN)
                return value.takeIf { it.isFinite() && it >= 0.0 }
            }
            val totalKcal = total("kcal")
            val totalProtein = total("protein")
            val totalCarbs = total("carbs")
            val totalFat = total("fat")
            if (totalKcal != null && totalProtein != null && totalCarbs != null && totalFat != null) {
                return ProductNutrition(
                    record.optString("name").ifBlank { "Scanned food" },
                    record.optString("barcode"),
                    totalKcal / grams * 100.0,
                    totalProtein / grams * 100.0,
                    totalCarbs / grams * 100.0,
                    totalFat / grams * 100.0,
                )
            }
        }
        return null
    }

    fun loadEditor(record: JSONObject? = null) {
        editing = record
        name = record?.optString("name").orEmpty()
        group = record?.optString("meal", "Lunch") ?: "Lunch"
        photo = record?.optString("photo").orEmpty()
        notes = record?.optString("notes").orEmpty()
        portion = record?.optString("portion").orEmpty()
        aiPredictions = emptyList()
        aiStatusMessage = ""
        aiMacroStatus = ""
        aiMacros = record?.takeIf { it.optString("nutritionSource") == "macro_model" }?.let { saved ->
            val grams = saved.optDouble("portionGrams", 0.0)
            if (grams > 0) MacroEstimate(
                calories = saved.optDouble("kcal", 0.0),
                protein = saved.optDouble("protein", 0.0),
                carbs = saved.optDouble("carbs", 0.0),
                fat = saved.optDouble("fat", 0.0),
                portionGrams = grams,
                portionLabel = saved.optString("portion", "saved portion"),
                source = saved.optString("macroSource", "On-device macro model (saved)")
            ) else null
        }
        recognizedDishId = record?.optString("dishId").orEmpty()
        barcode = record?.optString("barcode").orEmpty()
        fromBarcode = record?.optString("nutritionSource") == "openfoodfacts"
        barcodeNutrition = if (fromBarcode && record != null) {
            restoreBarcodeNutrition(record)
        } else null
        barcodeStatus = if (barcodeNutrition == null && fromBarcode) {
            "The saved product nutrition is unavailable. Look up the barcode again before saving."
        } else ""
        deleteConfirm = ""
        showEditor = true

        if (photo.isNotBlank()) {
            runAiRecognition(photo)
        }
    }

    fun markUnsavedPhoto(name: String) {
        if (name.isNotBlank() && name !in unsavedPhotos) unsavedPhotos = unsavedPhotos + name
    }

    /** Remove editor-only photos which were cancelled or superseded. */
    fun cleanupUnsavedPhotos(keep: String? = null) {
        unsavedPhotos.forEach { name ->
            if (name != keep) store.cleanupUnreferencedPhoto(name)
        }
        unsavedPhotos = if (keep == null) emptyList() else unsavedPhotos.filter { it == keep }
    }

    /** Close the editor and remove newly captured photos which were never persisted. */
    fun dismissEditor() {
        showEditor = false
        cleanupUnsavedPhotos()
    }

    suspend fun saveBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val outputName = "food_${UUID.randomUUID()}.jpg"
        store.withMediaLock {
            val file = store.photoFile(outputName) ?: error("Invalid food photo")
            val temporary = File(store.photoDirectory, ".$outputName.${UUID.randomUUID()}.tmp")
            try {
                temporary.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                        "Unable to encode the selected photo"
                    }
                    output.fd.sync()
                }
                check(temporary.renameTo(file)) { "Unable to save the selected photo" }
                outputName
            } finally {
                temporary.delete()
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            showEditor = true
            scope.launch {
                var bitmap: Bitmap? = null
                try {
                    bitmap = withContext(Dispatchers.IO) { decodeFoodSource(context, uri) }
                        ?: error("The selected image could not be decoded")
                    val savedName = saveBitmap(bitmap)
                    markUnsavedPhoto(savedName)
                    photo = savedName
                    // runAiRecognition owns the busy flag, otherwise the scan would be skipped.
                    runAiRecognition(savedName)
                } catch (e: OutOfMemoryError) {
                    aiStatusMessage = "This photo is too large to process safely. Choose a smaller image."
                } catch (e: Exception) {
                    aiStatusMessage = "Unable to read photo: ${e.localizedMessage}"
                } finally {
                    bitmap?.recycle()
                }
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val capturedName = pendingCameraName
        val capturedFile = store.photoFile(capturedName)
        if (ok && capturedName.isNotBlank() && capturedFile != null && capturedFile.isFile && capturedFile.length() > 0L) {
            markUnsavedPhoto(capturedName)
            photo = capturedName
            showEditor = true
            runAiRecognition(capturedName)
        } else {
            store.cleanupUnreferencedPhoto(capturedName)
            if (ok) aiStatusMessage = "The camera did not return a readable photo."
        }
        pendingCameraName = ""
    }

    fun startCamera() {
        runCatching {
            val fileName = "food_${UUID.randomUUID()}.jpg"
            val file = store.photoFile(fileName) ?: error("Cannot create camera file")
            file.parentFile?.mkdirs()
            pendingCameraName = fileName
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        }.onFailure { aiStatusMessage = "Camera unavailable: ${it.localizedMessage}" }
    }

    suspend fun loadBarcodeProduct(code: String) {
        barcodeBusy = true
        barcodeStatus = ""
        try {
            val product = FoodProductLookup.byBarcode(code)
            name = product.name
            barcode = product.barcode
            barcodeNutrition = product
            fromBarcode = true
            aiMacros = null
            recognizedDishId = ""
            aiPredictions = emptyList()
            barcodeStatus = "Product loaded: ${product.name}"
            if (portion.isBlank()) portion = estimatedPortionGrams.roundToInt().toString()
        } catch (e: Exception) {
            barcodeStatus = e.message ?: "Product unavailable."
            fromBarcode = false
            barcodeNutrition = null
        } finally { barcodeBusy = false }
    }

    fun lookupBarcode(code: String = barcode) {
        if (barcodeBusy) return
        scope.launch { loadBarcodeProduct(code) }
    }

    val barcodeCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val fileName = barcodeCameraName
        if (ok && fileName.isNotBlank()) scope.launch {
            barcodeBusy = true
            var bitmap: Bitmap? = null
            try {
                bitmap = withContext(Dispatchers.IO) {
                    decodeFoodPhoto(store.photoFile(fileName), 2200)
                } ?: error("Unable to read barcode image")
                val options = BarcodeScannerOptions.Builder().setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E
                ).build()
                val scanner = BarcodeScanning.getClient(options)
                val matches = try {
                    scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
                } finally { scanner.close() }
                val match = matches.firstNotNullOfOrNull { candidate ->
                    val raw = candidate.rawValue ?: return@firstNotNullOfOrNull null
                    val upcE = candidate.format == Barcode.FORMAT_UPC_E
                    if (!FoodProductLookup.validBarcode(raw, upcE)) null
                    else FoodProductLookup.canonicalBarcode(raw, upcE) to upcE
                } ?: error("No readable barcode found in photo.")
                barcode = match.first
                loadBarcodeProduct(match.first)
            } catch (e: OutOfMemoryError) {
                barcodeStatus = "The barcode photo needs too much memory. Close other apps and retry."
            } catch (e: Exception) {
                barcodeStatus = e.localizedMessage ?: "Barcode not recognized"
            } finally {
                bitmap?.recycle()
                barcodeBusy = false
                withContext(Dispatchers.IO) { store.cleanupUnreferencedPhoto(fileName) }
                barcodeCameraName = ""
            }
        } else {
            store.cleanupUnreferencedPhoto(fileName)
            barcodeCameraName = ""
        }
    }

    fun startBarcodeCamera() {
        runCatching {
            val fileName = "barcode_${UUID.randomUUID()}.jpg"
            val file = store.photoFile(fileName) ?: error("Unable to create scan image")
            file.parentFile?.mkdirs()
            barcodeCameraName = fileName
            barcodeCamera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        }.onFailure { barcodeStatus = "Camera unavailable: ${it.localizedMessage}" }
    }

    val dayMeals = remember(records, date) { records.filter { it.optString("date") == date } }
    val knownMeals = remember(dayMeals) {
        dayMeals.filter { meal ->
            meal.optBoolean("nutritionKnown", meal.optDouble("kcal", 0.0) > 0.0)
        }
    }
    val totalKcal = knownMeals.sumOf { it.optDouble("kcal", 0.0) }
    val totalProtein = knownMeals.sumOf { it.optDouble("protein", 0.0) }
    val totalCarbs = knownMeals.sumOf { it.optDouble("carbs", 0.0) }
    val totalFat = knownMeals.sumOf { it.optDouble("fat", 0.0) }

    val dayScores = knownMeals.mapNotNull {
        if (it.has("foodScore") && it.optInt("foodScore") > 0) it.optInt("foodScore")
        else null
    }
    val dayAverageScore = if (dayScores.isNotEmpty()) dayScores.average().roundToInt() else null
    val dayAverageGrade = dayAverageScore?.let { s ->
        when {
            s >= 85 -> "A+"
            s >= 75 -> "A"
            s >= 60 -> "B"
            s >= 45 -> "C"
            else -> "D"
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            PageHeading("AI NUTRITION", "Food", "Photo → AI recognizes dish → automatic nutrition & score")
        }

        item {
            CoachCard(insight = coachInsight, onAction = onOpen)
        }

        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("TWO ON-DEVICE AI MODELS", Mint)
                Text("1. Vision · ${MacroModel.VISION_MODEL_LABEL}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Recognises the dish from the photo, directly on the phone.",
                    color = Mist, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = GlassLight)
                Text("2. Macro · ${MacroModel.MODEL_LABEL}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "A small neural network bundled in the app estimates calories, protein, " +
                        "carbs, fat and the portion size. No API key, no cloud, fully offline.",
                    color = Mist, style = MaterialTheme.typography.bodySmall
                )
                if (aiMacroStatus.isNotBlank()) {
                    Text(aiMacroStatus, color = Ice, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                FormField("Date (YYYY-MM-DD)", date, { date = it.take(10) })
                if (!InputValidation.isValidDate(date)) {
                    Text(
                        "Enter a real date between ${InputValidation.MIN_VALID_YEAR} and ${InputValidation.MAX_VALID_YEAR}.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("DAILY DIET SCORE", Mint)
                        Text(
                            dayAverageScore?.let { "$it / 100" } ?: "—",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Ice
                        )
                        Text(
                            dayAverageGrade?.let { "Grade $it · Based on meals recorded today" }
                                ?: "Log your first meal with AI photo to view score",
                            color = Mist,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (dayAverageScore != null) {
                        ProgressRing(dayAverageScore / 100f, Modifier.size(96.dp))
                    }
                }

                HorizontalDivider(color = GlassLight)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallStat("KCAL", "${totalKcal.roundToInt()}", Modifier.weight(1f))
                    SmallStat("PROTEIN", "${formatFoodNumber(totalProtein)} g", Modifier.weight(1f), Mint)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallStat("CARBS", "${formatFoodNumber(totalCarbs)} g", Modifier.weight(1f), Ice)
                    SmallStat("FAT", "${formatFoodNumber(totalFat)} g", Modifier.weight(1f), Lavender)
                }

                HorizontalDivider(color = GlassLight)
                Text(
                    "Target: ${targetKcal.roundToInt()} kcal · P ${targetProtein.roundToInt()} g · C ${targetCarbs.roundToInt()} g · G ${targetFat.roundToInt()} g",
                    color = Mist, style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { (totalKcal / targetKcal).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = if (totalKcal <= targetKcal) Mint else Lavender
                )
                TextButton(onClick = { targetsEditor = true }) { Text("Edit daily targets") }

                Button(onClick = { loadEditor() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (aiModelState) {
                            "ready" -> "Photo Meal (AI scans automatically)"
                            "preparing" -> "Photo Meal (AI model preparing…)"
                            else -> "Photo Meal (AI unavailable — manual entry works)"
                        }
                    )
                }
                if (aiModelState == "failed") {
                    Text(
                        "AI recognition unavailable on this device. Manual entry still works.",
                        color = Mist,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (dayMeals.isEmpty()) {
            item {
                EmptyPanel("No meals recorded", "Take a photo of your meal — AI recognizes the dish and calculates nutrition automatically.") {
                    loadEditor()
                }
            }
        }

        items(dayMeals, key = { it.optString("id") }) { meal ->
            val score = meal.optInt("foodScore", 0)
            val grade = meal.optString("foodGrade", "")
            val kcalVal = meal.optDouble("kcal", 0.0)
            val portionGrams = meal.optString("portionGrams", "")
            val portionText = if (portionGrams.isNotBlank()) " ${portionGrams}g" else ""

            FrostCard(Modifier.fillMaxWidth(), onClick = { loadEditor(meal) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val bitmap = foodPhotoAsync(store.photoFile(meal.optString("photo")), 320)
                    if (bitmap != null) {
                        Image(
                            bitmap.asImageBitmap(), "Meal photo",
                            Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = GlassLight,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Restaurant, null, Modifier.size(34.dp), tint = Ice)
                            }
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Eyebrow(meal.optString("meal", "Meal"))
                            val source = meal.optString("nutritionSource")
                            if (source == "ai_food101" || source == "macro_model") {
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = Mint.copy(alpha = 0.2f)) {
                                    Text(if (source == "macro_model") " MACRO AI " else " VISION AI ",
                                        style = MaterialTheme.typography.labelSmall, color = Mint,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(meal.optString("name", "Meal"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${kcalVal.roundToInt()} kcal$portionText · P: ${formatFoodNumber(meal.optDouble("protein", 0.0))}g · C: ${formatFoodNumber(meal.optDouble("carbs", 0.0))}g · F: ${formatFoodNumber(meal.optDouble("fat", 0.0))}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mist
                        )
                    }

                    if (score > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (score >= 80) Mint.copy(alpha = 0.25f) else if (score >= 60) Ice.copy(alpha = 0.25f) else Lavender.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    grade.ifBlank { "$score" },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (score >= 80) Mint else if (score >= 60) Ice else Lavender
                                )
                            }
                            Text("$score pts", style = MaterialTheme.typography.labelSmall, color = Mist)
                        }
                    } else {
                        Icon(Icons.Rounded.ChevronRight, null, tint = Mist)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        }

        if (autoSaveToast.isNotBlank()) {
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                FrostCard(Modifier.widthIn(max = 320.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Ice,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(autoSaveToast, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }

    // ---- Auto nutrition editor — no grams/type typing required ----
    if (showEditor) {
        val dish = FoodDatabase.getDish(recognizedDishId)
        val ai = aiMacros
        val barcodeValues = barcodeNutrition
        val barcodeNutritionMissing = fromBarcode && barcodeValues == null
        val per100kcal = when {
            barcodeValues != null -> barcodeValues.kcal
            ai != null && ai.portionGrams > 0 -> ai.calories / ai.portionGrams * 100.0
            else -> dish?.kcalPer100g ?: 0.0
        }
        val per100protein = when {
            barcodeValues != null -> barcodeValues.protein
            ai != null && ai.portionGrams > 0 -> ai.protein / ai.portionGrams * 100.0
            else -> dish?.proteinPer100g ?: 0.0
        }
        val per100carbs = when {
            barcodeValues != null -> barcodeValues.carbs
            ai != null && ai.portionGrams > 0 -> ai.carbs / ai.portionGrams * 100.0
            else -> dish?.carbsPer100g ?: 0.0
        }
        val per100fat = when {
            barcodeValues != null -> barcodeValues.fat
            ai != null && ai.portionGrams > 0 -> ai.fat / ai.portionGrams * 100.0
            else -> dish?.fatPer100g ?: 0.0
        }

        val serving = ai?.portionGrams ?: numeric(portion)
        val calculatedTotals = if (!barcodeNutritionMissing && serving != null && serving > 0) {
            runCatching {
                NutritionMath.totalsForLabel(per100kcal, per100protein, per100carbs, per100fat, serving!!)
            }.getOrNull()
        } else null

        val intelligentScore: NutritionalScoreResult? = remember(calculatedTotals, serving, recognizedDishId, aiMacros) {
            if (calculatedTotals != null) {
                FoodDatabase.calculateIntelligentScore(
                    kcal = calculatedTotals.kcal,
                    protein = calculatedTotals.protein,
                    carbs = calculatedTotals.carbs,
                    fat = calculatedTotals.fat,
                    portionGrams = serving!!,
                    baseScore = dish?.healthScore
                )
            } else null
        }

        val validDate = InputValidation.isValidDate(date)
        val formValid = name.isNotBlank() && validDate

        ModalBottomSheet(
            onDismissRequest = { dismissEditor() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                    .padding(horizontal = 20.dp).padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (editing == null) "AI Food Scanner" else "Edit Meal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Photo Preview
                    val bitmap = foodPhotoAsync(store.photoFile(photo), 700)
                    if (bitmap != null) {
                        Image(
                            bitmap.asImageBitmap(), "Meal photo",
                            Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Photo Action Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::startCamera, Modifier.weight(1f)) {
                            Icon(Icons.Rounded.CameraAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Camera AI")
                        }
                        OutlinedButton(onClick = { gallery.launch("image/*") }, Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = ::startBarcodeCamera,
                            enabled = !barcodeBusy,
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (barcodeBusy) "Scanning…" else "Scan barcode")
                        }
                        FormField(
                            "Barcode",
                            barcode,
                            { value -> barcode = value.filter(Char::isDigit).take(14) },
                            Modifier.weight(1f),
                        )
                        Button(
                            onClick = { lookupBarcode() },
                            enabled = !barcodeBusy && FoodProductLookup.validBarcode(barcode),
                        ) { Text("Look up") }
                    }
                    if (barcodeStatus.isNotBlank()) {
                        Text(barcodeStatus, color = Mist, style = MaterialTheme.typography.bodySmall)
                    }

                    if (aiRunning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ice)
                    }

                    if (aiStatusMessage.isNotBlank()) {
                        FrostCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = Ice, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(aiStatusMessage, style = MaterialTheme.typography.bodySmall, color = Ice)
                            }
                        }
                    }

                    // AI Dish Suggestions Chips
                    if (aiPredictions.isNotEmpty()) {
                        Text("AI suggestions (tap to change):", style = MaterialTheme.typography.labelMedium, color = Mist)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(aiPredictions) { pred ->
                                val d = FoodDatabase.getDish(pred.label)
                                FilterChip(
                                    selected = recognizedDishId == pred.label.replace(' ', '_'),
                                    onClick = {
                                        if (d != null) applyPresetDish(d)
                                        else {
                                            name = pred.label.replaceFirstChar { it.uppercase() }
                                            recognizedDishId = pred.label.replace(' ', '_')
                                        }
                                    },
                                    label = { Text("${pred.label} (${(pred.confidence * 100).roundToInt()}%)") }
                                )
                            }
                        }
                    }

                    FormField("Meal name", name, { name = it.take(100) })
                    Chips(listOf("Breakfast", "Lunch", "Dinner", "Snack"), group) { group = it }

                    HorizontalDivider()

                    Text("Automatic nutrition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("AI recognized dish · portion estimated from your height/weight · adjust below if needed",
                        style = MaterialTheme.typography.bodySmall, color = Mist)

                    if (dish != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallStat("DISH", dish.displayName, Modifier.weight(1f), Ice)
                            SmallStat("PER 100G", "${per100kcal.roundToInt()} kcal", Modifier.weight(1f), Mint)
                        }
                    }

                    OutlinedButton(
                        onClick = { runMacroAi(dish, name) },
                        enabled = !aiMacroBusy && name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (aiMacroBusy) "Estimating macros…" else "Estimate macros with AI")
                    }
                    if (aiMacroBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ice)
                    if (aiMacroStatus.isNotBlank()) {
                        Text(aiMacroStatus, color = Ice, style = MaterialTheme.typography.bodySmall)
                    }
                    if (ai != null) {
                        Text("Fonte macro: ${ai.source}", color = Mint,
                            style = MaterialTheme.typography.labelSmall)
                    }

                    FormField("Portion (grams)", portion, {
                        portion = it.take(8)
                        // A manual portion change keeps the AI per-100g values but
                        // rescales the totals; no need to discard the estimate.
                    }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

                    // Calculated Result & Intelligent Score Card
                    FrostCard(Modifier.fillMaxWidth(), highlighted = calculatedTotals != null) {
                        if (calculatedTotals != null && intelligentScore != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Eyebrow("FOOD SCORE & GRADE")
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${intelligentScore.score} / 100",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Ice
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (intelligentScore.score >= 75) Mint.copy(alpha = 0.25f) else Ice.copy(alpha = 0.25f)
                                        ) {
                                            Text(
                                                " Grade ${intelligentScore.grade} ",
                                                color = if (intelligentScore.score >= 75) Mint else Ice,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "${intelligentScore.calorieLevel} · ${intelligentScore.proteinQuality}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Mist
                                    )
                                    Text(
                                        intelligentScore.feedback,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            HorizontalDivider(color = GlassLight)
                            Text("${calculatedTotals.kcal.roundToInt()} kcal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallStat("PROTEIN", "${formatFoodNumber(calculatedTotals.protein)} g", Modifier.weight(1f), Mint)
                                SmallStat("CARBS", "${formatFoodNumber(calculatedTotals.carbs)} g", Modifier.weight(1f), Ice)
                                SmallStat("FAT", "${formatFoodNumber(calculatedTotals.fat)} g", Modifier.weight(1f), Lavender)
                            }
                        } else {
                            Text("Take a photo or scan a barcode to calculate nutrition automatically.", color = Mist, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (editing != null) {
                        TextButton(onClick = {
                            val id = editing!!.optString("id")
                            if (deleteConfirm == id) {
                                store.remove("food", id)
                                dismissEditor()
                                deleteConfirm = ""
                            } else deleteConfirm = id
                        }) {
                            Text(if (deleteConfirm == editing!!.optString("id")) "Confirm delete meal" else "Delete meal", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { dismissEditor() }, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        enabled = formValid && calculatedTotals != null,
                        onClick = {
                            val oldPhoto = editing?.optString("photo")
                            val totals = calculatedTotals
                            val scoreRes = intelligentScore
                            val item = LocalStore.obj(
                                "id" to (editing?.optString("id") ?: LocalStore.freshId()),
                                "date" to date,
                                "meal" to group,
                                "name" to name.trim(),
                                "photo" to photo,
                                "portion" to portion.trim(),
                                "notes" to notes.trim(),
                                "kcal" to (totals?.kcal ?: JSONObject.NULL),
                                "protein" to (totals?.protein ?: JSONObject.NULL),
                                "carbs" to (totals?.carbs ?: JSONObject.NULL),
                                "fat" to (totals?.fat ?: JSONObject.NULL),
                                "nutritionKnown" to (totals != null),
                                "foodScore" to (scoreRes?.score ?: 0),
                                "foodGrade" to (scoreRes?.grade ?: ""),
                                "dishId" to recognizedDishId,
                                "portionGrams" to portion,
                                "per100kcal" to per100kcal.toString(),
                                "per100protein" to per100protein.toString(),
                                "per100carbs" to per100carbs.toString(),
                                "per100fat" to per100fat.toString(),
                                "barcode" to barcode,
                                "nutritionSource" to if (totals == null) "unverified"
                                    else if (fromBarcode && barcodeNutrition != null) "openfoodfacts"
                                    else if (aiMacros != null) "macro_model"
                                    else if (recognizedDishId.isNotBlank()) "ai_food101"
                                    else "label",
                                "macroSource" to (
                                    aiMacros?.source
                                        ?: if (barcodeNutrition != null) "Open Food Facts"
                                        else if (recognizedDishId.isNotBlank()) "FoodDatabase"
                                        else ""
                                    )
                            )
                            store.put("food", item)
                            if (!oldPhoto.isNullOrBlank() && oldPhoto != photo) {
                                store.cleanupUnreferencedPhoto(oldPhoto)
                            }
                            dismissEditor()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save meal")
                    }
                }
            }
        }
    }

    if (targetsEditor) {
        var kcalText by remember { mutableStateOf(targetKcal.roundToInt().toString()) }
        var proteinText by remember { mutableStateOf(targetProtein.roundToInt().toString()) }
        var carbsText by remember { mutableStateOf(targetCarbs.roundToInt().toString()) }
        var fatText by remember { mutableStateOf(targetFat.roundToInt().toString()) }
        val parsedKcal = numeric(kcalText)?.takeIf { it in 1.0..100_000.0 }
        val parsedProtein = numeric(proteinText)?.takeIf { it in 1.0..10_000.0 }
        val parsedCarbs = numeric(carbsText)?.takeIf { it in 1.0..10_000.0 }
        val parsedFat = numeric(fatText)?.takeIf { it in 1.0..10_000.0 }
        val targetsValid = parsedKcal != null && parsedProtein != null &&
            parsedCarbs != null && parsedFat != null
        BottomSheetFrame("Daily targets", { targetsEditor = false }) {
            Text("Values used for the target bar and the daily summary.",
                color = Mist, style = MaterialTheme.typography.bodySmall)
            FormField("Calorie (kcal)", kcalText, { kcalText = it.take(9) })
            FormField("Proteine (g)", proteinText, { proteinText = it.take(9) })
            FormField("Carboidrati (g)", carbsText, { carbsText = it.take(9) })
            FormField("Grassi (g)", fatText, { fatText = it.take(9) })
            if (!targetsValid) {
                Text("Enter finite, plausible daily targets.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                val safeKcal = parsedKcal ?: return@Button
                val safeProtein = parsedProtein ?: return@Button
                val safeCarbs = parsedCarbs ?: return@Button
                val safeFat = parsedFat ?: return@Button
                store.setSetting("food_target_kcal", safeKcal.toString())
                store.setSetting("food_target_protein", safeProtein.toString())
                store.setSetting("food_target_carbs", safeCarbs.toString())
                store.setSetting("food_target_fat", safeFat.toString())
                targetsEditor = false
            }, Modifier.fillMaxWidth(), enabled = targetsValid) { Text("Save targets") }
        }
    }
}

/** Decode a user-selected image with a strict size bound before it reaches the AI model. */
private fun decodeFoodSource(context: android.content.Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 1280) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val orientation = resolver.openInputStream(uri)?.use { stream ->
        runCatching {
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
    }
    if (matrix.isIdentity) return decoded
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (rotated !== decoded) decoded.recycle()
    return rotated
}

private fun numeric(text: String): Double? = InputValidation.parseNonNegativeNumber(text)

private fun formatFoodNumber(value: Double) = "%.1f".format(Locale.US, value)

private fun decodeFoodPhoto(file: File?, maxSide: Int = 900): Bitmap? {
    if (file?.isFile != true) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val rotation = runCatching {
            when (androidx.exifinterface.media.ExifInterface(file.absolutePath).getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        } else decoded
    }.getOrNull()
}

@Composable
private fun foodPhotoAsync(file: File?, maxSide: Int): Bitmap? {
    val image by produceState<Bitmap?>(null, file?.absolutePath, file?.lastModified(), maxSide) {
        value = withContext(Dispatchers.IO) { decodeFoodPhoto(file, maxSide) }
    }
    return image
}
