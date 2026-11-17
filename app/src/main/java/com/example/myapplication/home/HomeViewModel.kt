package com.example.myapplication.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.challenges.ChallengesScoreProvider
import com.example.myapplication.culture.CultureScoreProvider
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
    val todayFormatted: String = ""
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val settingsRepo = SettingsRepository(context)
    private val scoreAggregator = ScoreAggregator()

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        registerProviders()
        refreshHome()
    }

    private fun registerProviders() {
        scoreAggregator.register(TrackerScoreProvider(context))
        scoreAggregator.register(FoodScoreProvider(context))
        scoreAggregator.register(SchoolScoreProvider(context))
        scoreAggregator.register(MoneyScoreProvider(context))
        scoreAggregator.register(TasksScoreProvider(context))
        scoreAggregator.register(ChallengesScoreProvider(context))
        scoreAggregator.register(GymScoreProvider(context))
        scoreAggregator.register(CultureScoreProvider(context))
        scoreAggregator.register(SocialBlockerScoreProvider(context))
    }

    fun refreshHome() {
        val total = scoreAggregator.getTotalScore()
        val enabled = settingsRepo.settings.value.enabledModules
        val contributing = scoreAggregator.getContributingModuleNames()
        val todayStr = SimpleDateFormat("EEEE, d MMMM", Locale.ITALIAN).format(Date())
            .replaceFirstChar { it.uppercase() }

        val consistency = loadOrGenerateConsistency(total)

        _state.value = HomeState(
            totalScore = total,
            scoreText = "${total.toInt()} / 100",
            percentageText = "${total.toInt()}%",
            contributingModules = contributing,
            consistencyDays = consistency,
            enabledModules = enabled,
            todayFormatted = todayStr
        )
    }

    private fun loadOrGenerateConsistency(currentScore: Float): List<ConsistencyDay> {
        val daysOfWeek = listOf("L", "M", "M", "G", "V", "S", "D")
        val calendar = Calendar.getInstance()
        val todayDow = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0 = Lunedì

        return daysOfWeek.mapIndexed { index, name ->
            val isToday = index == todayDow
            val score = if (isToday) currentScore else if (index < todayDow) 80f else 0f
            ConsistencyDay(
                dayName = name,
                score = score,
                isToday = isToday
            )
        }
    }
}
