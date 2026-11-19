package com.example.myapplication.settings

import android.app.Application
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
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
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.GymData
import com.example.myapplication.gym.GymViewModel
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
                subtitle = "Personalizza profilo, disciplina, moduli e tema",
                onBack = onBack
            )

            // ── Tema Grafico Card ──
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Tema dell'App", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "Scegli tra nero profondo OLED (#000000) e bianco minimal ad alto contrasto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // OLED Option
                    val isOled = settings.themeMode == "oled"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isOled) Mint.copy(alpha = 0.15f) else DarkSurfaceVariant)
                            .border(1.dp, if (isOled) Mint else CardBorder, RoundedCornerShape(14.dp))
                            .clickable { repository.setThemeMode("oled") }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = if (isOled) Mint else TextSecondary, modifier = Modifier.size(18.dp))
                            Text("Nero OLED", color = if (isOled) Mint else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Light Option
                    val isLight = settings.themeMode == "light"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isLight) Mint.copy(alpha = 0.15f) else DarkSurfaceVariant)
                            .border(1.dp, if (isLight) Mint else CardBorder, RoundedCornerShape(14.dp))
                            .clickable { repository.setThemeMode("light") }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.LightMode, contentDescription = null, tint = if (isLight) Mint else TextSecondary, modifier = Modifier.size(18.dp))
                            Text("Bianco Light", color = if (isLight) Mint else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Disciplina di Allenamento Card ──
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Disciplina di Allenamento", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "Genera schede ed esercizi mirati in base alla tua disciplina preferita.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                val disciplines = listOf(
                    Triple("gym", "Palestra Pesi", "Panca piana, Squat, Manubri"),
                    Triple("calisthenics", "Calisthenics", "Trazioni, Dip, Sbarra & Skill"),
                    Triple("hybrid", "Ibrido", "Pesi + Corpo libero")
                )

                disciplines.forEach { (typeKey, title, sub) ->
                    val isSelected = settings.workoutType == typeKey
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Mint.copy(alpha = 0.12f) else DarkSurfaceVariant)
                            .border(1.dp, if (isSelected) Mint else CardBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                repository.setWorkoutType(typeKey)
                                // Rigenera schede predefinite in background
                                val gymVm = GymViewModel(context.applicationContext as Application)
                                gymVm.reloadTemplatesForType(typeKey)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) Mint else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Mint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Profilo Utente Card ──
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Profilo e Parametri Fisici", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "Utilizzati per il calcolo macro e fabbisogno calorico.",
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

            // ── Gestione Moduli Attivi Card ──
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Moduli e Spazi Attivi", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "Attiva o disattiva i moduli visualizzati in Home. Il Tracker è obbligatorio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                val allModules = listOf(
                    "tracker" to "Tracker Abitudini (Core)",
                    "food" to "Food & Macro (Vision AI)",
                    "gym" to "Allenamento (Palestra & Cali)",
                    "social_blocker" to "Blocco Social & Detox",
                    "school" to "School & Simulator",
                    "money" to "Money & Budget",
                    "tasks" to "Tasks & Promemoria",
                    "challenges" to "Sfide 1% Offline",
                    "wardrobe" to "Wardrobe & Meteo"
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

            // ── Privacy & Sicurezza Card ──
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Sicurezza & Privacy Offline", style = MaterialTheme.typography.titleMedium, color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "• Dati 100% offline salvati in formato JSON locale\n• Nessun tracciamento, zero analytics esterni\n• Modello AI Visione (Swin Transformer ONNX) 100% offline\n• Versione: 1.0 (Progress 1%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}
