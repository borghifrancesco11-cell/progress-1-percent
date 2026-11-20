package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════
//  Modelli dati condivisi tra i moduli — Progress 1%
// ══════════════════════════════════════════════════

/** Impostazioni app, preferenze utente e moduli abilitati */
@Serializable
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val enabledModules: Set<String> = setOf("tracker"),
    val userName: String = "",
    val userWeight: Float = 0f,
    val userHeight: Float = 0f,
    val userAge: Int = 0,
    val userGender: String = "",
    val themeMode: String = "oled", // "oled", "light"
    val workoutType: String = "gym", // "gym", "calisthenics", "hybrid"
)

/** Score giornaliero per la consistency chart */
@Serializable
data class DailyScore(
    val date: String = "", // yyyy-MM-dd
    val score: Float = 0f,
    val moduleScores: Map<String, Float> = emptyMap(),
)

/** Storico score settimanali */
@Serializable
data class ScoreHistory(
    val dailyScores: List<DailyScore> = emptyList(),
)

// ── Tracker ──

@Serializable
enum class GoalType { QUIT, COUNT }

@Serializable
enum class GoalPeriod { WEEK, MONTH, YEAR }

@Serializable
data class TrackerGoal(
    val id: String = "",
    val name: String = "",
    val type: GoalType = GoalType.COUNT,
    val period: GoalPeriod = GoalPeriod.WEEK,
    val targetCount: Int = 1,
    val icon: String = "🎯",
    val createdDate: String = "",
)

@Serializable
data class TrackerEntry(
    val goalId: String = "",
    val date: String = "",
    val count: Int = 0,
    val completed: Boolean = false,
)

@Serializable
data class TrackerData(
    val goals: List<TrackerGoal> = emptyList(),
    val entries: List<TrackerEntry> = emptyList(),
)

// ── School ──

@Serializable
data class Subject(
    val id: String = "",
    val name: String = "",
    val grades: List<Grade> = emptyList(),
)

@Serializable
data class Grade(
    val value: Float = 0f,
    val date: String = "",
    val description: String = "",
)

@Serializable
data class Exam(
    val id: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val date: String = "",
    val description: String = "",
)

@Serializable
data class SchoolData(
    val subjects: List<Subject> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val targetAverage: Float = 7f,
)

// ── Money ──

@Serializable
enum class TransactionType { INCOME, EXPENSE }

@Serializable
data class Transaction(
    val id: String = "",
    val amount: Float = 0f,
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "",
    val description: String = "",
    val date: String = "",
)

@Serializable
data class MonthlyBudget(
    val month: String = "", // yyyy-MM
    val budget: Float = 0f,
)

@Serializable
data class MoneyData(
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<MonthlyBudget> = emptyList(),
)

// ── Tasks ──

@Serializable
data class Task(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val dueDate: String = "", // yyyy-MM-dd
    val dueTime: String = "", // HH:mm
    val completed: Boolean = false,
    val notifyEnabled: Boolean = true,
)

@Serializable
data class TasksData(
    val tasks: List<Task> = emptyList(),
)

// ── Challenges ──

@Serializable
data class Challenge(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val durationDays: Int = 7,
    val completedDays: List<String> = emptyList(),
    val startDate: String = "",
    val active: Boolean = false,
    val completed: Boolean = false,
    val isCustom: Boolean = false,
)

@Serializable
data class ChallengesData(
    val challenges: List<Challenge> = emptyList(),
    val availableChallenges: List<Challenge> = emptyList(),
)

// ── Food ──

@Serializable
data class FoodItem(
    val id: String = "",
    val name: String = "",
    val calories: Int = 0,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f,
    val quantity: String = "1 porzione",
    val mealType: String = "Pranzo", // Colazione, Pranzo, Cena, Snack
    val date: String = "", // yyyy-MM-dd
    val imageRecognized: Boolean = false,
    val timeLogged: String = "",
)

