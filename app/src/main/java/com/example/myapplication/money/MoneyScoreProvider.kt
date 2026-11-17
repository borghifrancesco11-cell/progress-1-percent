package com.example.myapplication.money

import android.content.Context
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.MoneyData
import com.example.myapplication.data.model.TransactionType
import com.example.myapplication.score.ModuleScoreProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoneyScoreProvider(private val context: Context) : ModuleScoreProvider {
    override val moduleId: String = "money"
    override val displayName: String = "Money"

    private val settingsRepo = SettingsRepository(context)

    override fun getCurrentScore(): Float {
        val data = JsonStorage.load<MoneyData>(context, "money_data") ?: return 100f
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val budget = data.budgets.find { it.month == currentMonth }?.budget ?: 500f
        val expenses = data.transactions
            .filter { it.date.startsWith(currentMonth) && it.type == TransactionType.EXPENSE }
            .sumOf { it.amount.toDouble() }
            .toFloat()

        if (budget <= 0f) return 100f
        if (expenses <= budget) {
            // Sotto o pari al budget = punteggio eccellente (80 - 100)
            val savedRatio = (budget - expenses) / budget
            return 80f + (savedRatio * 20f).coerceIn(0f, 20f)
        } else {
            // Se supera il budget, cala proporzionalmente
            val excessRatio = (expenses - budget) / budget
            return (80f - excessRatio * 80f).coerceIn(0f, 80f)
        }
    }

    override fun isEnabled(): Boolean = settingsRepo.isModuleEnabled(moduleId)
}
