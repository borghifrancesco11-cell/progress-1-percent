package com.example.myapplication.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.MoneyData
import com.example.myapplication.data.model.MonthlyBudget
import com.example.myapplication.data.model.Transaction
import com.example.myapplication.data.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MoneyViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<MoneyData> = _data.asStateFlow()

    private fun loadData(): MoneyData {
        return JsonStorage.load<MoneyData>(context, "money_data") ?: MoneyData(
            transactions = emptyList(),
            budgets = emptyList()
        )
    }

    private fun saveData(newData: MoneyData) {
        _data.value = newData
        JsonStorage.save(context, "money_data", newData)
    }

    fun getCurrentDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    fun getCurrentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    fun addTransaction(amount: Float, type: TransactionType, category: String, description: String) {
        val newTx = Transaction(
            id = UUID.randomUUID().toString(),
            amount = amount,
            type = type,
            category = category,
            description = description,
            date = getCurrentDate()
        )
        saveData(_data.value.copy(transactions = _data.value.transactions + newTx))
    }

    fun deleteTransaction(txId: String) {
        saveData(_data.value.copy(transactions = _data.value.transactions.filter { it.id != txId }))
    }

    fun setMonthlyBudget(month: String, budget: Float) {
        val currentBudgets = _data.value.budgets.toMutableList()
        val idx = currentBudgets.indexOfFirst { it.month == month }
        if (idx >= 0) {
            currentBudgets[idx] = currentBudgets[idx].copy(budget = budget)
        } else {
            currentBudgets.add(MonthlyBudget(month = month, budget = budget))
        }
        saveData(_data.value.copy(budgets = currentBudgets))
    }

    fun getTodayExpenses(): Float {
        val today = getCurrentDate()
        return _data.value.transactions
            .filter { it.date == today && it.type == TransactionType.EXPENSE }
            .sumOf { it.amount.toDouble() }
            .toFloat()
    }

    fun getCurrentMonthExpenses(): Float {
        val currentMonth = getCurrentMonth()
        return _data.value.transactions
            .filter { it.date.startsWith(currentMonth) && it.type == TransactionType.EXPENSE }
            .sumOf { it.amount.toDouble() }
            .toFloat()
    }

    fun getCurrentMonthIncome(): Float {
        val currentMonth = getCurrentMonth()
        return _data.value.transactions
            .filter { it.date.startsWith(currentMonth) && it.type == TransactionType.INCOME }
            .sumOf { it.amount.toDouble() }
            .toFloat()
    }

    fun getCurrentMonthBudget(): Float {
        val currentMonth = getCurrentMonth()
        return _data.value.budgets.find { it.month == currentMonth }?.budget ?: 500f
    }
}
