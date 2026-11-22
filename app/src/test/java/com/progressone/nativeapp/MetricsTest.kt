package com.progressone.nativeapp

import com.progressone.nativeapp.data.SmartCounter
import com.progressone.nativeapp.data.Metrics
import com.progressone.nativeapp.data.LocalStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MetricsTest {
    @Test fun weightedAverageUsesWeights() {
        val grades = listOf(
            JSONObject().put("value", 6.0).put("weight", 1.0),
            JSONObject().put("value", 8.0).put("weight", 2.0)
        )
        assertEquals(22.0 / 3.0, Metrics.weightedAverage(grades)!!, 0.0001)
    }

    @Test fun habitDoneAndSkip() {
        val t = JSONObject().put("id", "habit1").put("type", "habit").put("target", 1)
        val done = LocalStore.Entry("habit1", "2026-11-04", 1.0, "done", "")
        assertTrue(Metrics.completion(t, listOf(done), "2026-11-04"))
        assertFalse(Metrics.completion(t, listOf(done.copy(status = "skip")), "2026-11-04"))
    }

    @Test fun reducingHabitRequiresARealEntry() {
        val t = JSONObject().put("id", "reduce1").put("type", "habit")
            .put("target", 2.0).put("bad", true)
        assertFalse(Metrics.completion(t, emptyList(), "2026-11-04"))
        assertTrue(Metrics.completion(t, listOf(LocalStore.Entry("reduce1", "2026-11-04", 1.0, "done", "")), "2026-11-04"))
        assertFalse(Metrics.completion(t, listOf(LocalStore.Entry("reduce1", "2026-11-04", 3.0, "done", "")), "2026-11-04"))
    }

    @Test fun durationTrackerCompletesAtTarget() {
        val t = JSONObject().put("id", "minutes1").put("type", "duration")
            .put("target", 30.0).put("unit", "min")
        assertFalse(Metrics.completion(t, emptyList(), "2026-11-04"))
        assertTrue(Metrics.completion(t, listOf(LocalStore.Entry("minutes1", "2026-11-04", 35.0, "done", "")), "2026-11-04"))
    }

    @Test fun averageTrackerCannotCompleteWithoutAnEntry() {
        val t = JSONObject().put("id", "average1").put("type", "average").put("target", 0.0)
        assertFalse(Metrics.completion(t, emptyList(), "2026-11-04"))
        assertFalse(Metrics.completion(t,
            listOf(LocalStore.Entry("average1", "2026-11-04", 4.0, "skip", "")), "2026-11-04"))
        assertTrue(Metrics.completion(t,
            listOf(LocalStore.Entry("average1", "2026-11-04", 4.0, "done", "")), "2026-11-04"))
    }

    @Test fun weeklyHabitMustNotCountEveryDayAsASeparateStreak() {
        val t = JSONObject().put("id", "weekly1").put("type", "habit")
            .put("freq", "week").put("target", 1.0)
        val entry = LocalStore.Entry("weekly1", "2026-11-02", 1.0, "done", "")
        assertEquals(0, Metrics.streak(t, listOf(entry), "2026-11-04"))
    }

    @Test fun dailyHabitDueOnlyOnSelectedWeekdays() {
        val t = JSONObject().put("id", "mon").put("type", "habit")
            .put("freq", "day").put("days", org.json.JSONArray().put(0))
        assertTrue(Metrics.due(t, "2026-11-02"))
        assertFalse(Metrics.due(t, "2026-11-03"))
    }

    @Test fun counterDoesNotClaimCompletionOnFirstTap() {
        val t = JSONObject().put("id", "pages").put("type", "counter").put("target", 5.0)
        val once = LocalStore.Entry("pages", "2026-11-04", 1.0, "done", "")
        assertFalse(Metrics.completion(t, listOf(once), "2026-11-04"))
        assertTrue(Metrics.completion(t, listOf(once.copy(value = 5.0)), "2026-11-04"))
    }

    @Test fun weeklyCounterAccumulatesAndResetsOnMonday() {
        val t = JSONObject().put("id", "week").put("type", "counter")
            .put("freq", "week").put("target", 6.0)
        val logs = listOf(LocalStore.Entry("week", "2026-11-02", 2.0, "done", ""),
            LocalStore.Entry("week", "2026-11-04", 4.0, "done", ""))
        assertTrue(Metrics.completion(t, logs, "2026-11-05"))
        assertFalse(Metrics.completion(t, logs, "2026-11-09"))
    }

    @Test fun counterUndoNeverBelowZero() {
        val t = JSONObject().put("id", "count").put("type", "counter").put("step", 1.0)
        val prior = listOf(LocalStore.Entry("count", "2026-11-04", 1.0, "done", ""))
        assertEquals(0.0, SmartCounter.next(t, prior, "2026-11-04", -1), .001)
        assertEquals(0.0, SmartCounter.next(t, emptyList(), "2026-11-04", -1), .001)
        assertEquals(2.0, SmartCounter.next(t, prior, "2026-11-04", 1), .001)
    }

    @Test fun reductionOnlyCountsExplicitCheckinAndSlipsInvalidate() {
        val t = JSONObject().put("id", "quit").put("type", "habit")
            .put("bad", true).put("target", 0.0)
        assertFalse(Metrics.completion(t, emptyList(), "2026-11-04"))
        assertTrue(Metrics.completion(t,
            listOf(LocalStore.Entry("quit", "2026-11-04", 0.0, "done", "")), "2026-11-04"))
        assertFalse(Metrics.completion(t,
            listOf(LocalStore.Entry("quit", "2026-11-04", 1.0, "done", "")), "2026-11-04"))
    }

    @Test fun weightedAverageEmpty() {
        assertNull(Metrics.weightedAverage(emptyList()))
    }
}
