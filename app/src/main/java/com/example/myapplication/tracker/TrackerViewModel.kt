package com.example.myapplication.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.GoalPeriod
import com.example.myapplication.data.model.GoalType
import com.example.myapplication.data.model.TrackerData
import com.example.myapplication.data.model.TrackerEntry
import com.example.myapplication.data.model.TrackerGoal
import com.example.myapplication.score.ModuleScoreProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<TrackerData> = _data.asStateFlow()

    private fun loadData(): TrackerData {
        return JsonStorage.load<TrackerData>(context, "tracker_data") ?: TrackerData(
            goals = emptyList(),
            entries = emptyList()
        )
    }

    private fun saveData(newData: TrackerData) {
        _data.value = newData
        JsonStorage.save(context, "tracker_data", newData)
    }

    fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun addGoal(name: String, type: GoalType, period: GoalPeriod, target: Int, icon: String = "🎯") {
        val newGoal = TrackerGoal(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
            period = period,
            targetCount = target,
            icon = icon,
            createdDate = getTodayDate()
        )
        saveData(_data.value.copy(goals = _data.value.goals + newGoal))
    }

    fun deleteGoal(goalId: String) {
        saveData(
            _data.value.copy(
                goals = _data.value.goals.filter { it.id != goalId },
                entries = _data.value.entries.filter { it.goalId != goalId }
            )
        )
    }

    fun incrementCount(goalId: String) {
        val today = getTodayDate()
        val currentEntries = _data.value.entries.toMutableList()
        val idx = currentEntries.indexOfFirst { it.goalId == goalId && it.date == today }
        if (idx >= 0) {
            val existing = currentEntries[idx]
            currentEntries[idx] = existing.copy(count = existing.count + 1, completed = true)
        } else {
            currentEntries.add(TrackerEntry(goalId = goalId, date = today, count = 1, completed = true))
        }
        saveData(_data.value.copy(entries = currentEntries))
    }

    fun logQuitSlip(goalId: String) {
        val today = getTodayDate()
        val currentEntries = _data.value.entries.toMutableList()
        val idx = currentEntries.indexOfFirst { it.goalId == goalId && it.date == today }
        if (idx >= 0) {
            currentEntries[idx] = currentEntries[idx].copy(count = currentEntries[idx].count + 1, completed = false)
        } else {
            currentEntries.add(TrackerEntry(goalId = goalId, date = today, count = 1, completed = false))
        }
        saveData(_data.value.copy(entries = currentEntries))
    }

    fun calculateScore(): Float {
        val goals = _data.value.goals
        if (goals.isEmpty()) return 100f
        val today = getTodayDate()

        var totalScore = 0f
        for (goal in goals) {
            val todayEntry = _data.value.entries.find { it.goalId == goal.id && it.date == today }
            when (goal.type) {
                GoalType.QUIT -> {
                    // Per QUIT, se non ci sono fallimenti registrati oggi lo score è 100
                    val slips = todayEntry?.count ?: 0
                    totalScore += if (slips == 0) 100f else (100f / (slips + 1))
                }
                GoalType.COUNT -> {
                    val count = todayEntry?.count ?: 0
                    val target = if (goal.targetCount <= 0) 1 else goal.targetCount
                    val ratio = (count.toFloat() / target.toFloat()).coerceAtMost(1f)
                    totalScore += ratio * 100f
                }
            }
        }
        return totalScore / goals.size
    }
}
