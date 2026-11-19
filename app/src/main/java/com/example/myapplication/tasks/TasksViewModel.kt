package com.example.myapplication.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.Task
import com.example.myapplication.data.model.TasksData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TasksViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<TasksData> = _data.asStateFlow()

    private fun loadData(): TasksData {
        return JsonStorage.load<TasksData>(context, "tasks_data") ?: TasksData(
            tasks = emptyList()
        )
    }

    private fun saveData(newData: TasksData) {
        _data.value = newData
        JsonStorage.save(context, "tasks_data", newData)
    }

    fun addTask(title: String, description: String, dueDate: String, dueTime: String, notify: Boolean) {
        val newTask = Task(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            dueDate = dueDate,
            dueTime = dueTime,
            completed = false,
            notifyEnabled = notify
        )
        saveData(_data.value.copy(tasks = _data.value.tasks + newTask))
    }

    fun toggleTask(taskId: String) {
        val updated = _data.value.tasks.map {
            if (it.id == taskId) it.copy(completed = !it.completed) else it
        }
        saveData(_data.value.copy(tasks = updated))
    }

    fun deleteTask(taskId: String) {
        saveData(_data.value.copy(tasks = _data.value.tasks.filter { it.id != taskId }))
    }

    fun getRemainingTasksCount(): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return _data.value.tasks.count { it.dueDate == today && !it.completed }
    }
}
