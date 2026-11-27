package com.progressone.nativeapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.GymDefaults
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.LocalCoachEngine
import com.progressone.nativeapp.data.enabledModuleIds
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal data class GymSet(val reps: Int, val weight: Double, val done: Boolean)
internal data class GymExercise(val name: String, val rest: Int, val bodyweight: Boolean, val sets: List<GymSet>)

/** The complete active-session state that must survive configuration changes. */
internal data class GymSessionState(
    val templateJson: String,
    val exercises: List<GymExercise>,
    val elapsedSeconds: Int,
    val restRemaining: Int
)

/**
 * JSON-only codec for the active workout. Keeping this independent of Compose and Android
 * makes the saveable state easy to verify and prevents accidental loss of set progress.
 */
internal object GymSessionStateCodec {
    fun encode(state: GymSessionState): String = JSONObject().apply {
        put("template", state.templateJson)
        put("elapsedSeconds", state.elapsedSeconds)
        put("restRemaining", state.restRemaining)
        put("exercises", JSONArray().apply {
            state.exercises.forEach { exercise ->
                put(JSONObject().apply {
                    put("name", exercise.name)
                    put("rest", exercise.rest)
                    put("bodyweight", exercise.bodyweight)
                    put("sets", JSONArray().apply {
                        exercise.sets.forEach { set ->
                            put(JSONObject().apply {
                                put("reps", set.reps)
                                put("weight", set.weight)
                                put("done", set.done)
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    fun decode(encoded: String): GymSessionState? = runCatching {
        val root = JSONObject(encoded)
        val templateJson = root.getString("template")
        // Validate the selected template before restoring the session.
        JSONObject(templateJson)
        val exerciseArray = root.optJSONArray("exercises") ?: JSONArray()
        val exercises = (0 until exerciseArray.length()).mapNotNull { exerciseIndex ->
            val exercise = exerciseArray.optJSONObject(exerciseIndex) ?: return@mapNotNull null
            val setArray = exercise.optJSONArray("sets") ?: JSONArray()
            GymExercise(
                name = exercise.optString("name", "Exercise"),
                rest = exercise.optInt("rest", 90).coerceAtLeast(0),
                bodyweight = exercise.optBoolean("bodyweight", false),
                sets = (0 until setArray.length()).mapNotNull { setIndex ->
                    val set = setArray.optJSONObject(setIndex) ?: return@mapNotNull null
                    GymSet(
                        reps = set.optInt("reps", 10),
                        weight = set.optDouble("weight", 0.0),
                        done = set.optBoolean("done", false)
                    )
                }
            )
        }
        GymSessionState(
            templateJson = templateJson,
            exercises = exercises,
            elapsedSeconds = root.optInt("elapsedSeconds", 0).coerceAtLeast(0),
            restRemaining = root.optInt("restRemaining", 0).coerceAtLeast(0)
        )
    }.getOrNull()
}

private class GymSessionHolder(initialState: GymSessionState?) {
    var state by mutableStateOf(initialState)
}

private val gymSessionSaver = Saver<GymSessionHolder, String>(
    save = { holder -> holder.state?.let { GymSessionStateCodec.encode(it) } ?: "" },
    restore = { encoded ->
        GymSessionHolder(
            if (encoded.isBlank()) null else GymSessionStateCodec.decode(encoded)
        )
    }
)

/** Gym & calisthenics — templates, guided sessions with rest timer, records and history. */
@Composable
fun GymScreen(
    store: LocalStore,
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
) {
    LaunchedEffect(Unit) { GymDefaults.seedIfEmpty(store) }
    val sessionHolder = rememberSaveable(saver = gymSessionSaver) {
        GymSessionHolder(null)
    }
    val activeState = sessionHolder.state
    val activeTemplate = activeState?.let { state ->
        runCatching { JSONObject(state.templateJson) }.getOrNull()
    }
    if (activeState != null && activeTemplate != null) {
        GymSession(
            store = store,
            state = activeState,
            onStateChange = { sessionHolder.state = it },
            onExit = { sessionHolder.state = null }
        )
    } else {
        GymOverview(
            store = store,
            snapshot = snapshot,
            onBack = onBack,
            onOpen = onOpen,
            onStart = { template ->
                sessionHolder.state = GymSessionState(
                    templateJson = template.toString(),
                    exercises = parseExercises(template),
                    elapsedSeconds = 0,
                    restRemaining = 0
                )
            }
        )
    }
}

@Composable
private fun GymOverview(
    store: LocalStore,
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onStart: (JSONObject) -> Unit
) {
    val todayIso = LocalDate.now().toString()
    val weekStart = LocalDate.now().minusDays(6).toString()
    val workouts = snapshot.gymWorkouts
    val todayWorkouts = workouts.count { it.optString("date") == todayIso }
    val weekWorkouts = workouts.count { it.optString("date") >= weekStart }
    val totalVolume = workouts.sumOf { workoutVolume(it) }
    val records = remember(workouts) { personalRecords(workouts) }
    val workoutDays = remember(workouts) {
        workouts.map { it.optString("date") }.filter { it >= weekStart }.toSet().size
    }
    val discipline = remember(store.revision) { store.setting("gym_type", "gym") }
    val enabledIds = remember(store.revision) { store.enabledModuleIds() }
    val sleepTarget = remember(store.revision) {
        (store.setting("sleep_target_hours", "8").toDoubleOrNull() ?: 8.0).coerceIn(4.0, 12.0)
    }
    val coachInsight = remember(snapshot, enabledIds, sleepTarget, todayIso) {
        LocalCoachEngine.gym(
            snapshot = snapshot,
            enabledModules = enabledIds,
            sleepTargetHours = sleepTarget,
            date = todayIso,
        )
    }
    var confirmDiscipline by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeading("TRAIN & PROGRESS", "Gym", "Templates, guided sessions and personal records")
        }

        item {
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow("TODAY", Mint)
                        Text(
                            text = if (todayWorkouts > 0) "$todayWorkouts workout${if (todayWorkouts == 1) "" else "s"} done" else "No workout yet today",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Ice
                        )
                        Text(
                            text = "$weekWorkouts sessions this week · ${workoutDays}/7 active days",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mist
                        )
                        Text(
                            text = "Volume ${totalVolume.roundToInt()} kg · ${records.size} records",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist
                        )
                    }
                    ProgressRing(workoutDays / 7f, Modifier.size(96.dp), caption = "week", sizeDp = 96.dp)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("SESSIONS", "${workouts.size}", Modifier.weight(1f), Ice)
                SmallStat("THIS WEEK", "$weekWorkouts", Modifier.weight(1f), Mint)
                SmallStat("VOLUME", "${totalVolume.roundToInt()} kg", Modifier.weight(1f), Lavender)
            }
        }

        item {
            CoachCard(insight = coachInsight, onAction = onOpen)
        }

        val heightCm = store.setting("user_height_cm", "").cleanNumber()
        val weightKg = store.setting("user_weight_kg", "").cleanNumber()
        val bmi = if (heightCm > 0 && weightKg > 0) {
            weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
        } else null
        val recommendationTag = when {
            bmi == null -> "Set weight & height in Settings for personalized plans"
            bmi < 18.5 -> "BMI ${"%.1f".format(Locale.US, bmi)} · Focus: Lean mass & strength"
            bmi < 25.0 -> "BMI ${"%.1f".format(Locale.US, bmi)} · Focus: Balanced strength & hypertrophy"
            else -> "BMI ${"%.1f".format(Locale.US, bmi)} · Focus: Conditioning & joint-friendly"
        }
        val recommendedTemplates = run {
            val all = snapshot.gymTemplates
            if (bmi == null) all.take(2)
            else {
                val preferredIds = when {
                    bmi < 18.5 -> setOf("gym_1", "gym_2", "hyb_1", "cali_1")
                    bmi < 25.0 -> when (discipline) {
                        "calisthenics" -> setOf("cali_1", "cali_2")
                        "hybrid" -> setOf("hyb_1", "hyb_2")
                        else -> setOf("gym_1", "gym_2")
                    }
                    else -> setOf("cali_1", "cali_2", "hyb_2", "gym_2")
                }
                val filtered = all.filter { it.optString("id") in preferredIds }
                (if (filtered.isNotEmpty()) filtered else all).take(2)
            }
        }

        item {
            if (heightCm > 0 && weightKg > 0 && bmi != null) {
                FrostCard(Modifier.fillMaxWidth()) {
                    Eyebrow("PHYSICAL PROFILE", Mint)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallStat("WEIGHT", "%.1f kg".format(Locale.US, weightKg), Modifier.weight(1f), Ice)
                        SmallStat("HEIGHT", "${heightCm.roundToInt()} cm", Modifier.weight(1f), Mint)
                        SmallStat("BMI", "%.1f".format(Locale.US, bmi), Modifier.weight(1f), Lavender)
                    }
                    Text(recommendationTag, color = Mint, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                FrostCard(Modifier.fillMaxWidth()) {
                    Eyebrow("PHYSICAL PROFILE", Mist)
                    Text("Add weight and height in Settings → Profile to unlock personalized workout recommendations.",
                        color = Mist, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            FrostCard(Modifier.fillMaxWidth()) {
                SectionHeading("Discipline", "Changing discipline reloads the default templates")
                Chips(listOf("gym", "calisthenics", "hybrid"), discipline) { chosen ->
                    if (chosen != discipline) confirmDiscipline = chosen
                }
            }
        }

        item {
            SectionHeading("Recommended for you", recommendationTag)
        }

        if (recommendedTemplates.isEmpty()) {
            item {
                EmptyPanel("No templates", "Restore the default templates to start training.") {
                    GymDefaults.regenerate(store, discipline)
                }
            }
        } else {
            items(recommendedTemplates, key = { "tpl_${it.optString("id")}" }) { template ->
                val exercises = template.optJSONArray("exercises")?.length() ?: 0
                val sets = countSets(template)
                FrostCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                template.optString("name"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "$exercises exercises · $sets sets",
                                style = MaterialTheme.typography.bodySmall,
                                color = Mist
                            )
                        }
                        Button(onClick = { onStart(template) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Start")
                        }
                    }
                }
            }
        }

        if (records.isNotEmpty()) {
            item { SectionHeading("Personal records", "Best completed weight per exercise") }
            item {
                FrostCard(Modifier.fillMaxWidth()) {
                    records.forEach { (name, weight) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "%.1f kg".format(Locale.US, weight),
                                color = Mint,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeading("History", "${workouts.size} workouts") }

        if (workouts.isEmpty()) {
            item {
                FrostCard(Modifier.fillMaxWidth()) {
                    Text("Your finished sessions will appear here.", color = Mist)
                }
            }
        } else {
            items(workouts.sortedByDescending { it.optString("date") }, key = { "wo_${it.optString("id")}" }) { workout ->
                FrostCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                workout.optString("name", "Workout"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${formatWorkoutDate(workout.optString("date"))} · ${workout.optInt("duration", 0)} min · ${workoutVolume(workout).roundToInt()} kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = Mist
                            )
                        }
                        IconButton(onClick = { store.remove("gym_workout", workout.optString("id")) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete workout", tint = Mist, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    confirmDiscipline?.let { chosen ->
        AlertDialog(
            onDismissRequest = { confirmDiscipline = null },
            title = { Text("Load $chosen templates?") },
            text = { Text("Default templates will be replaced. Workout history and records are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    GymDefaults.regenerate(store, chosen)
                    confirmDiscipline = null
                }) { Text("Load defaults") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscipline = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GymSession(
    store: LocalStore,
    state: GymSessionState,
    onStateChange: (GymSessionState) -> Unit,
    onExit: () -> Unit
) {
    val template = remember(state.templateJson) {
        runCatching { JSONObject(state.templateJson) }.getOrNull()
    }
    val templateName = template?.optString("name", "Workout") ?: "Workout"
    // This state object is deliberately stable for the lifetime of the composable. The
    // parent echoes every change into rememberSaveable, so a rotation can restore it
    // without replacing the state object captured by the timer effects.
    var workingState by remember { mutableStateOf(state) }

    fun publish(updated: GymSessionState) {
        workingState = updated
        onStateChange(updated)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val updated = workingState.copy(
                elapsedSeconds = workingState.elapsedSeconds + 1
            )
            publish(updated)
        }
    }
    LaunchedEffect(workingState.restRemaining) {
        val remaining = workingState.restRemaining
        if (remaining > 0) {
            delay(1000)
            publish(workingState.copy(restRemaining = (remaining - 1).coerceAtLeast(0)))
        }
    }
    BackHandler { onExit() }

    val exercises = workingState.exercises
    val elapsedSeconds = workingState.elapsedSeconds
    val restRemaining = workingState.restRemaining
    val totalSets = exercises.sumOf { it.sets.size }
    val doneSets = exercises.sumOf { exercise -> exercise.sets.count { it.done } }
    val volume = exercises.sumOf { exercise -> exercise.sets.filter { it.done }.sumOf { it.reps * it.weight } }

    fun toggle(exIndex: Int, setIndex: Int) {
        val updatedExercises = workingState.exercises.mapIndexed { i, exercise ->
            if (i != exIndex) exercise else exercise.copy(
                sets = exercise.sets.mapIndexed { j, set ->
                    if (j != setIndex) set else set.copy(done = !set.done)
                }
            )
        }
        val nowDone = updatedExercises[exIndex].sets[setIndex].done
        publish(
            workingState.copy(
                exercises = updatedExercises,
                restRemaining = if (nowDone) updatedExercises[exIndex].rest else workingState.restRemaining
            )
        )
    }

    fun adjust(exIndex: Int, setIndex: Int, delta: Double) {
        val updatedExercises = workingState.exercises.mapIndexed { i, exercise ->
            if (i != exIndex) exercise else exercise.copy(
                sets = exercise.sets.mapIndexed { j, set ->
                    if (j != setIndex) set else set.copy(weight = (set.weight + delta).coerceAtLeast(0.0))
                }
            )
        }
        publish(workingState.copy(exercises = updatedExercises))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close session")
                }
                Column(Modifier.weight(1f)) {
                    Text(templateName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${elapsedSeconds / 60}:${"%02d".format(elapsedSeconds % 60)} elapsed · $doneSets/$totalSets sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist
                    )
                }
                Text(
                    "${volume.roundToInt()} kg",
                    style = MaterialTheme.typography.titleMedium,
                    color = Mint,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            LinearProgressIndicator(
                progress = { if (totalSets == 0) 0f else doneSets.toFloat() / totalSets },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Mint,
                trackColor = GlassLight
            )
        }

        item {
            AnimatedVisibility(visible = restRemaining > 0) {
                FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = Ice)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Rest · ${restRemaining}s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Breathe, then start the next set.", style = MaterialTheme.typography.bodySmall, color = Mist)
                        }
                        TextButton(onClick = { publish(workingState.copy(restRemaining = 0)) }) { Text("Skip") }
                    }
                }
            }
        }

        exercises.forEachIndexed { exIndex, exercise ->
            item(key = "ex_$exIndex") {
                FrostCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            exercise.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(color = GlassLight, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "rest ${exercise.rest}s",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Mist
                            )
                        }
                    }
                    exercise.sets.forEachIndexed { setIndex, set ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Set ${setIndex + 1}",
                                Modifier.width(54.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Mist
                            )
                            Text(
                                "${set.reps} reps",
                                Modifier.width(66.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (exercise.bodyweight) {
                                Text("bodyweight", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Mist)
                            } else {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { adjust(exIndex, setIndex, -2.5) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease weight", modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        "%.1f kg".format(Locale.US, set.weight),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = { adjust(exIndex, setIndex, 2.5) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Increase weight", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            IconButton(onClick = { toggle(exIndex, setIndex) }) {
                                Icon(
                                    imageVector = if (set.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = if (set.done) "Set done" else "Mark set done",
                                    tint = if (set.done) Mint else Mist
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val record = LocalStore.obj(
                        "name" to templateName,
                        "date" to LocalDate.now().toString(),
                        "duration" to (elapsedSeconds / 60).coerceAtLeast(1),
                        "exercises" to JSONArray().apply {
                            exercises.forEach { exercise ->
                                put(JSONObject().apply {
                                    put("name", exercise.name)
                                    put("sets", JSONArray().apply {
                                        exercise.sets.forEach { set ->
                                            put(JSONObject().apply {
                                                put("reps", set.reps)
                                                put("weight", set.weight)
                                                put("done", set.done)
                                            })
                                        }
                                    })
                                })
                            }
                        }
                    )
                    store.put("gym_workout", record)
                    onExit()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = doneSets > 0
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Finish workout")
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun parseExercises(template: JSONObject): List<GymExercise> {
    val array = template.optJSONArray("exercises") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val exercise = array.optJSONObject(index) ?: return@mapNotNull null
        val setsArray = exercise.optJSONArray("sets") ?: JSONArray()
        GymExercise(
            name = exercise.optString("name", "Exercise"),
            rest = exercise.optInt("rest", 90),
            bodyweight = exercise.optBoolean("bodyweight", false),
            sets = (0 until setsArray.length()).mapNotNull { setIndex ->
                val set = setsArray.optJSONObject(setIndex) ?: return@mapNotNull null
                GymSet(set.optInt("reps", 10), set.optDouble("weight", 0.0), false)
            }
        )
    }
}

private fun countSets(template: JSONObject): Int {
    val array = template.optJSONArray("exercises") ?: return 0
    var total = 0
    for (i in 0 until array.length()) {
        total += array.optJSONObject(i)?.optJSONArray("sets")?.length() ?: 0
    }
    return total
}

private fun workoutVolume(workout: JSONObject): Double {
    val array = workout.optJSONArray("exercises") ?: return 0.0
    var total = 0.0
    for (i in 0 until array.length()) {
        val sets = array.optJSONObject(i)?.optJSONArray("sets") ?: continue
        for (j in 0 until sets.length()) {
            val set = sets.optJSONObject(j) ?: continue
            if (set.optBoolean("done")) total += set.optInt("reps") * set.optDouble("weight")
        }
    }
    return total
}

private fun personalRecords(workouts: List<JSONObject>): List<Pair<String, Double>> {
    val best = mutableMapOf<String, Double>()
    workouts.forEach { workout ->
        val array = workout.optJSONArray("exercises") ?: return@forEach
        for (i in 0 until array.length()) {
            val exercise = array.optJSONObject(i) ?: continue
            val name = exercise.optString("name")
            val sets = exercise.optJSONArray("sets") ?: continue
            for (j in 0 until sets.length()) {
                val set = sets.optJSONObject(j) ?: continue
                if (!set.optBoolean("done")) continue
                val weight = set.optDouble("weight", 0.0)
                if (name.isNotBlank() && weight > (best[name] ?: 0.0)) best[name] = weight
            }
        }
    }
    return best.entries.sortedByDescending { it.value }.take(6).map { it.key to it.value }
}

private fun formatWorkoutDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
} catch (_: Exception) {
    iso
}
