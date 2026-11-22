package com.progressone.nativeapp.ui

import androidx.compose.foundation.layout.*
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
import com.progressone.nativeapp.data.InsightLibrary
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import org.json.JSONObject
import java.time.LocalDate

private data class ChallengeIdea(val name: String, val description: String,
                                 val days: Int, val goal: Int, val category: String, val emoji: String)

private val ideas = listOf(
    ChallengeIdea("Read for 7 days", "Read something meaningful each day, even for a short time.", 7, 7, "Learning", "📖"),
    ChallengeIdea("Move every day", "Get some movement on each of the next 14 days.", 14, 14, "Health", "🚶"),
    ChallengeIdea("Study with intention", "Do a focused study session on 10 separate days.", 14, 10, "Learning", "🧠"),
    ChallengeIdea("Mindful spending", "Review spending for 7 days without judging yourself.", 7, 7, "Finance", "🌱"),
    ChallengeIdea("One small improvement", "Choose one positive action for the next 30 days.", 30, 30, "Personal", "✨"),
    ChallengeIdea("10-minute reading", "Read at least ten minutes on each of seven days.", 7, 7, "Learning", "📚"),
    ChallengeIdea("Daily journal", "Write a short reflection on ten of the next fourteen days.", 14, 10, "Personal", "📓"),
    ChallengeIdea("Walk outside", "Take an outdoor walk on ten separate days.", 14, 10, "Health", "🌿"),
    ChallengeIdea("Declutter a little", "Tidy one small area each day for a week.", 7, 7, "Personal", "🧺"),
    ChallengeIdea("Practice a skill", "Spend time deliberately practising something for two weeks.", 14, 10, "Learning", "🎯"),
    ChallengeIdea("Screen-free evening", "Take five evenings without scrolling before bed.", 7, 5, "Health", "🌙"),
    ChallengeIdea("Create for 10 days", "Draw, write, compose or make something small.", 14, 10, "Creative", "🎨"),
    ChallengeIdea("Save a little", "Record a money-saving action on ten days.", 14, 10, "Finance", "💰")
)

@Composable
fun ChallengesScreen(store: LocalStore, snapshot: AppSnapshot, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf("Active") }
    var custom by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    val today = LocalDate.now()
    val active = snapshot.challenges.filter { !it.optBoolean("finished") }
    val finished = snapshot.challenges.filter { it.optBoolean("finished") }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            PageHeading("PERSONAL · OFFLINE", "Challenges",
                "No public rankings. No likes. No comparison. Just commitments you choose.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SmallStat("ACTIVE", active.size.toString(), Modifier.weight(1f), Mint)
                SmallStat("FINISHED", finished.size.toString(), Modifier.weight(1f), Ice)
            }
        }
        item { Chips(listOf("Active", "Discover", "Completed"), selectedTab) { selectedTab = it } }
        when (selectedTab) {
            "Active" -> {
                if (active.isEmpty()) item {
                    EmptyPanel("No active challenges", "Pick a challenge or create your own.") {
                        selectedTab = "Discover"
                    }
                }
                items(active, key = { it.str("id") }) { challenge ->
                    ChallengeCard(challenge, snapshot.logs, store, onOpen = { selected = challenge })
                }
                item { FilledTonalButton(onClick = { selectedTab = "Discover" }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Explore, null); Text(tr("Explore challenges"))
                } }
            }
            "Discover" -> {
                item {
                    SectionHeading("Ideas to try", "You decide what success looks like") {
                        TextButton(onClick = { custom = true }) {
                            Icon(Icons.Rounded.Add, null); Text(tr("Custom"))
                        }
                    }
                }
                items(ideas) { idea ->
                    val joined = snapshot.challenges.any { it.str("title") == idea.name && !it.optBoolean("finished") }
                    FrostCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(idea.emoji, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(idea.name, style = MaterialTheme.typography.titleMedium)
                                Eyebrow("${idea.days} DAYS · ${idea.category}")
                            }
                        }
                        Text(idea.description, color = Mist)
                        Button(onClick = {
                            store.put("challenge", LocalStore.obj(
                                "title" to idea.name, "description" to idea.description,
                                "category" to idea.category, "emoji" to idea.emoji,
                                "goal" to idea.goal, "days" to idea.days,
                                "startDate" to today.toString(),
                                "endDate" to today.plusDays((idea.days - 1).toLong()).toString(),
                                "finished" to false))
                            selectedTab = "Active"
                        }, modifier = Modifier.fillMaxWidth(), enabled = !joined) {
                            Text(if (joined) "Already joined" else "Start challenge")
                        }
                    }
                }
            }
            "Completed" -> {
                if (finished.isEmpty()) item { EmptyPanel("No finished challenges", "They'll appear here when you complete them.") }
                items(finished, key = { it.str("id") }) { challenge ->
                    ChallengeCard(challenge, snapshot.logs, store, onOpen = { selected = challenge })
                }
            }
        }
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("NO TOXIC COMPETITION", Mint)
                Text(tr("You are never ranked against other people. You can skip a day and start again."), color = Mist)
            }
        }
    }
    if (custom) CustomChallenge(store, onDismiss = { custom = false }, onCreated = { selectedTab = "Active" })
    if (selected != null) {
        val c = selected!!
        val history = snapshot.logs.filter { it.ownerId == c.str("id") && it.status == "done" }
        var confirm by remember { mutableStateOf(false) }
        BottomSheetFrame(c.str("title"), { selected = null }) {
            Text(c.str("description"), color = Mist)
            Text("${history.size} / ${c.optInt("goal", 1)} days completed", color = Mint)
            Text("${c.str("startDate")} – ${c.str("endDate")}", color = Mist)
            if (history.size >= c.optInt("goal",1) && !c.optBoolean("finished")) {
                Button(onClick = { c.put("finished", true); store.put("challenge", c); selected = null },
                    Modifier.fillMaxWidth()) { Text(tr("Complete challenge")) }
            }
            if (confirm) TextButton(onClick = { store.remove("challenge", c.str("id")); selected = null }) {
                Text(tr("Confirm delete challenge"))
            } else TextButton(onClick = { confirm = true }) { Text(tr("Delete challenge")) }
        }
    }
}

