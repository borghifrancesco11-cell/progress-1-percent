package com.example.myapplication.tasks

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.TasksData
import com.example.myapplication.score.ModuleScoreProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TasksScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "tasks"
    override val displayName: String = "Tasks"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<TasksData>(context, "tasks_data") ?: return 100f
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayTasks = data.tasks.filter { it.dueDate == today }

        if (todayTasks.isEmpty()) return 100f
        val completed = todayTasks.count { it.completed }
        return (completed.toFloat() / todayTasks.size.toFloat()) * 100f
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
