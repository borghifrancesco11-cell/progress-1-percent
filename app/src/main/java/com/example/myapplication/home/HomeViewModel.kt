package com.example.myapplication.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.challenges.ChallengesScoreProvider
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.model.DailyScore
import com.example.myapplication.data.model.ScoreHistory
import com.example.myapplication.food.FoodScoreProvider
import com.example.myapplication.gym.GymScoreProvider
import com.example.myapplication.money.MoneyScoreProvider
import com.example.myapplication.school.SchoolScoreProvider
import com.example.myapplication.score.ScoreAggregator
import com.example.myapplication.socialblocker.SocialBlockerScoreProvider
import com.example.myapplication.tasks.TasksScoreProvider
import com.example.myapplication.tracker.TrackerScoreProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ConsistencyDay(
    val dayName: String,
    val score: Float,
    val isToday: Boolean
)

data class HomeState(
    val totalScore: Float = 0f,
    val scoreText: String = "0 / 100",
    val percentageText: String = "0%",
    val contributingModules: String = "",
    val consistencyDays: List<ConsistencyDay> = emptyList(),
    val enabledModules: Set<String> = emptySet(),
    val todayFormatted: String = "",
    val dailyQuote: String = "Migliora dell'1% ogni giorno e alla fine dell'anno sarai 37 volte migliore.",
    val dailyQuoteAuthor: String = "James Clear",
    val streakDays: Int = 1,
    val themeMode: String = "oled",
    val workoutType: String = "gym",
    val completedHabitsCount: Int = 0,
    val totalHabitsCount: Int = 0,
    val todayCalories: Int = 0,
    val targetCalories: Int = 2000,
    val todayWorkoutsCount: Int = 0,
    val tasksPendingCount: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val settingsRepo = SettingsRepository(context)
    private val scoreAggregator = ScoreAggregator()
    private var quoteIndex: Int = 0

    private val quotesList = listOf(
        "Migliora dell'1% ogni giorno e alla fine dell'anno sarai 37 volte migliore." to "James Clear",
        "Non è perché le cose sono difficili che non osiamo, è perché non osiamo che sono difficili." to "Seneca",
        "La felicità della tua vita dipende dalla qualità dei tuoi pensieri." to "Marco Aurelio",
        "Siamo ciò che facciamo ripetutamente. L'eccellenza non è un atto, ma un'abitudine." to "Aristotele",
        "L'unico modo di fare un ottimo lavoro è amare quello che fai." to "Steve Jobs",
        "La semplicità è la suprema sofisticazione." to "Leonardo Da Vinci",
        "Nessun uomo è libero se non è padrone di se stesso." to "Epitteto",
        "Non temo chi ha fatto 10.000 calci una volta, ma chi ha fatto un calcio 10.000 volte." to "Bruce Lee",
        "La nostra gloria più grande non sta nel non cadere mai, ma nel rialzarci sempre." to "Confucio",
        "Chi ha un perché abbastanza forte può superare qualsiasi come." to "Friedrich Nietzsche",
        "La vittoria più bella e più grande è vincere se stessi." to "Platone",
        "Il momento migliore per piantare un albero era 20 anni fa. Il secondo momento migliore è oggi." to "Proverbio Cinese",
        "Non contare i giorni, fai in modo che i giorni contino." to "Muhammad Ali",
        "La disciplina è il ponte tra gli obiettivi e i risultati." to "Jim Rohn",
        "Ciò che facciamo adesso fa eco nell'eternità." to "Marco Aurelio",
        "Non aspettare il momento perfetto, cogli il momento e rendilo perfetto." to "Arthur Conan Doyle"
    )

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        registerProviders()
        val cal = Calendar.getInstance()
        quoteIndex = cal.get(Calendar.DAY_OF_YEAR) % quotesList.size
        refreshHome()
    }

    private fun registerProviders() {
        scoreAggregator.register(TrackerScoreProvider(context))
        scoreAggregator.register(FoodScoreProvider(context))
        scoreAggregator.register(GymScoreProvider(context))
        scoreAggregator.register(SchoolScoreProvider(context))
        scoreAggregator.register(MoneyScoreProvider(context))
        scoreAggregator.register(TasksScoreProvider(context))
        scoreAggregator.register(ChallengesScoreProvider(context))
        scoreAggregator.register(SocialBlockerScoreProvider(context))
    }

    fun nextQuote() {
        quoteIndex = (quoteIndex + 1) % quotesList.size
        val q = quotesList[quoteIndex]
        _state.value = _state.value.copy(
            dailyQuote = q.first,
            dailyQuoteAuthor = q.second
        )
    }

    fun refreshHome() {
        val total = scoreAggregator.getTotalScore()
        val settings = settingsRepo.settings.value
        val enabled = settings.enabledModules
        val contributing = scoreAggregator.getContributingModuleNames()
        val todayStr = SimpleDateFormat("EEEE, d MMMM", Locale.ITALIAN).format(Date())
            .replaceFirstChar { it.uppercase() }
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val selectedQuote = quotesList[quoteIndex % quotesList.size]
        val consistency = loadOrGenerateConsistency(total)

        // Caricamento live quick stats
        var habitsCompleted = 0
        var habitsTotal = 0
        try {
            val trackerData = JsonStorage.load<com.example.myapplication.data.model.TrackerData>(context, "tracker_data")
            if (trackerData != null) {
                habitsTotal = trackerData.goals.size
                habitsCompleted = trackerData.entries.count { it.date == todayIso && it.completed }
            }
        } catch (_: Exception) {}

        var todayCalories = 0
        var targetCalories = 2000
        try {
            val foodData = JsonStorage.load<com.example.myapplication.data.model.FoodData>(context, "food_data")
            if (foodData != null) {
                todayCalories = foodData.entries.filter { it.date == todayIso }.sumOf { it.calories }
                if (foodData.dailyCalorieTarget > 0) targetCalories = foodData.dailyCalorieTarget
            }
        } catch (_: Exception) {}

        var todayWorkouts = 0
        try {
            val gymData = JsonStorage.load<com.example.myapplication.data.model.GymData>(context, "gym_data")
            if (gymData != null) {
                todayWorkouts = gymData.workouts.count { it.date == todayIso }
            }
        } catch (_: Exception) {}

        var tasksPending = 0
        try {
            val tasksData = JsonStorage.load<com.example.myapplication.data.model.TasksData>(context, "tasks_data")
            if (tasksData != null) {
                tasksPending = tasksData.tasks.count { !it.completed }
            }
        } catch (_: Exception) {}

        _state.value = HomeState(
            totalScore = total,
            scoreText = "${total.toInt()} / 100",
            percentageText = "${total.toInt()}%",
            contributingModules = contributing,
            consistencyDays = consistency,
            enabledModules = enabled,
            todayFormatted = todayStr,
            dailyQuote = selectedQuote.first,
            dailyQuoteAuthor = selectedQuote.second,
            streakDays = 1,
            themeMode = settings.themeMode,
            workoutType = settings.workoutType,
            completedHabitsCount = habitsCompleted,
            totalHabitsCount = habitsTotal,
            todayCalories = todayCalories,
            targetCalories = targetCalories,
            todayWorkoutsCount = todayWorkouts,
            tasksPendingCount = tasksPending
        )
    }

    fun toggleTheme() {
        val nextMode = if (settingsRepo.settings.value.themeMode == "light") "oled" else "light"
        settingsRepo.setThemeMode(nextMode)
        refreshHome()
    }

    private fun loadOrGenerateConsistency(currentScore: Float): List<ConsistencyDay> {
        val daysOfWeek = listOf("L", "M", "M", "G", "V", "S", "D")
        val calendar = Calendar.getInstance()
        val todayDow = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0 = Lunedì

        return daysOfWeek.mapIndexed { index, name ->
            val isToday = index == todayDow
            val score = if (isToday) currentScore else 0f
            ConsistencyDay(
                dayName = name,
                score = score,
                isToday = isToday
            )
        }
    }
}
