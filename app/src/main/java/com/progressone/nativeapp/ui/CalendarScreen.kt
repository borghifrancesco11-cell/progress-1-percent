package com.progressone.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.progressone.nativeapp.data.DailyProgressEngine
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.ProgressBreakdown
import com.progressone.nativeapp.data.TaskHistory
import com.progressone.nativeapp.data.enabledModuleIds
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val CalendarFullDateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
private val CalendarShortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val CalendarWeekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val CalendarModuleLabels = mapOf(
    "track" to "Tracker",
    "tasks" to "Tasks",
    "food" to "Food",
    "gym" to "Gym",
    "sleep" to "Sleep",
    "challenges" to "Challenges",
    "social" to "Social detox",
    "school" to "School",
    "money" to "Money",
)

/**
 * A compact annual view of the user's local progress. Tapping a heatmap cell selects that day and
 * reports it through [onDateSelected]; the selected date is always shown in the details card below.
 */
@Composable
fun CalendarScreen(
    store: LocalStore,
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onDateSelected: (LocalDate) -> Unit = {},
) {
    val today = remember { LocalDate.now() }
    var displayedYear by remember { mutableStateOf(today.year) }
    var selectedIso by remember { mutableStateOf(today.toString()) }
    val selectedDate = remember(selectedIso) { parseCalendarDate(selectedIso) ?: today }

    val enabledIds = remember(store.revision) { store.enabledModuleIds() }
    val yearHistory = remember(snapshot, enabledIds, displayedYear) {
        DailyProgressEngine.year(snapshot, enabledIds, displayedYear)
    }
    val selectedProgress = remember(snapshot, enabledIds, selectedDate) {
        DailyProgressEngine.forDate(snapshot, enabledIds, selectedDate.toString())
    }
    val currentStreak = remember(snapshot, enabledIds, today) {
        DailyProgressEngine.streak(
            snapshot = snapshot,
            enabledModules = enabledIds,
            today = today,
            lookbackDays = 730,
        )
    }

    val activeDays = remember(yearHistory, displayedYear) {
        DailyProgressEngine.activeDays(displayedYear, yearHistory)
    }
    val activeMonths = remember(yearHistory, displayedYear) {
        DailyProgressEngine.monthsWithActivity(displayedYear, yearHistory)
    }
    val averageScore = remember(yearHistory) {
        val activeScores = yearHistory.values.filter { it.completedActions > 0 }.map { it.score }
        if (activeScores.isEmpty()) 0 else activeScores.average().roundToInt()
    }
    val selectedTaskStats = remember(snapshot.tasks, selectedDate) {
        calendarTaskStats(snapshot.tasks, selectedDate)
    }
    val commitments = remember(snapshot.tasks, today) {
        calendarCommitments(snapshot.tasks, today)
    }
    val overdueTint = MaterialTheme.colorScheme.error
    val dueTodayTint = Ice
    val upcomingTint = Mint
    val unscheduledTint = Lavender

    fun moveToYear(year: Int) {
        val safeYear = year.coerceIn(1, 9999)
        val nextDate = runCatching {
            LocalDate.of(safeYear, selectedDate.monthValue, selectedDate.dayOfMonth)
        }.getOrElse {
            LocalDate.of(safeYear, selectedDate.monthValue, 1)
        }
        displayedYear = safeYear
        selectedIso = nextDate.toString()
        onDateSelected(nextDate)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    PageHeading(
                        "ANNUAL HISTORY · ON DEVICE",
                        "Calendar",
                        "A year of steady progress, one day at a time.",
                    )
                }
            }
        }

        item {
            CalendarYearNavigator(
                year = displayedYear,
                currentYear = today.year,
                onPrevious = { moveToYear(displayedYear - 1) },
                onNext = { moveToYear(displayedYear + 1) },
                onToday = {
                    displayedYear = today.year
                    selectedIso = today.toString()
                    onDateSelected(today)
                },
            )
        }

        item {
            CalendarMomentumCard(
                streak = currentStreak,
                activeDays = activeDays,
                activeMonths = activeMonths,
                averageScore = averageScore,
                hasActiveDays = activeDays > 0,
            )
        }

        item {
            CalendarHeatmapCard(
                year = displayedYear,
                history = yearHistory,
                selectedDate = selectedDate,
                today = today,
                onDateSelected = { date ->
                    selectedIso = date.toString()
                    onDateSelected(date)
                },
            )
        }

        item {
            CalendarSelectedDayCard(
                date = selectedDate,
                today = today,
                progress = selectedProgress,
                taskStats = selectedTaskStats,
            )
        }

        item {
            SectionHeading("Commitments", "Overdue, due today and what's next")
        }

        val overdue = commitments.filter { it.status(today) == CalendarCommitmentStatus.OVERDUE }
        val dueToday = commitments.filter { it.status(today) == CalendarCommitmentStatus.DUE_TODAY }
        val upcoming = commitments.filter { it.status(today) == CalendarCommitmentStatus.UPCOMING }
        val unscheduled = commitments.filter { it.status(today) == CalendarCommitmentStatus.UNSCHEDULED }

        if (commitments.isEmpty()) {
            item {
                FrostCard(Modifier.fillMaxWidth()) {
                    Text("No open commitments", color = MaterialTheme.colorScheme.onBackground)
                    Text("Tasks with a due date will appear here.", color = Mist)
                }
            }
        } else {
            CalendarCommitmentGroup(
                label = "Overdue",
                commitments = overdue,
                today = today,
                selectedDate = selectedDate,
                tint = overdueTint,
            )
            CalendarCommitmentGroup(
                label = "Due today",
                commitments = dueToday,
                today = today,
                selectedDate = selectedDate,
                tint = dueTodayTint,
            )
            CalendarCommitmentGroup(
                label = "Upcoming",
                commitments = upcoming,
                today = today,
                selectedDate = selectedDate,
                tint = upcomingTint,
            )
            CalendarCommitmentGroup(
                label = "No due date",
                commitments = unscheduled,
                today = today,
                selectedDate = selectedDate,
                tint = unscheduledTint,
            )
        }
    }
}

