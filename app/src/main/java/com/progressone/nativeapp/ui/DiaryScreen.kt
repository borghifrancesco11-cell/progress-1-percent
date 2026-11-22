package com.progressone.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import java.time.LocalDate

/** A real, editable daily journal. Notes and timeline remain only in SQLite and JSON backups. */
@Composable
fun DiaryScreen(store: LocalStore, snapshot: AppSnapshot, onBack: () -> Unit) {
    val rev = store.revision
    val notes = remember(rev) { store.all("journal") }
    val foods = remember(rev) { store.all("food") }
    var date by remember { mutableStateOf(Metrics.day()) }
    val original = remember(notes, date) { notes.firstOrNull { it.optString("date") == date } }
    var text by remember(original, date) { mutableStateOf(original?.optString("note") ?: "") }
    var mood by remember(original, date) { mutableStateOf(original?.optString("mood", "Okay") ?: "Okay") }
    val allDates = (0L..6L).map { LocalDate.now().minusDays(it).toString() }
    val dayLogs = remember(snapshot.logs, date) { snapshot.logs.filter { it.date == date } }
    val dayFood = foods.filter { it.optString("date") == date }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("Diary", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Chips(allDates, date) { date = it } }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Text("How was your day?", style = MaterialTheme.typography.titleMedium)
                Chips(listOf("Great", "Good", "Okay", "Hard"), mood) { mood = it }
                OutlinedTextField(value = text, onValueChange = { text = it.take(6000) },
                    label = { Text("Write your thoughts") }, minLines = 4,
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val record = original ?: LocalStore.obj("date" to date)
                        record.put("date", date); record.put("note", text.trim()); record.put("mood", mood)
                        store.put("journal", record)
                    }, modifier = Modifier.weight(1f)) { Text("Save entry") }
                    if (original != null) OutlinedButton(onClick = { store.remove("journal", original.optString("id")) }) {
                        Text("Delete")
                    }
                }
            }
        }
        item { SectionHeading("Daily timeline", "Only recorded activities appear") }
        if (dayLogs.isEmpty() && dayFood.isEmpty()) item {
            Text("No activities recorded for this date.", color = Mist)
        }
        items(dayLogs, key = { "log-" + it.ownerId }) { entry ->
            val tracker = snapshot.trackers.firstOrNull { it.optString("id") == entry.ownerId }
            FrostCard(Modifier.fillMaxWidth()) {
                Text(tracker?.optString("name", "Activity") ?: "Activity")
                Text("${entry.value} · ${entry.status}", color = Mist)
            }
        }
        items(dayFood, key = { "meal-" + it.optString("id") }) { meal ->
            FrostCard(Modifier.fillMaxWidth()) {
                Text(meal.optString("name", "Meal"))
                Text("${meal.optString("meal", "Food")} · ${meal.optInt("kcal", 0)} kcal", color = Mist)
            }
        }
    }
}
