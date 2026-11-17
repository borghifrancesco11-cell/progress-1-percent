package com.example.myapplication.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
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
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val settings by repository.settings.collectAsState()

    var nameState by remember(settings.userName) { mutableStateOf(settings.userName) }
    var weightState by remember(settings.userWeight) { mutableStateOf(if (settings.userWeight > 0f) settings.userWeight.toString() else "70") }
    var heightState by remember(settings.userHeight) { mutableStateOf(if (settings.userHeight > 0f) settings.userHeight.toInt().toString() else "175") }
    var ageState by remember(settings.userAge) { mutableStateOf(if (settings.userAge > 0) settings.userAge.toString() else "20") }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            ModuleHeader(
                categoryLabel = "App Configuration",
                title = "Impostazioni",
                subtitle = "Personalizza profilo, parametri fisici e moduli abilitati",
                onBack = onBack
            )
            // Profilo Utente Card
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Profilo e Parametri Fisici", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "Parametri utilizzati per il calcolo macro in Food e l'adattamento schede in Palestra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    label = { Text("Nome utente") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = weightState,
                        onValueChange = { weightState = it },
                        label = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = heightState,
                        onValueChange = { heightState = it },
                        label = { Text("Altezza (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ageState,
                    onValueChange = { ageState = it },
                    label = { Text("Età") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val updated = settings.copy(
                            userName = nameState.trim(),
                            userWeight = weightState.toFloatOrNull() ?: 70f,
                            userHeight = heightState.toFloatOrNull() ?: 175f,
                            userAge = ageState.toIntOrNull() ?: 20
                        )
                        repository.updateSettings(updated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Salva Profilo", fontWeight = FontWeight.Bold)
                }
            }

            // Gestione Moduli Attivi Card
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Moduli e Spazi Attivi", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "Attiva o disattiva i moduli visualizzati in Home. Il Tracker è obbligatorio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                val allModules = listOf(
                    "tracker" to "Tracker Abitudini (Obbligatorio)",
                    "food" to "Food & Macro AI",
                    "gym" to "Palestra (OpenGym)",
                    "school" to "School & Simulator",
                    "money" to "Money & Budget",
                    "tasks" to "Tasks & Promemoria",
                    "challenges" to "Challenges Offline",
                    "culture" to "Cultura & Audiolibri PDF",
                    "social_blocker" to "Blocco Social Media",
                    "wardrobe" to "Wardrobe & Outfit"
                )

                allModules.forEach { (id, label) ->
                    val isTracker = id == "tracker"
                    val isChecked = isTracker || settings.enabledModules.contains(id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { repository.toggleModule(id, it) },
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

            // Privacy & Sicurezza Card
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Sicurezza & Privacy Offline", style = MaterialTheme.typography.titleMedium, color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "• Dati 100% offline salvati in formato JSON locale\n• Nessun tracciamento o server esterno\n• Chiavi API nascoste e protette nel build di sistema\n• Versione: 1.0 (Progress 1%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
