package com.example.myapplication.challenges

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.ChallengesData
import com.example.myapplication.score.ModuleScoreProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChallengesScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "challenges"
    override val displayName: String = "Challenges"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<ChallengesData>(context, "challenges_data") ?: return 100f
        val active = data.challenges.filter { it.active }
        if (active.isEmpty()) return 100f

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val completedToday = active.count { it.completedDays.contains(today) }

        return (completedToday.toFloat() / active.size.toFloat()) * 100f
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
