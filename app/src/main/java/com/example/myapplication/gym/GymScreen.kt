package com.example.myapplication.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.WorkoutExercise
import com.example.myapplication.data.model.WorkoutSet
import com.example.myapplication.data.model.WorkoutTemplate
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.components.SelectableChip
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun GymScreen(
    onBack: () -> Unit,
    viewModel: GymViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Schede & Sessione, 1: Storico & PR

    var activeWorkoutTemplate by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var showNewTemplateDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (activeWorkoutTemplate == null && selectedTab == 0) {
                ProgressFab(
                    onClick = { showNewTemplateDialog = true },
                    icon = Icons.Default.Add,
                    contentDescription = "Nuova Scheda"
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
                categoryLabel = "OpenGym Workout",
                title = "Palestra",
                subtitle = "Allenamenti, carichi e recupero su misura per te",
                onBack = {
                    if (activeWorkoutTemplate != null) {
                        activeWorkoutTemplate = null
                    } else {
                        onBack()
                    }
                }
            )

            if (activeWorkoutTemplate != null) {
                ActiveWorkoutSession(
                    template = activeWorkoutTemplate!!,
                    onFinish = { duration, exercises ->
                        viewModel.saveCompletedWorkout(activeWorkoutTemplate!!.name, duration, exercises)
                        activeWorkoutTemplate = null
                    },
                    onCancel = { activeWorkoutTemplate = null }
                )
            } else {
                val heightM = profile.userHeight / 100f
                val bmi = if (heightM > 0f) profile.userWeight / (heightM * heightM) else 0f

                ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dati Fisici & Parametri", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${profile.userWeight} kg • ${profile.userHeight.toInt()} cm",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "BMI: ${String.format(java.util.Locale.US, "%.1f", bmi)} • Età: ${profile.userAge} anni",
                                style = MaterialTheme.typography.bodySmall,
                                color = Mint
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = Mint,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableChip(label = "Schede (${data.templates.size})", selected = selectedTab == 0, onClick = { selectedTab = 0 })
                    SelectableChip(label = "Storico & Record (${data.workouts.size})", selected = selectedTab == 1, onClick = { selectedTab = 1 })
                }

                Spacer(Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(data.templates, key = { it.id }) { template ->
                                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(template.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                                Text("${template.exercises.size} esercizi inclusi", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                            }
                                            IconButton(onClick = { viewModel.deleteTemplate(template.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))
                                        template.exercises.forEach { ex ->
                                            Text(
                                                text = "• ${ex.exerciseName}: ${ex.sets.size} serie",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        Button(
                                            onClick = { activeWorkoutTemplate = template },
                                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Avvia Allenamento", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                                    Text("Record Personali (PR)", style = MaterialTheme.typography.titleMedium, color = Mint, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    if (data.personalRecords.isEmpty()) {
                                        Text("Nessun record registrato.", color = TextSecondary)
                                    } else {
                                        data.personalRecords.forEach { (ex, weight) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(ex, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                                Text("${weight} kg", color = Mint, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            items(data.workouts.reversed(), key = { it.id }) { w ->
                                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(w.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text("Data: ${w.date} • Durata: ${w.durationMinutes} min", style = MaterialTheme.typography.bodySmall, color = Mint)
                                    Spacer(Modifier.height(6.dp))
                                    w.exercises.forEach { ex ->
                                        val completedSets = ex.sets.filter { it.completed }
                                        Text(
                                            text = "${ex.exerciseName}: ${completedSets.size}/${ex.sets.size} serie completate",
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

    if (showNewTemplateDialog) {
        CreateTemplateDialog(
            onDismiss = { showNewTemplateDialog = false },
            onConfirm = { name, exercises ->
                viewModel.addTemplate(name, exercises)
                showNewTemplateDialog = false
            }
        )
    }
}

@Composable
fun ActiveWorkoutSession(
    template: WorkoutTemplate,
    onFinish: (Int, List<WorkoutExercise>) -> Unit,
    onCancel: () -> Unit
) {
    var exercises by remember { mutableStateOf(template.exercises) }
    var restSecondsRemaining by remember { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning, restSecondsRemaining) {
        if (isTimerRunning && restSecondsRemaining > 0) {
            delay(1000L)
            restSecondsRemaining -= 1
            if (restSecondsRemaining == 0) {
                isTimerRunning = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProgressCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Allenamento in Corso", style = MaterialTheme.typography.titleSmall, color = Mint)
                    Text(template.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                if (restSecondsRemaining > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Mint.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Riposo: ${restSecondsRemaining}s",
                            color = Mint,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(exercises.indices.toList()) { exIdx ->
                val ex = exercises[exIdx]
                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                    Text(ex.exerciseName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    ex.sets.forEachIndexed { setIdx, set ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Set ${setIdx + 1}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Text("${set.reps} rip @ ${set.weight} kg", color = TextPrimary, fontWeight = FontWeight.SemiBold)

                            IconButton(
                                onClick = {
                                    val updatedSets = ex.sets.toMutableList()
                                    val newCompleted = !set.completed
                                    updatedSets[setIdx] = set.copy(completed = newCompleted)
                                    val updatedExs = exercises.toMutableList()
                                    updatedExs[exIdx] = ex.copy(sets = updatedSets)
                                    exercises = updatedExs

                                    if (newCompleted) {
                                        restSecondsRemaining = ex.restSeconds
                                        isTimerRunning = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (set.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (set.completed) Mint else TextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("Annulla")
            }
            Button(
                onClick = { onFinish(45, exercises) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Termina", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CreateTemplateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, List<WorkoutExercise>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ex1Name by remember { mutableStateOf("Panca Piana") }
    var ex2Name by remember { mutableStateOf("Squat") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Crea Nuova Scheda", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Scheda (es. Push Day)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ex1Name,
                    onValueChange = { ex1Name = it },
                    label = { Text("Primo esercizio") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ex2Name,
                    onValueChange = { ex2Name = it },
                    label = { Text("Secondo esercizio") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val exs = listOf(
                            WorkoutExercise("e1", ex1Name, listOf(WorkoutSet(10, 50f), WorkoutSet(8, 55f), WorkoutSet(6, 60f)), 90),
                            WorkoutExercise("e2", ex2Name, listOf(WorkoutSet(10, 70f), WorkoutSet(8, 80f), WorkoutSet(6, 85f)), 90)
                        )
                        onConfirm(name.trim(), exs)
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