@Serializable
data class FoodData(
    val entries: List<FoodItem> = emptyList(),
    val dailyCalorieTarget: Int = 2200,
    val dailyProteinTarget: Float = 120f,
    val dailyCarbsTarget: Float = 260f,
    val dailyFatTarget: Float = 70f,
)

// ── Wardrobe ──

@Serializable
data class ClothingItem(
    val id: String = "",
    val name: String = "",
    val category: String = "Top", // Top, Bottom, Outerwear, Shoes, Accessori
    val color: String = "",
    val seasons: List<String> = emptyList(),
    val photoUri: String? = null,
    val timesWorn: Int = 0,
)

@Serializable
data class Outfit(
    val id: String = "",
    val name: String = "",
    val itemIds: List<String> = emptyList(),
    val weatherCondition: String = "",
    val rating: Int = 5,
)

@Serializable
data class WishlistItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Float = 0f,
    val link: String = "",
)

@Serializable
data class SuitcaseList(
    val id: String = "",
    val name: String = "",
    val destination: String = "",
    val itemIds: List<String> = emptyList(),
    val packed: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class WardrobeData(
    val clothes: List<ClothingItem> = emptyList(),
    val outfits: List<Outfit> = emptyList(),
    val wishlist: List<WishlistItem> = emptyList(),
    val suitcases: List<SuitcaseList> = emptyList(),
)

// ── Gym / OpenGym & Calisthenics ──

@Serializable
data class WorkoutSet(
    val reps: Int = 0,
    val weightKg: Float = 0f,
    val completed: Boolean = false,
) {
    val weight: Float get() = weightKg
}

@Serializable
data class WorkoutExercise(
    val id: String = "",
    val name: String = "",
    val sets: List<WorkoutSet> = emptyList(),
    val restSeconds: Int = 90,
    val isBodyweight: Boolean = false,
) {
    val exerciseName: String get() = name
}

@Serializable
data class WorkoutTemplate(
    val id: String = "",
    val name: String = "",
    val exercises: List<WorkoutExercise> = emptyList(),
    val type: String = "gym", // "gym" o "calisthenics"
)

@Serializable
data class Workout(
    val id: String = "",
    val templateId: String = "",
    val name: String = "",
    val date: String = "",
    val exercises: List<WorkoutExercise> = emptyList(),
    val durationMinutes: Int = 0,
    val completed: Boolean = true,
)

@Serializable
data class GymData(
    val workouts: List<Workout> = emptyList(),
    val templates: List<WorkoutTemplate> = emptyList(),
    val personalRecords: Map<String, Float> = emptyMap(),
)

// ── Social Blocker ──

@Serializable
data class AppBlock(
    val packageName: String = "",
    val appName: String = "",
    val dailyLimitMinutes: Int = 30,
    val enabled: Boolean = true,
)

@Serializable
data class SocialBlockerData(
    val blockedApps: List<AppBlock> = listOf(
        AppBlock("com.instagram.android", "Instagram", 30),
        AppBlock("com.zhiliaoapp.musically", "TikTok", 30),
        AppBlock("com.twitter.android", "X (Twitter)", 30),
        AppBlock("com.facebook.katana", "Facebook", 30),
        AppBlock("com.snapchat.android", "Snapchat", 30),
        AppBlock("com.reddit.frontpage", "Reddit", 30),
    ),
    val enabled: Boolean = false,
)

// ── Sleep ──

@Serializable
data class SleepEntry(
    val id: String = "",
    val date: String = "", // yyyy-MM-dd, giorno del risveglio
    val bedtime: String = "", // HH:mm, addormentamento
    val wakeTime: String = "", // HH:mm, risveglio
    val durationMinutes: Int = 0,
    val quality: Int = 3, // 1–5
    val note: String = "",
)

@Serializable
data class SleepData(
    val entries: List<SleepEntry> = emptyList(),
    val targetHours: Float = 8f,
)
