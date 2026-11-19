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
        val loaded = JsonStorage.load<GymData>(context, "gym_data")
        if (loaded != null && loaded.templates.isNotEmpty()) {
            return loaded
        }
        return GymData(
            templates = getDefaultTemplatesForType(settingsRepo.settings.value.workoutType),
            workouts = emptyList(),
            personalRecords = emptyMap()
        )
    }

    fun getDefaultTemplatesForType(type: String): List<WorkoutTemplate> {
        return when (type) {
            "calisthenics" -> listOf(
                WorkoutTemplate(
                    id = "cali_1",
                    name = "Calisthenics (Pull & Push)",
                    type = "calisthenics",
                    exercises = listOf(
                        WorkoutExercise("c1", "Trazioni alla Sbarra (Pull-up)", listOf(WorkoutSet(8, 0f), WorkoutSet(8, 0f), WorkoutSet(6, 0f)), 90, isBodyweight = true),
                        WorkoutExercise("c2", "Dip alle Parallele", listOf(WorkoutSet(10, 0f), WorkoutSet(10, 0f), WorkoutSet(8, 0f)), 90, isBodyweight = true),
                        WorkoutExercise("c3", "Push-up a Terra (Piegamenti)", listOf(WorkoutSet(15, 0f), WorkoutSet(15, 0f), WorkoutSet(12, 0f)), 60, isBodyweight = true),
                        WorkoutExercise("c4", "Australian Pull-up (Orizzontali)", listOf(WorkoutSet(12, 0f), WorkoutSet(10, 0f), WorkoutSet(10, 0f)), 60, isBodyweight = true)
                    )
                ),
                WorkoutTemplate(
                    id = "cali_2",
                    name = "Core & Skills Calisthenics",
                    type = "calisthenics",
                    exercises = listOf(
                        WorkoutExercise("c5", "Hanging Leg Raises (Gambe alla Sbarra)", listOf(WorkoutSet(10, 0f), WorkoutSet(10, 0f), WorkoutSet(8, 0f)), 75, isBodyweight = true),
                        WorkoutExercise("c6", "L-Sit Hold (Secondi)", listOf(WorkoutSet(20, 0f), WorkoutSet(15, 0f), WorkoutSet(15, 0f)), 60, isBodyweight = true),
                        WorkoutExercise("c7", "Pike Push-up (Progressione Handstand)", listOf(WorkoutSet(8, 0f), WorkoutSet(8, 0f), WorkoutSet(6, 0f)), 75, isBodyweight = true),
                        WorkoutExercise("c8", "Plank Isometrico (Secondi)", listOf(WorkoutSet(60, 0f), WorkoutSet(60, 0f)), 45, isBodyweight = true)
                    )
                )
            )
            "hybrid" -> listOf(
                WorkoutTemplate(
                    id = "hyb_1",
                    name = "Upper Ibrido (Pesi & Sbarra)",
                    type = "hybrid",
                    exercises = listOf(
                        WorkoutExercise("h1", "Trazioni alla Sbarra", listOf(WorkoutSet(8, 0f), WorkoutSet(8, 0f), WorkoutSet(6, 0f)), 90, isBodyweight = true),
                        WorkoutExercise("h2", "Panca Piana con Bilanciere", listOf(WorkoutSet(8, 60f), WorkoutSet(8, 65f), WorkoutSet(6, 70f)), 90),
                        WorkoutExercise("h3", "Dip alle Parallele", listOf(WorkoutSet(10, 0f), WorkoutSet(10, 0f), WorkoutSet(8, 0f)), 75, isBodyweight = true),
                        WorkoutExercise("h4", "Curl con Manubri", listOf(WorkoutSet(10, 14f), WorkoutSet(10, 14f), WorkoutSet(8, 16f)), 60)
                    )
                ),
                WorkoutTemplate(
                    id = "hyb_2",
                    name = "Gambe & Core Ibrido",
                    type = "hybrid",
                    exercises = listOf(
                        WorkoutExercise("h5", "Squat con Bilanciere", listOf(WorkoutSet(8, 70f), WorkoutSet(8, 80f), WorkoutSet(6, 90f)), 120),
                        WorkoutExercise("h6", "Affondi in Camminata", listOf(WorkoutSet(10, 16f), WorkoutSet(10, 16f)), 75),
                        WorkoutExercise("h7", "Hanging Leg Raises", listOf(WorkoutSet(10, 0f), WorkoutSet(10, 0f)), 60, isBodyweight = true),
                        WorkoutExercise("h8", "Plank", listOf(WorkoutSet(60, 0f), WorkoutSet(60, 0f)), 45, isBodyweight = true)
                    )
                )
            )
            else -> listOf( // "gym"
                WorkoutTemplate(
                    id = "gym_1",
                    name = "Upper Body (Panca & Braccia)",
                    type = "gym",
                    exercises = listOf(
                        WorkoutExercise("g1", "Panca Piana con Bilanciere", listOf(WorkoutSet(10, 50f), WorkoutSet(8, 60f), WorkoutSet(6, 65f)), 90),
                        WorkoutExercise("g2", "Distensioni Manubri Inclinata", listOf(WorkoutSet(10, 18f), WorkoutSet(10, 20f), WorkoutSet(8, 22f)), 75),
                        WorkoutExercise("g3", "Curl Bicipiti con Manubri", listOf(WorkoutSet(12, 12f), WorkoutSet(10, 14f), WorkoutSet(8, 14f)), 60),
                        WorkoutExercise("g4", "French Press Tricipiti", listOf(WorkoutSet(12, 20f), WorkoutSet(10, 25f), WorkoutSet(10, 25f)), 60)
                    )
                ),
                WorkoutTemplate(
                    id = "gym_2",
                    name = "Lower Body (Squat & Gambe)",
                    type = "gym",
                    exercises = listOf(
                        WorkoutExercise("g5", "Squat con Bilanciere", listOf(WorkoutSet(10, 60f), WorkoutSet(8, 75f), WorkoutSet(6, 85f)), 120),
                        WorkoutExercise("g6", "Leg Press", listOf(WorkoutSet(12, 120f), WorkoutSet(10, 140f), WorkoutSet(10, 140f)), 90),
                        WorkoutExercise("g7", "Leg Curl Femorali", listOf(WorkoutSet(12, 35f), WorkoutSet(10, 40f)), 60),
                        WorkoutExercise("g8", "Plank Addome", listOf(WorkoutSet(60, 0f), WorkoutSet(60, 0f)), 45, isBodyweight = true)
                    )
                )
            )
        }
    }

    fun reloadTemplatesForType(type: String) {
        val templates = getDefaultTemplatesForType(type)
        val updated = _data.value.copy(templates = templates)
        saveData(updated)
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

        val prs = _data.value.personalRecords.toMutableMap()
        exercises.forEach { ex ->
            val maxWeight = ex.sets.filter { it.completed }.maxOfOrNull { it.weightKg } ?: 0f
            if (maxWeight > (prs[ex.name] ?: 0f)) {
                prs[ex.name] = maxWeight
            }
        }

        val updated = _data.value.copy(
            workouts = _data.value.workouts + newWorkout,
            personalRecords = prs
        )
        saveData(updated)
    }

    fun deleteTemplate(id: String) {
        val updated = _data.value.copy(templates = _data.value.templates.filter { it.id != id })
        saveData(updated)
    }

    fun addTemplate(name: String, exercises: List<WorkoutExercise>) {
        val newTemplate = WorkoutTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            exercises = exercises
        )
        val updated = _data.value.copy(templates = _data.value.templates + newTemplate)
        saveData(updated)
    }

    fun deleteWorkout(id: String) {
        val updated = _data.value.copy(workouts = _data.value.workouts.filter { it.id != id })
        saveData(updated)
    }

    fun getTodayWorkoutsCount(): Int {
        val today = getToday()
        return _data.value.workouts.count { it.date == today }
    }
}