@Composable
private fun CalendarYearNavigator(
    year: Int,
    currentYear: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    FrostCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = year > 1) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous year")
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    year.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text("Annual overview", style = MaterialTheme.typography.labelSmall, color = Mist)
            }
            IconButton(onClick = onNext, enabled = year < currentYear) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next year")
            }
            TextButton(onClick = onToday) {
                Text("Today")
            }
        }
    }
}

@Composable
private fun CalendarMomentumCard(
    streak: Int,
    activeDays: Int,
    activeMonths: Int,
    averageScore: Int,
    hasActiveDays: Boolean,
) {
    FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Mint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Mint)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Eyebrow("CURRENT STREAK", Mint)
                Text(
                    "$streak ${if (streak == 1) "day" else "days"}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Ice,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        streak == 0 -> "Record an activity to begin a new streak."
                        streak == 1 -> "One active day so far. Keep the rhythm going."
                        else -> "$streak consecutive days with recorded activity."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallStat("ACTIVE DAYS", activeDays.toString(), Modifier.weight(1f), Ice)
            SmallStat("ACTIVE MONTHS", activeMonths.toString(), Modifier.weight(1f), Mint)
            SmallStat(
                label = "AVG SCORE",
                value = if (hasActiveDays) "$averageScore%" else "—",
                modifier = Modifier.weight(1f),
                tint = Lavender,
            )
        }
    }
}

@Composable
private fun CalendarHeatmapCard(
    year: Int,
    history: Map<String, ProgressBreakdown>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    FrostCard(Modifier.fillMaxWidth()) {
        SectionHeading("Annual activity", "Tap a day to inspect it")
        CalendarHeatmap(
            year = year,
            history = history,
            selectedDate = selectedDate,
            today = today,
            onDateSelected = onDateSelected,
        )
        HorizontalDivider(color = GlassLight)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = Mist)
            Spacer(Modifier.width(6.dp))
            listOf(0.28f, 0.52f, 0.76f, 1f).forEach { alpha ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(11.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Mint.copy(alpha = alpha)),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("More", style = MaterialTheme.typography.labelSmall, color = Mist)
            Spacer(Modifier.weight(1f))
            Text(
                "Swipe sideways to explore",
                style = MaterialTheme.typography.labelSmall,
                color = Mist,
            )
        }
    }
}

