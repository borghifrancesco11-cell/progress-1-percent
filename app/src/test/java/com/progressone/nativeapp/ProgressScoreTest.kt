package com.progressone.nativeapp

import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.ProgressScore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProgressScoreTest {
    private fun goal(id: String, type: String, target: Double, bad: Boolean = false) =
        JSONObject().put("id", id).put("type", type).put("name", id).put("target", target)
            .put("freq", "day").put("bad", bad)
    private fun entry(id: String, value: Double, status: String = "done") =
        LocalStore.Entry(id, "2026-11-05", value, status, "")

    @Test fun scoreStartsAtZero() {
        assertEquals(0, ProgressScore.dayScore(emptyList(), emptyList(), "2026-11-05"))
    }
    @Test fun partialGoalScoresProportionally() {
        assertEquals(25, ProgressScore.dayScore(listOf(goal("a", "counter", 8.0)),
            listOf(entry("a", 2.0)), "2026-11-05"))
    }
    @Test fun goalScoresCapAtHundred() {
        assertEquals(100, ProgressScore.dayScore(listOf(goal("a", "counter", 1.0)),
            listOf(entry("a", 200.0)), "2026-11-05"))
    }
    @Test fun equallyWeightedGoals() {
        val goals = listOf(goal("a", "counter", 10.0), goal("b", "habit", 1.0))
        assertEquals(60, ProgressScore.dayScore(goals,
            listOf(entry("a", 2.0), entry("b", 1.0)), "2026-11-05"))
    }
    @Test fun noCreditForUnconfirmedQuitDay() {
        val goals = listOf(goal("a", "habit", 0.0, bad = true))
        assertEquals(0, ProgressScore.dayScore(goals, emptyList(), "2026-11-05"))
        assertEquals(0, ProgressScore.dayScore(goals, listOf(entry("a", 0.0, "pending")), "2026-11-05"))
        assertEquals(100, ProgressScore.dayScore(goals, listOf(entry("a", 0.0)), "2026-11-05"))
        assertEquals(0, ProgressScore.dayScore(goals, listOf(entry("a", 1.0)), "2026-11-05"))
    }
    @Test fun archivedGoalsDoNotCount() {
        val g = goal("a", "counter", 2.0).put("archived", true)
        assertEquals(0, ProgressScore.dayScore(listOf(g), listOf(entry("a", 2.0)), "2026-11-05"))
    }
}
