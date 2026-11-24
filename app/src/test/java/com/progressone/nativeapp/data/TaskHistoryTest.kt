package com.progressone.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskHistoryTest {
    @Test
    fun legacyCompletedTaskUsesDueDate() {
        val task = JSONObject().put("done", true).put("due", "2026-11-01")

        assertEquals(listOf("2026-11-01"), TaskHistory.completedDates(task))
    }

    @Test
    fun completingTaskPreservesEarlierDates() {
        val task = JSONObject()
            .put("done", true)
            .put("due", "2026-11-01")
            .put("completedAt", "2026-11-02")
            .put("completedHistory", JSONArray().put("2026-11-01").put("2026-11-02"))

        TaskHistory.markCompleted(task, "2026-11-03")

        assertEquals(
            listOf("2026-11-01", "2026-11-02", "2026-11-03"),
            TaskHistory.completedDates(task),
        )
        assertEquals("2026-11-03", task.optString("completedAt"))
    }

    @Test
    fun undoingOneDayDoesNotEraseEarlierCompletion() {
        val task = JSONObject()
            .put("done", true)
            .put("due", "2026-11-01")
            .put("completedAt", "2026-11-02")
            .put("completedHistory", JSONArray().put("2026-11-01").put("2026-11-02"))

        TaskHistory.markPending(task, "2026-11-02")

        assertTrue(TaskHistory.wasCompletedOn(task, "2026-11-01"))
        assertFalse(TaskHistory.wasCompletedOn(task, "2026-11-02"))
        assertFalse(task.optBoolean("done"))
    }
}
