package com.progressone.nativeapp.ui

import android.net.Uri
import android.Manifest
import android.os.Build
import com.progressone.nativeapp.reminders.ReminderScheduler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.InputValidation
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.ModulePreferences
import com.progressone.nativeapp.data.moduleSelected
import com.progressone.nativeapp.data.moduleEnabled
import com.progressone.nativeapp.data.setModuleEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(store: LocalStore, onBack: () -> Unit, onReplayTutorial: () -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var importConfirmation by remember { mutableStateOf(false) }
    val uiRevision = store.revision
    var profileName by remember(uiRevision) { mutableStateOf(store.setting("user_name", "")) }
    var profileWeight by remember(uiRevision) { mutableStateOf(store.setting("user_weight_kg", "")) }
    var profileHeight by remember(uiRevision) { mutableStateOf(store.setting("user_height_cm", "")) }
    var profileAge by remember(uiRevision) { mutableStateOf(store.setting("user_age", "")) }
    var profileError by remember { mutableStateOf("") }
    val currentTheme = remember(uiRevision) {
        ThemeCatalog.find(store.setting("ui_theme", "oled")).key
    }
    var reducedMotion by remember(store.revision) { mutableStateOf(store.setting("reduce_motion", "false") == "true") }
    val notificationsEnabled = remember(uiRevision) { store.setting("notifications_enabled", "true") == "true" }
    var notificationPermissionGranted by remember { mutableStateOf(ReminderScheduler.hasPermission(ctx)) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
        if (granted) {
            ReminderScheduler.deliverDue(ctx, store)
            ReminderScheduler.reschedule(ctx, store)
            message = "Tracker reminders enabled. Timing may vary with Android battery management."
        } else message = "Notification permission denied. Enable it from Android app settings."
    }


    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                        store.exportArchive(stream)
                    } ?: error("Could not write selected file")
                }
                message = "ZIP backup exported to the selected location."
            } catch (e: Exception) { message = e.message ?: "Backup export failed" }
            finally { busy = false }
        }
    }
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingImport = uri; importConfirmation = true }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        item { PageHeading("CONTROL · TRANSPARENCY", "Settings", "Everything personal stays on your phone.") }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("PROFILO & PARAMETRI FISICI", Ice)
                Text("Nome, peso e altezza vengono usati per stimare le porzioni, calcolare il BMI e personalizzare i suggerimenti.",
                    color = Mist, style = MaterialTheme.typography.bodySmall)
                FormField("Nome", profileName, {
                    profileName = it.take(40); profileError = ""
                })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormField("Peso (kg)", profileWeight, {
                        profileWeight = it.take(8); profileError = ""
                    }, Modifier.weight(1f))
                    FormField("Altezza (cm)", profileHeight, {
                        profileHeight = it.take(8); profileError = ""
                    }, Modifier.weight(1f))
                }
                FormField("Età", profileAge, {
                    profileAge = it.take(8); profileError = ""
                })
                val parsedHeight = if (profileHeight.isBlank()) null else InputValidation.parseHeightCm(profileHeight)
                val parsedWeight = if (profileWeight.isBlank()) null else InputValidation.parseWeightKg(profileWeight)
                val parsedAge = if (profileAge.isBlank()) null else InputValidation.parseAgeYears(profileAge)
                if (parsedHeight != null && parsedWeight != null && parsedHeight > 0 && parsedWeight > 0) {
                    val bmi = parsedWeight / ((parsedHeight / 100.0) * (parsedHeight / 100.0))
                    Text("BMI ${"%.1f".format(java.util.Locale.US, bmi)}", color = Mint,
                        style = MaterialTheme.typography.titleSmall)
                }
                if (profileError.isNotBlank()) {
                    Text(profileError, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = {
                    val weight = if (profileWeight.isBlank()) null else InputValidation.parseWeightKg(profileWeight)
                    val height = if (profileHeight.isBlank()) null else InputValidation.parseHeightCm(profileHeight)
                    val age = if (profileAge.isBlank()) null else InputValidation.parseAgeYears(profileAge)
                    val error = when {
                        profileWeight.isNotBlank() && weight == null ->
                            "Weight must be between ${InputValidation.MIN_WEIGHT_KG.toInt()} and ${InputValidation.MAX_WEIGHT_KG.toInt()} kg."
                        profileHeight.isNotBlank() && height == null ->
                            "Height must be between ${InputValidation.MIN_HEIGHT_CM.toInt()} and ${InputValidation.MAX_HEIGHT_CM.toInt()} cm."
                        profileAge.isNotBlank() && age == null ->
                            "Age must be between ${InputValidation.MIN_AGE_YEARS.toInt()} and ${InputValidation.MAX_AGE_YEARS.toInt()} years."
                        else -> ""
                    }
                    if (error.isNotBlank()) {
                        profileError = error
                    } else {
                        store.setSetting("user_name", profileName.trim())
                        store.setSetting("user_weight_kg", weight?.toString().orEmpty())
                        store.setSetting("user_height_cm", height?.toString().orEmpty())
                        store.setSetting("user_age", age?.toString().orEmpty())
                        profileError = ""
                        message = "Profilo salvato."
                    }
                }, Modifier.fillMaxWidth()) { Text("Save profile") }
            }
        }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("DATA & PRIVACY", Mint)
                Text(tr("Local database and photos, no account, no ads. ML Kit may send diagnostic telemetry; photos and AI inference stay on your device."), color = Mist)
                Text(tr("You are responsible for exporting a backup before uninstalling or changing devices."), color = Mist)
                Button(onClick = { saveBackup.launch("progress1-native-backup.zip") },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(tr("Export ZIP backup"))
                }
                OutlinedButton(onClick = { openBackup.launch(arrayOf("application/zip", "application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Icon(Icons.Rounded.FileUpload, null); Spacer(Modifier.width(8.dp)); Text(tr("Import backup / migrate"))
                }
                Text(tr("Imports Progress 1% v5 {progressiOne, progressiLocal}, standalone tracker exports, native JSON backups, and streaming ZIP archives."),
                    style = MaterialTheme.typography.bodySmall, color = Mist)
            }
        }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("APPEARANCE")
                SectionHeading("Theme", "OLED Dark or Light. Mint is the accent.")
                ThemeCatalog.themes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { item ->
                            val selected = currentTheme == item.key
                            Column(
                                modifier = Modifier.weight(1f)
                                    .border(if (selected) 2.dp else 1.dp,
                                        if (selected) item.accent else item.border,
                                        RoundedCornerShape(20.dp))
                                    .clickable { store.setSetting("ui_theme", item.key) }
                                    .padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(Modifier.fillMaxWidth().height(49.dp)
                                    .background(item.background, RoundedCornerShape(13.dp)),
                                    contentAlignment = Alignment.CenterStart) {
                                    Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Box(Modifier.size(17.dp).background(item.accent, RoundedCornerShape(50)))
                                        Box(Modifier.size(64.dp, 17.dp).background(item.surface, RoundedCornerShape(8.dp)))
                                    }
                                }
                                Text(item.title, style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                                if (selected) Text(tr("✓ Selected"), color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                SectionDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Reduce motion"))
                        Text(tr("Disables page transitions and swipe animations where possible."),
                            style = MaterialTheme.typography.bodySmall, color = Mist)
                    }
                    Switch(checked = reducedMotion, onCheckedChange = {
                        reducedMotion = it
                        store.setSetting("reduce_motion", it.toString())
                    })
                }
            }
        }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("YOUR MODULES · FREE", Mint)
                SectionHeading("Modules", "Show only what you need")
                Text("Turning a module off hides it from the app and Home. It never deletes saved data or photos.",
                    color = Mist, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        ModulePreferences.all.filter { it.id != ModulePreferences.TRACK && it.id != "today" }
                            .forEach { store.setModuleEnabled(it.id, false) }
                    }, modifier = Modifier.weight(1f)) { Text("Tracker only") }
                    OutlinedButton(onClick = {
                        ModulePreferences.all.filter { it.id != ModulePreferences.TRACK && it.id != "today" }
                            .forEach { store.setModuleEnabled(it.id, true) }
                    }, modifier = Modifier.weight(1f)) { Text("Enable all") }
                }
                ModuleToggleGroup(store, ModulePreferences.main.filterNot { it.id == "today" })
            }
        }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("WARDROBE OPTIONS")
                Text("These options are available whenever Wardrobe is on. You can keep only clothes, only lists, or both.",
                    color = Mist, style = MaterialTheme.typography.bodySmall)
                ModuleToggleGroup(store, ModulePreferences.wardrobe)
            }
        }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("NOTIFICATIONS")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Tracker and task notifications"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Local Android reminders for trackers and tasks with a scheduled time."),
                            color = Mist, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = notificationsEnabled, onCheckedChange = {
                        store.setSetting("notifications_enabled", it.toString())
                        if (it && !ReminderScheduler.hasPermission(ctx) && Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    })
                }
                if (notificationsEnabled && !notificationPermissionGranted && Build.VERSION.SDK_INT >= 33) {
                    Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(tr("Allow notifications"))
                    }
                } else if (notificationsEnabled) {
                    Text(tr("Reminders can arrive while the app is closed. Android may delay delivery; exact times are not guaranteed."),
                        color = Mist, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("GETTING STARTED")
                Text("Review the welcome tutorial and learn how to customize your modules.", color = Mist)
                OutlinedButton(onClick = onReplayTutorial, Modifier.fillMaxWidth()) { Text("Replay first-run tutorial") }
            }
        }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("ABOUT PROGRESS 1%")
                Text("Progress 1%", style = MaterialTheme.typography.titleMedium)
                Text("Created by Francesco Borghi", color = Mint)
                Text(tr("Built with Kotlin, Jetpack Compose and SQLite. No HTML UI, JavaScript or embedded WebView."), color = Mist)
                Text(tr("Garment backgrounds are automatically removed on-device using Google Play services ML Kit. An initial model download may be needed."), color = Mist)
                Text(tr("Challenges work offline. Evidence-backed insights are stored locally; links open in your browser only if you tap them."), color = Mist)
            }
        }
        if (message.isNotBlank()) item {
            FrostCard(Modifier.fillMaxWidth()) { Text(message, color = Mint) }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { TextButton(onClick = onBack) { Text(tr("Back to app")) } }
    }
    if (importConfirmation && pendingImport != null) {
        AlertDialog(onDismissRequest = { importConfirmation = false },
            title = { Text(tr("Import this backup?")) },
            text = { Text(tr("The backup replaces the current local records. Export a copy first if needed. Photos from your backup will be restored.")) },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImport!!
                    importConfirmation = false
                    scope.launch {
                        busy = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                    store.importBackupOrLegacy(stream)
                                } ?: error("Cannot read file")
                            }
                            message = result
                        } catch (e: Exception) { message = "Import failed: ${e.message}" }
                        finally { busy = false; pendingImport = null }
                    }
                }) { Text(tr("Import & replace")) }
            },
            dismissButton = { TextButton(onClick = { importConfirmation = false; pendingImport = null }) {
                Text(tr("Cancel"))
            } })
    }
}

/** A disabled parent hides children but never resets their individual saved choices. */
@Composable
private fun ModuleToggleGroup(store: LocalStore, modules: List<com.progressone.nativeapp.data.AppModule>) {
    modules.forEach { module ->
        val permanent = module.id == ModulePreferences.TRACK
        val selected = store.moduleSelected(module.id)
        val effective = store.moduleEnabled(module.id)
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(module.title, style = MaterialTheme.typography.titleSmall)
                Text(module.description + if (selected && !effective) " · Parent module off" else "",
                    color = Mist, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = selected, enabled = !permanent,
                onCheckedChange = { store.setModuleEnabled(module.id, it) })
        }
    }
}
