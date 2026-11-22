package com.progressone.nativeapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.R
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.ModulePreferences
import com.progressone.nativeapp.data.moduleSelected
import com.progressone.nativeapp.data.setModuleEnabled

@Composable
fun FirstRunTutorial(store: LocalStore, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var heightCm by rememberSaveable { mutableStateOf("") }
    var weightKg by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("WELCOME", style = MaterialTheme.typography.labelMedium, color = Ice,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onFinish) { Text("Skip") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Progress 1%",
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 180.dp),
                contentScale = ContentScale.Fit)
            when (step) {
                0 -> {
                    Text("One percent at a time", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Your goals, one quiet space. All from Home.", color = Mist)
                }
                1 -> {
                    Text("Choose your modules", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Turn off anything you don't need. Your records are never deleted.", color = Mist)
                    ModulePreferences.main.filter { it.id !in listOf("track", "today") }.forEach { option ->
                        val revision = store.revision
                        val checked = remember(revision) { store.moduleSelected(option.id) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.title, Modifier.weight(1f))
                            Switch(checked = checked,
                                onCheckedChange = { store.setModuleEnabled(option.id, it) })
                        }
                    }
                }
                2 -> {
                    Text("Tell us about yourself", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Your height and weight help the food scanner estimate portions automatically.", color = Mist)
                    FormField("Height (cm)", heightCm, { heightCm = it.take(4) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    FormField("Weight (kg)", weightKg, { weightKg = it.take(5) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
                else -> {
                    Text("Ready to begin", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Create a goal, record progress, and see your score out of 100.", color = Mist)
                    Text("English · Light / OLED Dark · Local data", color = Ice)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) OutlinedButton(onClick = { step-- }) { Text("Back") }
            Button(onClick = {
                if (step == 2) {
                    val h = heightCm.cleanNumber()
                    val w = weightKg.cleanNumber()
                    if (h > 0) store.setSetting("user_height_cm", h.toString())
                    if (w > 0) store.setSetting("user_weight_kg", w.toString())
                    store.setSetting("onboarding_complete", "true")
                    onFinish()
                } else step++
            }, Modifier.weight(1f)) {
                Text(if (step == 2) "Open Progress 1%" else "Continue")
            }
        }
    }
}
