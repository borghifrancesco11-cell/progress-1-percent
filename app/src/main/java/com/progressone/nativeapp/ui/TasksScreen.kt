package com.progressone.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.InputValidation
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.TaskHistory
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private fun persistTask(store: LocalStore, task: JSONObject, update: (JSONObject) -> Unit) {
    val updated = JSONObject(task.toString())
    update(updated)
    store.put("task", updated)
}

@Composable
fun TasksScreen(
    store: LocalStore,
    snapshot: AppSnapshot,
    focusTaskId: String? = null,
    onTaskFocusHandled: () -> Unit = {},
) {
    val todayIso = LocalDate.now().toString()
    var taskEditor by remember { mutableStateOf<JSONObject?>(null) }
    var showTaskEditor by remember { mutableStateOf(false) }

    val pendingTasks = snapshot.tasks.filter { !it.optBoolean("done") }
    val completedTasks = snapshot.tasks.filter { it.optBoolean("done") }

    // A notification carries the exact task id. Open that task directly instead of
    // making the user find it again in the list.
    LaunchedEffect(focusTaskId) {
        val id = focusTaskId?.trim().orEmpty()
        if (id.isBlank()) return@LaunchedEffect
        val task = snapshot.tasks.firstOrNull { it.optString("id") == id }
        if (task == null) {
            onTaskFocusHandled()
            return@LaunchedEffect
        }
        taskEditor = task
        showTaskEditor = true
        onTaskFocusHandled()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeading("DAILY TO-DOS", "Tasks", "Organize, track and complete your daily goals")
        }

        // Summary Card
        item {
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow("TASK COMPLETION", Mint)
                        val total = snapshot.tasks.size
                        val done = completedTasks.size
                        val pct = if (total == 0) 0 else ((done.toDouble() / total) * 100).toInt()
                        Text(
                            "$done / $total",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Ice
                        )
                        Text(
                            if (total == 0) "No tasks yet · Add your first to-do below"
                            else "$pct% completed · ${pendingTasks.size} remaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mist
                        )
                    }
                    if (snapshot.tasks.isNotEmpty()) {
                        val pct = completedTasks.size.toFloat() / snapshot.tasks.size.toFloat()
                        ProgressRing(pct, Modifier.size(80.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { taskEditor = null; showTaskEditor = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add new task")
                }
            }
        }

        if (snapshot.tasks.isEmpty()) {
            item {
                EmptyPanel("No tasks yet", "Stay organized by adding your daily tasks here.") {
                    taskEditor = null
                    showTaskEditor = true
                }
            }
        } else {
            if (pendingTasks.isNotEmpty()) {
                item {
                    SectionHeading("Pending Tasks", "${pendingTasks.size} tasks to complete")
                }
                items(pendingTasks, key = { "pending_${it.optString("id")}" }) { task ->
                    TaskCard(task, todayIso, onToggle = { checked ->
                        persistTask(store, task) { updated ->
                            if (checked) TaskHistory.markCompleted(updated, todayIso)
                            else TaskHistory.markPending(updated, todayIso)
                        }
                    }, onSetDueToday = {
                        persistTask(store, task) { updated ->
                            if (updated.optBoolean("done")) TaskHistory.markPending(updated, todayIso)
                            updated.put("due", todayIso)
                        }
                    }, onClick = {
                        taskEditor = task
                        showTaskEditor = true
                    })
                }
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    SectionHeading("Completed", "${completedTasks.size} tasks finished")
                }
                items(completedTasks, key = { "done_${it.optString("id")}" }) { task ->
                    TaskCard(task, todayIso, onToggle = { checked ->
                        persistTask(store, task) { updated ->
                            if (checked) TaskHistory.markCompleted(updated, todayIso)
                            else TaskHistory.markPending(updated, todayIso)
                        }
                    }, onSetDueToday = {
                        persistTask(store, task) { updated ->
                            if (updated.optBoolean("done")) TaskHistory.markPending(updated, todayIso)
                            updated.put("due", todayIso)
                        }
                    }, onClick = {
                        taskEditor = task
                        showTaskEditor = true
                    })
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showTaskEditor) {
        TaskEditorModal(store, taskEditor, onDismiss = { showTaskEditor = false })
    }
}

@Composable
private fun TaskCard(
    task: JSONObject,
    todayIso: String,
    onToggle: (Boolean) -> Unit,
    onSetDueToday: () -> Unit,
    onClick: () -> Unit
) {
    val isDone = task.optBoolean("done")
    val due = task.optString("due")
    val isOverdue = due.isNotBlank() && due < todayIso && !isDone
    var menuOpen by remember { mutableStateOf(false) }

    fun formatDue(dateStr: String): String {
        return try {
            val d = LocalDate.parse(dateStr)
            val today = LocalDate.parse(todayIso)
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
            val rel = when {
                d.isEqual(today) -> "Today"
                d.isEqual(today.plusDays(1)) -> "Tomorrow"
                d.isBefore(today) -> "Overdue"
                else -> d.format(fmt)
            }
            "$rel · ${d.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
        } catch (e: DateTimeParseException) {
            dateStr
        }
    }

    FrostCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isDone,
                onCheckedChange = onToggle
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.optString("name"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDone) Mist else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    maxLines = 1
                )
                if (due.isNotBlank()) {
                    Text(
                        formatDue(due),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) Color(0xFFFF6B6B) else Mist
                    )
                    val reminder = task.optString("reminder", "09:00")
                    if (runCatching { LocalTime.parse(reminder) }.isSuccess) {
                        Text(
                            "Local reminder · $reminder",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, "Task menu", tint = Mist, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isDone) "Mark undone" else "Mark done") },
                        onClick = {
                            menuOpen = false
                            onToggle(!isDone)
                        },
                        leadingIcon = {
                            Icon(
                                if (isDone) Icons.Rounded.CheckBoxOutlineBlank else Icons.Rounded.CheckBox,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Due today") },
                        onClick = {
                            menuOpen = false
                            onSetDueToday()
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskEditorModal(store: LocalStore, original: JSONObject?, onDismiss: () -> Unit) {
    var name by remember(original) { mutableStateOf(original?.optString("name") ?: "") }
    var due by remember(original) { mutableStateOf(original?.optString("due") ?: "") }
    var reminder by remember(original) { mutableStateOf(original?.optString("reminder", "09:00") ?: "09:00") }
    var confirmDelete by remember { mutableStateOf(false) }

    val today = LocalDate.now().toString()
    val validDue = due.isBlank() || InputValidation.isValidDate(due)
    val validReminder = due.isBlank() || InputValidation.isValidTime(reminder)

    BottomSheetFrame(if (original == null) "Add task" else "Edit task", onDismiss) {
        FormField("Task description", name, { name = it.take(120) })
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField("Due date YYYY-MM-DD", due, { due = it.take(10) }, modifier = Modifier.weight(1f))
            Button(onClick = { due = today }) {
                Text("Today")
            }
            Button(onClick = { due = "" }) {
                Text("Clear")
            }
        }
        
        Text(
            if (due.isBlank()) "No due date"
            else {
                try {
                    val d = LocalDate.parse(due)
                    val fmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
                    "Due: ${d.format(fmt)}"
                } catch (e: Exception) { "Invalid date" }
            },
            style = MaterialTheme.typography.bodySmall,
            color = Mist
        )

        if (due.isNotBlank()) {
            FormField("Local reminder time (HH:mm)", reminder, { reminder = it.take(5) })
            if (!validReminder) {
                Text("Enter a valid reminder time in HH:mm format.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "The reminder is scheduled on this device and can be disabled globally in Settings.",
                style = MaterialTheme.typography.labelSmall,
                color = Mist
            )
        }
        if (!validDue) {
            Text("Enter a real date between ${InputValidation.MIN_VALID_YEAR} and ${InputValidation.MAX_VALID_YEAR}.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        
        Button(
            onClick = {
                val record = original?.let { JSONObject(it.toString()) } ?: LocalStore.obj("done" to false)
                record.put("name", name.trim())
                record.put("due", due.trim())
                if (due.isNotBlank()) record.put("reminder", reminder.trim())
                else record.remove("reminder")
                store.put("task", record)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && validDue && validReminder
        ) {
            Text("Save task")
        }
        if (original != null) {
            OutlinedButton(
                onClick = {
                    if (confirmDelete) {
                        store.remove("task", original.optString("id"))
                        onDismiss()
                    } else confirmDelete = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (confirmDelete) "Confirm delete task" else "Delete task")
            }
        }
    }
}
