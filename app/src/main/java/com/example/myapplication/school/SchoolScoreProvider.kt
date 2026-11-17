package com.example.myapplication.school

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.SchoolData
import com.example.myapplication.score.ModuleScoreProvider

class SchoolScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "school"
    override val displayName: String = "School"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<SchoolData>(context, "school_data") ?: return 70f
        val allGrades = data.subjects.flatMap { it.grades }
        if (allGrades.isEmpty()) return 70f
        val avg = allGrades.map { it.value }.average().toFloat()
        val target = if (data.targetAverage <= 0f) 6.0f else data.targetAverage

        // Se la media è 10 = 100%, se è pari al target = 75%, sotto al target scala
        val ratio = (avg / target).coerceIn(0f, 1.5f)
        return (ratio * 75f).coerceIn(0f, 100f)
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
