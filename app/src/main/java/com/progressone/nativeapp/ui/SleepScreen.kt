package com.progressone.nativeapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progressone.nativeapp.data.InputValidation
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.SleepUtils
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Sleep & Recovery — nights, quality, seven-day rhythm and recovery score. */
@Composable
fun SleepScreen(
    store: LocalStore,
    snapshot: AppSnapshot,
) {
    val entries = snapshot.sleeps
    val targetHours = remember(store.revision) {
        (store.setting("sleep_target_hours", "8").toDoubleOrNull() ?: 8.0).coerceIn(4.0, 12.0)
    }
    val lastNight = remember(entries) { SleepUtils.lastNight(entries) }
    val score = remember(entries, targetHours) { SleepUtils.score(lastNight, targetHours) }
    val average7 = remember(entries) { SleepUtils.averageMinutesLastWeek(entries) }
    val averageQuality = remember(entries) { SleepUtils.averageQuality(entries) }
    var editorEntry by remember { mutableStateOf<JSONObject?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val history = remember(entries) { entries.sortedByDescending { it.optString("date") } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeading("SLEEP & RECOVERY", "Sleep", "Track nights, quality and physical recovery")
        }

        item {
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(if (lastNight?.optString("date") == SleepUtils.todayIso()) "LAST NIGHT" else "LAST NIGHT LOGGED", Mint)
                        Text(
                            text = SleepUtils.formatDuration(lastNight?.optInt("minutes") ?: 0),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Ice
                        )
                        Text(
                            text = lastNight?.let { "${it.optString("bed")} → ${it.optString("wake")}" }
                                ?: "No night recorded yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mist
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = qualityStars(lastNight?.optInt("quality") ?: 0),
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(color = GlassLight, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "Target ${targetHours.formatHours()}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Mist
                                )
                            }
                        }
                    }
                    ProgressRing(
                        progress = score / 100f,
                        modifier = Modifier.size(104.dp),
                        caption = "recovery",
                        sizeDp = 104.dp
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat(
                    label = "7-DAY AVERAGE",
                    value = if (average7 > 0) SleepUtils.formatDuration(average7) else "--",
                    modifier = Modifier.weight(1f),
                    tint = Ice
                )
                SmallStat(
                    label = "QUALITY",
                    value = if (averageQuality > 0) "%.1f / 5".format(Locale.US, averageQuality) else "--",
                    modifier = Modifier.weight(1f),
                    tint = Mint
                )
                SmallStat(
                    label = "NIGHTS",
                    value = "${entries.size}",
                    modifier = Modifier.weight(1f),
                    tint = Lavender
                )
            }
        }

        item {
            FrostCard(Modifier.fillMaxWidth()) {
                SectionHeading("Last 7 days", "Sleep duration against your target")
                Spacer(Modifier.height(6.dp))
                val week = remember(entries) { buildSleepWeek(entries) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    week.forEach { day -> SleepBar(day = day, target = targetHours) }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SectionHeading("NIGHTS", "${entries.size} recorded")
                }
                FilledTonalButton(onClick = { editorEntry = null; showEditor = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }

        if (history.isEmpty()) {
            item {
                EmptyPanel("No nights yet", "Add last night to start building your recovery trend.") {
                    editorEntry = null
                    showEditor = true
                }
            }
        } else {
            items(history, key = { "night_${it.optString("id")}" }) { entry ->
                SleepNightCard(entry, onEdit = { editorEntry = entry; showEditor = true }, onDelete = {
                    store.remove("sleep", entry.optString("id"))
                })
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showEditor) {
        SleepEditor(
            store = store,
            original = editorEntry,
            existing = entries,
            targetHours = targetHours,
            onDismiss = { showEditor = false }
        )
    }
}

private data class SleepWeekDay(
    val label: String,
    val minutes: Int,
    val isTonight: Boolean
)

private fun buildSleepWeek(entries: List<JSONObject>): List<SleepWeekDay> {
    val labelFormat = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
    return (6 downTo 0).map { offset ->
        val date = LocalDate.now().minusDays(offset.toLong())
        val record = entries.firstOrNull { it.optString("date") == date.toString() }
        SleepWeekDay(
            label = date.format(labelFormat),
            minutes = record?.optInt("minutes") ?: 0,
            isTonight = offset == 0
        )
    }
}

@Composable
private fun SleepBar(day: SleepWeekDay, target: Double) {
    val ratio by animateFloatAsState(
        targetValue = if (target > 0) (day.minutes / 60.0 / target).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "sleepBar"
    )
    Column(
        modifier = Modifier.width(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (day.minutes > 0) SleepUtils.formatDuration(day.minutes) else "-",
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isTonight) Ice else Mist,
            fontSize = 10.sp,
            fontWeight = if (day.isTonight) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.height(72.dp), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight(ratio.coerceAtLeast(0.05f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            day.minutes <= 0 -> GlassLight
                            day.isTonight -> Ice
                            else -> Ice.copy(alpha = 0.35f)
                        }
                    )
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isTonight) Ice else Mist,
            fontWeight = if (day.isTonight) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SleepNightCard(entry: JSONObject, onEdit: () -> Unit, onDelete: () -> Unit) {
    FrostCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatNightDate(entry.optString("date")),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = SleepUtils.formatDuration(entry.optInt("minutes")),
                        style = MaterialTheme.typography.labelMedium,
                        color = Ice,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${entry.optString("bed")} → ${entry.optString("wake")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist
                )
                if (entry.optString("note").isNotBlank()) {
                    Text(
                        text = entry.optString("note"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist
                    )
                }
            }
            Text(
                text = qualityStars(entry.optInt("quality")),
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit night", tint = Mist, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete night", tint = Mist, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun SleepEditor(
    store: LocalStore,
    original: JSONObject?,
    existing: List<JSONObject>,
    targetHours: Double,
    onDismiss: () -> Unit
) {
    var date by remember(original) { mutableStateOf(original?.optString("date") ?: SleepUtils.todayIso()) }
    var bedtime by remember(original) { mutableStateOf(original?.optString("bed") ?: "23:00") }
    var wakeTime by remember(original) { mutableStateOf(original?.optString("wake") ?: "07:00") }
    var quality by remember(original) { mutableIntStateOf(original?.optInt("quality", 4) ?: 4) }
    var note by remember(original) { mutableStateOf(original?.optString("note") ?: "") }
    var targetText by remember(targetHours) { mutableStateOf(targetHours.formatHours().removeSuffix("h")) }

    val duration = remember(bedtime, wakeTime) { SleepUtils.durationBetween(bedtime, wakeTime) }
    val validDate = InputValidation.isValidDate(date)
    val validBedtime = InputValidation.isValidTime(bedtime)
    val validWakeTime = InputValidation.isValidTime(wakeTime)
    val parsedTarget = remember(targetText) {
        targetText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    BottomSheetFrame(if (original == null) "Record a night" else "Edit night", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField("Bedtime HH:mm", bedtime, { bedtime = it.take(5) }, Modifier.weight(1f))
            FormField("Wake HH:mm", wakeTime, { wakeTime = it.take(5) }, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (duration > 0 && validBedtime && validWakeTime) {
                        "Duration ${SleepUtils.formatDuration(duration)}"
                    } else {
                        "Invalid time (use HH:mm)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (duration > 0 && validBedtime && validWakeTime) Mint else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "The date is the wake-up day.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist
                )
            }
            FormField("Target h", targetText, { targetText = it.take(4) }, Modifier.width(96.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quality", style = MaterialTheme.typography.bodySmall, color = Mist)
            Spacer(Modifier.width(10.dp))
            (1..5).forEach { star ->
                Text(
                    text = if (star <= quality) "★" else "☆",
                    color = if (star <= quality) MaterialTheme.colorScheme.tertiary else Mist,
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { quality = star }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        FormField("Note (optional)", note, { note = it.take(140) })
        FormField("Date YYYY-MM-DD", date, { date = it.take(10) })
        if (!validDate) {
            Text(
                "Enter a real date between ${InputValidation.MIN_VALID_YEAR} and ${InputValidation.MAX_VALID_YEAR}.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = {
                val existingId = existing.firstOrNull { it.optString("date") == date.trim() }?.optString("id")
                val record = original ?: LocalStore.obj()
                if (original == null && !existingId.isNullOrBlank()) record.put("id", existingId)
                record.put("date", date.trim())
                record.put("bed", bedtime.trim())
                record.put("wake", wakeTime.trim())
                record.put("minutes", duration)
                record.put("quality", quality)
                record.put("note", note.trim())
                store.put("sleep", record)
                if (parsedTarget != null && parsedTarget > 0) {
                    store.setSetting("sleep_target_hours", parsedTarget.coerceIn(4.0, 12.0).toString())
                }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = duration > 0 && validDate && validBedtime && validWakeTime && note.length <= 140
        ) {
            Text(if (original == null) "Save night" else "Update night")
        }
        if (original != null) {
            OutlinedButton(
                onClick = { store.remove("sleep", original.optString("id")); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete night") }
        }
    }
}

private fun qualityStars(quality: Int): String {
    val q = quality.coerceIn(0, 5)
    return "★".repeat(q) + "☆".repeat(5 - q)
}

private fun formatNightDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
} catch (_: Exception) {
    iso
}

private fun Double.formatHours(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}h" else "$rounded" + "h"
}
