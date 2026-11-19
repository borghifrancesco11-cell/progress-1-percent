package com.example.myapplication.food

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.FoodData
import com.example.myapplication.data.model.FoodItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed interface FoodUiState {
    data object Idle : FoodUiState
    data object ProcessingImage : FoodUiState
    data class RecognitionDone(val foodName: String, val macros: MacroInfo) : FoodUiState
    data class Error(val message: String) : FoodUiState
}

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val classifier = FoodClassifier(context)

    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<FoodData> = _data.asStateFlow()

    private val _uiState = MutableStateFlow<FoodUiState>(FoodUiState.Idle)
    val uiState: StateFlow<FoodUiState> = _uiState.asStateFlow()

    private fun loadData(): FoodData {
        return JsonStorage.load<FoodData>(context, "food_data") ?: FoodData(
            entries = emptyList(),
            dailyCalorieTarget = 2200,
            dailyProteinTarget = 120f,
            dailyCarbsTarget = 260f,
            dailyFatTarget = 70f
        )
    }

    private fun saveData(newData: FoodData) {
        _data.value = newData
        JsonStorage.save(context, "food_data", newData)
    }

    fun getToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    fun getCurrentTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    fun getImageModelName(): String = classifier.modelEngineName
    fun getMacroModelName(): String = if (BuildConfig.GEMINI_API_KEY.isNotBlank()) "Google Gemini 1.5 Flash (Cloud)" else "Database Nutrizionale Offline (Zero Rete)"

    fun analyzeFoodImage(bitmap: Bitmap) {
        _uiState.value = FoodUiState.ProcessingImage
        viewModelScope.launch {
            try {
                val recognition = classifier.classifyImage(bitmap)
                val macros = MacroCalculator.estimateMacros(recognition.localizedName)
                _uiState.value = FoodUiState.RecognitionDone(recognition.localizedName, macros)
            } catch (e: Exception) {
                _uiState.value = FoodUiState.Error(e.localizedMessage ?: "Errore analisi cibo")
            }
        }
    }

    fun resetUiState() {
        _uiState.value = FoodUiState.Idle
    }

    fun addFoodItem(name: String, mealType: String, calories: Int, protein: Float, carbs: Float, fat: Float, quantity: String, isAi: Boolean = false) {
        val newItem = FoodItem(
            id = UUID.randomUUID().toString(),
            name = name,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            quantity = quantity,
            mealType = mealType,
            date = getToday(),
            imageRecognized = isAi,
            timeLogged = getCurrentTime()
        )
        saveData(_data.value.copy(entries = _data.value.entries + newItem))
        resetUiState()
    }

    fun updateFoodItem(updatedItem: FoodItem) {
        val list = _data.value.entries.map {
            if (it.id == updatedItem.id) updatedItem else it
        }
        saveData(_data.value.copy(entries = list))
    }

    fun deleteFoodItem(id: String) {
        saveData(_data.value.copy(entries = _data.value.entries.filter { it.id != id }))
    }

    fun getTodayEntries(): List<FoodItem> {
        val today = getToday()
        return _data.value.entries.filter { it.date == today }
    }

    fun getTodayCalories(): Int = getTodayEntries().sumOf { it.calories }
    fun getTodayProtein(): Float = getTodayEntries().sumOf { it.protein.toDouble() }.toFloat()
    fun getTodayCarbs(): Float = getTodayEntries().sumOf { it.carbs.toDouble() }.toFloat()
    fun getTodayFat(): Float = getTodayEntries().sumOf { it.fat.toDouble() }.toFloat()

    fun getLoggedMealsCount(): Int {
        val today = getToday()
        return _data.value.entries.filter { it.date == today }.map { it.mealType }.distinct().size
    }
}
