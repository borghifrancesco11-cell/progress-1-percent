package com.example.myapplication.school

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.Subject
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.components.SelectableChip
import com.example.myapplication.ui.theme.*

@Composable
fun SchoolScreen(
    onBack: () -> Unit,
    viewModel: SchoolViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Materie, 1: Verifiche, 2: Simulatore
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddGradeDialog by remember { mutableStateOf<Subject?>(null) }
    var showAddExamDialog by remember { mutableStateOf(false) }

    val overallAvg = viewModel.calculateOverallAverage()

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                ProgressFab(
                    onClick = { showAddSubjectDialog = true },
                    icon = Icons.Default.Add,
                    contentDescription = "Aggiungi materia"
                )
            } else if (selectedTab == 1) {
                ProgressFab(
                    onClick = { showAddExamDialog = true },
                    icon = Icons.Default.CalendarMonth,
                    contentDescription = "Aggiungi verifica"
                )
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
                categoryLabel = "School & Grades",
                title = "School",
                subtitle = "Monitora le tue medie e simula i tuoi prossimi voti",
                onBack = onBack
            )

            // Media Generale Card
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
                            text = "Media Generale",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", overallAvg),
                            style = MaterialTheme.typography.displayMedium,
                            color = if (overallAvg >= data.targetAverage) Mint else ScoreYellow,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Target: ${String.format(java.util.Locale.US, "%.1f", data.targetAverage)} • ${data.subjects.sumOf { it.grades.size }} voti registrati",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tab Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableChip(
                    label = "Materie e Voti",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                SelectableChip(
                    label = "Verifiche (${data.exams.size})",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                SelectableChip(
                    label = "Simulatore",
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }

            Spacer(Modifier.height(8.dp))

            when (selectedTab) {
                0 -> {
                    if (data.subjects.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nessuna materia registrata.\nTocca + per aggiungerne una!",
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(data.subjects, key = { it.id }) { subject ->
                                val subAvg = viewModel.calculateSubjectAverage(subject)
                                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = subject.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Media: ${if (subject.grades.isNotEmpty()) String.format(java.util.Locale.US, "%.2f", subAvg) else "N/D"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (subAvg >= 6f) Mint else ScoreRed
                                            )
                                            if (subject.grades.isNotEmpty()) {
                                                Text(
                                                    text = "Ultimi voti: " + subject.grades.takeLast(4).joinToString(", ") { String.format(java.util.Locale.US, "%.1f", it.value) },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextTertiary,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                        Row {
                                            IconButton(onClick = { showAddGradeDialog = subject }) {
                                                Icon(Icons.Default.Add, contentDescription = "Aggiungi voto", tint = Mint)
                                            }
                                            IconButton(onClick = { viewModel.deleteSubject(subject.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (data.exams.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nessuna verifica programmata.\nTocca l'icona calendario per aggiungerne una!",
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(data.exams, key = { it.id }) { exam ->
                                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = exam.subjectName,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Data: ${exam.date}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Mint
                                            )
                                            if (exam.description.isNotBlank()) {
                                                Text(
                                                    text = exam.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteExam(exam.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    SimulatorSection(
                        subjects = data.subjects,
                        currentOverall = overallAvg,
                        onSimulate = viewModel::simulateGrade
                    )
                }
            }
        }
    }

    showAddGradeDialog?.let { sub ->
        AddGradeDialog(
            subjectName = sub.name,
            onDismiss = { showAddGradeDialog = null },
            onConfirm = { grade, desc ->
                viewModel.addGrade(sub.id, grade, desc)
                showAddGradeDialog = null
            }
        )
    }

    if (showAddSubjectDialog) {
        AddSubjectDialog(
            onDismiss = { showAddSubjectDialog = false },
            onConfirm = { name ->
                viewModel.addSubject(name)
                showAddSubjectDialog = false
            }
        )
    }

    if (showAddExamDialog) {
        AddExamDialog(
            subjects = data.subjects,
            onDismiss = { showAddExamDialog = false },
            onConfirm = { subId, date, desc ->
                viewModel.addExam(subId, date, desc)
                showAddExamDialog = false
            }
        )
    }
}

@Composable
fun SimulatorSection(
    subjects: List<Subject>,
    currentOverall: Float,
    onSimulate: (String, Float) -> Pair<Float, Float>
) {
    var selectedSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var simulatedGradeText by remember { mutableStateOf("8.0") }
    var simulationResult by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProgressCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Simulatore di Voti",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Inserisci un ipotetico voto per vedere l'impatto sulla media di materia e sulla media generale.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            if (subjects.isNotEmpty()) {
                Text("Seleziona Materia:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.take(4).forEach { sub ->
                        SelectableChip(
                            label = sub.name,
                            selected = selectedSubjectId == sub.id,
                            onClick = { selectedSubjectId = sub.id }
                        )
                    }
                }

                OutlinedTextField(
                    value = simulatedGradeText,
                    onValueChange = { simulatedGradeText = it },
                    label = { Text("Possibile Voto (es. 8.5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val grade = simulatedGradeText.toFloatOrNull() ?: 0f
                        if (selectedSubjectId.isNotBlank() && grade > 0f) {
                            simulationResult = onSimulate(selectedSubjectId, grade)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Calcola Impatto", fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Aggiungi prima delle materie.", color = TextSecondary)
            }
        }

        simulationResult?.let { (newSubAvg, newOverallAvg) ->
            ProgressCard(modifier = Modifier.fillMaxWidth()) {
                Text("Risultato Simulazione", style = MaterialTheme.typography.titleMedium, color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Nuova Media Materia", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", newSubAvg),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Nuova Media Generale", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", newOverallAvg),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (newOverallAvg >= currentOverall) Mint else ScoreRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddGradeDialog(
    subjectName: String,
    onDismiss: () -> Unit,
    onConfirm: (Float, String) -> Unit
) {
    var gradeText by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Aggiungi voto a $subjectName", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = gradeText,
                    onValueChange = { gradeText = it },
                    label = { Text("Voto (es. 7.5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descrizione (es. Compito in classe)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val g = gradeText.toFloatOrNull()
                    if (g != null && g > 0) onConfirm(g, desc.trim())
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
fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nuova Materia", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome materia") },
                colors = customTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
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
fun AddExamDialog(
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var selectedSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var date by remember { mutableStateOf("2026-11-30") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Nuova Verifica", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seleziona Materia:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                subjects.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSubjectId = s.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedSubjectId == s.id,
                            onClick = { selectedSubjectId = s.id },
                            colors = RadioButtonDefaults.colors(selectedColor = Mint)
                        )
                        Text(s.name, color = TextPrimary)
                    }
                }
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Data (YYYY-MM-DD)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Argomento verifica") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedSubjectId.isNotBlank()) onConfirm(selectedSubjectId, date.trim(), desc.trim()) },
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
