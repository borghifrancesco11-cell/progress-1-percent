package com.progressone.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

@Composable
fun SchoolScreen(store: LocalStore, snapshot: AppSnapshot) {
    var page by remember { mutableStateOf("Grades") }
    var addSubject by remember { mutableStateOf(false) }
    var addGrade by remember { mutableStateOf(false) }
    var addExam by remember { mutableStateOf(false) }
    var goalEditor by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf<JSONObject?>(null) }
    val average = Metrics.weightedAverage(snapshot.grades)
    val goal = snapshot.goalGrade
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { PageHeading("STUDY · RESULTS", "School", "") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SmallStat("GRADE AVERAGE", average?.let { "%.2f".format(Locale.US, it) } ?: "—",
                    Modifier.weight(1f), Lavender)
                SmallStat("TARGET", "%.1f".format(Locale.US, goal), Modifier.weight(1f), Ice)
            }
        }
        item { Chips(listOf("Grades", "Exams", "Simulator"), page) { page = it } }
        when (page) {
            "Grades" -> {
                item {
                    SectionHeading("Subjects", "Weighted averages per subject") {
                        TextButton(onClick = { addSubject = true }) { Icon(Icons.Rounded.Add, null); Text("Subject") }
                    }
                }
                if (snapshot.subjects.isEmpty()) item { EmptyPanel("Add a subject", "Then record your first grade.") {
                    addSubject = true
                } }
                items(snapshot.subjects, key = { it.str("id") }) { subject ->
                    val grades = snapshot.grades.filter { it.str("subject") == subject.str("id") }
                    val score = Metrics.weightedAverage(grades)
                    FrostCard(Modifier.fillMaxWidth(), onClick = { selectedSubject = subject }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(subject.str("name"), style = MaterialTheme.typography.titleMedium)
                                Text("${grades.size} grades · tap to view", color = Mist)
                            }
                            Text(score?.let { "%.2f".format(Locale.US, it) } ?: "—",
                                color = Lavender, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                item {
                    Button(onClick = { addGrade = true }, Modifier.fillMaxWidth(),
                        enabled = snapshot.subjects.isNotEmpty()) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Add grade")
                    }
                }
            }
            "Exams" -> {
                item { SectionHeading("Upcoming exams") {
                    TextButton(onClick = { addExam = true }) { Icon(Icons.Rounded.Add, null); Text("Exam") }
                } }
                val ordered = snapshot.exams.sortedBy { it.str("date") }
                if (ordered.isEmpty()) item { EmptyPanel("No exams planned", "Add a date to stay ready.") {
                    addExam = true
                } }
                items(ordered, key = { it.str("id") }) { exam ->
                    var confirm by remember { mutableStateOf(false) }
                    FrostCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Event, null, tint = Lavender)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(exam.str("title"), fontWeight = FontWeight.Medium)
                                Text(exam.str("date"), color = Mist)
                                Text(snapshot.subjects.firstOrNull { it.str("id") == exam.str("subject") }
                                    ?.str("name") ?: "", color = Mist)
                            }
                            IconButton(onClick = { if (confirm) store.remove("exam", exam.str("id")) else confirm = true }) {
                                Icon(Icons.Rounded.Delete, "Delete exam")
                            }
                        }
                    }
                }
            }
            "Simulator" -> {
                item { SectionHeading("What grade do I need?", "Calculate the next grade's impact") }
                item {
                    var selected by remember { mutableStateOf(snapshot.subjects.firstOrNull()?.str("id") ?: "") }
                    var nextWeight by remember { mutableStateOf("1") }
                    val subjectGrades = snapshot.grades.filter { it.str("subject") == selected }
                    val weightSum = subjectGrades.sumOf { it.num("weight", 1.0) }
                    val currentPoints = subjectGrades.sumOf { it.num("value") * it.num("weight", 1.0) }
                    val nw = nextWeight.cleanNumber().coerceAtLeast(.01)
                    val needed = (goal * (weightSum + nw) - currentPoints) / nw
                    FrostCard(Modifier.fillMaxWidth()) {
                        Eyebrow("YOUR SUBJECT")
                        Chips(snapshot.subjects.map { it.str("name") },
                            snapshot.subjects.firstOrNull { it.str("id") == selected }?.str("name") ?: "") { value ->
                            selected = snapshot.subjects.firstOrNull { it.str("name") == value }?.str("id") ?: ""
                        }
                        FormField("Next grade weight", nextWeight, { nextWeight = it })
                        Text(if (subjectGrades.isEmpty()) "Add grades first."
                        else "You need ${"%.2f".format(Locale.US, needed)} on your next assessment.",
                            style = MaterialTheme.typography.titleMedium)
                        if (needed > 10) Text("Not reachable with a single grade at this weight.", color = Mist)
                        if (needed <= 0 && subjectGrades.isNotEmpty()) Text("Target already secured with this next grade.", color = Mint)
                    }
                }
                item {
                    var hypothetical by remember { mutableStateOf("8.0") }
                    var simSubject by remember { mutableStateOf(snapshot.subjects.firstOrNull()?.str("id") ?: "") }
                    val simGrades = snapshot.grades.filter { it.str("subject") == simSubject }
                    val subjectBefore = Metrics.weightedAverage(simGrades)
                    val overallBefore = average
                    val hypotheticalValue = hypothetical.cleanNumber()
                    val valid = hypotheticalValue in 0.0..10.0 && simSubject.isNotBlank()
                    val simulatedSubject = if (valid) Metrics.weightedAverage(
                        simGrades + JSONObject().put("value", hypotheticalValue).put("weight", 1.0)
                    ) else null
                    val simulatedOverall = if (valid) Metrics.weightedAverage(
                        snapshot.grades + JSONObject().put("subject", simSubject)
                            .put("value", hypotheticalValue).put("weight", 1.0)
                    ) else null
                    FrostCard(Modifier.fillMaxWidth()) {
                        Eyebrow("GRADE SIMULATOR", Mint)
                        Text("Enter a hypothetical grade to see its impact on your subject and overall average.",
                            color = Mist, style = MaterialTheme.typography.bodySmall)
                        Chips(snapshot.subjects.map { it.str("name") },
                            snapshot.subjects.firstOrNull { it.str("id") == simSubject }?.str("name") ?: "") { value ->
                            simSubject = snapshot.subjects.firstOrNull { it.str("name") == value }?.str("id") ?: ""
                        }
                        FormField("Hypothetical grade (0–10)", hypothetical, { hypothetical = it.take(4) })
                        Text(
                            "Subject: ${subjectBefore?.let { "%.2f".format(Locale.US, it) } ?: "—"} → " +
                                (simulatedSubject?.let { "%.2f".format(Locale.US, it) } ?: "—"),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Overall: ${overallBefore?.let { "%.2f".format(Locale.US, it) } ?: "—"} → " +
                                (simulatedOverall?.let { "%.2f".format(Locale.US, it) } ?: "—"),
                            color = Ice, style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                item { FilledTonalButton(onClick = { goalEditor = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Tune, null); Text("Set target average")
                } }
            }
        }
        item { Spacer(Modifier.height(14.dp)) }
    }
    if (addSubject) {
        var name by remember { mutableStateOf("") }
        BottomSheetFrame("New subject", { addSubject = false }) {
            FormField("Subject name", name, { name = it })
            Button(onClick = {
                store.put("subject", LocalStore.obj("name" to name.trim()))
                addSubject = false
            }, Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { Text("Save subject") }
        }
    }
    if (addGrade) GradeEditor(store, snapshot.subjects) { addGrade = false }
    if (addExam) ExamEditor(store, snapshot.subjects) { addExam = false }
    if (goalEditor) {
        var grade by remember { mutableStateOf(goal.toString()) }
        BottomSheetFrame("Average target", { goalEditor = false }) {
            FormField("Target 0–10", grade, { grade = it })
            Button(onClick = {
                store.setSetting("goalGrade", grade.cleanNumber().toString()); goalEditor = false
            }, Modifier.fillMaxWidth(), enabled = grade.cleanNumber() in 0.0..10.0) { Text("Save target") }
        }
    }
    if (selectedSubject != null) {
        val subject = selectedSubject!!
        val grades = snapshot.grades.filter { it.str("subject") == subject.str("id") }.sortedByDescending { it.str("date") }
        var confirm by remember { mutableStateOf(false) }
        BottomSheetFrame(subject.str("name"), { selectedSubject = null }) {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (grades.isEmpty()) Text("No grades yet.", color = Mist)
                grades.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${g.num("value")} · ${g.str("kind", "Grade")}")
                            Text("${g.str("date")} · weight ${g.num("weight", 1.0)}", color = Mist)
                        }
                        IconButton(onClick = { store.remove("grade", g.str("id")) }) {
                            Icon(Icons.Rounded.Delete, "Delete grade")
                        }
                    }
                }
                if (confirm) TextButton(onClick = {
                    grades.forEach { store.remove("grade", it.str("id")) }
                    store.remove("subject", subject.str("id")); selectedSubject = null
                }) { Text("Confirm delete subject and grades") }
                else TextButton(onClick = { confirm = true }) { Text("Delete subject") }
            }
        }
    }
}

