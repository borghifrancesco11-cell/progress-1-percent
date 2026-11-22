package com.progressone.nativeapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/** Simple, goal-first interface. Advanced legacy goal types remain readable/editable. */
@Composable
fun TrackScreen(store: LocalStore, snapshot: AppSnapshot, onChallenges: () -> Unit,
                showChallenges: Boolean = true) {
    val today = Metrics.day()
    val goals = snapshot.trackers.filterNot { it.optBoolean("archived") }
    val due = goals.filter { Metrics.due(it) }
    val score = ProgressScore.dayScore(goals, snapshot.logs)
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var newGoal by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var showArchive by remember { mutableStateOf(false) }

    fun action(goal: JSONObject, which: String) {
        val id = goal.optString("id")
        // Read fresh indexed history to avoid losing fast consecutive taps while UI refreshes.
        val currentHistory = store.entries(id)
        val entry = currentHistory.firstOrNull { it.date == today }
        val value = entry?.value ?: 0.0
        val type = goal.optString("type", "habit")
        when {
            goal.optBoolean("bad") -> when (which) {
                "confirm" -> store.saveEntry(id, today, value, "done", entry?.notes.orEmpty())
                "plus" -> store.saveEntry(id, today, SmartCounter.corrected(value, 1.0), "pending", entry?.notes.orEmpty())
                "minus" -> if (value > 0.0) store.saveEntry(id, today,
                    SmartCounter.corrected(value, -1.0), "pending", entry?.notes.orEmpty())
            }
            type == "habit" -> {
                if (Metrics.completion(goal, currentHistory)) store.deleteEntry(id, today)
                else store.saveEntry(id, today, goal.optDouble("target", 1.0).coerceAtLeast(1.0), "done")
            }
            type in listOf("counter", "duration", "target") -> {
                val next = SmartCounter.next(goal, currentHistory, today, if (which == "minus") -1 else 1)
                store.saveEntry(id, today, next, "done", entry?.notes.orEmpty())
            }
            else -> selected = goal
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your score", color = Mist)
                        Text("$score / 100", color = Ice,
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Based on progress towards your goals",
                            color = Mist, style = MaterialTheme.typography.bodySmall)
                    }
                    ProgressRing(score / 100f, Modifier.size(92.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Button(onClick = { editing = null; newGoal = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add goal")
                }
            }
        }
        if (due.isEmpty()) item {
            EmptyPanel("No goals yet", "Add one habit or a goal to get started.") {
                editing = null; newGoal = true
            }
        }
        items(due, key = { it.optString("id") }) { goal ->
            GoalTile(goal, snapshot.logs, onOpen = { selected = goal }, onAction = { action(goal, it) })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showArchive = true }) {
                    Icon(Icons.Rounded.Inventory2, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Archived (${snapshot.trackers.size - goals.size})")
                }
            }
        }
        if (showChallenges) item {
            TextButton(onClick = onChallenges) { Text("Challenges") }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
    if (newGoal) GoalEditor(store, editing, onDismiss = { newGoal = false })
    if (selected != null) GoalDetail(store, selected!!, snapshot.logs,
        onEdit = { editing = selected; selected = null; newGoal = true },
        onDismiss = { selected = null })
    if (showArchive) BottomSheetFrame("Archived goals", { showArchive = false }) {
        val archived = snapshot.trackers.filter { it.optBoolean("archived") }
        if (archived.isEmpty()) Text("Nothing archived", color = Mist)
        archived.forEach { goal ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(goal.optString("name"), Modifier.weight(1f))
                TextButton(onClick = { goal.put("archived", false); store.put("tracker", goal) }) {
                    Text("Restore")
                }
            }
        }
    }
}

