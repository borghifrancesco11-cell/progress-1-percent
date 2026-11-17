package com.example.myapplication.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.ClothingItem
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.components.SelectableChip
import com.example.myapplication.ui.theme.*

@Composable
fun WardrobeScreen(
    onBack: () -> Unit,
    viewModel: WardrobeViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Outfit Meteo, 1: Armadio, 2: Wishlist, 3: Valigie

    var showAddClothDialog by remember { mutableStateOf(false) }
    var showAddWishDialog by remember { mutableStateOf(false) }
    var showAddSuitcaseDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            when (selectedTab) {
                1 -> ProgressFab(onClick = { showAddClothDialog = true }, icon = Icons.Default.Add, contentDescription = "Aggiungi capo")
                2 -> ProgressFab(onClick = { showAddWishDialog = true }, icon = Icons.Default.Add, contentDescription = "Aggiungi a wishlist")
                3 -> ProgressFab(onClick = { showAddSuitcaseDialog = true }, icon = Icons.Default.Luggage, contentDescription = "Nuova valigia")
            }
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
                categoryLabel = "Wardrobe & Style",
                title = "Wardrobe",
                subtitle = "Outfit intelligenti sul meteo, wishlist e valigie da viaggio",
                onBack = onBack
            )
            // Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SelectableChip(label = "Outfit Meteo", selected = selectedTab == 0, onClick = { selectedTab = 0 })
                SelectableChip(label = "Armadio (${data.clothes.size})", selected = selectedTab == 1, onClick = { selectedTab = 1 })
                SelectableChip(label = "Wishlist (${data.wishlist.size})", selected = selectedTab == 2, onClick = { selectedTab = 2 })
                SelectableChip(label = "Valigie (${data.suitcases.size})", selected = selectedTab == 3, onClick = { selectedTab = 3 })
            }

            when (selectedTab) {
                0 -> WeatherOutfitSection(clothes = data.clothes, onSaveOutfit = viewModel::saveOutfit, onGenerate = viewModel::generateWeatherOutfit)
                1 -> ClothesListSection(clothes = data.clothes, onDelete = viewModel::deleteClothing)
                2 -> WishlistSection(wishlist = data.wishlist, onDelete = viewModel::deleteWishlistItem)
                3 -> SuitcasesSection(suitcases = data.suitcases, clothes = data.clothes, onToggleItem = viewModel::toggleSuitcaseItem)
            }
        }
    }

    if (showAddClothDialog) {
        AddClothingDialog(
            onDismiss = { showAddClothDialog = false },
            onConfirm = { name, cat, color, seasons ->
                viewModel.addClothing(name, cat, color, seasons)
                showAddClothDialog = false
            }
        )
    }

    if (showAddWishDialog) {
        AddWishlistDialog(
            onDismiss = { showAddWishDialog = false },
            onConfirm = { name, desc, price ->
                viewModel.addWishlistItem(name, desc, price)
                showAddWishDialog = false
            }
        )
    }

    if (showAddSuitcaseDialog) {
        AddSuitcaseDialog(
            clothes = data.clothes,
            onDismiss = { showAddSuitcaseDialog = false },
            onConfirm = { name, dest, ids ->
                viewModel.addSuitcase(name, dest, ids)
                showAddSuitcaseDialog = false
            }
        )
    }
}

