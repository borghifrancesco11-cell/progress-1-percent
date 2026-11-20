package com.example.myapplication.sleep

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.SleepData
import com.example.myapplication.score.ModuleScoreProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SleepScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "sleep"
    override val displayName: String = "Sonno"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<SleepData>(context, "sleep_data") ?: return 20f
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val entry = data.entries.firstOrNull { it.date == today } ?: return 20f

        val target = if (data.targetHours > 0f) data.targetHours else 8f
        val durationHours = entry.durationMinutes / 60f

        // 70% durata rispetto all'obiettivo, 30% qualità percepita.
        val durationScore = (durationHours / target).coerceIn(0f, 1f) * 70f
        val qualityScore = (entry.quality.coerceIn(1, 5) / 5f) * 30f
        return (durationScore + qualityScore).coerceIn(0f, 100f)
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
