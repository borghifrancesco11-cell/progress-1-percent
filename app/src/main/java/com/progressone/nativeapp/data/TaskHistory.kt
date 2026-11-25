package com.progressone.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Completion history kept inside the task payload, without requiring a database migration.
 *
 * A task completed before its deadline is attributed to the deadline. A task completed after its
 * deadline remains attributed to the day it was actually completed. This keeps daily progress and
 * streaks deterministic without rewriting the recorded completion date.
 */
object TaskHistory {
    private val datePattern = Regex("\\d{4}-\\d{2}-\\d{2}")

    /** The task's valid calendar due date, if one exists. */
    fun dueDate(task: JSONObject): LocalDate? {
        val raw = task.optString("due").trim()
        if (raw.isEmpty()) return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    /** A task without a valid due date can never be overdue. */
    fun isOverdue(task: JSONObject, date: String): Boolean {
        val current = parseDate(date) ?: return false
        val due = dueDate(task) ?: return false
        return !task.optBoolean("done") && due.isBefore(current)
    }

    /**
     * Effective dates used by daily progress, history and streaks.
     *
     * Early completions are moved to the due date; late completions stay on their actual date.
     */
    fun completedDates(task: JSONObject): List<String> {
        val due = dueDate(task)?.toString()
        return recordedCompletionDates(task)
            .map { completed ->
                if (due != null && completed < due) due else completed
            }
            .toSortedSet()
            .toList()
    }

    fun wasCompletedOn(task: JSONObject, date: String): Boolean = date in completedDates(task)

    fun completedInYear(task: JSONObject, year: Int): Boolean =
        completedDates(task).any { it.startsWith("$year-") }

    /** Record the actual completion date. Attribution to a due date is derived, never destructive. */
    fun markCompleted(task: JSONObject, date: String) {
        require(isValidDate(date)) { "Invalid completion date" }
        val history = linkedSetOf<String>()
        history += recordedCompletionDates(task)
        history += date
        task.put("completedHistory", JSONArray(history.sorted()))
        task.put("completedAt", date)
        task.put("done", true)
    }

    /** Undoing removes only the selected recorded date, preserving genuine earlier completions. */
    fun markPending(task: JSONObject, date: String) {
        require(isValidDate(date)) { "Invalid completion date" }
        val remaining = recordedCompletionDates(task).filterNot { it == date }
        task.put("completedHistory", JSONArray(remaining))
        if (task.optString("completedAt") == date) task.remove("completedAt")
        task.put("done", false)
    }

    /** Raw dates are retained so an undo can remove an early completion without touching history. */
    internal fun recordedCompletionDates(task: JSONObject): Set<String> {
        val dates = linkedSetOf<String>()
        val history = task.optJSONArray("completedHistory")
        if (history != null) {
            for (index in 0 until history.length()) {
                val value = history.optString(index).trim()
                if (isValidDate(value)) dates += value
            }
        }
        task.optString("completedAt").trim().takeIf(::isValidDate)?.let(dates::add)
        // Legacy tasks have only a boolean flag; their due date is the best available date.
        if (task.optBoolean("done") && dates.isEmpty()) {
            dueDate(task)?.toString()?.let(dates::add)
        }
        return dates
    }

    private fun parseDate(value: String): LocalDate? {
        val iso = value.trim()
        if (!isValidDate(iso)) return null
        return runCatching { LocalDate.parse(iso) }.getOrNull()
    }

    private fun isValidDate(value: String): Boolean =
        datePattern.matches(value) && runCatching { LocalDate.parse(value) }.isSuccess
}