@Composable
fun WeatherOutfitSection(
    clothes: List<ClothingItem>,
    onSaveOutfit: (String, List<ClothingItem>) -> Unit,
    onGenerate: (String) -> List<ClothingItem>
) {
    var weather by remember { mutableStateOf("Caldo / Sole") }
    var currentOutfit by remember { mutableStateOf(onGenerate(weather)) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProgressCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Outfit per le Condizioni Meteo",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Seleziona il meteo per ricevere un abbinamento generato in base ai capi del tuo armadio.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Caldo / Sole", "Mite / Variabile", "Freddo / Inverno").forEach { w ->
                    SelectableChip(
                        label = w,
                        selected = weather == w,
                        onClick = {
                            weather = w
                            currentOutfit = onGenerate(w)
                        }
                    )
                }
            }
        }

        Text(
            text = "Outfit Consigliato",
            style = MaterialTheme.typography.titleMedium,
            color = Mint,
            fontWeight = FontWeight.SemiBold
        )

        if (currentOutfit.isEmpty()) {
            Text("Aggiungi capi nell'armadio per generare un outfit completo.", color = TextSecondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(currentOutfit) { item ->
                    ProgressCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (item.category) {
                                    "Top" -> Icons.Default.Checkroom
                                    "Bottom" -> Icons.Default.DryCleaning
                                    "Shoes" -> Icons.AutoMirrored.Filled.DirectionsWalk
                                    else -> Icons.Default.AccessibilityNew
                                },
                                contentDescription = null,
                                tint = Mint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${item.category} • Colore: ${item.color}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onSaveOutfit("Outfit $weather", currentOutfit) },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Salva questo Outfit tra i Preferiti", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ClothesListSection(
    clothes: List<ClothingItem>,
    onDelete: (String) -> Unit
) {
    if (clothes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("L'armadio è vuoto.\nTocca + per aggiungere i tuoi vestiti!", color = TextSecondary)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(clothes, key = { it.id }) { item ->
                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Categoria: ${item.category} | Colore: ${item.color}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            if (item.seasons.isNotEmpty()) {
                                Text("Stagioni: ${item.seasons.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                            }
                        }
                        IconButton(onClick = { onDelete(item.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WishlistSection(
    wishlist: List<com.example.myapplication.data.model.WishlistItem>,
    onDelete: (String) -> Unit
) {
    if (wishlist.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nessun articolo nella wishlist.\nTocca + per aggiungerne uno!", color = TextSecondary)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(wishlist, key = { it.id }) { item ->
                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            if (item.description.isNotBlank()) {
                                Text(item.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Text("Prezzo stimato: €${String.format(java.util.Locale.US, "%.2f", item.price)}", style = MaterialTheme.typography.bodySmall, color = Mint)
                        }
                        IconButton(onClick = { onDelete(item.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuitcasesSection(
    suitcases: List<com.example.myapplication.data.model.SuitcaseList>,
    clothes: List<ClothingItem>,
    onToggleItem: (String, String) -> Unit
) {
    if (suitcases.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nessuna lista valigie creata.\nTocca l'icona valigia per crearne una!", color = TextSecondary)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(suitcases, key = { it.id }) { s ->
                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Destinazione: ${s.destination}", style = MaterialTheme.typography.bodySmall, color = Mint)
                    Spacer(Modifier.height(8.dp))
                    Text("Capi da mettere in valigia:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                    s.itemIds.forEach { itemId ->
                        val item = clothes.find { it.id == itemId }
                        val isPacked = s.packed[itemId] ?: false
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleItem(s.id, itemId) }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isPacked,
                                onCheckedChange = { onToggleItem(s.id, itemId) },
                                colors = CheckboxDefaults.colors(checkedColor = Mint)
                            )
                            Text(
                                text = item?.name ?: "Capo $itemId",
                                color = if (isPacked) TextTertiary else TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddClothingDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Top") }
    var color by remember { mutableStateOf("Nero") }
    var selectedSeasons by remember { mutableStateOf(setOf("Primavera", "Estate")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nuovo Capo", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome capo (es. Felpa)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Categoria:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Top", "Bottom", "Shoes", "Outerwear").forEach { cat ->
                        SelectableChip(label = cat, selected = category == cat, onClick = { category = cat })
                    }
                }
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Colore") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), category, color.trim(), selectedSeasons.toList()) },
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

@Composable
fun AddWishlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Aggiungi a Wishlist", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Articolo") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Note / Taglia / Link") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Prezzo stimato (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), desc.trim(), priceText.toFloatOrNull() ?: 0f)
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

@Composable
fun AddSuitcaseDialog(
    clothes: List<ClothingItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("Viaggio") }
    var dest by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nuova Valigia", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome lista (es. Mare 2026)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dest,
                    onValueChange = { dest = it },
                    label = { Text("Destinazione") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Seleziona capi da includere:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                clothes.take(6).forEach { c ->
                    val isSel = selectedIds.contains(c.id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (isSel) selectedIds - c.id else selectedIds + c.id
                            }
                    ) {
                        Checkbox(
                            checked = isSel,
                            onCheckedChange = {
                                selectedIds = if (isSel) selectedIds - c.id else selectedIds + c.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Mint)
                        )
                        Text(c.name, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), dest.trim(), selectedIds.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Crea")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = TextSecondary) }
        }
    )
}
