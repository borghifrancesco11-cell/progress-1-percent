package com.example.myapplication.food

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var selectedItemForDetail by remember { mutableStateOf<FoodItem?>(null) }
    var showModelsInfo by remember { mutableStateOf(false) }

    val todayEntries = viewModel.getTodayEntries()
    val totalCalories = viewModel.getTodayCalories()
    val totalProtein = viewModel.getTodayProtein()
    val totalCarbs = viewModel.getTodayCarbs()
    val totalFat = viewModel.getTodayFat()
    val loggedMeals = viewModel.getLoggedMealsCount()

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
                onBack = onBack,
                actions = {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI Scan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )

            // Pill per verificare i 2 modelli AI (Image Recognition + Macro Calculator)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .clickable { showModelsInfo = !showModelsInfo }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Verifica Modelli AI Integrati",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = if (showModelsInfo) "Chiudi ▲" else "Dettagli ▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mint
                )
            }

            AnimatedVisibility(visible = showModelsInfo) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text("1. Vision Model: ${viewModel.getImageModelName()}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("   (Riconoscimento su 101 classi alimentari con pesi Swin-Transformer FP32)", color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("2. Macro Model: ${viewModel.getMacroModelName()}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("   (Stima precisa nutrienti per porzione: calorie, proteine, carboidrati, grassi)", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Card Macro Giornalieri
            ProgressCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Calorie Oggi", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$totalCalories kcal",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Mint,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target: ${data.dailyCalorieTarget} kcal • $loggedMeals pasti",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabeledProgressBar(
                        label = "Proteine",
                        value = totalProtein,
                        maxValue = data.dailyProteinTarget,
                        modifier = Modifier.weight(1f),
                        color = CategoryBlue
                    )
                    LabeledProgressBar(
                        label = "Carboidrati",
                        value = totalCarbs,
                        maxValue = data.dailyCarbsTarget,
                        modifier = Modifier.weight(1f),
                        color = ScoreYellow
                    )
                    LabeledProgressBar(
                        label = "Grassi",
                        value = totalFat,
                        maxValue = data.dailyFatTarget,
                        modifier = Modifier.weight(1f),
                        color = CategoryPink
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "PASTI REGISTRATI",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (todayEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nessun alimento registrato oggi.\nScatta una foto con AI Scan o tocca + per inserire!",
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 13.sp
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
                            onClick = { selectedItemForDetail = item },
                            onDelete = { viewModel.deleteFoodItem(item.id) }
                        )
                    }
                }
            }
        }
    }

    // Modal Dettaglio / Modifica Pasto Cliccato
    selectedItemForDetail?.let { item ->
        FoodDetailDialog(
            item = item,
            onDismiss = { selectedItemForDetail = null },
            onSave = { updated ->
                viewModel.updateFoodItem(updated)
                selectedItemForDetail = null
            },
            onDelete = {
                viewModel.deleteFoodItem(item.id)
                selectedItemForDetail = null
            }
        )
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
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ProgressCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
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
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "AI",
                            fontSize = 10.sp,
                            color = Mint,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Mint.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${item.mealType} • ${item.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "${item.calories} kcal  |  P: ${item.protein.toInt()}g  C: ${item.carbs.toInt()}g  F: ${item.fat.toInt()}g",
                    style = MaterialTheme.typography.labelSmall,
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
fun FoodDetailDialog(
    item: FoodItem,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var calories by remember { mutableStateOf(item.calories.toString()) }
    var protein by remember { mutableStateOf(item.protein.toInt().toString()) }
    var carbs by remember { mutableStateOf(item.carbs.toInt().toString()) }
    var fat by remember { mutableStateOf(item.fat.toInt().toString()) }
    var quantity by remember { mutableStateOf(item.quantity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dettaglio Pasto",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = ScoreRed)
                }
            }
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
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Porzione (es. 100g, 1 piatto)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("Calorie (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Proteine (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carboidrati (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
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
                    val updated = item.copy(
                        name = name.trim(),
                        quantity = quantity.trim(),
                        calories = calories.toIntOrNull() ?: item.calories,
                        protein = protein.toFloatOrNull() ?: item.protein,
                        carbs = carbs.toFloatOrNull() ?: item.carbs,
                        fat = fat.toFloatOrNull() ?: item.fat
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Salva Modifiche")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi", color = TextSecondary) }
        }
    )
}

@Composable
fun ConfirmAiFoodDialog(
    foodName: String,
    macros: MacroInfo,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Float, Float, Float, String) -> Unit
) {
    var name by remember { mutableStateOf(foodName) }
    var selectedMealType by remember { mutableStateOf("Pranzo") }
    var calories by remember { mutableStateOf(macros.calories.toString()) }
    var protein by remember { mutableStateOf(macros.protein.toInt().toString()) }
    var carbs by remember { mutableStateOf(macros.carbs.toInt().toString()) }
    var fat by remember { mutableStateOf(macros.fat.toInt().toString()) }
    var quantity by remember { mutableStateOf(macros.portion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Column {
                Text("Cibo Riconosciuto!", color = Mint, fontWeight = FontWeight.Bold)
                Text("Fonte: ${macros.sourceEngine}", color = TextSecondary, fontSize = 11.sp)
            }
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Colazione", "Pranzo", "Cena", "Snack").forEach { type ->
                        SelectableChip(
                            label = type,
                            selected = selectedMealType == type,
                            onClick = { selectedMealType = type },
                            modifier = Modifier.weight(1f)
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("Calorie") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Proteine (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carboidrati (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
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
                    val cal = calories.toIntOrNull() ?: macros.calories
                    val p = protein.toFloatOrNull() ?: macros.protein
                    val c = carbs.toFloatOrNull() ?: macros.carbs
                    val f = fat.toFloatOrNull() ?: macros.fat
                    onConfirm(name, selectedMealType, cal, p, c, f, quantity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Registra Pasto")
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
    var selectedMealType by remember { mutableStateOf("Pranzo") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1 porzione") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Aggiungi Alimento", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome alimento") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Colazione", "Pranzo", "Cena", "Snack").forEach { type ->
                        SelectableChip(
                            label = type,
                            selected = selectedMealType == type,
                            onClick = { selectedMealType = type },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Porzione (es. 100g, 1 piatto)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("Calorie (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Proteine (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carboidrati (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
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
                    if (name.isNotBlank()) {
                        val cal = calories.toIntOrNull() ?: 300
                        val p = protein.toFloatOrNull() ?: 15f
                        val c = carbs.toFloatOrNull() ?: 35f
                        val f = fat.toFloatOrNull() ?: 10f
                        onConfirm(name.trim(), selectedMealType, cal, p, c, f, quantity.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = TextSecondary) }
        }
    )
}
