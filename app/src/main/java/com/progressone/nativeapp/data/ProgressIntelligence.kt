package com.progressone.nativeapp.data

import com.progressone.nativeapp.ui.AppSnapshot
import java.time.LocalDate
import kotlin.math.roundToInt

/** Deterministic, fully local progress intelligence shared by Home, Food, Gym and Calendar. */
enum class CoachTone { POSITIVE, ACTION, RECOVERY, NEUTRAL }

data class CoachInsight(
    val eyebrow: String,
    val title: String,
    val message: String,
    val actionLabel: String,
    val actionRoute: String,
    val tone: CoachTone = CoachTone.NEUTRAL,
)

data class ProgressBreakdown(
    val score: Int = 0,
    val moduleScores: Map<String, Int> = emptyMap(),
    val normalizedWeights: Map<String, Float> = emptyMap(),
    val completedActions: Int = 0,
)

/**
 * Weighted score engine. Only enabled modules with an actual signal for the day
 * contribute, so a module without data never silently penalizes the user.
 */
object SmartScoreEngine {
    private val baseWeights = mapOf(
        "track" to 0.26f,
        "tasks" to 0.18f,
        "food" to 0.16f,
        "gym" to 0.16f,
        "sleep" to 0.15f,
        "challenges" to 0.05f,
        "social" to 0.02f,
        "school" to 0.01f,
        "money" to 0.01f,
    )

    fun calculate(
        moduleScores: Map<String, Int>,
        enabledModules: Set<String>,
        priorities: Map<String, Float> = emptyMap(),
    ): ProgressBreakdown {
        val active = moduleScores.filter { (id, score) ->
            id in enabledModules && score in 0..100
        }
        if (active.isEmpty()) return ProgressBreakdown()

        val raw = active.keys.associateWith { id ->
            (baseWeights[id] ?: 0.03f) + (priorities[id] ?: 0f).coerceAtLeast(0f)
        }
        val totalWeight = raw.values.sum().takeIf { it > 0f } ?: 1f
        val normalized = raw.mapValues { (_, weight) -> weight / totalWeight }
        val score = normalized.entries.fold(0f) { total, (id, weight) ->
            total + weight * (active[id] ?: 0)
        }.roundToInt().coerceIn(0, 100)
        return ProgressBreakdown(
            score = score,
            moduleScores = active,
            normalizedWeights = normalized,
        )
    }
}