@Composable
private fun GradeEditor(store: LocalStore, subjects: List<JSONObject>, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(subjects.firstOrNull()?.str("id") ?: "") }
    var grade by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("1") }
    var date by remember { mutableStateOf(Metrics.day()) }
    var kind by remember { mutableStateOf("Written") }
    BottomSheetFrame("Record grade", onDismiss) {
        Text("Subject", color = Mist)
        Chips(subjects.map { it.str("name") }, subjects.firstOrNull { it.str("id") == chosen }?.str("name") ?: "") { s ->
            chosen = subjects.firstOrNull { it.str("name") == s }?.str("id") ?: ""
        }
        FormField("Grade (0–10)", grade, { grade = it })
        FormField("Weight", weight, { weight = it })
        FormField("Date YYYY-MM-DD", date, { date = it })
        Chips(listOf("Written", "Oral", "Practical", "Other"), kind) { kind = it }
        Button(onClick = {
            store.put("grade", LocalStore.obj("subject" to chosen, "value" to grade.cleanNumber(),
                "weight" to weight.cleanNumber(), "date" to date, "kind" to kind))
            onDismiss()
        }, Modifier.fillMaxWidth(), enabled = chosen.isNotBlank() && grade.cleanNumber() in 0.0..10.0
            && weight.cleanNumber() > 0 && runCatching { LocalDate.parse(date.trim()) }.isSuccess) {
            Text("Save grade")
        }
    }
}

@Composable
private fun ExamEditor(store: LocalStore, subjects: List<JSONObject>, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(Metrics.day()) }
    var subject by remember { mutableStateOf(subjects.firstOrNull()?.str("id") ?: "") }
    BottomSheetFrame("New exam", onDismiss) {
        FormField("Exam title", title, { title = it })
        FormField("Date YYYY-MM-DD", date, { date = it })
        Chips(subjects.map { it.str("name") }, subjects.firstOrNull { it.str("id") == subject }?.str("name") ?: "") { name ->
            subject = subjects.firstOrNull { it.str("name") == name }?.str("id") ?: ""
        }
        Button(onClick = { store.put("exam", LocalStore.obj("title" to title.trim(), "date" to date,
            "subject" to subject)); onDismiss() }, Modifier.fillMaxWidth(),
            enabled = title.isNotBlank() && runCatching { LocalDate.parse(date.trim()) }.isSuccess) { Text("Save exam") }
    }
}
