package com.progressone.nativeapp.ui

import com.progressone.nativeapp.data.LocalStore
import org.json.JSONObject

data class AppSnapshot(
    val trackers: List<JSONObject>,
    val tasks: List<JSONObject>,
    val challenges: List<JSONObject>,
    val garments: List<JSONObject>,
    val outfits: List<JSONObject>,
    val wears: List<JSONObject>,
    val subjects: List<JSONObject>,
    val grades: List<JSONObject>,
    val exams: List<JSONObject>,
    val transactions: List<JSONObject>,
    val packing: List<JSONObject>,
    val wishlist: List<JSONObject>,
    val foods: List<JSONObject>,
    val sleeps: List<JSONObject>,
    val gymTemplates: List<JSONObject>,
    val gymWorkouts: List<JSONObject>,
    val logs: List<LocalStore.Entry>,
    val goalGrade: Double,
    val monthBudget: Double,
    /** Monthly spending budgets keyed by YYYY-MM. */
    val budgetsByMonth: Map<String, Double> = emptyMap()
) {
    companion object {
        fun load(store: LocalStore): AppSnapshot {
            val month = java.time.LocalDate.now().toString().take(7)
            val budgets = loadBudgets(store)
            return AppSnapshot(
                trackers = store.all("tracker"),
                tasks = store.all("task"),
                challenges = store.all("challenge"),
                garments = store.all("garment"),
                outfits = store.all("outfit"),
                wears = store.all("wear"),
                subjects = store.all("subject"),
                grades = store.all("grade"),
                exams = store.all("exam"),
                transactions = store.all("transaction"),
                packing = store.all("packing"),
                wishlist = store.all("wishlist"),
                foods = store.all("food"),
                sleeps = store.all("sleep"),
                gymTemplates = store.all("gym_template"),
                gymWorkouts = store.all("gym_workout"),
                logs = store.entries(),
                goalGrade = store.setting("goalGrade", "7").toDoubleOrNull() ?: 7.0,
                monthBudget = budgets[month] ?: 0.0,
                budgetsByMonth = budgets
            )
        }

        /** Reads every persisted budget_YYYY-MM setting without changing the current-month API. */
        private fun loadBudgets(store: LocalStore): Map<String, Double> {
            val budgets = linkedMapOf<String, Double>()
            store.readableDatabase.query(
                "settings", arrayOf("key", "value"), null, null, null, null, null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    if (!key.startsWith("budget_")) continue
                    val month = key.removePrefix("budget_")
                    if (!month.matches(Regex("\\d{4}-\\d{2}"))) continue
                    cursor.getString(1).toDoubleOrNull()?.let { budgets[month] = it }
                }
            }
            return budgets
        }
    }
}
