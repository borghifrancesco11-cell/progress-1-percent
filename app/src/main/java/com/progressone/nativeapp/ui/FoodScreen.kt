package com.progressone.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.NutritionMath
import com.progressone.nativeapp.data.NutritionTotals
import com.progressone.nativeapp.foodvision.Food101Classifier
import com.progressone.nativeapp.foodvision.FoodDatabase
import com.progressone.nativeapp.foodvision.FoodDishInfo
import com.progressone.nativeapp.foodvision.MacroAiEstimator
import com.progressone.nativeapp.foodvision.MacroEstimate
import com.progressone.nativeapp.foodvision.NutritionalScoreResult
import com.progressone.nativeapp.media.ImageProcessor
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
fun FoodScreen(store: LocalStore) {
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

    // Preload ONNX model in background when entering Food Screen
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val instance = runCatching { Food101Classifier.getInstance(context) }.getOrNull()
            aiAvailable = instance != null
            if (!aiAvailable) {
                aiStatusMessage = "AI food recognition unavailable on this device"
            }
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
                    "nutritionSource" to if (ai != null) "gemini_ai" else "ai_food101",
                    "macroSource" to (ai?.source ?: "FoodDatabase")
                )
                store.put("food", item)
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

    fun runMacroAi(dish: FoodDishInfo?, label: String = name) {
        if (label.isBlank() || aiMacroBusy) return
        scope.launch {
            aiMacroBusy = true
            aiMacroStatus = "Stima macro con ${MacroAiEstimator.macroModelLabel()}…"
            try {
                val estimate = if (MacroAiEstimator.isCloudConfigured()) {
                    withTimeoutOrNull(12_000L) { MacroAiEstimator.estimate(label, dish) }
                        ?: MacroAiEstimator.offline(label, dish, "Gemini timeout")
                } else {
                    MacroAiEstimator.offline(label, dish, "Database nutrizionale offline")
                }
                aiMacros = estimate
                portion = estimate.portionGrams.roundToInt().toString()
                aiMacroStatus = "Macro da ${estimate.source} · ${estimate.portionLabel}"
            } catch (e: Exception) {
                aiMacroStatus = "Stima macro non riuscita: ${e.localizedMessage ?: "errore"}"
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
        if (photoPath.isBlank() || aiRunning) return
        if (!aiAvailable) {
            aiStatusMessage = "AI food recognition unavailable on this device (native library issue)"
            return
        }
        scope.launch {
            aiRunning = true
            aiStatusMessage = "Analyzing your meal with on-device AI…"
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeFoodPhoto(store.photoFile(photoPath), 500)
                } ?: error("Could not load photo for AI analysis")

                val classifier = Food101Classifier.getInstance(context) ?: run {
                    aiAvailable = false
                    aiStatusMessage = "AI food recognition unavailable on this device"
                    aiRunning = false
                    return@launch
                }
                val predictions = withContext(Dispatchers.Default) {
                    classifier.classify(bitmap, topK = 4)
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
                        aiMacroStatus = "Stima macro con ${MacroAiEstimator.macroModelLabel()}…"
                        val estimate = if (MacroAiEstimator.isCloudConfigured()) {
                            withTimeoutOrNull(12_000L) { MacroAiEstimator.estimate(dish.displayName, dish) }
                                ?: MacroAiEstimator.offline(dish.displayName, dish, "Gemini timeout")
                        } else {
                            MacroAiEstimator.offline(dish.displayName, dish, "Database nutrizionale offline")
                        }
                        aiMacros = estimate
                        portion = estimate.portionGrams.roundToInt().toString()
                        aiMacroStatus = "Macro da ${estimate.source} · ${estimate.portionLabel}"
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
            } catch (e: Exception) {
                aiStatusMessage = "AI analysis error: ${e.localizedMessage ?: "Processing error"}"
            } finally {
                aiRunning = false
            }
        }
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
        aiMacros = record?.takeIf { it.optString("nutritionSource") == "gemini_ai" }?.let { saved ->
            val grams = saved.optDouble("portionGrams", 0.0)
            if (grams > 0) MacroEstimate(
                calories = saved.optDouble("kcal", 0.0),
                protein = saved.optDouble("protein", 0.0),
                carbs = saved.optDouble("carbs", 0.0),
                fat = saved.optDouble("fat", 0.0),
                portionGrams = grams,
                portionLabel = saved.optString("portion", "porzione salvata"),
                source = saved.optString("macroSource", "Gemini (salvato)")
            ) else null
        }
        recognizedDishId = record?.optString("dishId").orEmpty()
        barcode = record?.optString("barcode").orEmpty()
        fromBarcode = record?.optString("nutritionSource") == "openfoodfacts"
        barcodeStatus = ""
        deleteConfirm = ""
        showEditor = true

        if (photo.isNotBlank()) {
            runAiRecognition(photo)
        }
    }

    suspend fun saveBitmap(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val outputName = "food_${UUID.randomUUID()}.jpg"
        val file = store.photoFile(outputName) ?: error("Invalid food photo")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        outputName
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            showEditor = true
            scope.launch {
                aiRunning = true
                try {
                    val bitmap = ImageProcessor.decode(context, uri)
                    val savedName = saveBitmap(bitmap)
                    photo = savedName
                    runAiRecognition(savedName)
                } catch (e: Exception) {
                    aiStatusMessage = "Unable to read photo: ${e.localizedMessage}"
                } finally { aiRunning = false }
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && pendingCameraName.isNotBlank()) {
            photo = pendingCameraName
            showEditor = true
            runAiRecognition(pendingCameraName)
        } else {
            store.photoFile(pendingCameraName)?.delete()
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

    fun lookupBarcode(code: String = barcode) {
        if (barcodeBusy) return
        scope.launch {
            barcodeBusy = true
            barcodeStatus = ""
            try {
                val product = com.progressone.nativeapp.data.FoodProductLookup.byBarcode(code)
                name = product.name
                barcode = product.barcode
                fromBarcode = true
                barcodeStatus = "Product loaded: ${product.name}"
                if (portion.isBlank()) portion = estimatedPortionGrams.roundToInt().toString()
            } catch (e: Exception) {
                barcodeStatus = e.message ?: "Product unavailable."
                fromBarcode = false
            } finally { barcodeBusy = false }
        }
    }

    val barcodeCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val fileName = barcodeCameraName
        if (ok && fileName.isNotBlank()) scope.launch {
            barcodeBusy = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
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
                val code = matches.mapNotNull { it.rawValue }
                    .firstOrNull { com.progressone.nativeapp.data.FoodProductLookup.validBarcode(it) }
                    ?: error("No readable barcode found in photo.")
                barcode = code
                lookupBarcode(code)
            } catch (e: Exception) {
                barcodeStatus = e.localizedMessage ?: "Barcode not recognized"
            } finally {
                barcodeBusy = false
                withContext(Dispatchers.IO) { store.photoFile(fileName)?.delete() }
                barcodeCameraName = ""
            }
        } else {
            store.photoFile(fileName)?.delete()
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
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("DUE MODELLI AI · VERIFICA", Mint)
                Text("1. Vision · ${MacroAiEstimator.VISION_MODEL_LABEL}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Riconosce il piatto dalla foto, direttamente sul telefono.",
                    color = Mist, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = GlassLight)
                Text("2. Macro · ${MacroAiEstimator.macroModelLabel()}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (MacroAiEstimator.isCloudConfigured())
                        "Gemini stima calorie, proteine, carboidrati, grassi e porzione per il piatto riconosciuto."
                    else
                        "Nessuna GEMINI_API_KEY configurata: la stima usa il database nutrizionale locale, non un secondo modello AI.",
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
                TextButton(onClick = { targetsEditor = true }) { Text("Modifica target giornalieri") }

                Button(onClick = { loadEditor() }, modifier = Modifier.fillMaxWidth(), enabled = aiAvailable) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (aiAvailable) "Photo Meal (AI scans automatically)" else "Photo Meal (AI unavailable)")
                }
                if (!aiAvailable) {
                    Text("AI recognition not available on this device. Manual entry still works.", color = Mist, style = MaterialTheme.typography.bodySmall)
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
                            if (source == "ai_food101" || source == "gemini_ai") {
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = Mint.copy(alpha = 0.2f)) {
                                    Text(if (source == "gemini_ai") " GEMINI " else " AI ",
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
        val per100kcal = if (ai != null && ai.portionGrams > 0) ai.calories / ai.portionGrams * 100.0
            else dish?.kcalPer100g ?: 0.0
        val per100protein = if (ai != null && ai.portionGrams > 0) ai.protein / ai.portionGrams * 100.0
            else dish?.proteinPer100g ?: 0.0
        val per100carbs = if (ai != null && ai.portionGrams > 0) ai.carbs / ai.portionGrams * 100.0
            else dish?.carbsPer100g ?: 0.0
        val per100fat = if (ai != null && ai.portionGrams > 0) ai.fat / ai.portionGrams * 100.0
            else dish?.fatPer100g ?: 0.0

        val serving = ai?.portionGrams ?: numeric(portion)
        val calculatedTotals = if (serving != null && serving > 0) {
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

        val formValid = name.isNotBlank() && runCatching { LocalDate.parse(date) }.isSuccess

        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
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
                        Text(if (aiMacroBusy) "Stima macro in corso…" else "Stima macro con AI")
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
                                showEditor = false
                                deleteConfirm = ""
                            } else deleteConfirm = id
                        }) {
                            Text(if (deleteConfirm == editing!!.optString("id")) "Confirm delete meal" else "Delete meal", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showEditor = false }, modifier = Modifier.weight(1f)) {
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
                                    else if (aiMacros != null) "gemini_ai"
                                    else if (recognizedDishId.isNotBlank()) "ai_food101"
                                    else if (fromBarcode) "openfoodfacts"
                                    else "label",
                                "macroSource" to (aiMacros?.source ?: if (recognizedDishId.isNotBlank()) "FoodDatabase" else "")
                            )
                            store.put("food", item)
                            if (!oldPhoto.isNullOrBlank() && oldPhoto != photo) {
                                store.cleanupUnreferencedPhoto(oldPhoto)
                            }
                            showEditor = false
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
        BottomSheetFrame("Target giornalieri", { targetsEditor = false }) {
            Text("Valori usati per la barra dei target e per il riepilogo giornaliero.",
                color = Mist, style = MaterialTheme.typography.bodySmall)
            FormField("Calorie (kcal)", kcalText, { kcalText = it.take(5) })
            FormField("Proteine (g)", proteinText, { proteinText = it.take(5) })
            FormField("Carboidrati (g)", carbsText, { carbsText = it.take(5) })
            FormField("Grassi (g)", fatText, { fatText = it.take(5) })
            Button(onClick = {
                store.setSetting("food_target_kcal", kcalText.cleanNumber().coerceAtLeast(0.0).toString())
                store.setSetting("food_target_protein", proteinText.cleanNumber().coerceAtLeast(0.0).toString())
                store.setSetting("food_target_carbs", carbsText.cleanNumber().coerceAtLeast(0.0).toString())
                store.setSetting("food_target_fat", fatText.cleanNumber().coerceAtLeast(0.0).toString())
                targetsEditor = false
            }, Modifier.fillMaxWidth()) { Text("Salva target") }
        }
    }
}

private fun numeric(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0 }

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