@Composable
private fun GoalTile(goal: JSONObject, logs: List<LocalStore.Entry>, onOpen: () -> Unit,
                     onAction: (String) -> Unit) {
    val type = goal.optString("type", "habit")
    val bad = goal.optBoolean("bad")
    val points = ProgressScore.forGoal(goal, logs).toInt()
    val progress = Metrics.progress(goal, logs)
    val target = goal.optDouble("target", 1.0)
    val record = logs.firstOrNull { it.ownerId == goal.optString("id") && it.date == Metrics.day() }
    val formattedProgress = "%.1f".format(Locale.US, progress).removeSuffix(".0")
    val formattedTarget = "%.1f".format(Locale.US, target).removeSuffix(".0")
    FrostCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.optString("emoji", "✦"), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.optString("name"), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                Text(when {
                    bad -> "${record?.value?.toInt() ?: 0} slips · allowed ${target.toInt()}"
                    type == "project" -> "Milestones"
                    type == "average" -> "Tap to record a value"
                    else -> "$formattedProgress / $formattedTarget ${goal.optString("unit")}".trim()
                }, color = Mist, style = MaterialTheme.typography.labelSmall)
            }
            Text("$points", color = Ice, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(progress = { points / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp), color = Ice)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            when {
                bad -> {
                    if ((record?.value ?: 0.0) > 0) TextButton(onClick = { onAction("minus") }) {
                        Text("Undo slip")
                    }
                    TextButton(onClick = { onAction("plus") }) { Text("+ Slip") }
                    TextButton(onClick = { onAction("confirm") }) { Text("Confirm day") }
                }
                type == "habit" -> TextButton(onClick = { onAction("check") }) {
                    Text(if (Metrics.completion(goal, logs)) "Undo" else "Done")
                }
                type in listOf("counter", "duration", "target") -> {
                    TextButton(onClick = { onAction("minus") },
                        enabled = progress > 0) { Text("−") }
                    TextButton(onClick = { onAction("plus") }) { Text("+") }
                }
                else -> TextButton(onClick = onOpen) { Text("Record") }
            }
        }
    }
}

@Composable
private fun GoalEditor(store: LocalStore, original: JSONObject?, onDismiss: () -> Unit) {
    val existingType = original?.optString("type", "habit") ?: "habit"
    val existingBad = original?.optBoolean("bad") ?: false
    var kind by remember(original) { mutableStateOf(when {
        existingBad -> "Quit"; existingType == "habit" -> "Build"; else -> "Count"
    }) }
    var name by remember(original) { mutableStateOf(original?.optString("name") ?: "") }
    var target by remember(original) { mutableStateOf(original?.optDouble("target", 1.0)?.let {
        "%.1f".format(Locale.US, it).removeSuffix(".0")
    } ?: "1") }
    var unit by remember(original) { mutableStateOf(original?.optString("unit") ?: "") }
    var step by remember(original) { mutableStateOf(original?.optDouble("step", 1.0)?.toString() ?: "1") }
    var frequency by remember(original) { mutableStateOf(original?.optString("freq", "day") ?: "day") }
    var reminder by remember(original) { mutableStateOf(original?.optString("reminder") ?: "") }
    var category by remember(original) { mutableStateOf(original?.optString("tag", "Personal") ?: "Personal") }
    var more by remember { mutableStateOf(false) }
    var milestones by remember(original) { mutableStateOf(original?.optJSONArray("milestones")?.let { array ->
        (0 until array.length()).joinToString("\n") { array.optJSONObject(it)?.optString("name") ?: "" }
    } ?: "") }
    val isNew = original == null
    BottomSheetFrame(if (isNew) "Add goal" else "Edit goal", onDismiss) {
        Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("What do you want to do?", color = Mist)
            Chips(listOf("Build", "Quit", "Count"), kind) { chosen ->
                kind = chosen
                if (isNew) {
                    target = if (chosen == "Quit") "0" else "1"
                    unit = if (chosen == "Quit") "slips" else ""
                    frequency = "day"
                }
            }
            Text(when (kind) {
                "Build" -> "Mark a habit done each day."
                "Quit" -> "Set the maximum slips and confirm each day yourself."
                else -> "Count actions toward a target. Each tap adds a step."
            }, style = MaterialTheme.typography.bodySmall, color = Mist)
            FormField("Goal name", name, { name = it.take(80) })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField(if (kind == "Quit") "Maximum slips" else "Target", target,
                    { target = it.take(12) }, Modifier.weight(1f))
                if (kind == "Count") FormField("Unit", unit, { unit = it.take(20) }, Modifier.weight(1f))
            }
            if (kind == "Count") FormField("Amount per tap", step, { step = it.take(12) })
            if (kind != "Quit") {
                Text("Repeat", color = Mist)
                Chips(listOf("day", "week", "month", "year"), frequency) { frequency = it }
            }
            TextButton(onClick = { more = !more }) { Text(if (more) "Hide options" else "More options") }
            if (more) {
                FormField("Category", category, { category = it.take(30) })
                FormField("Reminder (HH:mm)", reminder, { reminder = it.take(5) })
                if (existingType == "project") FormField("Milestones (one per line)", milestones,
                    { milestones = it }, singleLine = false)
            }
            val goalValue = target.replace(',', '.').toDoubleOrNull()
            val stepValue = step.replace(',', '.').toDoubleOrNull()
            val valid = name.isNotBlank() && goalValue != null && goalValue.isFinite() &&
                goalValue >= 0 && (kind == "Quit" || goalValue > 0) &&
                (kind != "Count" || (stepValue != null && stepValue.isFinite() && stepValue > 0))
            Button(enabled = valid, onClick = {
                val goal = original ?: LocalStore.obj("created" to Metrics.day())
                val savedType = when (kind) {
                    "Build", "Quit" -> "habit"
                    else -> if (existingType in listOf("counter", "duration", "target", "average", "project") && !isNew)
                        existingType else "counter"
                }
                goal.put("name", name.trim())
                goal.put("type", savedType)
                goal.put("bad", kind == "Quit")
                goal.put("emoji", if (kind == "Quit") "⊘" else if (savedType == "habit") "✓" else "◎")
                goal.put("target", goalValue!!)
                goal.put("unit", if (kind == "Quit") "slips" else unit.trim())
                goal.put("step", if (kind == "Count") stepValue ?: 1.0 else 1.0)
                goal.put("freq", if (kind == "Quit") "day" else frequency)
                goal.put("tag", category.ifBlank { "Personal" })
                goal.put("reminder", reminder)
                if (savedType == "project") {
                    val old = original?.optJSONArray("milestones") ?: JSONArray()
                    val names = milestones.lines().map { it.trim() }.filter { it.isNotBlank() }
                    goal.put("milestones", JSONArray().apply {
                        names.forEach { label ->
                            val match = (0 until old.length()).mapNotNull { old.optJSONObject(it) }
                                .firstOrNull { it.optString("name") == label }
                            put(match ?: JSONObject().put("name", label).put("done", false))
                        }
                    })
                }
                store.put("tracker", goal)
                onDismiss()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save goal") }
        }
    }
}

