package com.example.myapplication.gym

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.GymData
import com.example.myapplication.score.ModuleScoreProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GymScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "gym"
    override val displayName: String = "Palestra (Gym)"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<GymData>(context, "gym_data") ?: return 75f
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hasTrainedToday = data.workouts.any { it.date == today && it.completed }

        return if (hasTrainedToday) {
            100f
        } else {
            // Se si è allenato ieri o negli ultimi 2 giorni = riposo attivo (80%)
            val recent = data.workouts.filter { it.completed }.takeLast(3)
            if (recent.isNotEmpty()) 80f else 60f
        }
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
