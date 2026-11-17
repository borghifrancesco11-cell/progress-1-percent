package com.example.myapplication.socialblocker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
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
import com.example.myapplication.data.model.AppBlock
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.LabeledProgressBar
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.theme.*

@Composable
fun SocialBlockerScreen(
    onBack: () -> Unit,
    viewModel: SocialBlockerViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    val hasPermission = viewModel.hasUsageStatsPermission()
    var selectedAppToEdit by remember { mutableStateOf<AppBlock?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ProgressFab(
                onClick = { showAddCustomDialog = true },
                icon = Icons.Default.Add,
                contentDescription = "Aggiungi App"
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
                categoryLabel = "Digital Wellness",
                title = "Blocco Social",
                subtitle = "Imposta limiti d'uso per proteggere la tua concentrazione",
                onBack = onBack
            )
            // Permission Card se manca l'accesso a Benessere Digitale
            if (!hasPermission) {
                ProgressCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = ScoreYellow, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accesso Benessere Digitale", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                "Per monitorare automaticamente il tempo effettivo sui social media, autorizza l'accesso nelle impostazioni di sistema.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.openUsageStatsSettings() },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abilita Permesso di Utilizzo", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Card Stato Generale & Impatto Score
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Blocchi Social & Detox", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "Rispettare i limiti aumenta la tua percentuale in Home. Oltre il limite, ricevi un avviso per staccare.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = data.enabled,
                        onCheckedChange = { viewModel.toggleMasterBlock(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = Mint,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "App Monitorate Oggi",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(data.blockedApps, key = { it.packageName }) { app ->
                    val usedMinutes = viewModel.getUsageMinutesToday(app.packageName)
                    val isExceeded = usedMinutes > app.dailyLimitMinutes

                    ProgressCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isExceeded && app.enabled) {
                                            Text(
                                                text = " (Limite Superato!)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ScoreRed,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Oggi: $usedMinutes min / Limite: ${app.dailyLimitMinutes} min",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isExceeded && app.enabled) ScoreRed else TextSecondary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { selectedAppToEdit = app }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Modifica limite", tint = Mint)
                                    }
                                    Switch(
                                        checked = app.enabled,
                                        onCheckedChange = { viewModel.toggleAppBlock(app.packageName) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = DarkBackground,
                                            checkedTrackColor = Mint,
                                            uncheckedThumbColor = TextTertiary,
                                            uncheckedTrackColor = DarkSurfaceVariant
                                        )
                                    )
                                }
                            }

                            if (app.enabled) {
                                Spacer(Modifier.height(8.dp))
                                LabeledProgressBar(
                                    label = "Tempo utilizzato",
                                    value = usedMinutes.toFloat(),
                                    maxValue = if (app.dailyLimitMinutes > 0) app.dailyLimitMinutes.toFloat() else 1f,
                                    color = if (isExceeded) ScoreRed else Mint
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedAppToEdit?.let { app ->
        EditLimitDialog(
            app = app,
            onDismiss = { selectedAppToEdit = null },
            onConfirm = { newLimit ->
                viewModel.updateLimit(app.packageName, newLimit)
                selectedAppToEdit = null
            }
        )
    }

    if (showAddCustomDialog) {
        AddCustomAppDialog(
            onDismiss = { showAddCustomDialog = false },
            onConfirm = { pkg, name, limit ->
                viewModel.addCustomApp(pkg, name, limit)
                showAddCustomDialog = false
            }
        )
    }
}

@Composable
fun EditLimitDialog(
    app: AppBlock,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var limitText by remember { mutableStateOf(app.dailyLimitMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Imposta Limite per ${app.appName}", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                label = { Text("Limite giornaliero (minuti)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = customTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val lim = limitText.toIntOrNull() ?: 30
                    onConfirm(lim)
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
fun AddCustomAppDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var pkg by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Aggiungi App da Bloccare", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome App (es. YouTube)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Package Name (es. com.google.android.youtube)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Limite giornaliero (minuti)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pkg.isNotBlank() && name.isNotBlank()) {
                        val lim = limitText.toIntOrNull() ?: 30
                        onConfirm(pkg.trim(), name.trim(), lim)
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
