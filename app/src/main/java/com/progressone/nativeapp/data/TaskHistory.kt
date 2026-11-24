package com.progressone.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject

/** Completion history kept inside the task payload, without requiring a database migration. */
object TaskHistory {
    private val datePattern = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun completedDates(task: JSONObject): List<String> {
        val dates = linkedSetOf<String>()
        val history = task.optJSONArray("completedHistory")
        if (history != null) {
            for (index in 0 until history.length()) {
                val value = history.optString(index)
                if (datePattern.matches(value)) dates += value
            }
        }
        task.optString("completedAt").takeIf(datePattern::matches)?.let(dates::add)
        // Legacy tasks have only a boolean flag; their due date is the best available date.
        if (task.optBoolean("done") && dates.isEmpty()) {
            task.optString("due").takeIf(datePattern::matches)?.let(dates::add)
        }
        return dates.sorted()
    }

    fun wasCompletedOn(task: JSONObject, date: String): Boolean = date in completedDates(task)

    fun completedInYear(task: JSONObject, year: Int): Boolean =
        completedDates(task).any { it.startsWith("$year-") }

    fun markCompleted(task: JSONObject, date: String) {
        require(datePattern.matches(date))
        val history = linkedSetOf<String>()
        history += completedDates(task)
        history += date
        task.put("completedHistory", JSONArray(history.sorted()))
        task.put("completedAt", date)
        task.put("done", true)
    }

    /** Undoing removes only the current day, preserving genuine earlier completions. */
    fun markPending(task: JSONObject, date: String) {
        require(datePattern.matches(date))
        val remaining = completedDates(task).filterNot { it == date }
        task.put("completedHistory", JSONArray(remaining))
        if (task.optString("completedAt") == date) task.remove("completedAt")
        task.put("done", false)
    }
}