@Composable
private fun GoalDetail(store: LocalStore, goal: JSONObject, logs: List<LocalStore.Entry>,
                       onEdit: () -> Unit, onDismiss: () -> Unit) {
    val id = goal.optString("id")
    var date by remember(id) { mutableStateOf(Metrics.day()) }
    var value by remember(id, date) { mutableStateOf(
        logs.firstOrNull { it.ownerId == id && it.date == date }?.value?.toString() ?: "") }
    var note by remember(id, date) { mutableStateOf(
        logs.firstOrNull { it.ownerId == id && it.date == date }?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    BottomSheetFrame(goal.optString("name"), onDismiss) {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recorded progress · ${ProgressScore.forGoal(goal, logs).toInt()} / 100", color = Ice)
            FormField("Date (YYYY-MM-DD)", date, { date = it.take(10) })
            FormField(if (goal.optBoolean("bad")) "Slips" else "Value", value, { value = it.take(15) })
            FormField("Note", note, { note = it.take(250) }, singleLine = false)
            if (goal.optString("type") == "project") {
                val milestones = goal.optJSONArray("milestones") ?: JSONArray()
                Text("Milestones", color = Ice)
                for (index in 0 until milestones.length()) {
                    val milestone = milestones.optJSONObject(index) ?: continue
                    val checked = milestone.optBoolean("done")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { next ->
                            milestone.put("done", next); store.put("tracker", goal)
                        })
                        Text(milestone.optString("name"))
                    }
                }
            }
            Button(enabled = runCatching { LocalDate.parse(date) }.isSuccess &&
                value.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true,
                onClick = {
                    store.saveEntry(id, date, value.replace(',', '.').toDouble(), "done", note)
                    onDismiss()
                }) { Text("Save record") }
            OutlinedButton(enabled = runCatching { LocalDate.parse(date) }.isSuccess,
                onClick = { store.deleteEntry(id, date); onDismiss() }) { Text("Clear selected date") }
            OutlinedButton(onClick = onEdit) { Text("Edit goal") }
            OutlinedButton(onClick = {
                if (confirmArchive) {
                    goal.put("archived", true); store.put("tracker", goal); onDismiss()
                } else confirmArchive = true
            }) { Text(if (confirmArchive) "Confirm archive" else "Archive goal") }
            TextButton(onClick = {
                if (confirmDelete) { store.remove("tracker", id); onDismiss() }
                else confirmDelete = true
            }) { Text(if (confirmDelete) "Confirm permanent deletion" else "Delete goal") }
        }
    }
}
