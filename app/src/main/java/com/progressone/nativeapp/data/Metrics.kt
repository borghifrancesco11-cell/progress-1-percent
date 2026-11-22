package com.progressone.nativeapp.data

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

object Metrics {
    fun day(): String = LocalDate.now().toString()
    /** Value displayed by a counter. Daily measurements never spill into the next day.
     * A cumulative target deliberately keeps its last value instead. */
    fun progress(t: JSONObject, logs: List<LocalStore.Entry>, date: String = day()): Double {
        val id = t.optString("id")
        val kind = t.optString("type", "habit")
        if (kind == "target") return logs.filter { it.ownerId == id && it.date <= date && it.status != "skip" }
            .maxByOrNull { it.date }?.value?.coerceAtLeast(0.0) ?: 0.0
        val freq = t.optString("freq", "day")
        val start = if (freq == "day") LocalDate.parse(date) else frequencyStart(LocalDate.parse(date), freq)
        return logs.filter { it.ownerId == id && it.date >= start.toString() && it.date <= date &&
            it.status != "skip" && it.status != "no" }.sumOf { it.value.coerceAtLeast(0.0) }
    }

    /** Only an explicit check-in is a successful day of avoiding a negative habit.
     * An unrecorded day cannot be silently treated as a clean day. */
    fun completion(t: JSONObject, logs: List<LocalStore.Entry>, date: String = day()): Boolean {
        val kind = t.optString("type", "habit")
        val target = t.optDouble("target", 1.0).coerceAtLeast(0.0)
        val id = t.optString("id")
        val log = logs.firstOrNull { it.ownerId == id && it.date == date }
        if (kind == "project") {
            val steps = t.optJSONArray("milestones") ?: return false
            return steps.length() > 0 && (0 until steps.length()).all { steps.optJSONObject(it)?.optBoolean("done") == true }
        }
        if (kind == "target") return progress(t, logs, date) >= target && target > 0
        if (t.optBoolean("bad", false)) {
            val freq = t.optString("freq", "day")
            val start = if (freq == "day") date else frequencyStart(LocalDate.parse(date), freq).toString()
            val period = logs.filter { it.ownerId == id && it.date >= start && it.date <= date }
            return period.any { it.status == "done" } &&
                period.none { it.status == "skip" || it.status == "no" } &&
                period.sumOf { it.value.coerceAtLeast(0.0) } <= target
        }
        if (kind == "average") return log != null && log.status == "done" && log.value >= target
        if (kind == "habit" && t.optString("freq", "day") == "day")
            return log != null && log.status == "done" && log.value >= target && target > 0
        // Counter and duration are NEVER complete merely because a nonzero entry was saved.
        // Weekly/monthly items accumulate only values inside their own calendar period.
        return progress(t, logs, date) >= target && target > 0
    }

    fun due(t: JSONObject, date: String = day()): Boolean {
        val from = t.optString("created", "")
        val until = t.optString("deadline", "")
        if (from.isNotBlank() && date < from) return false
        if (until.isNotBlank() && date > until && t.optString("type") != "project") return false
        if (t.optString("freq", "day") == "day") {
            val days = t.optJSONArray("days")
            if (days != null && days.length() > 0) {
                val index = LocalDate.parse(date).dayOfWeek.value - 1
                if (!(0 until days.length()).any { days.optInt(it, -1) == index }) return false
            }
        }
        return !t.optBoolean("archived")
    }

    fun daySummary(trackers: List<JSONObject>, logs: List<LocalStore.Entry>, date: String = day()): Pair<Int, Int> {
        val active = trackers.filter { due(it, date) }
        return active.count { completion(it, logs, date) } to active.size
    }

    fun streak(t: JSONObject, logs: List<LocalStore.Entry>, date: String = day()): Int {
        if (t.optString("type", "habit") != "habit" || t.optString("freq", "day") != "day") return 0
        var value = 0
        var current = LocalDate.parse(date)
        repeat(366) {
            val iso = current.toString()
            if (due(t, iso) && logs.any { it.ownerId == t.optString("id") && it.date == iso && it.status == "skip" }) {
                current = current.minusDays(1)
                return@repeat
            }
            if (due(t, iso)) {
                if (!completion(t, logs, iso)) return value
                value++
            }
            current = current.minusDays(1)
        }
        return value
    }

