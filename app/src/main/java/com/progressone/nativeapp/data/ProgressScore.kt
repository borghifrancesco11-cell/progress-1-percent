package com.progressone.nativeapp.data

import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** Each active due objective has equal weight. Partial work earns partial points;
 * unlogged "quit" days NEVER earn points. The final score is always 0..100. */
object ProgressScore {
    fun forGoal(goal: JSONObject, logs: List<LocalStore.Entry>, date: String = Metrics.day()): Double {
        if (!Metrics.due(goal, date)) return 0.0
        val type = goal.optString("type", "habit")
        if (goal.optBoolean("bad")) return if (Metrics.completion(goal, logs, date)) 100.0 else 0.0
        if (type == "project") {
            val steps = goal.optJSONArray("milestones") ?: return 0.0
            if (steps.length() == 0) return 0.0
            val completed = (0 until steps.length()).count { steps.optJSONObject(it)?.optBoolean("done") == true }
            return 100.0 * completed / steps.length()
        }
        val target = goal.optDouble("target", 1.0)
        if (!target.isFinite() || target <= 0) return 0.0
        val achieved = if (type == "average") {
            logs.firstOrNull { it.ownerId == goal.optString("id") && it.date == date && it.status == "done" }
                ?.value ?: 0.0
        } else Metrics.progress(goal, logs, date)
        return (achieved / target * 100.0).takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0
    }

    fun dayScore(goals: List<JSONObject>, logs: List<LocalStore.Entry>, date: String = Metrics.day()): Int {
        val due = goals.filter { Metrics.due(it, date) }
        if (due.isEmpty()) return 0
        return due.map { forGoal(it, logs, date) }.average().roundToInt().coerceIn(0, 100)
    }

    fun lastSeven(goals: List<JSONObject>, logs: List<LocalStore.Entry>,
                  end: LocalDate = LocalDate.now()): List<Int> =
        (6 downTo 0).map { dayScore(goals, logs, end.minusDays(it.toLong()).toString()) }
}