/** Computes daily module signals and their year history from local records. */
object DailyProgressEngine {
    fun today(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        socialScore: Int? = null,
        date: String = LocalDate.now().toString(),
        sleepTargetHours: Double = 8.0,
    ): ProgressBreakdown {
        val activeGoals = snapshot.trackers.filterNot { it.optBoolean("archived") }
        val archivedGoals = snapshot.trackers.filter { it.optBoolean("archived") }
        val scores = linkedMapOf<String, Int>()
        var actions = 0

        if ("track" in enabledModules && snapshot.trackers.isNotEmpty()) {
            val (done, _) = Metrics.daySummary(activeGoals, snapshot.logs, date)
            // Archiving changes today's denominator, but it must not erase an activity that was
            // already recorded. Evaluate a copy with the archive flag removed: the persisted
            // tracker is never mutated as a side effect of calculating history.
            val archivedCompletedGoals = archivedGoals.mapNotNull { tracker ->
                val historicalCopy = org.json.JSONObject(tracker.toString()).put("archived", false)
                if (Metrics.due(historicalCopy, date) &&
                    Metrics.completion(historicalCopy, snapshot.logs, date)
                ) historicalCopy else null
            }
            val scoredGoals = activeGoals + archivedCompletedGoals
            if (scoredGoals.isNotEmpty()) {
                scores["track"] = ProgressScore.dayScore(scoredGoals, snapshot.logs, date)
            }
            actions += done + archivedCompletedGoals.size
        }

        if ("tasks" in enabledModules) {
            val due = snapshot.tasks.filter { TaskHistory.dueDate(it)?.toString() == date }
            val completed = snapshot.tasks.count { task ->
                date in TaskHistory.completedDates(task)
            }
            if (due.isNotEmpty()) {
                val doneDue = due.count { task ->
                    date in TaskHistory.completedDates(task)
                }
                scores["tasks"] = (doneDue * 100.0 / due.size).roundToInt()
            } else if (completed > 0) {
                // A task completed after its due date still belongs to the day it was done.
                scores["tasks"] = 100
            }
            actions += completed
        }

        if ("food" in enabledModules) {
            val meals = snapshot.foods.filter { it.optString("date") == date }
            if (meals.isNotEmpty()) {
                val mealScores = meals.mapNotNull { meal ->
                    if (meal.has("foodScore") && meal.optInt("foodScore") > 0) meal.optInt("foodScore") else null
                }
                if (mealScores.isNotEmpty()) scores["food"] = mealScores.average().roundToInt()
                actions += meals.size
            }
        }

        val sleepEnabled = "sleep" in enabledModules
        val sleepEntry = if (sleepEnabled) {
            snapshot.sleeps.firstOrNull { it.optString("date") == date }
        } else null
        val sleepScore = sleepEntry?.let { SleepUtils.score(it, sleepTargetHours) }
        if (sleepEnabled && sleepEntry != null) {
            scores["sleep"] = sleepScore ?: 0
            actions += 1
        }

        if ("gym" in enabledModules) {
            val workouts = snapshot.gymWorkouts.filter { it.optString("date") == date }
            if (workouts.isNotEmpty()) {
                // Disabled Sleep is not an input to Gym. Only an enabled, recorded sleep entry may
                // adjust the session score.
                val recoveryFactor = sleepScore?.let { 0.70f + 0.30f * (it / 100f) } ?: 1f
                scores["gym"] = (100f * recoveryFactor).roundToInt().coerceIn(0, 100)
                actions += workouts.size
            }
        }

        if ("challenges" in enabledModules) {
            // Only currently eligible challenges belong in today's denominator. Completions from
            // challenges that have since ended remain historical activity and must not disappear.
            val eligibleChallenges = snapshot.challenges.filter { challenge ->
                challenge.optString("id").isNotBlank() &&
                    challengeIsScheduledOn(challenge, date)
            }
            val eligibleIds = eligibleChallenges.map { it.optString("id") }.toSet()
            val challengesById = snapshot.challenges
                .filter { it.optString("id").isNotBlank() }
                .associateBy { it.optString("id") }
            val doneToday = snapshot.logs.count {
                it.ownerId in eligibleIds && it.date == date && it.status == "done"
            }
            val endedDone = snapshot.logs.count { entry ->
                entry.ownerId !in eligibleIds &&
                    entry.date == date && entry.status == "done" &&
                    challengesById[entry.ownerId]?.let { challenge ->
                        challengeDateIsWithinWindow(challenge, date)
                    } == true
            }
            if (eligibleChallenges.isNotEmpty()) {
                scores["challenges"] = (doneToday * 100.0 / eligibleChallenges.size).roundToInt()
            } else if (endedDone > 0) {
                scores["challenges"] = 100
            }
            actions += doneToday + endedDone
        }

        if ("social" in enabledModules && socialScore != null) {
            scores["social"] = socialScore.coerceIn(0, 100)
        }

        if ("school" in enabledModules) {
            val dailyGrades = snapshot.grades.filter { it.optString("date") == date }
            Metrics.weightedAverage(dailyGrades)?.let { average ->
                scores["school"] = (average * 10).roundToInt().coerceIn(0, 100)
                actions += dailyGrades.size
            }
        }

        if ("money" in enabledModules) {
            val dailyTransactions = snapshot.transactions.filter { it.optString("date") == date }
            if (dailyTransactions.isNotEmpty()) {
                // A transaction is an action even when this month has no usable budget;
                // however, without that month's budget there is no money score to contribute.
                actions += dailyTransactions.size
                moneyScore(snapshot, date.take(7))?.let { scores["money"] = it }
            }
        }

        val overdue = if ("tasks" in enabledModules) {
            snapshot.tasks.count { task -> TaskHistory.isOverdue(task, date) }
        } else 0
        val pending = if ("tasks" in enabledModules) {
            snapshot.tasks.count {
                TaskHistory.dueDate(it)?.toString() == date && !it.optBoolean("done")
            }
        } else 0
        val priorities = buildMap {
            if (overdue > 0) put("tasks", 0.12f)
            else if (pending > 0) put("tasks", 0.07f)
            if (sleepEnabled && sleepScore != null && sleepScore < 55) put("sleep", 0.06f)
        }
        val result = SmartScoreEngine.calculate(scores, enabledModules, priorities)
        return result.copy(completedActions = actions)
    }

