package com.example.myapplication.food

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.FoodData
import com.example.myapplication.score.ModuleScoreProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FoodScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "food"
    override val displayName: String = "Food"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<FoodData>(context, "food_data") ?: return 50f
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayEntries = data.entries.filter { it.date == today }

        if (todayEntries.isEmpty()) return 20f

        // 3 pasti registrati = base 60%
        val loggedMeals = todayEntries.map { it.mealType }.distinct().size
        val mealScore = (loggedMeals / 3f).coerceAtMost(1f) * 60f

        // Rispetto target calorico = fino a 40%
        val totalCalories = todayEntries.sumOf { it.calories }
        val target = if (data.dailyCalorieTarget <= 0) 2000 else data.dailyCalorieTarget
        val calDiff = Math.abs(totalCalories - target)
        val calRatio = (1f - (calDiff.toFloat() / target.toFloat())).coerceIn(0f, 1f)
        val calorieScore = calRatio * 40f

        return (mealScore + calorieScore).coerceIn(0f, 100f)
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
