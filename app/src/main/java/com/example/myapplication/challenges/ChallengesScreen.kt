package com.example.myapplication.challenges

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.Challenge
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.LabeledProgressBar
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.components.SelectableChip
import com.example.myapplication.ui.theme.*

@Composable
fun ChallengesScreen(
    onBack: () -> Unit,
    viewModel: ChallengesViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Attive, 1: Disponibili, 2: Completate
    var showCustomDialog by remember { mutableStateOf(false) }

    val activeChallenges = data.challenges.filter { it.active }
    val completedChallenges = data.challenges.filter { it.completed }
    val today = viewModel.getToday()

    Scaffold(
        floatingActionButton = {
            ProgressFab(
                onClick = { showCustomDialog = true },
                icon = Icons.Default.Add,
                contentDescription = "Nuova Sfida"
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
                categoryLabel = "Personal Growth",
                title = "Challenges",
                subtitle = "Affronta sfide di costanza completamente offline",
                onBack = onBack
            )
            ProgressCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Sfide in corso", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                        Text(
                            text = "${activeChallenges.size} Attive",
                            style = MaterialTheme.typography.displayMedium,
                            color = Mint,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${completedChallenges.size} sfide completate finora",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableChip(
                    label = "In corso (${activeChallenges.size})",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                SelectableChip(
                    label = "Disponibili (${data.availableChallenges.size})",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                SelectableChip(
                    label = "Completate (${completedChallenges.size})",
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Attive
                    if (activeChallenges.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nessuna sfida attiva al momento.\nVai su 'Disponibili' per iniziare una sfida!",
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(activeChallenges, key = { it.id }) { c ->
                                val doneToday = c.completedDays.contains(today)
                                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = c.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = c.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary
                                                )
                                            }
                                            IconButton(onClick = { viewModel.abandonChallenge(c.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Abbandona", tint = TextTertiary)
                                            }
                                        }

                                        Spacer(Modifier.height(10.dp))

                                        LabeledProgressBar(
                                            label = "Progresso giorni",
                                            value = c.completedDays.size.toFloat(),
                                            maxValue = c.durationDays.toFloat()
                                        )

                                        Spacer(Modifier.height(10.dp))

                                        Button(
                                            onClick = { viewModel.checkInToday(c.id) },
                                            enabled = !doneToday,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (doneToday) DarkSurfaceVariant else Mint,
                                                contentColor = if (doneToday) TextTertiary else DarkBackground
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = if (doneToday) "Completato oggi!" else "Segna completato oggi",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Disponibili
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(data.availableChallenges, key = { it.id }) { c ->
                            ProgressCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = c.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${c.description} • Durata: ${c.durationDays} giorni",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.startChallenge(c)
                                            selectedTab = 0
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Avvia")
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Completate
                    if (completedChallenges.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nessuna sfida completata ancora.\nLa costanza ti porterà qui!", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(completedChallenges, key = { it.id }) { c ->
                                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Text(
                                            text = c.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Mint,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Completata con successo in ${c.durationDays} giorni!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        AddCustomChallengeDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { title, desc, days ->
                viewModel.addCustomChallenge(title, desc, days)
                showCustomDialog = false
            }
        )
    }
}

@Composable
fun AddCustomChallengeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var daysText by remember { mutableStateOf("7") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Crea Sfida Personalizzata", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome della sfida") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descrizione / Regole") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Durata in giorni (es. 7, 14, 30)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysText.toIntOrNull() ?: 7
                    if (title.isNotBlank()) onConfirm(title.trim(), desc.trim(), days)
                },
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
