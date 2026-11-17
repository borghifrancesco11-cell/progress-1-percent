package com.example.myapplication.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.GoalPeriod
import com.example.myapplication.data.model.GoalType
import com.example.myapplication.data.model.TrackerGoal
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.components.SelectableChip
import com.example.myapplication.ui.theme.*

@Composable
fun TrackerScreen(
    onBack: () -> Unit,
    viewModel: TrackerViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    var selectedPeriod by remember { mutableStateOf(GoalPeriod.WEEK) }
    var showAddDialog by remember { mutableStateOf(false) }
    val score = viewModel.calculateScore()
    val today = viewModel.getTodayDate()

    Scaffold(
        floatingActionButton = {
            ProgressFab(
                onClick = { showAddDialog = true },
                icon = Icons.Default.Add,
                contentDescription = "Nuovo Goal"
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
                categoryLabel = "Habit & Goal Tracker",
                title = "Tracker",
                subtitle = "Costruisci nuove abitudini ed elimina quelle indesiderate",
                onBack = onBack
            )

            // Scheda Score separato del Tracker
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
                        Text(
                            text = "Score Tracker Dedicato",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${score.toInt()}%",
                            style = MaterialTheme.typography.displayMedium,
                            color = Mint,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Punteggio indipendente basato sulla costanza nei tuoi goal",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Filtro periodicità minimal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableChip(
                    label = "Settimana",
                    selected = selectedPeriod == GoalPeriod.WEEK,
                    onClick = { selectedPeriod = GoalPeriod.WEEK }
                )
                SelectableChip(
                    label = "Mese",
                    selected = selectedPeriod == GoalPeriod.MONTH,
                    onClick = { selectedPeriod = GoalPeriod.MONTH }
                )
                SelectableChip(
                    label = "Anno",
                    selected = selectedPeriod == GoalPeriod.YEAR,
                    onClick = { selectedPeriod = GoalPeriod.YEAR }
                )
            }

            Spacer(Modifier.height(8.dp))

            val filteredGoals = data.goals.filter { it.period == selectedPeriod }

            if (filteredGoals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun goal impostato per questo periodo.\nTocca + per crearne uno!",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredGoals, key = { it.id }) { goal ->
                        val entry = data.entries.find { it.goalId == goal.id && it.date == today }
                        GoalCard(
                            goal = goal,
                            currentCount = entry?.count ?: 0,
                            onIncrement = { viewModel.incrementCount(goal.id) },
                            onLogSlip = { viewModel.logQuitSlip(goal.id) },
                            onDelete = { viewModel.deleteGoal(goal.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            defaultPeriod = selectedPeriod,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, period, target, icon ->
                viewModel.addGoal(name, type, period, target, icon)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun GoalCard(
    goal: TrackerGoal,
    currentCount: Int,
    onIncrement: () -> Unit,
    onLogSlip: () -> Unit,
    onDelete: () -> Unit
) {
    ProgressCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = goal.icon,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (goal.type) {
                            GoalType.QUIT -> "Abitudine da eliminare (Quit)"
                            GoalType.COUNT -> "Progresso oggi: $currentCount / ${goal.targetCount}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (goal.type == GoalType.QUIT && currentCount > 0) ScoreRed else TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (goal.type == GoalType.QUIT) {
                    IconButton(onClick = onLogSlip) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Ho ceduto",
                            tint = ScoreRed
                        )
                    }
                } else {
                    IconButton(onClick = onIncrement) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "+1",
                            tint = Mint
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina",
                        tint = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun AddGoalDialog(
    defaultPeriod: GoalPeriod,
    onDismiss: () -> Unit,
    onConfirm: (String, GoalType, GoalPeriod, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GoalType.COUNT) }
    var period by remember { mutableStateOf(defaultPeriod) }
    var targetText by remember { mutableStateOf("1") }
    var icon by remember { mutableStateOf("🎯") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Text("Nuovo Goal", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome obiettivo") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tipo:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip(
                        label = "Count (Costruisci)",
                        selected = type == GoalType.COUNT,
                        onClick = { type = GoalType.COUNT }
                    )
                    SelectableChip(
                        label = "Quit (Elimina)",
                        selected = type == GoalType.QUIT,
                        onClick = { type = GoalType.QUIT }
                    )
                }

                if (type == GoalType.COUNT) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        label = { Text("Target giornaliero") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Periodicità:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip(
                        label = "Settimana",
                        selected = period == GoalPeriod.WEEK,
                        onClick = { period = GoalPeriod.WEEK }
                    )
                    SelectableChip(
                        label = "Mese",
                        selected = period == GoalPeriod.MONTH,
                        onClick = { period = GoalPeriod.MONTH }
                    )
                    SelectableChip(
                        label = "Anno",
                        selected = period == GoalPeriod.YEAR,
                        onClick = { period = GoalPeriod.YEAR }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val target = targetText.toIntOrNull() ?: 1
                        onConfirm(name.trim(), type, period, target, icon)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = TextSecondary)
            }
        }
    )
}
