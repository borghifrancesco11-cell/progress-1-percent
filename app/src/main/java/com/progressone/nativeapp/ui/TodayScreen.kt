package com.progressone.nativeapp.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.HomeProgressEngine
import com.progressone.nativeapp.data.HomeProgressModel
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import com.progressone.nativeapp.data.ProgressBreakdown
import com.progressone.nativeapp.data.ProgressScore
import com.progressone.nativeapp.data.SleepUtils
import com.progressone.nativeapp.data.SocialUtils
import com.progressone.nativeapp.data.TaskHistory
import com.progressone.nativeapp.data.LocalCoachEngine
import com.progressone.nativeapp.data.enabledModuleIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val enabledIds by produceState<Set<String>>(emptySet(), store.revision) {
        value = withContext(Dispatchers.IO) { store.enabledModuleIds() }
    }

    // Weekly tracker scores are context only; the hero score is the weighted daily score.
    val weekScores = ProgressScore.lastSeven(activeGoals, snapshot.logs)
    val weeklyAvgTracker = if (weekScores.isNotEmpty()) weekScores.average().roundToInt() else 0
    val (goalsTodayDone, goalsTodayTotal) = Metrics.daySummary(activeGoals, snapshot.logs, todayIso)

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

    // Sleep: last night + weekly average. A disabled Sleep module never reads or applies its
    // recovery target to Home scoring.
    val sleepTarget by produceState(8.0, store.revision, enabledIds) {
        value = withContext(Dispatchers.IO) {
            if ("sleep" in enabledIds) {
                (store.setting("sleep_target_hours", "8").toDoubleOrNull() ?: 8.0)
                    .coerceIn(4.0, 12.0)
            } else 8.0
        }
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
    val socialScore by produceState<Int?>(initialValue = null, store.revision, isEnabled("social")) {
        value = if (isEnabled("social")) {
            withContext(Dispatchers.IO) { runCatching { SocialUtils.score(context, store) }.getOrNull() }
        } else null
    }
    val socialSummary = remember(store.revision) { SocialUtils.summary(store) }

    // The daily score and streak are CPU-heavy history calculations. Keep them off the UI thread
    // and expose one deterministic model to the composable.
    val homeProgress by produceState<HomeProgressModel?>(
        initialValue = null,
        snapshot,
        enabledIds,
        socialScore,
        sleepTarget,
        todayIso,
    ) {
        if (enabledIds.isNotEmpty()) {
            value = withContext(Dispatchers.Default) {
                HomeProgressEngine.calculate(
                    snapshot = snapshot,
                    enabledModules = enabledIds,
                    socialScore = socialScore,
                    date = todayIso,
                    today = LocalDate.parse(todayIso),
                    sleepTargetHours = sleepTarget,
                    lookbackDays = 365,
                )
            }
        }
    }
    val currentHomeProgress = homeProgress?.takeIf {
        it.enabledModules == enabledIds &&
            it.socialScore == socialScore &&
            it.sleepTargetHours == sleepTarget
    }
    val dailyBreakdown = currentHomeProgress?.dailyBreakdown ?: ProgressBreakdown()
    val dailyStreak = currentHomeProgress?.streak ?: 0
    val coachInsight = remember(snapshot, enabledIds, dailyBreakdown.score, sleepTarget, todayIso) {
        LocalCoachEngine.home(
            snapshot = snapshot,
            enabledModules = enabledIds,
            totalScore = dailyBreakdown.score,
            sleepTargetHours = sleepTarget,
            date = todayIso,
        )
    }
    val moduleNames = mapOf(
        "track" to "Tracker", "tasks" to "Tasks", "food" to "Food", "gym" to "Gym",
        "sleep" to "Sleep", "challenges" to "Challenges", "social" to "Detox",
        "school" to "School", "money" to "Money",
    )
    val dailyContributions = dailyBreakdown.moduleScores.entries.map { (id, score) ->
        (moduleNames[id] ?: id.replaceFirstChar { it.uppercase() }) to score
    }

    // Last 7 days consistency for tracker
    val weekDays = (0..6).map { LocalDate.now().minusDays(it.toLong()) }.reversed()
    val weekValues = weekDays.map { day -> ProgressScore.dayScore(activeGoals, snapshot.logs, day.toString()) }
    val dayLabels = weekDays.map { it.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)) }

    // Food summary for the day
    val todayMeals = snapshot.foods.filter {
        it.optString("date") == todayIso &&
            (it.optBoolean("nutritionKnown", false) || it.optDouble("kcal", 0.0) > 0.0)
    }
    val todayTasksLeft = snapshot.tasks.count {
        !it.optBoolean("done") && TaskHistory.dueDate(it)?.toString() == todayIso
    }
    val upcomingExams = snapshot.exams.count { it.str("date") >= todayIso }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date header
        item {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
                color = Mist,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ---- HERO: one card with the score, the ring and the three key numbers ----
        item {
            val animatedScore by animateIntAsState(
                targetValue = dailyBreakdown.score,
                animationSpec = tween(if (reduceMotion) 0 else 700, easing = FastOutSlowInEasing),
                label = "daily-score"
            )
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ProgressRing(animatedScore / 100f, Modifier.size(96.dp), sizeDp = 96.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Eyebrow("TODAY'S SCORE", Mint)
                        Text(
                            "$animatedScore / 100",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Ice,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (dailyContributions.isEmpty()) "Log one action to start today's score"
                            else dailyContributions.joinToString(", ") { "${it.first} ${it.second}" },
                            color = Mist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = GlassLight)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SmallStat(
                        "GOALS",
                        if (goalsTodayTotal == 0) "—" else "$goalsTodayDone/$goalsTodayTotal",
                        Modifier.weight(1f),
                        Ice
                    )
                    SmallStat("TASKS", "$todayTasksLeft", Modifier.weight(1f), Mint)
                    SmallStat(
                        "STREAK",
                        "${dailyStreak}d",
                        Modifier.weight(1f),
                        Lavender
                    )
                }
            }
        }

        // ---- SLIDE BAR: 4 menus, each with 2 logically paired features ----
        item {
            data class SlideMenu(val name: String, val modules: List<HomeModule>)
            val foodModule = if (isEnabled("food")) HomeModule(
                "Food", Icons.Rounded.Restaurant,
                when {
                    todayMeals.isEmpty() -> "No meal yet"
                    else -> "${todayMeals.size} meal${if (todayMeals.size != 1) "s" else ""} · ${todayMeals.sumOf { it.optDouble("kcal", 0.0) }.roundToInt()} kcal"
                }, "food"
            ) else null
            val gymModule = if (isEnabled("gym")) HomeModule(
                "Gym", Icons.Rounded.FitnessCenter,
                when {
                    gymData.todayCount > 0 -> "Done today"
                    gymData.weekCount > 0 -> "${gymData.weekCount} this week"
                    else -> "Start"
                }, "gym"
            ) else null
            val sleepModule = if (isEnabled("sleep")) HomeModule(
                "Sleep", Icons.Rounded.Bedtime,
                when {
                    sleepData.minutes > 0 -> SleepUtils.formatDuration(sleepData.minutes)
                    sleepData.weekNights > 0 -> "avg ${SleepUtils.formatDuration(sleepData.weekAverage)}"
                    else -> "Log"
                }, "sleep"
            ) else null
            val trackerModule = HomeModule(
                "Tracker", Icons.Rounded.TrackChanges,
                if (goalsTodayTotal == 0) "No goals today"
                else "$goalsTodayDone/$goalsTodayTotal goals", "track"
            )
            val tasksModule = HomeModule(
                "Tasks", Icons.Rounded.Checklist,
                if (todayTasksLeft == 0) "All done" else "$todayTasksLeft left today", "tasks"
            )
            val schoolModule = if (isEnabled("school")) {
                val avg = Metrics.weightedAverage(snapshot.grades)
                HomeModule(
                    "School", Icons.Rounded.School,
                    when {
                        avg != null -> "Avg %.1f".format(Locale.US, avg)
                        upcomingExams > 0 -> "$upcomingExams exams"
                        else -> "Add subject"
                    }, "school"
                )
            } else null
            val moneyModule = if (isEnabled("money")) HomeModule(
                "Money", Icons.Rounded.AccountBalanceWallet,
                when {
                    moneyData.spentToday > 0 -> "${euro(moneyData.spentToday)} today"
                    moneyData.budget > 0 -> "${euro(moneyData.spent)} / ${euro(moneyData.budget)}"
                    moneyData.spent > 0 -> "${euro(moneyData.spent)} this month"
                    else -> "Add expense"
                }, "money"
            ) else null
            val challengesModule = if (isEnabled("challenges")) HomeModule(
                "Challenges", Icons.Rounded.EmojiEvents,
                if (snapshot.challenges.isEmpty()) "None yet"
                else "${snapshot.challenges.count { it.optBoolean("finished") }} of ${snapshot.challenges.size} done",
                "challenges"
            ) else null
            val wardrobeModule = if (isEnabled("wardrobe")) HomeModule(
                "Wardrobe", Icons.Rounded.Checkroom,
                when {
                    snapshot.outfits.isNotEmpty() -> "${snapshot.outfits.size} outfits"
                    snapshot.garments.isNotEmpty() -> "${snapshot.garments.size} garments"
                    else -> "Build it"
                }, "wardrobe"
            ) else null
            val detoxModule = if (isEnabled("social")) HomeModule(
                "Detox", Icons.Rounded.SelfImprovement, socialSummary, "social"
            ) else null

            val menus = listOf(
                SlideMenu("Body", listOfNotNull(foodModule, gymModule).take(2)),
                SlideMenu("Recovery", listOfNotNull(sleepModule, trackerModule).take(2)),
                SlideMenu("Mind", listOfNotNull(tasksModule, schoolModule ?: detoxModule).take(2)),
                SlideMenu("Life", listOfNotNull(moneyModule, challengesModule, wardrobeModule, detoxModule).take(2))
            )

            val pagerState = rememberPagerState(pageCount = { menus.size })
            val coroutineScope = rememberCoroutineScope()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Slide bar selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassLight, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    menus.forEachIndexed { index, menu ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) Ice.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        if (reduceMotion) pagerState.scrollToPage(index)
                                        else pagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = menu.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Ice else Mist,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Horizontal swipeable pager for the 4 menus
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val pair = menus[page].modules
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pair.forEach { module ->
                            HomeTile(module, Modifier.weight(1f)) { onOpen(module.route) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                        if (pair.isEmpty()) {
                            FrostCard(Modifier.fillMaxWidth()) {
                                Text("No modules enabled in this category", color = Mist, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private data class HomeModule(
    val name: String,
    val icon: ImageVector,
    val subtitle: String,
    val route: String
)

@Composable
private fun HomeRow(name: String, icon: ImageVector, subtitle: String, onClick: () -> Unit) {
    FrostCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Ice, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Mist, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Mist, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HomeTile(module: HomeModule, modifier: Modifier, onClick: () -> Unit) {
    FrostCard(modifier, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(module.icon, contentDescription = null, tint = Ice, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    module.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    module.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
