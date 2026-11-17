package com.example.myapplication.food

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.FoodItem
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.LabeledProgressBar
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.components.SelectableChip
import com.example.myapplication.ui.theme.*

@Composable
fun FoodScreen(
    onBack: () -> Unit,
    viewModel: FoodViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showManualDialog by remember { mutableStateOf(false) }

    val todayEntries = viewModel.getTodayEntries()
    val totalCalories = viewModel.getTodayCalories()
    val totalProtein = viewModel.getTodayProtein()
    val totalCarbs = viewModel.getTodayCarbs()
    val totalFat = viewModel.getTodayFat()
    val loggedMeals = viewModel.getLoggedMealsCount()

    // Image Picker Launcher per AI Photo
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            if (bitmap != null) {
                viewModel.analyzeFoodImage(bitmap)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            ProgressFab(
                onClick = { showManualDialog = true },
                icon = Icons.Default.Add,
                contentDescription = "Aggiungi cibo"
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            ModuleHeader(
                categoryLabel = "AI Nutrition & Food",
                title = "Food",
                subtitle = "Riconoscimento intelligente del cibo e calcolo macro",
                onBack = onBack,
                actions = {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI Scan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )

            // Card Calorie e Macro
            ProgressCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Calorie Oggi", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$totalCalories kcal",
                            style = MaterialTheme.typography.displayMedium,
                            color = Mint,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Target: ${data.dailyCalorieTarget} kcal • $loggedMeals/3 pasti registrati",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                LabeledProgressBar(
                    label = "Proteine",
                    value = totalProtein,
                    maxValue = data.dailyProteinTarget,
                    color = CategoryBlue
                )
                Spacer(Modifier.height(8.dp))
                LabeledProgressBar(
                    label = "Carboidrati",
                    value = totalCarbs,
                    maxValue = data.dailyCarbsTarget,
                    color = CategoryOrange
                )
                Spacer(Modifier.height(8.dp))
                LabeledProgressBar(
                    label = "Grassi",
                    value = totalFat,
                    maxValue = data.dailyFatTarget,
                    color = CategoryPink
                )
            }

            if (uiState is FoodUiState.ProcessingImage) {
                ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = Mint, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Analisi cibo in corso...", color = TextPrimary)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "PASTI REGISTRATI",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(8.dp))

            if (todayEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nessun alimento registrato oggi.\nScatta una foto o tocca + per inserire!",
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(todayEntries, key = { it.id }) { item ->
                        FoodItemRow(
                            item = item,
                            onDelete = { viewModel.deleteFoodItem(item.id) }
                        )
                    }
                }
            }
        }
    }

    (uiState as? FoodUiState.RecognitionDone)?.let { recognized ->
        ConfirmAiFoodDialog(
            foodName = recognized.foodName,
            macros = recognized.macros,
            onDismiss = { viewModel.resetUiState() },
            onConfirm = { name, mealType, cal, p, c, f, qty ->
                viewModel.addFoodItem(name, mealType, cal, p, c, f, qty, isAi = true)
            }
        )
    }

    if (showManualDialog) {
        AddManualFoodDialog(
            onDismiss = { showManualDialog = false },
            onConfirm = { name, mealType, cal, p, c, f, qty ->
                viewModel.addFoodItem(name, mealType, cal, p, c, f, qty, isAi = false)
                showManualDialog = false
            }
        )
    }
}

@Composable
fun FoodItemRow(
    item: FoodItem,
    onDelete: () -> Unit
) {
    ProgressCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.imageRecognized) {
                        Text(
                            text = " (AI)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mint,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${item.mealType} • ${item.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "${item.calories} kcal | P: ${item.protein.toInt()}g | C: ${item.carbs.toInt()}g | F: ${item.fat.toInt()}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
            }
        }
    }
}

@Composable
fun ConfirmAiFoodDialog(
    foodName: String,
    macros: MacroInfo,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Float, Float, Float, String) -> Unit
) {
    var name by remember { mutableStateOf(foodName) }
    var mealType by remember { mutableStateOf("Pranzo") }
    var caloriesText by remember { mutableStateOf(macros.calories.toString()) }
    var proteinText by remember { mutableStateOf(macros.protein.toInt().toString()) }
    var carbsText by remember { mutableStateOf(macros.carbs.toInt().toString()) }
    var fatText by remember { mutableStateOf(macros.fat.toInt().toString()) }
    var quantity by remember { mutableStateOf(macros.portion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text("Cibo Riconosciuto da AI", color = Mint, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome alimento") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Pasto:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Colazione", "Pranzo", "Cena", "Snack").forEach { type ->
                        SelectableChip(
                            label = type,
                            selected = mealType == type,
                            onClick = { mealType = type }
                        )
                    }
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Porzione stimata") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it },
                        label = { Text("Calorie (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it },
                        label = { Text("Proteine (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it },
                        label = { Text("Carboidrati (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { fatText = it },
                        label = { Text("Grassi (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim(),
                        mealType,
                        caloriesText.toIntOrNull() ?: macros.calories,
                        proteinText.toFloatOrNull() ?: macros.protein,
                        carbsText.toFloatOrNull() ?: macros.carbs,
                        fatText.toFloatOrNull() ?: macros.fat,
                        quantity.trim()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Conferma e Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = TextSecondary) }
        }
    )
}

@Composable
fun AddManualFoodDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Float, Float, Float, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf("Pranzo") }
    var caloriesText by remember { mutableStateOf("300") }
    var proteinText by remember { mutableStateOf("15") }
    var carbsText by remember { mutableStateOf("40") }
    var fatText by remember { mutableStateOf("10") }
    var quantity by remember { mutableStateOf("1 porzione") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Inserisci Alimento", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        val estimate = MacroCalculator.estimateOffline(it)
                        caloriesText = estimate.calories.toString()
                        proteinText = estimate.protein.toInt().toString()
                        carbsText = estimate.carbs.toInt().toString()
                        fatText = estimate.fat.toInt().toString()
                    },
                    label = { Text("Nome alimento (es. Risotto)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Colazione", "Pranzo", "Cena", "Snack").forEach { type ->
                        SelectableChip(
                            label = type,
                            selected = mealType == type,
                            onClick = { mealType = type }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it },
                        label = { Text("Calorie (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it },
                        label = { Text("Proteine (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name.trim(),
                            mealType,
                            caloriesText.toIntOrNull() ?: 300,
                            proteinText.toFloatOrNull() ?: 15f,
                            carbsText.toFloatOrNull() ?: 40f,
                            fatText.toFloatOrNull() ?: 10f,
                            quantity.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = TextSecondary) }
        }
    )
}
