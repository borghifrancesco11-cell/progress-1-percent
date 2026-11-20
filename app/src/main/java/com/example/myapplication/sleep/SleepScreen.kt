package com.example.myapplication.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.SleepEntry
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.CircularProgress
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class SleepDayBar(
    val label: String,
    val minutes: Int,
    val isToday: Boolean,
)

@Composable
fun SleepScreen(
    onBack: () -> Unit,
    viewModel: SleepViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val today = SleepViewModel.todayIso()
    val heroEntry = data.entries.firstOrNull { it.date == today }
        ?: data.entries.maxByOrNull { it.date }
    val target = if (data.targetHours > 0f) data.targetHours else 8f
    val heroMinutes = heroEntry?.durationMinutes ?: 0
    val heroPercentage = (heroMinutes / 60f / target * 100f).coerceIn(0f, 100f)

    val average7 = viewModel.lastSevenAverageMinutes()
    val averageQuality = viewModel.averageQuality()
    val weekDays = buildWeekDays(data.entries)
    val history = data.entries.sortedByDescending { it.date }

    Scaffold(
        floatingActionButton = {
            ProgressFab(
                onClick = { showAddDialog = true },
                icon = Icons.Default.Add,
                contentDescription = "Registra sonno"
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
                categoryLabel = "Sleep & Recovery",
                title = "Sonno",
                subtitle = "Traccia le tue notti e il recupero fisico",
                onBack = onBack
            )

            // ── Ultima notte ──
            ProgressCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (data.entries.any { it.date == today }) "ULTIMA NOTTE" else "ULTIMA NOTTE REGISTRATA",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mint,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (heroMinutes > 0) SleepViewModel.formatDuration(heroMinutes) else "--",
                            style = MaterialTheme.typography.displayMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = heroEntry?.let { "${it.bedtime} → ${it.wakeTime}" } ?: "Nessuna notte registrata",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = qualityStars(heroEntry?.quality ?: 0),
                                color = ScoreYellow,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Obiettivo ${if (target == target.toInt().toFloat()) "${target.toInt()}h" else "${target}h"}",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    CircularProgress(
                        percentage = heroPercentage,
                        size = 96.dp,
                        strokeWidth = 10.dp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Statistiche rapide ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepStatCard(
                    title = "Media 7g",
                    value = if (average7 > 0) SleepViewModel.formatDuration(average7) else "--",
                    modifier = Modifier.weight(1f)
                )
                SleepStatCard(
                    title = "Qualità",
                    value = if (averageQuality > 0f) "${"%.1f".format(averageQuality)}/5" else "--",
                    modifier = Modifier.weight(1f)
                )
                SleepStatCard(
                    title = "Notti",
                    value = "${data.entries.size}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Grafico ultimi 7 giorni ──
            ProgressCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Ultimi 7 giorni",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    weekDays.forEach { day ->
                        SleepDayColumn(day = day, targetHours = target)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "STORICO NOTTI",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )

            Spacer(Modifier.height(8.dp))

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna notte registrata.\nTocca + per aggiungere la prima!",
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(history, key = { it.id }) { entry ->
                        SleepRow(entry = entry, onDelete = { viewModel.deleteEntry(entry.id) })
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSleepDialog(
            defaultDate = today,
            onDismiss = { showAddDialog = false },
            onConfirm = { date, bed, wake, quality, note ->
                viewModel.addEntry(bed, wake, quality, note, date)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SleepStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ProgressCard(modifier = modifier, cornerRadius = 18.dp) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SleepDayColumn(day: SleepDayBar, targetHours: Float) {
    val ratio = (day.minutes / 60f / targetHours).coerceIn(0f, 1f)
    Column(
        modifier = Modifier.width(38.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (day.minutes > 0) SleepViewModel.formatDuration(day.minutes) else "-",
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isToday) Mint else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.height(72.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight(ratio.coerceAtLeast(0.05f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            day.minutes <= 0 -> DarkSurfaceLight
                            day.isToday -> Mint
                            else -> Mint.copy(alpha = 0.35f)
                        }
                    )
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isToday) Mint else TextTertiary,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SleepRow(
    entry: SleepEntry,
    onDelete: () -> Unit,
) {
    ProgressCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDate(entry.date),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = SleepViewModel.formatDuration(entry.durationMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mint,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${entry.bedtime} → ${entry.wakeTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
            Text(
                text = qualityStars(entry.quality),
                color = ScoreYellow,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
            }
        }
    }
}

@Composable
private fun AddSleepDialog(
    defaultDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int, String) -> Unit,
) {
    var date by remember { mutableStateOf(defaultDate) }
    var bedtime by remember { mutableStateOf("23:00") }
    var wakeTime by remember { mutableStateOf("07:00") }
    var quality by remember { mutableStateOf(4) }
    var note by remember { mutableStateOf("") }

    val duration = SleepViewModel.durationBetween(bedtime, wakeTime)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Registra notte", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Data risveglio (YYYY-MM-DD)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = bedtime,
                        onValueChange = { bedtime = it },
                        label = { Text("Addormentamento") },
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = wakeTime,
                        onValueChange = { wakeTime = it },
                        label = { Text("Risveglio") },
                        colors = customTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (duration > 0) "Durata stimata: ${SleepViewModel.formatDuration(duration)}" else "Orario non valido (usa HH:mm)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (duration > 0) Mint else ScoreRed,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Qualità", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(10.dp))
                    (1..5).forEach { star ->
                        Text(
                            text = if (star <= quality) "★" else "☆",
                            color = if (star <= quality) ScoreYellow else TextTertiary,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { quality = star }
                                .padding(horizontal = 3.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (opzionale)") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (duration > 0 && date.isNotBlank()) {
                        onConfirm(date.trim(), bedtime.trim(), wakeTime.trim(), quality, note)
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

private fun qualityStars(quality: Int): String {
    val q = quality.coerceIn(0, 5)
    return "★".repeat(q) + "☆".repeat(5 - q)
}

private fun formatDate(isoDate: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatter = SimpleDateFormat("EEE d MMM", Locale.ITALIAN)
        val parsed = parser.parse(isoDate)
        if (parsed != null) formatter.format(parsed).replaceFirstChar { it.uppercase() } else isoDate
    } catch (_: Exception) {
        isoDate
    }
}

private fun buildWeekDays(entries: List<SleepEntry>): List<SleepDayBar> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayFmt = SimpleDateFormat("EEE", Locale.ITALIAN)
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -6)
    return (0 until 7).map {
        val date = fmt.format(cal.time)
        val entry = entries.firstOrNull { it.date == date }
        val label = dayFmt.format(cal.time).take(1).uppercase()
        val isToday = it == 6
        cal.add(Calendar.DAY_OF_YEAR, 1)
        SleepDayBar(
            label = label,
            minutes = entry?.durationMinutes ?: 0,
            isToday = isToday
        )
    }
}
