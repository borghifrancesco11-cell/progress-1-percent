package com.example.myapplication.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.theme.*

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Step Indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (state.step == index) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(if (state.step == index) Mint else CardBorderActive)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            when (state.step) {
                0 -> WelcomeStep()
                1 -> ProfileStep(
                    name = state.name,
                    weight = state.weight,
                    height = state.height,
                    onUpdate = viewModel::updateProfile
                )
                2 -> WorkoutTypeStep(
                    selectedType = state.workoutType,
                    onSelect = viewModel::setWorkoutType
                )
                3 -> ModulesStep(
                    selectedModules = state.selectedModules,
                    onToggle = viewModel::toggleModule
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.step > 0) {
                OutlinedButton(
                    onClick = { viewModel.prevStep() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Indietro")
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = {
                    if (state.step < 3) {
                        viewModel.nextStep()
                    } else {
                        viewModel.finishOnboarding()
                        onFinished()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (state.step == 3) "Inizia il Viaggio 🚀" else "Continua →",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Mint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text("1%", color = Mint, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Progress 1%",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Migliora ogni giorno dell'1%",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        )

        ProgressCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FeaturePoint(
                    icon = Icons.Default.AllInclusive,
                    title = "Principio Kaizen",
                    desc = "La percentuale in Home riassume la tua costanza giornaliera"
                )
                FeaturePoint(
                    icon = Icons.Default.CloudOff,
                    title = "100% Offline & Privato",
                    desc = "Tutti i dati restano protetti esclusivamente sul tuo dispositivo"
                )
                FeaturePoint(
                    icon = Icons.Default.Tune,
                    title = "Modulare & Su Misura",
                    desc = "Attiva solo gli spazi che utilizzi realmente"
                )
            }
        }
    }
}

@Composable
fun FeaturePoint(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun ProfileStep(
    name: String,
    weight: String,
    height: String,
    onUpdate: (String, String, String, String, String) -> Unit
) {
    var nameState by remember { mutableStateOf(name) }
    var weightState by remember { mutableStateOf(weight) }
    var heightState by remember { mutableStateOf(height) }

    Column {
        Text(
            text = "Il tuo Profilo",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Servono per personalizzare i macro e l'intensità degli allenamenti.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = nameState,
            onValueChange = {
                nameState = it
                onUpdate(nameState, weightState, heightState, "20", "M")
            },
            label = { Text("Nome utente") },
            colors = customTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = weightState,
                onValueChange = {
                    weightState = it
                    onUpdate(nameState, weightState, heightState, "20", "M")
                },
                label = { Text("Peso (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = customTextFieldColors()
            )
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedTextField(
                value = heightState,
                onValueChange = {
                    heightState = it
                    onUpdate(nameState, weightState, heightState, "20", "M")
                },
                label = { Text("Altezza (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = customTextFieldColors()
            )
        }
    }
}

@Composable
fun WorkoutTypeStep(
    selectedType: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            text = "La tua Disciplina",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Scegli il tuo stile di allenamento preferito.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        val options = listOf(
            Triple("palestra", "🏋️ Palestra (Pesi & Macchinari)", "Panca piana, squat, carichi progressivi e massimali PR"),
            Triple("calisthenics", "🤸 Calisthenics (Corpo Libero)", "Trazioni, dip, piegamenti, skills alla sbarra e core"),
            Triple("hybrid", "⚡ Ibrido (Pesi + Corpo Libero)", "Combina trazioni zavorrate, bilancieri e lavoro funzionale")
        )

        options.forEach { (typeKey, title, desc) ->
            val isSelected = selectedType == typeKey
            ProgressCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onSelect(typeKey) },
                cornerRadius = 16.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(typeKey) },
                        colors = RadioButtonDefaults.colors(selectedColor = Mint)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(desc, color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ModulesStep(
    selectedModules: Set<String>,
    onToggle: (String) -> Unit
) {
    Column {
        Text(
            text = "Moduli Attivi",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Attiva le funzioni che desideri. Tracker è obbligatorio.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        val modules = listOf(
            Pair("tracker", "🎯 Tracker (Obbligatorio)"),
            Pair("food", "🥗 Food & Macro AI"),
            Pair("gym", "🏋️ Palestra / Calisthenics"),
            Pair("school", "📚 School & Simulatore"),
            Pair("money", "💰 Money & Budget"),
            Pair("tasks", "📝 Tasks & Promemoria"),
            Pair("challenges", "🏆 Challenges Offline"),
            Pair("social_blocker", "📵 Blocco Social Media"),
            Pair("wardrobe", "👔 Wardrobe & Outfit")
        )

        modules.forEach { (id, title) ->
            val isTracker = id == "tracker"
            val isChecked = isTracker || selectedModules.contains(id)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, if (isChecked) CardBorderActive else CardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                Switch(
                    checked = isChecked,
                    onCheckedChange = { onToggle(id) },
                    enabled = !isTracker,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = Mint,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Mint,
    unfocusedBorderColor = CardBorder,
    focusedLabelColor = Mint,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Mint
)