@Composable
private fun CalendarHeatmap(
    year: Int,
    history: Map<String, ProgressBreakdown>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val weeks = remember(year) { calendarHeatmapWeeks(year) }
    val horizontalScroll = rememberScrollState()
    val cellSize = 11.dp
    val cellGap = 2.dp
    val weekWidth = 13.dp
    val monthHeaderHeight = 18.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(30.dp)) {
            Spacer(Modifier.height(monthHeaderHeight))
            CalendarWeekdayLabels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier.height(if (index == CalendarWeekdayLabels.lastIndex) cellSize else weekWidth),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(label, fontSize = 8.sp, color = Mist, maxLines = 1)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScroll),
        ) {
            Row(Modifier.height(monthHeaderHeight)) {
                weeks.forEach { week ->
                    Box(
                        modifier = Modifier.width(weekWidth),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (week.monthLabel != null) {
                            Text(
                                week.monthLabel,
                                fontSize = 9.sp,
                                color = Mist,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Row {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                        week.dates.forEach { date ->
                            val inYear = date.year == year
                            val progress = history[date.toString()]
                            CalendarHeatmapCell(
                                date = date,
                                progress = progress,
                                inYear = inYear,
                                selected = inYear && date == selectedDate,
                                today = inYear && date == today,
                                size = cellSize,
                                onClick = { if (inYear) onDateSelected(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarHeatmapCell(
    date: LocalDate,
    progress: ProgressBreakdown?,
    inYear: Boolean,
    selected: Boolean,
    today: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    val cellColor = when {
        !inYear -> Color.Transparent
        date.isAfter(LocalDate.now()) -> GlassLight.copy(alpha = 0.42f)
        progress == null || progress.completedActions <= 0 -> GlassLight
        else -> Mint.copy(
            alpha = when {
                progress.score >= 80 -> 1f
                progress.score >= 55 -> 0.76f
                progress.score >= 30 -> 0.52f
                else -> 0.28f
            },
        )
    }
    val borderColor = when {
        selected -> Ice
        today -> Lavender
        else -> Color.Transparent
    }
    val borderWidth = when {
        selected -> 1.5.dp
        today -> 1.dp
        else -> 0.dp
    }
    val actionCount = progress?.completedActions ?: 0
    val description = if (inYear) {
        "$date, ${if (actionCount == 1) "1 activity" else "$actionCount activities"}, score ${progress?.score ?: 0}"
    } else {
        ""
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(cellColor)
            .border(borderWidth, borderColor, shape)
            .then(if (inYear) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun CalendarSelectedDayCard(
    date: LocalDate,
    today: LocalDate,
    progress: ProgressBreakdown,
    taskStats: CalendarTaskDayStats,
) {
    FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Eyebrow(if (date == today) "TODAY · SELECTED" else "SELECTED DAY", if (date == today) Mint else Ice)
                Text(
                    date.format(CalendarFullDateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        date.isAfter(today) -> "No activity yet"
                        progress.completedActions == 0 -> "No activity recorded"
                        progress.completedActions == 1 -> "1 activity recorded"
                        else -> "${progress.completedActions} activities recorded"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
            }
            ProgressRing(
                progress = progress.score / 100f,
                modifier = Modifier.size(76.dp),
                caption = "score",
                sizeDp = 76.dp,
            )
        }

        HorizontalDivider(color = GlassLight)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${progress.score} / 100",
                    style = MaterialTheme.typography.titleLarge,
                    color = Ice,
                    fontWeight = FontWeight.Bold,
                )
                Text("Daily score", style = MaterialTheme.typography.labelSmall, color = Mist)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        taskStats.due == 0 && taskStats.completed == 0 -> "No commitments"
                        taskStats.due == 0 -> "${taskStats.completed} completed"
                        taskStats.completed == 0 -> "${taskStats.due} due"
                        else -> "${taskStats.completed} done · ${taskStats.due} due"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Task activity", style = MaterialTheme.typography.labelSmall, color = Mist)
            }
        }

        if (progress.moduleScores.isNotEmpty()) {
            Text("Daily breakdown", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            progress.moduleScores.forEach { (moduleId, score) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        CalendarModuleLabels[moduleId] ?: moduleId.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                    )
                    Text(
                        "$score%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (score >= 70) Mint else Ice,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                LinearProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = if (score >= 70) Mint else Ice,
                    trackColor = GlassLight,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.CalendarCommitmentGroup(
    label: String,
    commitments: List<CalendarCommitment>,
    today: LocalDate,
    selectedDate: LocalDate,
    tint: Color,
) {
    if (commitments.isEmpty()) return
    item {
        Eyebrow("$label · ${commitments.size}", tint)
    }
    items(
        items = commitments,
        key = { it.key },
    ) { commitment ->
        CalendarCommitmentCard(commitment, today, selectedDate)
    }
}

@Composable
private fun CalendarCommitmentCard(
    commitment: CalendarCommitment,
    today: LocalDate,
    selectedDate: LocalDate,
) {
    val status = commitment.status(today)
    val tint = when (status) {
        CalendarCommitmentStatus.OVERDUE -> MaterialTheme.colorScheme.error
        CalendarCommitmentStatus.DUE_TODAY -> Ice
        CalendarCommitmentStatus.UPCOMING -> Mint
        CalendarCommitmentStatus.UNSCHEDULED -> Lavender
    }
    val dueText = when (status) {
        CalendarCommitmentStatus.OVERDUE -> "Overdue · ${commitment.dueDate?.format(CalendarShortDateFormatter)}"
        CalendarCommitmentStatus.DUE_TODAY -> "Due today"
        CalendarCommitmentStatus.UPCOMING -> "Due ${commitment.dueDate?.format(CalendarShortDateFormatter)}"
        CalendarCommitmentStatus.UNSCHEDULED -> "No due date"
    }

    FrostCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    commitment.task.optString("name").ifBlank { "Untitled commitment" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dueText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == CalendarCommitmentStatus.OVERDUE) tint else Mist,
                )
            }
            if (commitment.dueDate == selectedDate) {
                Text("SELECTED", style = MaterialTheme.typography.labelSmall, color = Ice)
            }
        }
    }
}

private data class CalendarHeatmapWeek(
    val dates: List<LocalDate>,
    val monthLabel: String?,
)

private data class CalendarTaskDayStats(
    val due: Int,
    val completed: Int,
)

private enum class CalendarCommitmentStatus {
    OVERDUE,
    DUE_TODAY,
    UPCOMING,
    UNSCHEDULED,
}

private data class CalendarCommitment(
    val task: JSONObject,
    val dueDate: LocalDate?,
    val originalIndex: Int,
) {
    val key: String = "calendar_commitment_${originalIndex}_${task.optString("id")}"

    fun status(today: LocalDate): CalendarCommitmentStatus = when {
        dueDate == null -> CalendarCommitmentStatus.UNSCHEDULED
        dueDate.isBefore(today) -> CalendarCommitmentStatus.OVERDUE
        dueDate == today -> CalendarCommitmentStatus.DUE_TODAY
        else -> CalendarCommitmentStatus.UPCOMING
    }
}

private fun calendarHeatmapWeeks(year: Int): List<CalendarHeatmapWeek> {
    val firstDay = LocalDate.of(year, 1, 1)
    val lastDay = LocalDate.of(year, 12, 31)
    val gridStart = firstDay.minusDays((firstDay.dayOfWeek.value - 1).toLong())
    val gridEnd = lastDay.plusDays((7 - lastDay.dayOfWeek.value).toLong())
    val weekCount = ((gridEnd.toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()

    return List(weekCount) { weekIndex ->
        val dates = List(7) { weekdayIndex ->
            gridStart.plusDays((weekIndex * 7 + weekdayIndex).toLong())
        }
        val firstInYear = dates.firstOrNull { it.year == year }
        val monthLabel = if (firstInYear != null && firstInYear.dayOfMonth == 1) {
            firstInYear.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        } else {
            null
        }
        CalendarHeatmapWeek(dates, monthLabel)
    }
}

private fun calendarTaskStats(tasks: List<JSONObject>, date: LocalDate): CalendarTaskDayStats {
    val due = tasks.count { calendarTaskDueDate(it) == date }
    val completed = tasks.count { task ->
        date.toString() in TaskHistory.completedDates(task)
    }
    return CalendarTaskDayStats(due, completed)
}

private fun calendarCommitments(tasks: List<JSONObject>, today: LocalDate): List<CalendarCommitment> {
    return tasks.mapIndexedNotNull { index, task ->
        if (task.optBoolean("done")) {
            null
        } else {
            CalendarCommitment(task, calendarTaskDueDate(task), index)
        }
    }.sortedWith(
        compareBy<CalendarCommitment> { commitment ->
            when (commitment.status(today)) {
                CalendarCommitmentStatus.OVERDUE -> 0
                CalendarCommitmentStatus.DUE_TODAY -> 1
                CalendarCommitmentStatus.UPCOMING -> 2
                CalendarCommitmentStatus.UNSCHEDULED -> 3
            }
        }.thenBy { it.dueDate?.toString() ?: "" }
            .thenBy { it.originalIndex },
    )
}

private fun calendarTaskDueDate(task: JSONObject): LocalDate? =
    parseCalendarDate(task.optString("due"))

private fun parseCalendarDate(value: String): LocalDate? {
    val iso = value.trim().take(10)
    if (iso.length < 10) return null
    return runCatching { LocalDate.parse(iso) }.getOrNull()
}
