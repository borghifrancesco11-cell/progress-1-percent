package com.progressone.nativeapp.data

import com.progressone.nativeapp.ui.AppSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarProgressModelTest {
    private fun snapshot(
        tasks: List<JSONObject> = emptyList(),
        sleeps: List<JSONObject> = emptyList(),
        trackers: List<JSONObject> = emptyList(),
        challenges: List<JSONObject> = emptyList(),
        logs: List<LocalStore.Entry> = emptyList(),
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
        transactions = emptyList(),
        packing = emptyList(),
        wishlist = emptyList(),
        foods = emptyList(),
        sleeps = sleeps,
        gymTemplates = emptyList(),
        gymWorkouts = emptyList(),
        logs = logs,
        goalGrade = 7.0,
        monthBudget = 0.0,
        budgetsByMonth = emptyMap(),
    )

    @Test
    fun modelUsesConfiguredSleepTargetForAnnualHistory() {
        val sleep = JSONObject().apply {
            put("date", "2026-11-05")
            put("minutes", 360)
            put("quality", 3)
        }
        val fixture = snapshot(sleeps = listOf(sleep))

        val eightHourTarget = CalendarProgressEngine.calculate(
            snapshot = fixture,
            enabledModules = setOf("sleep"),
            year = 2026,
            selectedDate = LocalDate.of(2026, 11, 5),
            today = LocalDate.of(2026, 11, 5),
            sleepTargetHours = 8.0,
        )
        val sixHourTarget = CalendarProgressEngine.calculate(
            snapshot = fixture,
            enabledModules = setOf("sleep"),
            year = 2026,
            selectedDate = LocalDate.of(2026, 11, 5),
            today = LocalDate.of(2026, 11, 5),
            sleepTargetHours = 6.0,
        )

        assertEquals(70, eightHourTarget.history.getValue("2026-11-05").moduleScores["sleep"])
        assertEquals(88, sixHourTarget.history.getValue("2026-11-05").moduleScores["sleep"])
        assertEquals(70, eightHourTarget.selectedProgress.moduleScores["sleep"])
        assertEquals(88, sixHourTarget.selectedProgress.moduleScores["sleep"])
    }

    @Test
    fun modelProducesAnnualActivityAndStreakFromEffectiveTaskDates() {
        val task = JSONObject().apply {
            put("id", "calendar-task")
            put("due", "2026-11-05")
            put("completedAt", "2026-11-03")
            put("done", true)
        }
        val model = CalendarProgressEngine.calculate(
            snapshot = snapshot(tasks = listOf(task)),
            enabledModules = setOf("tasks"),
            year = 2026,
            selectedDate = LocalDate.of(2026, 11, 5),
            today = LocalDate.of(2026, 11, 6),
            sleepTargetHours = 8.0,
        )

        assertEquals(365, model.history.size)
        assertEquals(1, model.activeDays)
        assertEquals(1, model.activeMonths)
        assertEquals(1, model.currentStreak)
        assertEquals(100, model.selectedProgress.moduleScores["tasks"])
        assertTrue(model.history.getValue("2026-11-05").completedActions > 0)
        assertEquals(0, model.history.getValue("2026-11-03").completedActions)
    }
}
