package com.example.myapplication.gym

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GymViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val settingsRepo = SettingsRepository(context)

    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<GymData> = _data.asStateFlow()

    private val _userProfile = MutableStateFlow(settingsRepo.settings.value)
    val userProfile: StateFlow<AppSettings> = _userProfile.asStateFlow()

    private fun loadData(): GymData {
        return JsonStorage.load<GymData>(context, "gym_data") ?: GymData(
            templates = listOf(
                WorkoutTemplate(
                    id = "t1",
                    name = "Upper Body (Petto & Braccia)",
                    exercises = listOf(
                        WorkoutExercise("e1", "Panca Piana con Bilanciere", listOf(WorkoutSet(10, 60f), WorkoutSet(8, 70f), WorkoutSet(6, 75f)), 90),
                        WorkoutExercise("e2", "Distensioni Manubri Inclinata", listOf(WorkoutSet(10, 22f), WorkoutSet(10, 24f), WorkoutSet(8, 24f)), 75),
                        WorkoutExercise("e3", "Curl Bicipiti con Manubri", listOf(WorkoutSet(12, 14f), WorkoutSet(10, 16f), WorkoutSet(8, 16f)), 60),
                        WorkoutExercise("e4", "French Press Tricipiti", listOf(WorkoutSet(12, 25f), WorkoutSet(10, 30f), WorkoutSet(10, 30f)), 60)
                    )
                ),
                WorkoutTemplate(
                    id = "t2",
                    name = "Lower Body (Gambe & Core)",
                    exercises = listOf(
                        WorkoutExercise("e5", "Squat con Bilanciere", listOf(WorkoutSet(10, 80f), WorkoutSet(8, 90f), WorkoutSet(6, 100f)), 120),
                        WorkoutExercise("e6", "Leg Press", listOf(WorkoutSet(12, 140f), WorkoutSet(10, 160f), WorkoutSet(10, 160f)), 90),
                        WorkoutExercise("e7", "Leg Curl Femorali", listOf(WorkoutSet(12, 40f), WorkoutSet(10, 45f)), 60),
                        WorkoutExercise("e8", "Plank Addome", listOf(WorkoutSet(60, 0f), WorkoutSet(60, 0f)), 45)
                    )
                )
            ),
            workouts = emptyList(),
            personalRecords = mapOf("Panca Piana con Bilanciere" to 75f, "Squat con Bilanciere" to 100f)
        )
    }

    private fun saveData(newData: GymData) {
        _data.value = newData
        JsonStorage.save(context, "gym_data", newData)
    }

    fun getToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun saveCompletedWorkout(name: String, durationMinutes: Int, exercises: List<WorkoutExercise>) {
        val today = getToday()
        val newWorkout = Workout(
            id = UUID.randomUUID().toString(),
            name = name,
            exercises = exercises,
            date = today,
            durationMinutes = durationMinutes,
            completed = true
        )

        // Aggiorna PR se ci sono nuovi carichi massimi
        val prs = _data.value.personalRecords.toMutableMap()
        exercises.forEach { ex ->
            val maxWeight = ex.sets.filter { it.completed }.maxOfOrNull { it.weight } ?: 0f
            if (maxWeight > (prs[ex.exerciseName] ?: 0f)) {
                prs[ex.exerciseName] = maxWeight
            }
        }

        saveData(_data.value.copy(workouts = _data.value.workouts + newWorkout, personalRecords = prs))
    }

    fun addTemplate(name: String, exercises: List<WorkoutExercise>) {
        val template = WorkoutTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            exercises = exercises
        )
        saveData(_data.value.copy(templates = _data.value.templates + template))
    }

    fun deleteTemplate(id: String) {
        saveData(_data.value.copy(templates = _data.value.templates.filter { it.id != id }))
    }

    fun getTodayWorkouts(): List<Workout> {
        val today = getToday()
        return _data.value.workouts.filter { it.date == today }
    }
}
