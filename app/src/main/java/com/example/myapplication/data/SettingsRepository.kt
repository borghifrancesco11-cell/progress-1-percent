package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val context: Context) {

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val saved = JsonStorage.load<AppSettings>(context, "app_settings")
        val settings = saved ?: AppSettings(
            onboardingCompleted = false,
            enabledModules = setOf("tracker", "food", "gym", "school", "money", "tasks", "challenges", "wardrobe", "social_blocker", "sleep"),
            themeMode = "oled",
            workoutType = "gym"
        )
        // I moduli rimossi dall'app non devono restare attivi nei dati salvati.
        val removedModules = setOf("culture", "zetamath")
        return settings.copy(enabledModules = settings.enabledModules - removedModules)
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        JsonStorage.save(context, "app_settings", newSettings)
    }

    fun toggleModule(moduleId: String, enabled: Boolean) {
        if (moduleId == "tracker") return // Tracker è obbligatorio
        val current = _settings.value.enabledModules.toMutableSet()
        if (enabled) {
            current.add(moduleId)
        } else {
            current.remove(moduleId)
        }
        val updated = _settings.value.copy(enabledModules = current)
        updateSettings(updated)
    }

    fun setThemeMode(mode: String) {
        updateSettings(_settings.value.copy(themeMode = mode))
    }

    fun setWorkoutType(type: String) {
        updateSettings(_settings.value.copy(workoutType = type))
    }

    fun completeOnboarding(
        enabledModules: Set<String>,
        name: String,
        weight: Float,
        height: Float,
        age: Int,
        gender: String,
        workoutType: String = "gym",
        themeMode: String = "oled"
    ) {
        val updated = AppSettings(
            onboardingCompleted = true,
            enabledModules = enabledModules + "tracker",
            userName = name,
            userWeight = weight,
            userHeight = height,
            userAge = age,
            userGender = gender,
            workoutType = workoutType,
            themeMode = themeMode
        )
        updateSettings(updated)
    }

    fun isModuleEnabled(moduleId: String): Boolean {
        if (moduleId == "tracker") return true
        return _settings.value.enabledModules.contains(moduleId)
    }
}
