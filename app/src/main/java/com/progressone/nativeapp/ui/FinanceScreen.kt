package com.progressone.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.InputValidation
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import org.json.JSONObject
import java.util.Locale

private val financeCategories = listOf("Food", "Transport", "Clothing", "School", "Leisure", "Health", "Savings", "Home", "Other")

@Composable
fun FinanceScreen(store: LocalStore, snapshot: AppSnapshot) {
    val (income, expenses, balance) = Metrics.balance(snapshot.transactions)
    val budget = snapshot.monthBudget
    val month = Metrics.day().take(7)
    var page by remember { mutableStateOf("Entries") }
    var adding by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { PageHeading("PRIVATE · ON DEVICE", "Finances", "") }
        item {
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Eyebrow("MONTHLY BALANCE · $month")
                Text(euro(balance), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold, color = if (balance >= 0) Mint else Lavender)
                Text("Money in ${euro(income)}  ·  Money out ${euro(expenses)}", color = Mist)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("INCOME", euro(income), Modifier.weight(1f), Mint)
                SmallStat("EXPENSES", euro(expenses), Modifier.weight(1f), Lavender)
            }
        }
        item { Chips(listOf("Entries", "Categories", "Budget"), page) { page = it } }
        when (page) {
            "Entries" -> {
                item { SectionHeading("Transactions", "Most recent first") {
                    Button(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null); Text("Add") }
                } }
                if (snapshot.transactions.isEmpty()) item {
                    EmptyPanel("No transactions yet", "Record money coming in or going out.") {
                        adding = true
                    }
                }
                items(snapshot.transactions.sortedByDescending { it.str("date") }, key = { it.str("id") }) { t ->
                    FrostCard(Modifier.fillMaxWidth(), onClick = { selected = t }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (t.str("type") == "income") Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                                null, tint = if (t.str("type") == "income") Mint else Lavender)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.str("title"), fontWeight = FontWeight.Medium)
                                Text("${t.str("category")} · ${t.str("date")}", color = Mist)
                            }
                            Text("${if (t.str("type") == "income") "+" else "−"}${euro(t.num("amount"))}",
                                color = if (t.str("type") == "income") Mint else Color.White)
                        }
                    }
                }
            }
            "Categories" -> {
                val spent = snapshot.transactions.filter { it.str("type") == "expense" &&
                    it.str("date").startsWith(month) }.groupBy { it.str("category", "Other") }
                    .mapValues { (_, xs) -> xs.sumOf { it.num("amount") } }
                item { SectionHeading("Where it goes", "Expenses this month") }
                if (spent.isEmpty()) item { EmptyPanel("No spending to chart", "Log an expense to see categories.") }
                items(spent.entries.sortedByDescending { it.value }) { group ->
                    FrostCard(Modifier.fillMaxWidth()) {
                        Row {
                            Text(group.key, Modifier.weight(1f))
                            Text(euro(group.value), color = Lavender)
                        }
                        LinearProgressIndicator(progress = { if (expenses == 0.0) 0f else
                            (group.value / expenses).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp), color = Lavender)
                    }
                }
            }
            "Budget" -> {
                item {
                    FrostCard(Modifier.fillMaxWidth()) {
                        SectionHeading("Spending budget", "A limit you choose")
                        Text(if (budget > 0) euro(budget) else "Not set",
                            style = MaterialTheme.typography.headlineMedium)
                        if (budget > 0) {
                            LinearProgressIndicator(progress = { (expenses / budget).toFloat().coerceIn(0f, 1f) },
                                Modifier.fillMaxWidth().height(9.dp), color = if (expenses > budget) Lavender else Mint)
                            Text(if (expenses > budget) "${euro(expenses-budget)} over budget"
                                else "${euro(budget-expenses)} remaining", color = Mist)
                        }
                        Button(onClick = { editingBudget = true }, Modifier.fillMaxWidth()) { Text("Edit monthly budget") }
                    }
                }
                item {
                    FrostCard(Modifier.fillMaxWidth()) {
                        Eyebrow("A HEALTHIER RELATIONSHIP WITH NUMBERS", Mint)
                        Text("This budget is private and descriptive—not a score. It reflects your own goals.", color = Mist)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    if (adding) TransactionEditor(store) { adding = false }
    if (selected != null) {
        val t = selected!!
        var confirm by remember { mutableStateOf(false) }
        BottomSheetFrame(t.str("title"), onDismiss = { selected = null }) {
            Text("${t.str("type")} · ${t.str("category")} · ${t.str("date")}", color = Mist)
            Text(euro(t.num("amount")), style = MaterialTheme.typography.headlineLarge)
            if (confirm) Button(onClick = { store.remove("transaction", t.str("id")); selected = null },
                Modifier.fillMaxWidth()) { Text("Confirm delete") }
            else TextButton(onClick = { confirm = true }) { Text("Delete transaction") }
        }
    }
    if (editingBudget) {
        var value by remember { mutableStateOf(if (budget <= 0) "" else budget.toString()) }
        val parsedBudget = InputValidation.parseAmount(value)
        BottomSheetFrame("Monthly spending budget", { editingBudget = false }) {
            FormField("Budget €", value, { value = it.take(32) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (value.isNotBlank() && parsedBudget == null) {
                Text("Enter a finite budget between 0 and ${InputValidation.MAX_MONEY_AMOUNT.toLong()} €.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                val safeBudget = parsedBudget ?: return@Button
                store.setSetting("budget_$month", safeBudget.toString())
                editingBudget = false
            }, Modifier.fillMaxWidth(), enabled = value.isBlank() || parsedBudget != null) { Text("Save budget") }
        }
    }
}

@Composable
private fun TransactionEditor(store: LocalStore, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf("expense") }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var date by remember { mutableStateOf(Metrics.day()) }
    val parsedAmount = InputValidation.parseAmount(amount)
    val validDate = InputValidation.isValidDate(date)
    BottomSheetFrame("New transaction", onDismiss) {
        Chips(listOf("expense", "income"), type) { type = it }
        FormField("Description", title, { title = it.take(120) })
        FormField("Amount €", amount, { amount = it.take(32) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        FormField("Date YYYY-MM-DD", date, { date = it.take(10) })
        if (amount.isNotBlank() && parsedAmount == null) {
            Text("Enter a finite amount between 0 and ${InputValidation.MAX_MONEY_AMOUNT.toLong()} €.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (!validDate) {
            Text("Enter a real date between ${InputValidation.MIN_VALID_YEAR} and ${InputValidation.MAX_VALID_YEAR}.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Eyebrow("CATEGORY")
        Chips(financeCategories, category) { category = it }
        Button(onClick = {
            val safeAmount = parsedAmount ?: return@Button
            if (safeAmount <= 0.0) return@Button
            store.put("transaction", LocalStore.obj("type" to type, "title" to title.trim(),
                "amount" to safeAmount, "category" to category, "date" to date.trim()))
            onDismiss()
        }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && parsedAmount != null &&
            parsedAmount > 0.0 && validDate) { Text("Save transaction") }
    }
}
