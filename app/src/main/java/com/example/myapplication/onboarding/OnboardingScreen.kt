package com.example.myapplication.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
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
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Progress 1%",
                style = MaterialTheme.typography.displayMedium,
                color = Mint,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Migliora ogni giorno dell'1%",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            when (state.step) {
                0 -> WelcomeStep()
                1 -> ProfileStep(
                    name = state.name,
                    weight = state.weight,
                    height = state.height,
                    age = state.age,
                    onUpdate = viewModel::updateProfile
                )
                2 -> ModulesStep(
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Text("Indietro")
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Button(
                onClick = {
                    if (state.step < 2) {
                        viewModel.nextStep()
                    } else {
                        viewModel.finishOnboarding()
                        onFinished()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (state.step == 2) "Inizia il viaggio" else "Continua",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Benvenuto in Progress 1%",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tutto nell'app ruota attorno alla percentuale in Home: ogni giorno puoi progredire nelle varie aree della tua vita (Abitudini, Alimentazione, Palestra, Studio, Finanze, Task e Cultura).\n\nTutti i dati restano offline e privati sul tuo dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun ProfileStep(
    name: String,
    weight: String,
    height: String,
    age: String,
    onUpdate: (String, String, String, String, String) -> Unit
) {
    var nameState by remember { mutableStateOf(name) }
    var weightState by remember { mutableStateOf(weight) }
    var heightState by remember { mutableStateOf(height) }
    var ageState by remember { mutableStateOf(age) }

    ProgressCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Il tuo Profilo",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Questi parametri aiutano a calcolare i macro nutrizionali e a personalizzare gli allenamenti della Palestra.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        OutlinedTextField(
            value = nameState,
            onValueChange = {
                nameState = it
                onUpdate(nameState, weightState, heightState, ageState, "M")
            },
            label = { Text("Nome o Nickname") },
            modifier = Modifier.fillMaxWidth(),
            colors = customTextFieldColors()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = weightState,
                onValueChange = {
                    weightState = it
                    onUpdate(nameState, weightState, heightState, ageState, "M")
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
                    onUpdate(nameState, weightState, heightState, ageState, "M")
                },
                label = { Text("Altezza (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = customTextFieldColors()
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = ageState,
            onValueChange = {
                ageState = it
                onUpdate(nameState, weightState, heightState, ageState, "M")
            },
            label = { Text("Età") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = customTextFieldColors()
        )
    }
}

@Composable
fun ModulesStep(
    selectedModules: Set<String>,
    onToggle: (String) -> Unit
) {
    Column {
        Text(
            text = "Personalizza la tua esperienza",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Puoi disattivare le funzioni che non ti interessano. Solo Tracker è obbligatorio. Potrai riattivarle in qualsiasi momento nelle impostazioni.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        val modules = listOf(
            Triple("tracker", "Tracker Abitudini (Obbligatorio)", "Goal di tipo quit e count con varie periodicità"),
            Triple("food", "Food & Macro AI", "Traccia pasti, riconoscimento immagine e calcolo macro"),
            Triple("gym", "Palestra (OpenGym)", "Allenamenti, serie, ripetizioni e carichi su misura"),
            Triple("school", "School & Simulator", "Medie scolastiche, verifiche e simulatore voti"),
            Triple("money", "Money & Budget", "Entrate, uscite e pianificazione budget mensile"),
            Triple("tasks", "Tasks & Promemoria", "Attività giornaliere con notifiche e promemoria"),
            Triple("challenges", "Challenges Offline", "Sfide offline per costruire nuove abitudini"),
            Triple("culture", "Cultura & Audiolibri PDF", "Ascolto PDF in background e quiz Sophia"),
            Triple("social_blocker", "Blocco Social Media", "Limita l'uso dei social e aumenta il punteggio"),
            Triple("wardrobe", "Wardrobe & Outfit", "Armadio, meteo outfit e valigie (non influisce su %)")
        )

        modules.forEach { (id, title, desc) ->
            val isTracker = id == "tracker"
            val isChecked = isTracker || selectedModules.contains(id)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, if (isChecked) CardBorderActive.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(text = desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
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
