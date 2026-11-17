package com.example.myapplication.money

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.Transaction
import com.example.myapplication.data.model.TransactionType
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.LabeledProgressBar
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.components.SelectableChip
import com.example.myapplication.ui.theme.*

@Composable
fun MoneyScreen(
    onBack: () -> Unit,
    viewModel: MoneyViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    val todayExpenses = viewModel.getTodayExpenses()
    val monthExpenses = viewModel.getCurrentMonthExpenses()
    val monthIncome = viewModel.getCurrentMonthIncome()
    val monthBudget = viewModel.getCurrentMonthBudget()

    Scaffold(
        floatingActionButton = {
            ProgressFab(
                onClick = { showAddDialog = true },
                icon = Icons.Default.Add,
                contentDescription = "Nuova Transazione"
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
                categoryLabel = "Personal Finance",
                title = "Money",
                subtitle = "Gestisci entrate, uscite e budget mensile",
                onBack = onBack,
                actions = {
                    Button(
                        onClick = { showBudgetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = Mint),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Budget", fontSize = 13.sp)
                    }
                }
            )

            // Card Spese Oggi & Mese
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
                        Text("Spese Oggi", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "€${String.format(java.util.Locale.US, "%.2f", todayExpenses)}",
                            style = MaterialTheme.typography.displayMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Entrate Mese", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            text = "+€${String.format(java.util.Locale.US, "%.2f", monthIncome)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Mint,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Uscite Mese", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            text = "-€${String.format(java.util.Locale.US, "%.2f", monthExpenses)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = ScoreRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                LabeledProgressBar(
                    label = "Budget Mensile Speso",
                    value = monthExpenses,
                    maxValue = if (monthBudget > 0f) monthBudget else 1f,
                    color = if (monthExpenses <= monthBudget) Mint else ScoreRed
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "TRANSAZIONI RECENTI",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))

            if (data.transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nessuna transazione registrata.\nTocca + per aggiungerne una!",
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(data.transactions.reversed(), key = { it.id }) { tx ->
                        TransactionRow(
                            tx = tx,
                            onDelete = { viewModel.deleteTransaction(tx.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            categories = data.categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, type, cat, desc ->
                viewModel.addTransaction(amount, type, cat, desc)
                showAddDialog = false
            }
        )
    }

    if (showBudgetDialog) {
        SetBudgetDialog(
            currentBudget = monthBudget,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { newBudget ->
                viewModel.setMonthlyBudget(viewModel.getCurrentMonth(), newBudget)
                showBudgetDialog = false
            }
        )
    }
}

@Composable
fun TransactionRow(
    tx: Transaction,
    onDelete: () -> Unit
) {
    ProgressCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description.ifBlank { tx.category },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${tx.category} • ${tx.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (tx.type == TransactionType.INCOME) "+" else "-"}€${String.format(java.util.Locale.US, "%.2f", tx.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (tx.type == TransactionType.INCOME) Mint else ScoreRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
                }
            }
        }
    }
}

@Composable
fun AddTransactionDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Float, TransactionType, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "Altro") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nuova Transazione", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip(
                        label = "Uscita (Spesa)",
                        selected = type == TransactionType.EXPENSE,
                        onClick = { type = TransactionType.EXPENSE }
                    )
                    SelectableChip(
                        label = "Entrata",
                        selected = type == TransactionType.INCOME,
                        onClick = { type = TransactionType.INCOME }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Importo (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Categoria:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(4).forEach { cat ->
                        SelectableChip(
                            label = cat,
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descrizione (opzionale)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toFloatOrNull()
                    if (amount != null && amount > 0) {
                        onConfirm(amount, type, selectedCategory, desc.trim())
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

@Composable
fun SetBudgetDialog(
    currentBudget: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var budgetText by remember { mutableStateOf(currentBudget.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Pianifica Budget Mensile", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = it },
                label = { Text("Budget mensile (€)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = customTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val b = budgetText.toFloatOrNull()
                    if (b != null && b > 0) onConfirm(b)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Aggiorna")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = TextSecondary) }
        }
    )
}
