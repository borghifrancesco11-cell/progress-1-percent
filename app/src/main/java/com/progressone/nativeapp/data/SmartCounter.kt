package com.progressone.nativeapp.data

import org.json.JSONObject
import kotlin.math.max

/** Explicit, undoable tap actions without double-counting earlier dates. */
object SmartCounter {
    fun step(tracker: JSONObject): Double = tracker.optDouble("step", if (tracker.optString("type") == "duration") 5.0 else 1.0)
        .takeIf { it.isFinite() && it > 0.0 } ?: 1.0

    fun corrected(current: Double, delta: Double): Double =
        max(0.0, (current.takeIf { it.isFinite() } ?: 0.0) + (delta.takeIf { it.isFinite() } ?: 0.0))

    /** Value for this one calendar date, even when the completion goal is weekly/monthly. */
    fun recordedToday(id: String, logs: List<LocalStore.Entry>, date: String = Metrics.day()): Double =
        logs.firstOrNull { it.ownerId == id && it.date == date && it.status != "skip" }?.value ?: 0.0

    fun next(tracker: JSONObject, logs: List<LocalStore.Entry>, date: String = Metrics.day(),
             direction: Int = 1): Double {
        val base = if (tracker.optString("type") == "target") Metrics.progress(tracker, logs, date)
            else recordedToday(tracker.optString("id"), logs, date)
        return corrected(base, step(tracker) * direction.coerceIn(-1, 1))
    }
}
