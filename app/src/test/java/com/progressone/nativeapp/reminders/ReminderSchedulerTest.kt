package com.progressone.nativeapp.reminders

import com.progressone.nativeapp.data.LocalStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderSchedulerTest {
    private fun tracker(
        id: String = "tracker-1",
        reminder: String = "09:00",
        archived: Boolean = false,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", "Read")
        put("reminder", reminder)
        put("freq", "day")
        put("archived", archived)
    }

    private fun task(
        id: String = "task-1",
        due: String = "2026-11-05",
        reminder: String = "09:00",
        done: Boolean = false,
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", "Submit form")
        put("due", due)
        put("reminder", reminder)
        put("done", done)
    }

    private fun log(id: String, date: String): LocalStore.Entry =
        LocalStore.Entry(id, date, 1.0, "done", "")

    @Test
    fun nextWakeupUsesEarliestValidTrackerAndTask() {
        val now = LocalDateTime.of(2026, 11, 5, 8, 0)
        val wakeup = ReminderScheduler.nextWakeup(
            trackers = listOf(tracker(reminder = "10:00")),
            tasks = listOf(task(reminder = "09:00")),
            logs = emptyList(),
            now = now,
        )

        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), wakeup)
    }

    @Test
    fun nextWakeupRejectsTasksWithoutValidDueDateOrTime() {
        val now = LocalDateTime.of(2026, 11, 5, 8, 0)
        val wakeup = ReminderScheduler.nextWakeup(
            trackers = emptyList(),
            tasks = listOf(
                task(id = "no-date", due = ""),
                task(id = "invalid-date", due = "2026-02-30"),
                task(id = "invalid-time", reminder = "24:00"),
                task(id = "completed", done = true),
            ),
            logs = emptyList(),
            now = now,
        )

        assertNull(wakeup)
    }

    @Test
    fun dueRemindersSelectOnlyValidDueRecords() {
        val now = LocalDateTime.of(2026, 11, 5, 10, 0)
        val deliveries = ReminderScheduler.dueReminders(
            trackers = listOf(
                tracker(id = "tracker-due"),
                tracker(id = "tracker-future", reminder = "11:00"),
                tracker(id = "tracker-completed"),
            ),
            tasks = listOf(
                task(id = "task-due"),
                task(id = "task-invalid-date", due = "2026-02-30"),
                task(id = "task-invalid-time", reminder = "not-a-time"),
                task(id = "task-completed", done = true),
            ),
            logs = listOf(log("tracker-completed", "2026-11-05")),
            now = now,
        )

        assertEquals(
            listOf(
                ReminderDestinationType.TRACKER,
                ReminderDestinationType.TASK,
            ),
            deliveries.map { it.destination.type },
        )
        assertEquals("tracker-due", deliveries[0].destination.id)
        assertEquals("task-due", deliveries[1].destination.id)
    }

    @Test
    fun notificationMarkerMakesRepeatedDeliveryIdempotent() {
        val tracker = ReminderDestination(ReminderDestinationType.TRACKER, "same-id")
        val task = ReminderDestination(ReminderDestinationType.TASK, "same-id")

        assertEquals("last_notified_same-id", ReminderScheduler.notificationSettingKey(tracker))
        assertEquals("last_notified_task_same-id", ReminderScheduler.notificationSettingKey(task))
        assertTrue(ReminderScheduler.alreadyNotified(tracker, "2026-11-05", "2026-11-05"))
        assertFalse(ReminderScheduler.alreadyNotified(tracker, "2026-11-05", "2026-11-04"))
        assertNotEquals(
            ReminderScheduler.notificationId(tracker),
            ReminderScheduler.notificationId(task),
        )
    }

    @Test
    fun globalNotificationToggleIsExplicit() {
        assertTrue(ReminderScheduler.notificationsEnabled("true"))
        assertTrue(ReminderScheduler.notificationsEnabled("TRUE"))
        assertFalse(ReminderScheduler.notificationsEnabled("false"))
        assertFalse(ReminderScheduler.notificationsEnabled(""))
    }
}