    fun weeklyCompletion(trackers: List<JSONObject>, logs: List<LocalStore.Entry>, end: LocalDate = LocalDate.now()): List<Int> =
        (6 downTo 0).map { offset ->
            val date = end.minusDays(offset.toLong()).toString()
            val (done, total) = daySummary(trackers, logs, date)
            if (total == 0) 0 else (100.0 * done / total).roundToInt()
        }

    fun weightedAverage(grades: List<JSONObject>): Double? {
        val selected = grades.filter { it.optDouble("weight", 1.0) > 0 && it.optDouble("value", -1.0) in 0.0..10.0 }
        val sum = selected.sumOf { it.optDouble("weight", 1.0) }
        return if (sum == 0.0) null else selected.sumOf {
            it.optDouble("value") * it.optDouble("weight", 1.0)
        } / sum
    }

    fun balance(tx: List<JSONObject>, month: String = day().take(7)): Triple<Double, Double, Double> {
        val current = tx.filter { it.optString("date").startsWith(month) }
        val income = current.filter { it.optString("type") == "income" }.sumOf { it.optDouble("amount") }
        val expense = current.filter { it.optString("type") != "income" }.sumOf { it.optDouble("amount") }
        return Triple(income, expense, income - expense)
    }

    private fun frequencyStart(date: LocalDate, freq: String): LocalDate = when (freq) {
        "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        "month" -> date.withDayOfMonth(1)
        "quarter" -> date.withMonth(((date.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1)
        "year" -> date.withDayOfYear(1)
        else -> date
    }
}

/** Editorially curated offline facts. No invented per-month intelligence or performance figures. */
data class Insight(val trigger: String, val title: String, val detail: String, val source: String, val url: String)
object InsightLibrary {
    val items = listOf(
        Insight("read", "Reading builds skills", "Reading practice supports vocabulary and text comprehension. The benefit varies by person and material; there is no reliable fixed monthly IQ gain.", "National Reading Panel, NICHD", "https://www.nichd.nih.gov/research/supported/nrp"),
        Insight("book", "Make reading a habit", "Consistency and active engagement with text are more meaningful than an invented percentage improvement.", "National Reading Panel, NICHD", "https://www.nichd.nih.gov/research/supported/nrp"),
        Insight("study", "Test yourself to remember", "Research on retrieval practice finds that recalling material can improve long-term retention compared with repeated study alone.", "Roediger & Karpicke, 2006", "https://pubmed.ncbi.nlm.nih.gov/16507066/"),
        Insight("learn", "Practice remembering", "Self-testing is a useful learning strategy. Review over time rather than only rereading notes.", "Roediger & Karpicke, 2006", "https://pubmed.ncbi.nlm.nih.gov/16507066/"),
        Insight("exercise", "Movement matters", "WHO recommends adults aim for 150–300 minutes of moderate-intensity aerobic physical activity per week.", "World Health Organization, 2020", "https://www.who.int/publications/i/item/9789240015128"),
        Insight("workout", "Movement matters", "Any amount of physical activity is better than none; gradually increase toward recommended levels.", "World Health Organization, 2020", "https://www.who.int/publications/i/item/9789240015128"),
        Insight("walk", "Small walks add up", "Physical activity supports both physical and mental health. Even brief movement contributes to your weekly total.", "World Health Organization, 2020", "https://www.who.int/publications/i/item/9789240015128"),
        Insight("sleep", "Sleep supports learning", "Sleep is important for attention and memory. Healthy sleep recommendations depend on age.", "CDC — About Sleep", "https://www.cdc.gov/sleep/about/index.html"),
        Insight("save", "Keep your goals realistic", "Track actual savings against your own budget, rather than assuming one universal ideal saving rate.", "Consumer Financial Protection Bureau", "https://www.consumerfinance.gov/consumer-tools/budgeting/"),
        Insight("budget", "Know where money goes", "A spending tracker can make financial decisions clearer. Choose categories that reflect your real life.", "Consumer Financial Protection Bureau", "https://www.consumerfinance.gov/consumer-tools/budgeting/")
    )
    fun forTitle(title: String): Insight? = items.firstOrNull { title.contains(it.trigger, ignoreCase = true) }
}
