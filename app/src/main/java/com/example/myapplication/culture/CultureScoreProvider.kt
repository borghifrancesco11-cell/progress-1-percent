package com.example.myapplication.culture

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.CultureData
import com.example.myapplication.score.ModuleScoreProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CultureScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "culture"
    override val displayName: String = "Cultura & Audiolibri"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<CultureData>(context, "culture_data") ?: return 70f
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        var score = 30f // Base

        // Quiz completato oggi? +35%
        if (data.dailyQuizCompleted == today) {
            score += 35f
        }

        // Lettura fatta oggi? +35%
        val readToday = data.books.any { it.lastReadDate == today }
        if (readToday) {
            score += 35f
        }

        return score.coerceIn(0f, 100f)
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
