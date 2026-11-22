package com.progressone.nativeapp.data

import org.json.JSONObject
import java.time.LocalDate

/** Shared math for the Sleep & Recovery module. All data stays in the local SQLite store. */
object SleepUtils {

    fun todayIso(): String = LocalDate.now().toString()

    fun parseMinutes(value: String): Int? {
        val parts = value.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** Duration in minutes, correctly handling nights that cross midnight. */
    fun durationBetween(bedtime: String, wakeTime: String): Int {
        val bed = parseMinutes(bedtime) ?: return 0
        val wake = parseMinutes(wakeTime) ?: return 0
        var diff = wake - bed
        if (diff <= 0) diff += 24 * 60
        return diff
    }

    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "--"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    /** 70% duration against the nightly target, 30% perceived quality, always 0..100. */
    fun score(entry: JSONObject?, targetHours: Double): Int {
        if (entry == null) return 0
        val target = if (targetHours > 0) targetHours else 8.0
        val hours = entry.optInt("minutes") / 60.0
        val quality = entry.optInt("quality", 3).coerceIn(1, 5)
        val durationPart = (hours / target).coerceIn(0.0, 1.0) * 70.0
        val qualityPart = quality / 5.0 * 30.0
        return (durationPart + qualityPart).toInt().coerceIn(0, 100)
    }

    fun lastNight(entries: List<JSONObject>): JSONObject? =
        entries.maxByOrNull { it.optString("date") }

    fun averageMinutesLastWeek(entries: List<JSONObject>): Int {
        val days = (0..6).map { LocalDate.now().minusDays(it.toLong()).toString() }.toSet()
        val relevant = entries.filter { it.optString("date") in days && it.optInt("minutes") > 0 }
        if (relevant.isEmpty()) return 0
        return relevant.sumOf { it.optInt("minutes") } / relevant.size
    }

    fun averageQuality(entries: List<JSONObject>): Double {
        val relevant = entries.filter { it.optInt("quality") in 1..5 }
        if (relevant.isEmpty()) return 0.0
        return relevant.map { it.optInt("quality") }.average()
    }
}
