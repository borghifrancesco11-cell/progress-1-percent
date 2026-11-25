package com.progressone.nativeapp.data

import com.progressone.nativeapp.ui.AppSnapshot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressIntelligenceTest {
    private fun snapshot(
        trackers: List<JSONObject> = emptyList(),
        tasks: List<JSONObject> = emptyList(),
        challenges: List<JSONObject> = emptyList(),
        transactions: List<JSONObject> = emptyList(),
        foods: List<JSONObject> = emptyList(),
        sleeps: List<JSONObject> = emptyList(),
        gymWorkouts: List<JSONObject> = emptyList(),
        logs: List<LocalStore.Entry> = emptyList(),
        monthBudget: Double = 0.0,
        budgetsByMonth: Map<String, Double> = emptyMap(),
    ): AppSnapshot = AppSnapshot(
        trackers = trackers,
        tasks = tasks,
        challenges = challenges,
        garments = emptyList(),
        outfits = emptyList(),
        wears = emptyList(),
        subjects = emptyList(),
        grades = emptyList(),
        exams = emptyList(),
        transactions = transactions,
        packing = emptyList(),
        wishlist = emptyList(),
        foods = foods,
        sleeps = sleeps,
        gymTemplates = emptyList(),
        gymWorkouts = gymWorkouts,
        logs = logs,
        goalGrade = 7.0,
        monthBudget = monthBudget,
        budgetsByMonth = budgetsByMonth,
    )

    private fun challenge(
        id: String,
        startDate: String? = null,
        endDate: String? = null,
        finished: Boolean = false,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        if (startDate != null) put("startDate", startDate)
        if (endDate != null) put("endDate", endDate)
        put("finished", finished)
    }

    private fun log(id: String, date: String): LocalStore.Entry =
        LocalStore.Entry(id, date, 1.0, "done", "")

    @Test
    fun scoreUsesModuleWeightsAndMissingSignalsDoNotPenalize() {
        val weighted = SmartScoreEngine.calculate(
            moduleScores = mapOf("track" to 100, "food" to 50),
            enabledModules = setOf("track", "food", "sleep", "gym"),
        )

        // The weighted result is 81, rather than the unweighted average of 75.
        assertEquals(81, weighted.score)
        assertEquals(1f, weighted.normalizedWeights.values.sum(), 0.0001f)

        val task = JSONObject().apply {
            put("id", "task-1")
            put("due", "2026-11-05")
            put("completedAt", "2026-11-05")
            put("completedHistory", JSONArray().put("2026-11-05"))
            put("done", true)
        }
        val daily = DailyProgressEngine.forDate(
            snapshot = snapshot(tasks = listOf(task)),
            enabledModules = setOf("track", "tasks", "food", "gym", "sleep"),
            date = "2026-11-05",
        )

        assertEquals(100, daily.score)
        assertEquals(setOf("tasks"), daily.moduleScores.keys)
        assertEquals(1f, daily.normalizedWeights.getValue("tasks"), 0.0001f)
    }

    @Test
    fun taskCompletionUsesEveryDateInTaskHistory() {
        val task = JSONObject().apply {
            put("id", "task-1")
            put("due", "2026-11-01")
            put("completedAt", "2026-11-03")
            put(
                "completedHistory",
                JSONArray().put("2026-11-01").put("2026-11-03"),
            )
            put("done", true)
        }
        val fixture = snapshot(tasks = listOf(task))

        val firstCompletion = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            date = "2026-11-01",
        )
        assertEquals(100, firstCompletion.moduleScores["tasks"])
        assertEquals(1, firstCompletion.completedActions)

        val latestCompletion = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            date = "2026-11-03",
        )
        assertEquals(100, latestCompletion.moduleScores["tasks"])
        assertEquals(1, latestCompletion.completedActions)

        val untouchedDay = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            date = "2026-11-02",
        )
        assertFalse("tasks" in untouchedDay.moduleScores)
        assertEquals(0, untouchedDay.completedActions)
    }

    @Test
    fun challengeDenominatorOnlyIncludesUnfinishedChallengesInTheRequestedWindow() {
        val fixture = snapshot(
            challenges = listOf(
                challenge("active", "2026-11-05", "2026-11-05"),
                challenge("legacy"),
                challenge("future", "2026-11-06"),
                challenge("expired", endDate = "2026-11-04"),
                challenge("finished", "2026-11-05", "2026-11-05", finished = true),
            ),
            logs = listOf(
                log("active", "2026-11-05"),
                log("future", "2026-11-05"),
                log("expired", "2026-11-05"),
                log("finished", "2026-11-05"),
            ),
        )

        val result = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("challenges"),
            date = "2026-11-05",
        )

        // Only "active" and "legacy" are eligible. The finished challenge's valid completion
        // remains historical activity even though it is no longer in today's denominator.
        assertEquals(50, result.moduleScores["challenges"])
        assertEquals(2, result.completedActions)
    }

    @Test
    fun historicalMoneyUsesTheBudgetPersistedForThatMonth() {
        val fixture = snapshot(
            transactions = listOf(
                JSONObject().put("date", "2026-10-04").put("type", "expense").put("amount", 80.0),
                JSONObject().put("date", "2026-11-04").put("type", "expense").put("amount", 10.0),
            ),
            monthBudget = 50.0,
            budgetsByMonth = mapOf("2026-10" to 100.0),
        )

        val historical = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("money"),
            date = "2026-10-04",
        )
        assertEquals(100, historical.moduleScores["money"])

        // November has no persisted budget. The current budget must not be borrowed.
        val missingBudget = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("money"),
            date = "2026-11-04",
        )
        assertFalse("money" in missingBudget.moduleScores)
        assertEquals(0, missingBudget.score)
    }

    @Test
    fun taskHistoryDrivesStreakAndActiveDays() {
        val task = JSONObject().apply {
            put("id", "task-1")
            put("due", "2026-11-01")
            put("completedAt", "2026-11-02")
            put(
                "completedHistory",
                JSONArray().put("2026-11-01").put("2026-11-02"),
            )
            put("done", true)
        }
        val fixture = snapshot(tasks = listOf(task))
        val history = DailyProgressEngine.year(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            year = 2026,
        )

        assertEquals(2, DailyProgressEngine.activeDays(2026, history))
        assertEquals(
            2,
            DailyProgressEngine.streak(
                snapshot = fixture,
                enabledModules = setOf("tasks"),
                today = LocalDate.of(2026, 11, 3),
            ),
        )
    }

    @Test
    fun taskWithoutDueDateIsNeverOverdue() {
        val task = JSONObject().apply {
            put("id", "unscheduled")
            put("name", "No deadline")
            put("done", false)
        }
        val fixture = snapshot(tasks = listOf(task))

        assertFalse(TaskHistory.isOverdue(task, "2026-11-05"))
        val coach = LocalCoachEngine.home(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            totalScore = 0,
            date = "2026-11-05",
        )
        assertFalse(coach.title.contains("overdue", ignoreCase = true))
    }

    @Test
    fun taskCompletedBeforeDueDateIsAttributedToDueDate() {
        val task = JSONObject().apply {
            put("id", "task-early")
            put("due", "2026-11-05")
            put("completedAt", "2026-11-03")
            put("done", true)
        }
        val fixture = snapshot(tasks = listOf(task))

        assertEquals(listOf("2026-11-05"), TaskHistory.completedDates(task))
        val dueDay = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            date = "2026-11-05",
        )
        assertEquals(100, dueDay.moduleScores["tasks"])
        assertEquals(1, dueDay.completedActions)

        val earlyDay = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            date = "2026-11-03",
        )
        assertFalse("tasks" in earlyDay.moduleScores)
        assertEquals(0, earlyDay.completedActions)
    }

    @Test
    fun taskCompletedAfterDueDateRemainsOnActualCompletionDate() {
        val task = JSONObject().apply {
            put("id", "task-late")
            put("due", "2026-11-01")
            put("completedAt", "2026-11-03")
            put("done", true)
        }
        val fixture = snapshot(tasks = listOf(task))

        assertEquals(listOf("2026-11-03"), TaskHistory.completedDates(task))
        val dueDay = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            date = "2026-11-01",
        )
        assertEquals(0, dueDay.moduleScores["tasks"])
        val completedDay = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("tasks"),
            date = "2026-11-03",
        )
        assertEquals(100, completedDay.moduleScores["tasks"])
        assertEquals(1, completedDay.completedActions)
    }

    @Test
    fun archivedTrackerCompletionRemainsInHistoricalHeatmapAndStreak() {
        val tracker = JSONObject().apply {
            put("id", "archived-tracker")
            put("name", "Archived habit")
            put("archived", true)
        }
        val fixture = snapshot(
            trackers = listOf(tracker),
            logs = listOf(log("archived-tracker", "2026-11-05")),
        )
        val history = DailyProgressEngine.year(
            snapshot = fixture,
            enabledModules = setOf("track"),
            year = 2026,
        )

        assertEquals(1, DailyProgressEngine.activeDays(2026, history))
        assertTrue(tracker.optBoolean("archived"))
        assertEquals(
            1,
            DailyProgressEngine.streak(
                snapshot = fixture,
                enabledModules = setOf("track"),
                today = LocalDate.of(2026, 11, 6),
            ),
        )
    }

    @Test
    fun finishedChallengeCompletionRemainsInHistoricalHeatmapAndStreak() {
        val fixture = snapshot(
            challenges = listOf(
                challenge(
                    id = "finished-challenge",
                    startDate = "2026-11-01",
                    endDate = "2026-11-07",
                    finished = true,
                ),
            ),
            logs = listOf(log("finished-challenge", "2026-11-05")),
        )
        val history = DailyProgressEngine.year(
            snapshot = fixture,
            enabledModules = setOf("challenges"),
            year = 2026,
        )

        assertEquals(1, DailyProgressEngine.activeDays(2026, history))
        assertTrue(fixture.challenges.single().optBoolean("finished"))
        assertEquals(
            1,
            DailyProgressEngine.streak(
                snapshot = fixture,
                enabledModules = setOf("challenges"),
                today = LocalDate.of(2026, 11, 6),
            ),
        )
    }

    @Test
    fun disabledSleepDoesNotChangeGymScore() {
        val sleep = JSONObject().apply {
            put("date", "2026-11-05")
            put("minutes", 120)
            put("quality", 1)
        }
        val workout = JSONObject().put("date", "2026-11-05")
        val fixture = snapshot(sleeps = listOf(sleep), gymWorkouts = listOf(workout))

        val gymOnly = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("gym"),
            date = "2026-11-05",
        )
        assertEquals(100, gymOnly.moduleScores["gym"])
        assertFalse("sleep" in gymOnly.moduleScores)

        val gymWithSleep = DailyProgressEngine.forDate(
            snapshot = fixture,
            enabledModules = setOf("gym", "sleep"),
            date = "2026-11-05",
        )
        assertTrue(gymWithSleep.moduleScores.getValue("gym") < 100)
    }

    @Test
    fun coachRoutesOnlyTargetEnabledModulesAndAreDeterministic() {
        val lowSleep = JSONObject().apply {
            put("date", "2026-11-05")
            put("minutes", 120)
            put("quality", 1)
        }
        val workout = JSONObject().put("date", "2026-11-05")

        val homeWithCalendar = LocalCoachEngine.home(
            snapshot = snapshot(sleeps = listOf(lowSleep)),
            enabledModules = setOf("track", "tasks", "calendar"),
            totalScore = 90,
            date = "2026-11-05",
        )
        assertEquals("calendar", homeWithCalendar.actionRoute)

        val enabledWithoutCalendar = linkedSetOf("tasks", "track")
        val withoutCalendar = LocalCoachEngine.home(
            snapshot = snapshot(sleeps = listOf(lowSleep)),
            enabledModules = enabledWithoutCalendar,
            totalScore = 90,
            date = "2026-11-05",
        )
        assertNotEquals("calendar", withoutCalendar.actionRoute)
        assertTrue(withoutCalendar.actionRoute in enabledWithoutCalendar)
        assertEquals(
            withoutCalendar.actionRoute,
            LocalCoachEngine.home(
                snapshot = snapshot(sleeps = listOf(lowSleep)),
                enabledModules = linkedSetOf("track", "tasks"),
                totalScore = 90,
                date = "2026-11-05",
            ).actionRoute,
        )

        val gymWithoutSleep = LocalCoachEngine.gym(
            snapshot = snapshot(gymWorkouts = listOf(workout)),
            enabledModules = setOf("gym"),
            date = "2026-11-05",
        )
        assertEquals("gym", gymWithoutSleep.actionRoute)
        assertNotEquals("sleep", gymWithoutSleep.actionRoute)

        val gymWithSleep = LocalCoachEngine.gym(
            snapshot = snapshot(gymWorkouts = listOf(workout), sleeps = listOf(lowSleep)),
            enabledModules = setOf("gym", "sleep"),
            date = "2026-11-05",
        )
        assertEquals("sleep", gymWithSleep.actionRoute)

        val foodAction = LocalCoachEngine.food(
            snapshot = snapshot(),
            enabledModules = setOf("food"),
            targetKcal = 2200.0,
            targetProtein = 120.0,
            date = "2026-11-05",
        )
        assertEquals("food", foodAction.actionRoute)

        val gymWithoutSleepData = LocalCoachEngine.gym(
            snapshot = snapshot(),
            enabledModules = setOf("gym"),
            date = "2026-11-05",
        )
        assertEquals("gym", gymWithoutSleepData.actionRoute)
        assertFalse(gymWithoutSleepData.message.contains("sleep supports", ignoreCase = true))
    }
}
