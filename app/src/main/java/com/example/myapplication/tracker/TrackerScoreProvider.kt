package com.example.myapplication.tracker

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.GoalType
import com.example.myapplication.data.model.TrackerData
import com.example.myapplication.score.ModuleScoreProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackerScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "tracker"
    override val displayName: String = "Tracker"

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<TrackerData>(context, "tracker_data") ?: return 100f
        val goals = data.goals
        if (goals.isEmpty()) return 100f
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        var total = 0f
        for (goal in goals) {
            val entry = data.entries.find { it.goalId == goal.id && it.date == today }
            when (goal.type) {
                GoalType.QUIT -> {
                    val slips = entry?.count ?: 0
                    total += if (slips == 0) 100f else (100f / (slips + 1))
                }
                GoalType.COUNT -> {
                    val count = entry?.count ?: 0
                    val target = if (goal.targetCount <= 0) 1 else goal.targetCount
                    total += (count.toFloat() / target.toFloat()).coerceAtMost(1f) * 100f
                }
            }
        }
        return total / goals.size
    }

    override fun isEnabled(): Boolean = true // Tracker è sempre abilitato
}
