package com.example.myapplication.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val weight: String = "",
    val height: String = "",
    val age: String = "",
    val gender: String = "Non specificato",
    val workoutType: String = "gym", // "gym", "calisthenics", "hybrid"
    val selectedModules: Set<String> = setOf(
        "tracker", "food", "gym", "school", "money", "tasks", "challenges", "wardrobe", "social_blocker"
    )
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun nextStep() {
        _state.value = _state.value.copy(step = _state.value.step + 1)
    }

    fun prevStep() {
        if (_state.value.step > 0) {
            _state.value = _state.value.copy(step = _state.value.step - 1)
        }
    }

    fun updateProfile(name: String, weight: String, height: String, age: String, gender: String) {
        _state.value = _state.value.copy(
            name = name,
            weight = weight,
            height = height,
            age = age,
            gender = gender
        )
    }

    fun setWorkoutType(type: String) {
        _state.value = _state.value.copy(workoutType = type)
    }

    fun toggleModule(moduleId: String) {
        if (moduleId == "tracker") return // Tracker non può essere disattivato
        val current = _state.value.selectedModules.toMutableSet()
        if (current.contains(moduleId)) {
            current.remove(moduleId)
        } else {
            current.add(moduleId)
        }
        _state.value = _state.value.copy(selectedModules = current)
    }

    fun finishOnboarding() {
        val s = _state.value
        repository.completeOnboarding(
            enabledModules = s.selectedModules,
            name = s.name.trim().ifBlank { "Utente" },
            weight = s.weight.toFloatOrNull() ?: 70f,
            height = s.height.toFloatOrNull() ?: 175f,
            age = s.age.toIntOrNull() ?: 20,
            gender = s.gender,
            workoutType = s.workoutType
        )
    }
}
