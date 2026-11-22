package com.progressone.nativeapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import com.progressone.nativeapp.data.ProgressScore
import com.progressone.nativeapp.data.SleepUtils
import com.progressone.nativeapp.data.SocialUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Home screen — everything revolves around the percentage circle. Minimal and clean. */
@Composable
fun TodayScreen(
    store: LocalStore,
    snapshot: AppSnapshot,
    isEnabled: (String) -> Boolean,
    onOpen: (String) -> Unit
) {
    val todayIso = LocalDate.now().toString()
    val month = todayIso.take(7)
    val activeGoals = snapshot.trackers.filterNot { it.optBoolean("archived") }
    val reduceMotion = remember(store.revision) { store.setting("reduce_motion", "false") == "true" }

    // Weekly tracker scores
    val weekScores = ProgressScore.lastSeven(activeGoals, snapshot.logs)
    val weeklyAvgTracker = if (weekScores.isNotEmpty()) weekScores.average().roundToInt() else 0
    val (goalsTodayDone, goalsTodayTotal) = Metrics.daySummary(activeGoals, snapshot.logs, todayIso)

    // Food weekly average
    val foodWeeklyAvg = remember(snapshot.foods) {
        val last7 = (0..6).map { LocalDate.now().minusDays(it.toLong()).toString() }
        val dailyAvgs = last7.map { day ->
            val meals = snapshot.foods.filter {
                it.optString("date") == day &&
                    it.optBoolean("nutritionKnown", it.optDouble("kcal", 0.0) > 0)
            }
            val scores = meals.mapNotNull { meal ->
                if (meal.has("foodScore") && meal.optInt("foodScore") > 0) meal.optInt("foodScore") else null
            }
            if (scores.isNotEmpty()) scores.average() else null
        }.filterNotNull()
        if (dailyAvgs.isNotEmpty()) dailyAvgs.average().roundToInt() else null
    }

    // Tasks completion
    val tasksCompletion = remember(snapshot.tasks) {
        val total = snapshot.tasks.size
        if (total == 0) null else (snapshot.tasks.count { it.optBoolean("done") }.toDouble() / total * 100).roundToInt()
    }

    // Challenges completion
    val challengesScore = remember(snapshot.challenges) {
        val total = snapshot.challenges.size
        if (total == 0) null else (snapshot.challenges.count { it.optBoolean("finished") }.toDouble() / total * 100).roundToInt()
    }

    // School average grade normalized to 100
    val schoolScore = remember(snapshot.grades) {
        Metrics.weightedAverage(snapshot.grades)?.let { (it * 10).roundToInt().coerceIn(0, 100) }
    }

    // Money: budget adherence for the current month
    val moneyData = remember(snapshot.transactions, snapshot.monthBudget, month) {
        val spent = snapshot.transactions
            .filter { it.str("type") != "income" && it.str("date").startsWith(month) }
            .sumOf { it.num("amount") }
        val earned = snapshot.transactions
            .filter { it.str("type") == "income" && it.str("date").startsWith(month) }
            .sumOf { it.num("amount") }
        val spentToday = snapshot.transactions
            .filter { it.str("type") != "income" && it.str("date") == todayIso }
            .sumOf { it.num("amount") }
        val budget = snapshot.monthBudget
        val score = if (budget > 0) {
            val ratio = spent / budget
            when {
                ratio <= 0.8 -> 100
                ratio >= 1.2 -> 0
                else -> ((1.2 - ratio) / 0.4 * 100).roundToInt()
            }
        } else null
        MoneySnapshot(spent, earned, spentToday, budget, score)
    }

    // Gym: active days over the last seven
    val gymData = remember(snapshot.gymWorkouts) {
        val weekStart = LocalDate.now().minusDays(6).toString()
        val week = snapshot.gymWorkouts.filter { it.optString("date") >= weekStart }
        GymSnapshot(
            todayCount = snapshot.gymWorkouts.count { it.optString("date") == todayIso },
            weekCount = week.size,
            activeDays = week.map { it.optString("date") }.toSet().size,
            lastDate = snapshot.gymWorkouts.maxOfOrNull { it.optString("date") } ?: ""
        )
    }

    // Sleep: last night + weekly average
    val sleepTarget = remember(store.revision) {
        (store.setting("sleep_target_hours", "8").toDoubleOrNull() ?: 8.0).coerceIn(4.0, 12.0)
    }
    val sleepData = remember(snapshot.sleeps, sleepTarget) {
        val last = SleepUtils.lastNight(snapshot.sleeps)
        val last7 = (0..6).map { LocalDate.now().minusDays(it.toLong()).toString() }.toSet()
        val recorded = snapshot.sleeps.filter { it.optString("date") in last7 }
        val weeklyScore = if (recorded.isEmpty()) 0
        else recorded.map { SleepUtils.score(it, sleepTarget) }.average().roundToInt()
        SleepSnapshot(
            minutes = last?.optInt("minutes") ?: 0,
            score = SleepUtils.score(last, sleepTarget),
            weekAverage = SleepUtils.averageMinutesLastWeek(snapshot.sleeps),
            weekNights = recorded.size,
            weekScore = weeklyScore
        )
    }

    // Social detox: usage stats must never block the UI thread.
    val context = LocalContext.current
    val socialScore by produceState<Int?>(initialValue = null, store.revision) {
        value = withContext(Dispatchers.IO) { runCatching { SocialUtils.score(context, store) }.getOrNull() }
    }
    val socialSummary = remember(store.revision) { SocialUtils.summary(store) }

    // Composite weekly score from enabled modules that actually have data.
    val contributions = buildList {
        if (activeGoals.isNotEmpty() || weekScores.isNotEmpty()) add("Tracker" to weeklyAvgTracker)
        if (isEnabled("food")) foodWeeklyAvg?.let { add("Food" to it) }
        if (snapshot.tasks.isNotEmpty()) tasksCompletion?.let { add("Tasks" to it) }
        if (isEnabled("challenges")) challengesScore?.let { add("Challenges" to it) }
        if (isEnabled("school")) schoolScore?.let { add("School" to it) }
        if (isEnabled("money")) moneyData.score?.let { add("Money" to it) }
        if (isEnabled("gym") && gymData.weekCount > 0) {
            add("Gym" to ((gymData.activeDays / 3.0) * 100).roundToInt().coerceIn(0, 100))
        }
        if (isEnabled("sleep") && sleepData.weekNights > 0) {
            add("Sleep" to sleepData.weekScore)
        }
        if (isEnabled("social")) socialScore?.let { add("Detox" to it) }
    }
    val weeklyComposite = if (contributions.isNotEmpty()) contributions.map { it.second }.average().roundToInt() else 0

    // Last 7 days consistency for tracker
    val weekDays = (0..6).map { LocalDate.now().minusDays(it.toLong()) }.reversed()
    val weekValues = weekDays.map { day -> ProgressScore.dayScore(activeGoals, snapshot.logs, day.toString()) }
    val dayLabels = weekDays.map { it.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)) }

    // Food summary for the day
    val todayMeals = snapshot.foods.filter {
        it.optString("date") == todayIso && it.optBoolean("nutritionKnown", false)
    }
    val todayTasksLeft = snapshot.tasks.count { !it.optBoolean("done") }
    val upcomingExams = snapshot.exams.count { it.str("date") >= todayIso }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date header
        item {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                color = Mist,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ---- HERO: Score of the week card ----
        item {
            val animatedScore by animateIntAsState(
                targetValue = weeklyComposite,
                animationSpec = tween(if (reduceMotion) 0 else 700, easing = FastOutSlowInEasing),
                label = "composite"
            )
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Eyebrow("SCORE OF THE WEEK", Mint)
                        Text(
                            "$animatedScore / 100",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Ice,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (contributions.isEmpty()) "Log a goal or a meal to start your week"
                            else "Live average of ${contributions.joinToString(", ") { it.first }}",
                            color = Mist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                    ProgressRing(animatedScore / 100f, Modifier.size(110.dp), sizeDp = 110.dp)
                }
            }
        }

        // Today at a glance
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallStat(
                    "TODAY",
                    if (goalsTodayTotal == 0) "No goals" else "$goalsTodayDone/$goalsTodayTotal",
                    Modifier.weight(1f),
                    Ice
                )
                SmallStat("TASKS LEFT", "$todayTasksLeft", Modifier.weight(1f), Mint)
                SmallStat(
                    "STREAK",
                    "${activeGoals.maxOfOrNull { Metrics.streak(it, snapshot.logs, todayIso) } ?: 0}d",
                    Modifier.weight(1f),
                    Lavender
                )
            }
        }

        // Last 7 days consistency
        item {
            FrostCard(Modifier.fillMaxWidth()) {
                SectionHeading("Last 7 days", "Tracker consistency, 0-100")
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    weekValues.forEachIndexed { index, value ->
                        val height by animateFloatAsState(
                            targetValue = (value / 100f).coerceIn(0f, 1f),
                            animationSpec = tween(
                                durationMillis = if (reduceMotion) 0 else 550,
                                delayMillis = if (reduceMotion) 0 else index * 45,
                                easing = FastOutSlowInEasing
                            ),
                            label = "weekBar$index"
                        )
                        val isToday = index == weekValues.lastIndex
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (value > 0) "$value" else "–",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) Ice else Mist,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(modifier = Modifier.height(64.dp), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    modifier = Modifier
                                        .width(26.dp)
                                        .fillMaxHeight(height.coerceAtLeast(0.05f))
                                        .background(
                                            if (isToday) Ice else Ice.copy(alpha = 0.32f),
                                            RoundedCornerShape(6.dp)
                                        )
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                dayLabels[index],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) Ice else Mist,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Food snapshot (only if meals logged today)
        if (todayMeals.isNotEmpty()) {
            val todayKcal = todayMeals.sumOf { it.optDouble("kcal", 0.0) }
            item {
                FrostCard(Modifier.fillMaxWidth(), onClick = { onOpen("food") }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.LocalDining, contentDescription = null, tint = Ice, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Eyebrow("TODAY'S FOOD", Mint)
                            Text(
                                "${todayMeals.size} meal${if (todayMeals.size != 1) "s" else ""} · ${todayKcal.roundToInt()} kcal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text("Tap to log more with AI photo recognition", color = Mist, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Mist)
                    }
                }
            }
        }

        // ---- Navigation grid — Your space ----
        item {
            Text("YOUR SPACE", style = MaterialTheme.typography.labelSmall, color = Mist)
        }

        // Tracker full width
        item {
            val subtitle = if (goalsTodayTotal == 0) "No goals due today · add your first"
            else "$goalsTodayDone of $goalsTodayTotal goals today · 7-day ${weeklyAvgTracker}%"
            HomeLinkWithSubtitle("Tracker", Icons.Rounded.TrackChanges, subtitle, Modifier.fillMaxWidth()) { onOpen("track") }
        }

        // Food / Wardrobe
        if (isEnabled("food") || isEnabled("wardrobe")) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isEnabled("food")) {
                        val info = if (todayMeals.isNotEmpty()) "${todayMeals.size} / 3 meals today" else "0 / 3 meals today"
                        HomeLinkWithSubtitle("Food", Icons.Rounded.Restaurant, info, Modifier.weight(1f)) { onOpen("food") }
                    } else Spacer(Modifier.weight(1f))
                    if (isEnabled("wardrobe")) {
                        val info = when {
                            snapshot.outfits.isNotEmpty() -> "${snapshot.outfits.size} outfits saved"
                            snapshot.garments.isNotEmpty() -> "${snapshot.garments.size} garments"
                            else -> "Build your wardrobe"
                        }
                        HomeLinkWithSubtitle("Wardrobe", Icons.Rounded.Checkroom, info, Modifier.weight(1f)) { onOpen("wardrobe") }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }

        // Gym / Sleep
        if (isEnabled("gym") || isEnabled("sleep")) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isEnabled("gym")) {
                        val info = when {
                            gymData.todayCount > 0 -> "Done today · ${gymData.weekCount}/week"
                            gymData.weekCount > 0 -> "${gymData.weekCount} sessions this week"
                            else -> "Start your first session"
                        }
                        HomeLinkWithSubtitle("Gym", Icons.Rounded.FitnessCenter, info, Modifier.weight(1f)) { onOpen("gym") }
                    } else Spacer(Modifier.weight(1f))
                    if (isEnabled("sleep")) {
                        val info = when {
                            sleepData.minutes > 0 -> "${SleepUtils.formatDuration(sleepData.minutes)} last night"
                            sleepData.weekNights > 0 -> "Avg ${SleepUtils.formatDuration(sleepData.weekAverage)}"
                            else -> "Record last night"
                        }
                        HomeLinkWithSubtitle("Sleep", Icons.Rounded.Bedtime, info, Modifier.weight(1f)) { onOpen("sleep") }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }

        // Social / Challenges
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (isEnabled("social")) {
                    HomeLinkWithSubtitle("Social detox", Icons.Rounded.SelfImprovement, socialSummary, Modifier.weight(1f)) { onOpen("social") }
                } else Spacer(Modifier.weight(1f))
                if (isEnabled("challenges")) {
                    val info = if (snapshot.challenges.isEmpty()) "No challenges yet"
                    else "${snapshot.challenges.count { it.optBoolean("finished") }} of ${snapshot.challenges.size} done"
                    HomeLinkWithSubtitle("Challenges", Icons.Rounded.EmojiEvents, info, Modifier.weight(1f)) { onOpen("challenges") }
                } else Spacer(Modifier.weight(1f))
            }
        }

        // School / Money
        if (isEnabled("school") || isEnabled("money")) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isEnabled("school")) {
                        val avg = Metrics.weightedAverage(snapshot.grades)
                        val info = when {
                            avg != null -> "Avg %.1f".format(Locale.US, avg)
                            upcomingExams > 0 -> "$upcomingExams exams upcoming"
                            else -> "Add a subject"
                        }
                        HomeLinkWithSubtitle("School", Icons.Rounded.School, info, Modifier.weight(1f)) { onOpen("school") }
                    } else Spacer(Modifier.weight(1f))
                    if (isEnabled("money")) {
                        val info = when {
                            moneyData.spentToday > 0 -> "${euro(moneyData.spentToday)} today"
                            moneyData.budget > 0 -> "${euro(moneyData.spent)} / ${euro(moneyData.budget)}"
                            moneyData.spent > 0 -> "${euro(moneyData.spent)} this month"
                            else -> "Log your first expense"
                        }
                        HomeLinkWithSubtitle("Money", Icons.Rounded.AccountBalanceWallet, info, Modifier.weight(1f)) { onOpen("money") }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }

        // Tasks full width
        item {
            HomeLinkWithSubtitle(
                "Tasks",
                Icons.Rounded.Checklist,
                if (todayTasksLeft == 0) "Nothing left today" else "$todayTasksLeft remaining",
                Modifier.fillMaxWidth()
            ) { onOpen("tasks") }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private data class MoneySnapshot(
    val spent: Double,
    val earned: Double,
    val spentToday: Double,
    val budget: Double,
    val score: Int?
)

private data class GymSnapshot(
    val todayCount: Int,
    val weekCount: Int,
    val activeDays: Int,
    val lastDate: String
)

private data class SleepSnapshot(
    val minutes: Int,
    val score: Int,
    val weekAverage: Int,
    val weekNights: Int,
    val weekScore: Int
)

@Composable
private fun HomeLinkWithSubtitle(name: String, icon: ImageVector, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    FrostCard(modifier, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Ice, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Mist, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Mist, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SummaryScreen(
    store: LocalStore,
    snapshot: AppSnapshot,
    showSchool: Boolean,
    showWardrobe: Boolean,
    showMoney: Boolean,
    showChallenges: Boolean,
    showOutfits: Boolean,
    onBack: () -> Unit
) {
    val active = snapshot.trackers.filterNot { it.optBoolean("archived") }
    val todayIso = LocalDate.now().toString()
    val todayScore = ProgressScore.dayScore(active, snapshot.logs, todayIso)
    val (todayDone, todayTotal) = Metrics.daySummary(active, snapshot.logs, todayIso)
    val week = ProgressScore.lastSeven(active, snapshot.logs)
    val weeklyAvg = if (week.isNotEmpty()) week.average().roundToInt() else 0

    val totalChallenges = snapshot.challenges.size
    val finishedChallenges = snapshot.challenges.count { it.optBoolean("finished") }
    val challengePercentage = if (totalChallenges == 0) 0 else ((finishedChallenges.toDouble() / totalChallenges) * 100).roundToInt()

    val averageGrade = Metrics.weightedAverage(snapshot.grades)
    val targetGrade = snapshot.goalGrade

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                PageHeading("INTELLIGENT TRACKER", "Progress", "Comprehensive progress analytics & scores")
            }
        }

        item {
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow("TODAY'S GOAL COMPLETION")
                        Text("$todayScore / 100", color = Ice, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (todayTotal == 0) "No active goals due today"
                            else "$todayDone of $todayTotal goals completed today",
                            color = Mist, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    ProgressRing(todayScore / 100f, Modifier.size(96.dp))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("7-DAY AVERAGE", "$weeklyAvg / 100", Modifier.weight(1f), Ice)
                SmallStat("CHALLENGES DONE", "$challengePercentage%", Modifier.weight(1f), Mint)
            }
        }

        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Text("Last 7 Days Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                week.forEachIndexed { index, value ->
                    val dayDate = LocalDate.now().minusDays((6 - index).toLong())
                    val daySummary = Metrics.daySummary(active, snapshot.logs, dayDate.toString())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            dayDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
                            modifier = Modifier.width(95.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (index == 6) Ice else MaterialTheme.colorScheme.onBackground
                        )
                        LinearProgressIndicator(
                            progress = { value / 100f },
                            modifier = Modifier.weight(1f).height(10.dp),
                            color = if (value >= 80) Mint else if (value >= 50) Ice else Lavender,
                            trackColor = GlassLight
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "$value% (${daySummary.first}/${daySummary.second})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mist
                        )
                    }
                }
            }
        }

        if (active.isNotEmpty()) {
            item {
                SectionHeading("Active Goals Status", "Current progress on your regular commitments")
            }
            items(active, key = { "sum_goal_${it.optString("id")}" }) { goal ->
                val points = ProgressScore.forGoal(goal, snapshot.logs, todayIso).roundToInt()
                val target = goal.optDouble("target", 1.0)
                val current = Metrics.progress(goal, snapshot.logs, todayIso)
                val isDone = Metrics.completion(goal, snapshot.logs, todayIso)

                FrostCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(goal.optString("name"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (goal.optBoolean("bad")) "Avoid habit: ${if (isDone) "Kept clean today" else "Not yet marked"}"
                                else "Progress: ${"%.1f".format(Locale.US, current).removeSuffix(".0")} / ${"%.1f".format(Locale.US, target).removeSuffix(".0")}",
                                color = Mist,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDone) Mint.copy(alpha = 0.2f) else GlassLight
                        ) {
                            Text(
                                "$points pts",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (isDone) Mint else Ice,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        if (showChallenges && snapshot.challenges.isNotEmpty()) {
            item {
                SectionHeading("Challenges Overview", "$finishedChallenges of $totalChallenges completed ($challengePercentage%)")
            }
            item {
                FrostCard(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { if (totalChallenges == 0) 0f else finishedChallenges.toFloat() / totalChallenges },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = Mint,
                        trackColor = GlassLight
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$finishedChallenges completed, ${snapshot.challenges.size - finishedChallenges} ongoing",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist
                    )
                }
            }
        }

        if (showSchool && snapshot.subjects.isNotEmpty()) {
            item {
                SectionHeading("Academic Overview", "Weighted grades & target status")
            }
            item {
                FrostCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Current Average", color = Mist, style = MaterialTheme.typography.labelSmall)
                            Text(
                                averageGrade?.let { "%.2f".format(Locale.US, it) } ?: "—",
                                color = Lavender,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Target", color = Mist, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "%.1f".format(Locale.US, targetGrade),
                                color = Ice,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