@Composable
private fun ChallengeCard(c: JSONObject, logs: List<LocalStore.Entry>, store: LocalStore,
                          onOpen: () -> Unit) {
    val history = logs.filter { it.ownerId == c.str("id") && it.status == "done" }
    val doneToday = history.any { it.date == Metrics.day() }
    val goal = c.optInt("goal", 7).coerceAtLeast(1)
    val progress = (history.size / goal.toFloat()).coerceIn(0f, 1f)
    val outOfWindow = Metrics.day() < c.str("startDate") || Metrics.day() > c.str("endDate")
    FrostCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.str("emoji", "✦"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(c.str("title"), style = MaterialTheme.typography.titleMedium)
                Text(c.str("category", "Personal"), color = Mist)
            }
            Text("${history.size}/$goal", color = Ice, fontWeight = FontWeight.SemiBold)
        }
        Text(c.str("description"), color = Mist, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(9.dp),
            color = Mint)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(onClick = {
                if (doneToday) store.deleteEntry(c.str("id"), Metrics.day())
                else store.saveEntry(c.str("id"), Metrics.day(), 1.0, "done")
            }, enabled = !c.optBoolean("finished") && !outOfWindow,
                modifier = Modifier.weight(1f)) {
                Icon(if (doneToday) Icons.Rounded.CheckCircle else Icons.Rounded.AddTask, null)
                Spacer(Modifier.width(8.dp))
                Text(if (doneToday) "Undo today" else "Mark today")
            }
            IconButton(onClick = onOpen) { Icon(Icons.Rounded.MoreHoriz, "Challenge details") }
        }
        if (outOfWindow && !c.optBoolean("finished")) Text(tr("Outside scheduled dates · open details to archive or remove"), color = Mist)
        if (history.size >= goal) Text(tr("Goal reached · open details to finish"), color = Mint)
    }
}

@Composable
private fun CustomChallenge(store: LocalStore, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("14") }
    var target by remember { mutableStateOf("10") }
    var category by remember { mutableStateOf("Personal") }
    BottomSheetFrame("Your challenge", onDismiss) {
        FormField("Challenge name", title, { title = it })
        FormField("What will you do?", details, { details = it }, singleLine = false)
        FormField("Duration (days)", days, { days = it })
        FormField("Days to mark as completed", target, { target = it })
        Chips(listOf("Personal", "Health", "Learning", "Finance", "Creative"), category) { category = it }
        val validDays = days.toIntOrNull() ?: 0
        val validTarget = target.toIntOrNull() ?: 0
        Button(onClick = {
            store.put("challenge", LocalStore.obj("title" to title.trim(), "description" to details.trim(),
                "category" to category, "emoji" to "✦", "goal" to validTarget, "days" to validDays,
                "startDate" to Metrics.day(),
                "endDate" to LocalDate.now().plusDays((validDays-1).toLong()).toString(),
                "finished" to false))
            onDismiss(); onCreated()
        }, modifier = Modifier.fillMaxWidth(), enabled = title.isNotBlank() && validDays in 1..365 &&
            validTarget in 1..validDays) { Text(tr("Create offline challenge")) }
    }
}