    fun forDate(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        date: String,
        sleepTargetHours: Double = 8.0,
    ): ProgressBreakdown = today(
        snapshot = snapshot,
        enabledModules = enabledModules,
        socialScore = null,
        date = date,
        sleepTargetHours = sleepTargetHours,
    )

    fun year(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        year: Int,
        sleepTargetHours: Double = 8.0,
    ): Map<String, ProgressBreakdown> {
        val start = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        val result = linkedMapOf<String, ProgressBreakdown>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            val iso = cursor.toString()
            result[iso] = forDate(snapshot, enabledModules, iso, sleepTargetHours)
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /** A streak tolerates an untouched current day and starts from yesterday in that case. */
    fun streak(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        today: LocalDate = LocalDate.now(),
        lookbackDays: Int = 730,
        sleepTargetHours: Double = 8.0,
    ): Int {
        var cursor = if (forDate(snapshot, enabledModules, today.toString(), sleepTargetHours).completedActions == 0) {
            today.minusDays(1)
        } else {
            today
        }
        var streak = 0
        while (streak < lookbackDays) {
            val day = forDate(snapshot, enabledModules, cursor.toString(), sleepTargetHours)
            if (day.completedActions <= 0) break
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun activeDays(year: Int, history: Map<String, ProgressBreakdown>): Int =
        history.count { (date, day) -> date.startsWith("$year-") && day.completedActions > 0 }

    fun monthsWithActivity(year: Int, history: Map<String, ProgressBreakdown>): Int =
        history.filterKeys { it.startsWith("$year-") }
            .filterValues { it.completedActions > 0 }
            .keys.map { it.substring(5, 7).toInt() }
            .distinct()
            .size
}

/** Background-computed Home inputs shared by Compose and JVM tests. */
data class HomeProgressModel(
    val enabledModules: Set<String>,
    val socialScore: Int?,
    val sleepTargetHours: Double,
    val dailyBreakdown: ProgressBreakdown,
    val streak: Int,
)

object HomeProgressEngine {
    fun calculate(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        socialScore: Int? = null,
        date: String = LocalDate.now().toString(),
        today: LocalDate = LocalDate.now(),
        sleepTargetHours: Double = 8.0,
        lookbackDays: Int = 365,
    ): HomeProgressModel = HomeProgressModel(
        enabledModules = enabledModules.toSet(),
        socialScore = socialScore,
        sleepTargetHours = sleepTargetHours,
        dailyBreakdown = DailyProgressEngine.today(
            snapshot = snapshot,
            enabledModules = enabledModules,
            socialScore = socialScore,
            date = date,
            sleepTargetHours = sleepTargetHours,
        ),
        streak = DailyProgressEngine.streak(
            snapshot = snapshot,
            enabledModules = enabledModules,
            today = today,
            lookbackDays = lookbackDays.coerceAtLeast(0),
            sleepTargetHours = sleepTargetHours,
        ),
    )
}

/**
 * Complete, deterministic input/output for the Calendar screen.
 *
 * The model is intentionally free of Compose and Android APIs so a UI can calculate it on a
 * background dispatcher and tests can verify the same result without a device.
 */
data class CalendarProgressModel(
    val year: Int,
    val selectedDate: String,
    val enabledModules: Set<String>,
    val sleepTargetHours: Double,
    val history: Map<String, ProgressBreakdown>,
    val selectedProgress: ProgressBreakdown,
    val currentStreak: Int,
    val activeDays: Int,
    val activeMonths: Int,
    val averageScore: Int,
)

object CalendarProgressEngine {
    fun calculate(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        year: Int,
        selectedDate: LocalDate,
        today: LocalDate = LocalDate.now(),
        sleepTargetHours: Double = 8.0,
        lookbackDays: Int = 730,
    ): CalendarProgressModel {
        val effectiveSleepTarget = sleepTargetHours.takeIf { it.isFinite() && it > 0.0 } ?: 8.0
        val history = DailyProgressEngine.year(
            snapshot = snapshot,
            enabledModules = enabledModules,
            year = year,
            sleepTargetHours = effectiveSleepTarget,
        )
        val selectedProgress = DailyProgressEngine.forDate(
            snapshot = snapshot,
            enabledModules = enabledModules,
            date = selectedDate.toString(),
            sleepTargetHours = effectiveSleepTarget,
        )
        val currentStreak = DailyProgressEngine.streak(
            snapshot = snapshot,
            enabledModules = enabledModules,
            today = today,
            lookbackDays = lookbackDays.coerceAtLeast(0),
            sleepTargetHours = effectiveSleepTarget,
        )
        val activeScores = history.values
            .filter { it.completedActions > 0 }
            .map { it.score }
        return CalendarProgressModel(
            year = year,
            selectedDate = selectedDate.toString(),
            enabledModules = enabledModules.toSet(),
            sleepTargetHours = effectiveSleepTarget,
            history = history,
            selectedProgress = selectedProgress,
            currentStreak = currentStreak,
            activeDays = DailyProgressEngine.activeDays(year, history),
            activeMonths = DailyProgressEngine.monthsWithActivity(year, history),
            averageScore = if (activeScores.isEmpty()) 0 else activeScores.average().roundToInt(),
        )
    }
}

private fun challengeIsScheduledOn(challenge: org.json.JSONObject, date: String): Boolean {
    if (challenge.optBoolean("finished")) return false
    return challengeDateIsWithinWindow(challenge, date)
}

private fun challengeDateIsWithinWindow(challenge: org.json.JSONObject, date: String): Boolean {
    val start = challenge.optString("startDate").trim()
    val end = challenge.optString("endDate").trim()
    if (start.isNotEmpty() && date < start) return false
    if (end.isNotEmpty() && date > end) return false
    return true
}

private fun moneyScore(snapshot: AppSnapshot, month: String): Int? {
    // Historical months must use their own persisted budget. In particular, do not
    // silently borrow the current month's budget for a month that has no budget.
    val budget = snapshot.budgetsByMonth[month]?.takeIf { it.isFinite() && it > 0.0 }
        ?: return null
    val spent = snapshot.transactions
        .filter { it.optString("type") != "income" && it.optString("date").startsWith(month) }
        .sumOf { it.optDouble("amount", 0.0) }
    val ratio = spent / budget
    return when {
        ratio <= 0.8 -> 100
        ratio >= 1.2 -> 0
        else -> ((1.2 - ratio) / 0.4 * 100).roundToInt()
    }
}

/** Fixed route order keeps coaching deterministic even when the enabled set is unordered. */
private val COACH_CORE_ROUTE_PRIORITY = listOf("track", "tasks", "calendar")
private val COACH_OPTIONAL_ROUTE_PRIORITY = listOf(
    "food", "gym", "sleep", "challenges", "social", "school", "money"
)

/** Returns an enabled module route, or Settings when no module route is available. */
private fun coachRoute(
    enabledModules: Set<String>,
    preferred: String,
    fallbacks: List<String> = emptyList(),
): String {
    val candidates = buildList {
        add(preferred)
        addAll(fallbacks)
        addAll(COACH_CORE_ROUTE_PRIORITY)
        addAll(COACH_OPTIONAL_ROUTE_PRIORITY)
    }
    return candidates.firstOrNull { it in enabledModules } ?: "settings"
}

/** Rule-based coaching. Every decision is derived from local data; no network or AI API is used. */
object LocalCoachEngine {
    fun home(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        totalScore: Int,
        sleepTargetHours: Double = 8.0,
        date: String = LocalDate.now().toString(),
    ): CoachInsight {
        val overdue = if ("tasks" in enabledModules) {
            snapshot.tasks.count { task -> TaskHistory.isOverdue(task, date) }
        } else 0
        val pending = if ("tasks" in enabledModules) {
            snapshot.tasks.count {
                TaskHistory.dueDate(it)?.toString() == date && !it.optBoolean("done")
            }
        } else 0
        val sleep = if ("sleep" in enabledModules) {
            snapshot.sleeps.firstOrNull { it.optString("date") == date }
        } else null
        val sleepScore = sleep?.let { SleepUtils.score(it, sleepTargetHours) } ?: 100
        val trained = "gym" in enabledModules && snapshot.gymWorkouts.any {
            it.optString("date") == date
        }
        val meals = if ("food" in enabledModules) {
            snapshot.foods.count { it.optString("date") == date }
        } else 0
        val activeGoals = snapshot.trackers.filterNot { it.optBoolean("archived") }
        val (doneGoals, dueGoals) = Metrics.daySummary(activeGoals, snapshot.logs, date)

        return when {
            overdue > 0 -> CoachInsight(
                eyebrow = "RESET TODAY",
                title = if (overdue == 1) "1 overdue commitment" else "$overdue overdue commitments",
                message = "Close the most important one first. A small reset brings balance back to the day.",
                actionLabel = "Open commitments",
                actionRoute = coachRoute(enabledModules, "tasks"),
                tone = CoachTone.ACTION,
            )
            sleepScore < 55 -> CoachInsight(
                eyebrow = "RECOVERY",
                title = "Protect your energy today",
                message = "Sleep is below your target. Choose mobility or a light load and resume gradually tomorrow.",
                actionLabel = "Open recovery",
                actionRoute = coachRoute(enabledModules, "sleep", listOf("track", "tasks")),
                tone = CoachTone.RECOVERY,
            )
            pending > 0 -> CoachInsight(
                eyebrow = "FIRST STEP",
                title = if (pending == 1) "1 commitment to close" else "$pending commitments to close",
                message = "Choose the smallest one and complete it now: it is the quickest way to lift today's score.",
                actionLabel = "View commitments",
                actionRoute = coachRoute(enabledModules, "tasks"),
                tone = CoachTone.ACTION,
            )
            trained -> CoachInsight(
                eyebrow = "ACTIVE RECOVERY",
                title = "Workout completed",
                message = "You built today's rhythm. Protect your recovery and avoid adding unnecessary load.",
                actionLabel = "View the year",
                actionRoute = coachRoute(enabledModules, "calendar", listOf("gym", "track", "tasks")),
                tone = CoachTone.POSITIVE,
            )
            meals == 0 && "food" in enabledModules -> CoachInsight(
                eyebrow = "NUTRITION",
                title = "Log your first meal",
                message = "A photo and an approximate portion are enough. Both local models estimate calories and macros on-device.",
                actionLabel = "Scan food",
                actionRoute = coachRoute(enabledModules, "food"),
                tone = CoachTone.ACTION,
            )
            dueGoals > doneGoals && "track" in enabledModules -> CoachInsight(
                eyebrow = "GOAL",
                title = "Light up the first goal",
                message = "You have ${dueGoals - doneGoals} ${if (dueGoals - doneGoals == 1) "goal waiting" else "goals waiting"}. Record the simplest check-in.",
                actionLabel = "Open habits",
                actionRoute = coachRoute(enabledModules, "track"),
                tone = CoachTone.ACTION,
            )
            totalScore >= 75 -> CoachInsight(
                eyebrow = "MOMENTUM",
                title = "Strong balance today",
                message = "You built a solid base. Keep the rhythm with one easy commitment instead of adding more load.",
                actionLabel = "Open calendar",
                actionRoute = coachRoute(enabledModules, "calendar", listOf("track", "tasks")),
                tone = CoachTone.POSITIVE,
            )
            else -> CoachInsight(
                eyebrow = "LOCAL COACH",
                title = "The next 1% is small",
                message = "Complete one easy action. Consistency grows from repeatable steps, not a perfect day.",
                actionLabel = "Choose a goal",
                actionRoute = coachRoute(enabledModules, "track", listOf("tasks", "calendar")),
                tone = CoachTone.NEUTRAL,
            )
        }
    }

    fun food(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        targetKcal: Double,
        targetProtein: Double,
        sleepTargetHours: Double = 8.0,
        date: String = LocalDate.now().toString(),
    ): CoachInsight {
        if ("food" !in enabledModules) {
            return CoachInsight(
                eyebrow = "FOOD DISABLED",
                title = "The Food module is off",
                message = "Your data is still saved. Turn Food back on in Settings whenever you want to use it.",
                actionLabel = "Open settings",
                actionRoute = "settings",
            )
        }
        val meals = snapshot.foods.filter { it.optString("date") == date }
        val known = meals.filter { it.optBoolean("nutritionKnown", it.optDouble("kcal", 0.0) > 0.0) }
        val kcal = known.sumOf { it.optDouble("kcal", 0.0) }
        val protein = known.sumOf { it.optDouble("protein", 0.0) }
        val recovery = if ("sleep" in enabledModules) {
            snapshot.sleeps.firstOrNull { it.optString("date") == date }
                ?.let { SleepUtils.score(it, sleepTargetHours) }
        } else null

        return when {
            known.isEmpty() -> CoachInsight(
                eyebrow = "NUTRITION COACH",
                title = "Log your first meal",
                message = "Take a photo: dish recognition and macro estimation happen entirely on your phone.",
                actionLabel = "Scan food",
                actionRoute = "food",
                tone = CoachTone.ACTION,
            )
            recovery != null && recovery < 55 -> CoachInsight(
                eyebrow = "RECOVERY + NUTRITION",
                title = "Avoid extreme restriction today",
                message = "With low recovery, focus on energy and protein. The Coach adapts its advice to your recorded sleep.",
                actionLabel = "Open recovery",
                actionRoute = "sleep",
                tone = CoachTone.RECOVERY,
            )
            targetProtein > 0 && protein < targetProtein * 0.55 -> CoachInsight(
                eyebrow = "PROTEIN",
                title = "Add a protein source",
                message = "You recorded ${protein.roundToInt()} g of protein against a ${targetProtein.roundToInt()} g target. Your next meal can close the gap.",
                actionLabel = "Add a meal",
                actionRoute = "food",
                tone = CoachTone.ACTION,
            )
            targetKcal > 0 && kcal > targetKcal * 1.15 -> CoachInsight(
                eyebrow = "BALANCING",
                title = "Move energy to your next meal",
                message = "Recorded calories are above target. Choose a lighter next meal without extreme restriction.",
                actionLabel = "Open diary",
                actionRoute = "food",
                tone = CoachTone.ACTION,
            )
            else -> CoachInsight(
                eyebrow = "NUTRITION COACH",
                title = "Your diary is balanced",
                message = "Calories and protein are in line with your targets. Keep the portion simple and log upcoming meals too.",
                actionLabel = "Scan the next meal",
                actionRoute = "food",
                tone = CoachTone.POSITIVE,
            )
        }
    }

    fun gym(
        snapshot: AppSnapshot,
        enabledModules: Set<String>,
        sleepTargetHours: Double = 8.0,
        date: String = LocalDate.now().toString(),
    ): CoachInsight {
        if ("gym" !in enabledModules) {
            return CoachInsight(
                eyebrow = "GYM DISABLED",
                title = "The Gym module is off",
                message = "Your workouts are not deleted. Turn the module back on to receive recovery guidance.",
                actionLabel = "Open settings",
                actionRoute = "settings",
            )
        }
        val sleep = if ("sleep" in enabledModules) {
            snapshot.sleeps.firstOrNull { it.optString("date") == date }
        } else null
        val sleepScore = sleep?.let { SleepUtils.score(it, sleepTargetHours) }
        val trainedToday = snapshot.gymWorkouts.any { it.optString("date") == date }
        val previousDate = LocalDate.parse(date).minusDays(1).toString()
        val trainedYesterday = snapshot.gymWorkouts.any { it.optString("date") == previousDate }
        return when {
            sleepScore != null && sleepScore < 55 -> CoachInsight(
                eyebrow = "LOW RECOVERY",
                title = "Choose light load or mobility",
                message = "Your recorded sleep indicates low recovery. Avoid a max effort and prioritize technique and control.",
                actionLabel = "Log sleep",
                actionRoute = "sleep",
                tone = CoachTone.RECOVERY,
            )
            trainedToday -> CoachInsight(
                eyebrow = "SESSION COMPLETE",
                title = "Protect your recovery",
                message = if (sleep != null) {
                    "You already trained today. A walk or mobility work is more useful than another heavy session."
                } else {
                    "You already trained today. Review your session or add mobility work instead of another heavy session."
                },
                actionLabel = if (sleep != null) "Open recovery" else "View history",
                actionRoute = if (sleep != null) "sleep" else "gym",
                tone = CoachTone.POSITIVE,
            )
            trainedYesterday -> CoachInsight(
                eyebrow = "ACTIVE RECOVERY",
                title = "After yesterday, choose quality over volume",
                message = "You trained yesterday. Rest or mobility is sensible; a moderate load is fine only if you feel ready.",
                actionLabel = if (sleep != null) "Open sleep" else "Open gym",
                actionRoute = if (sleep != null) "sleep" else "gym",
                tone = CoachTone.RECOVERY,
            )
            sleepScore != null -> CoachInsight(
                eyebrow = "TRAINING COACH",
                title = "Recovery is balanced",
                message = "Your recorded sleep supports the session. Follow the planned workout without adding unnecessary intensity.",
                actionLabel = "View templates",
                actionRoute = "gym",
                tone = CoachTone.POSITIVE,
            )
            else -> CoachInsight(
                eyebrow = "TRAINING COACH",
                title = "Your training plan is ready",
                message = "No sleep entry is available for today, so this plan does not use recovery data. Follow the planned workout without adding unnecessary intensity.",
                actionLabel = "View templates",
                actionRoute = "gym",
                tone = CoachTone.NEUTRAL,
            )
        }
    }
}
