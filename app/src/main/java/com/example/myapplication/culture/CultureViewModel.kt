package com.example.myapplication.culture

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.Book
import com.example.myapplication.data.model.CultureData
import com.example.myapplication.data.model.QuizQuestion
import com.example.myapplication.data.model.QuizResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CultureViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<CultureData> = _data.asStateFlow()

    private val _isPlayingTts = MutableStateFlow(false)
    val isPlayingTts: StateFlow<Boolean> = _isPlayingTts.asStateFlow()

    private val sophiaQuestions = listOf(
        QuizQuestion(
            id = "q1",
            category = "Filosofia",
            question = "Chi ha formulato la celebre massima 'So di non sapere'?",
            options = listOf("Platone", "Socrate", "Aristotele", "Cartesio"),
            correctIndex = 1,
            explanation = "Socrate considerava la consapevolezza della propria ignoranza il primo passo fondamentale per la ricerca della verità."
        ),
        QuizQuestion(
            id = "q2",
            category = "Filosofia",
            question = "Cosa intende Friedrich Nietzsche con il concetto di 'Oltreuomo' (Übermensch)?",
            options = listOf("Un essere geneticamente superiore", "Chi supera i valori tradizionali e crea il proprio senso", "Un sovrano illuminato", "Un'intelligenza artificiale"),
            correctIndex = 1,
            explanation = "L'Oltreuomo è l'individuo capace di superare il nichilismo e creare nuovi valori fondati sulla fedeltà alla terra."
        ),
        QuizQuestion(
            id = "q3",
            category = "Storia & Geopolitica",
            question = "In quale anno avvenne la caduta del Muro di Berlino?",
            options = listOf("1989", "1991", "1975", "1985"),
            correctIndex = 0,
            explanation = "Il Muro di Berlino cadde il 9 novembre 1989, segnando la fine simbolica della Guerra Fredda."
        ),
        QuizQuestion(
            id = "q4",
            category = "Scienza & Pensiero",
            question = "Quale principio afferma che non si può conoscere contemporaneamente con esattezza posizione e quantità di moto di una particella?",
            options = listOf("Principio di Entropia", "Principio di Indeterminazione di Heisenberg", "Relatività Ristretta", "Teorema di Gödel"),
            correctIndex = 1,
            explanation = "Formulato da Werner Heisenberg nel 1927, è uno dei pilastri fondamentali della meccanica quantistica."
        ),
        QuizQuestion(
            id = "q5",
            category = "Letteratura",
            question = "Chi è l'autore dell'opera filosofico-letteraria 'Così parlò Zarathustra'?",
            options = listOf("Arthur Schopenhauer", "Friedrich Nietzsche", "Franz Kafka", "Fëdor Dostoevskij"),
            correctIndex = 1,
            explanation = "Nietzsche vi espone attraverso parabole e aforismi concetti come la morte di Dio, l'eterno ritorno e l'Oltreuomo."
        )
    )

    private fun loadData(): CultureData {
        return JsonStorage.load<CultureData>(context, "culture_data") ?: CultureData(
            books = listOf(
                Book(
                    id = "b1",
                    title = "Lettere a Lucilio (Seneca)",
                    pdfPath = "",
                    currentPage = 12,
                    totalPages = 180,
                    lastReadDate = getToday(),
                    readingTimeMinutes = 25
                )
            ),
            quizResults = emptyList(),
            dailyQuizCompleted = ""
        )
    }

    private fun saveData(newData: CultureData) {
        _data.value = newData
        JsonStorage.save(context, "culture_data", newData)
    }

    fun getToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun getSophiaQuestions(): List<QuizQuestion> = sophiaQuestions

    fun startTtsReading(bookTitle: String, textToRead: String) {
        val intent = Intent(context, TtsReadingService::class.java).apply {
            action = TtsReadingService.ACTION_START
            putExtra(TtsReadingService.EXTRA_TITLE, bookTitle)
            putExtra(TtsReadingService.EXTRA_TEXT, textToRead)
        }
        ContextCompat.startForegroundService(context, intent)
        _isPlayingTts.value = true

        // Incrementa tempo di lettura
        val updatedBooks = _data.value.books.map {
            if (it.title == bookTitle) it.copy(readingTimeMinutes = it.readingTimeMinutes + 15, lastReadDate = getToday()) else it
        }
        saveData(_data.value.copy(books = updatedBooks))
    }

    fun stopTtsReading() {
        val intent = Intent(context, TtsReadingService::class.java).apply {
            action = TtsReadingService.ACTION_STOP
        }
        context.startService(intent)
        _isPlayingTts.value = false
    }

    fun recordQuizResult(correct: Int, total: Int, category: String) {
        val result = QuizResult(
            date = getToday(),
            category = category,
            correctAnswers = correct,
            totalQuestions = total
        )
        saveData(_data.value.copy(quizResults = _data.value.quizResults + result, dailyQuizCompleted = getToday()))
    }

    fun addBook(title: String, totalPages: Int, uri: Uri?) {
        val book = Book(
            id = UUID.randomUUID().toString(),
            title = title,
            pdfPath = uri?.toString() ?: "",
            currentPage = 1,
            totalPages = totalPages,
            lastReadDate = getToday(),
            readingTimeMinutes = 0
        )
        saveData(_data.value.copy(books = _data.value.books + book))
    }

    fun deleteBook(id: String) {
        saveData(_data.value.copy(books = _data.value.books.filter { it.id != id }))
    }
}
