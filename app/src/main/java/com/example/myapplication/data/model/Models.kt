package com.example.myapplication.data.model

import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════
//  Modelli dati condivisi tra i moduli
// ══════════════════════════════════════════════════

/** Impostazioni app e moduli abilitati */
@Serializable
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val enabledModules: Set<String> = setOf("tracker"),
    val userName: String = "",
    val userWeight: Float = 0f,
    val userHeight: Float = 0f,
    val userAge: Int = 0,
    val userGender: String = "",
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
    val categories: List<String> = listOf(
        "Cibo", "Trasporti", "Intrattenimento", "Shopping",
        "Salute", "Istruzione", "Abbonamenti", "Altro"
    ),
)

// ── Tasks ──

@Serializable
data class Task(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val dueDate: String = "",
    val dueTime: String = "",
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
    val startDate: String = "",
    val completedDays: List<String> = emptyList(),
    val active: Boolean = false,
    val completed: Boolean = false,
)

@Serializable
data class ChallengesData(
    val challenges: List<Challenge> = emptyList(),
    val availableChallenges: List<Challenge> = listOf(
        Challenge(id = "c1", title = "7 giorni senza social", description = "Non usare i social media per 7 giorni", durationDays = 7),
        Challenge(id = "c2", title = "Meditazione 5 min/giorno", description = "Medita almeno 5 minuti ogni giorno per 14 giorni", durationDays = 14),
        Challenge(id = "c3", title = "Leggi 20 pagine/giorno", description = "Leggi almeno 20 pagine ogni giorno per 30 giorni", durationDays = 30),
        Challenge(id = "c4", title = "No junk food 7 giorni", description = "Evita il cibo spazzatura per 7 giorni", durationDays = 7),
        Challenge(id = "c5", title = "Sveglia alle 6:00", description = "Svegliati alle 6:00 ogni mattina per 14 giorni", durationDays = 14),
        Challenge(id = "c6", title = "Allenamento 30 giorni", description = "Allenati ogni giorno per 30 giorni", durationDays = 30),
        Challenge(id = "c7", title = "Gratitudine quotidiana", description = "Scrivi 3 cose per cui sei grato ogni giorno", durationDays = 21),
        Challenge(id = "c8", title = "Niente caffeina", description = "Niente caffè o bevande con caffeina per 7 giorni", durationDays = 7),
        Challenge(id = "c9", title = "Cammina 10.000 passi", description = "Cammina almeno 10.000 passi al giorno per 14 giorni", durationDays = 14),
        Challenge(id = "c10", title = "Doccia fredda", description = "Fai una doccia fredda ogni giorno per 30 giorni", durationDays = 30),
    ),
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
    val date: String = "",
    val imageRecognized: Boolean = false,
)

@Serializable
data class FoodData(
    val entries: List<FoodItem> = emptyList(),
    val dailyCalorieTarget: Int = 2000,
    val dailyProteinTarget: Float = 50f,
    val dailyCarbsTarget: Float = 250f,
    val dailyFatTarget: Float = 65f,
)

// ── Wardrobe ──

@Serializable
data class ClothingItem(
    val id: String = "",
    val name: String = "",
    val category: String = "", // Top, Bottom, Shoes, Accessories, Outerwear
    val color: String = "",
    val imagePath: String = "",
    val seasons: List<String> = emptyList(), // Primavera, Estate, Autunno, Inverno
)

@Serializable
data class Outfit(
    val id: String = "",
    val name: String = "",
    val itemIds: List<String> = emptyList(),
    val saved: Boolean = false,
)

@Serializable
data class WishlistItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Float = 0f,
    val purchased: Boolean = false,
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

// ── Gym ──

@Serializable
data class Exercise(
    val id: String = "",
    val name: String = "",
    val muscleGroup: String = "",
    val equipment: String = "",
    val description: String = "",
)

@Serializable
data class WorkoutSet(
    val reps: Int = 0,
    val weight: Float = 0f,
    val completed: Boolean = false,
)

@Serializable
data class WorkoutExercise(
    val exerciseId: String = "",
    val exerciseName: String = "",
    val sets: List<WorkoutSet> = emptyList(),
    val restSeconds: Int = 90,
)

@Serializable
data class Workout(
    val id: String = "",
    val name: String = "",
    val exercises: List<WorkoutExercise> = emptyList(),
    val date: String = "",
    val durationMinutes: Int = 0,
    val completed: Boolean = false,
)

@Serializable
data class WorkoutTemplate(
    val id: String = "",
    val name: String = "",
    val exercises: List<WorkoutExercise> = emptyList(),
)

@Serializable
data class GymData(
    val workouts: List<Workout> = emptyList(),
    val templates: List<WorkoutTemplate> = emptyList(),
    val personalRecords: Map<String, Float> = emptyMap(), // exerciseId -> max weight
)

// ── Culture ──

@Serializable
data class Book(
    val id: String = "",
    val title: String = "",
    val pdfPath: String = "",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val lastReadDate: String = "",
    val readingTimeMinutes: Int = 0,
)

@Serializable
data class QuizQuestion(
    val id: String = "",
    val category: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val explanation: String = "",
)

@Serializable
data class QuizResult(
    val date: String = "",
    val category: String = "",
    val correctAnswers: Int = 0,
    val totalQuestions: Int = 0,
)

@Serializable
data class CultureData(
    val books: List<Book> = emptyList(),
    val quizResults: List<QuizResult> = emptyList(),
    val dailyQuizCompleted: String = "", // data ultimo quiz
    val ttsSpeed: Float = 1.0f,
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
