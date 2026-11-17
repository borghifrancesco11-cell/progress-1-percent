package com.example.myapplication.culture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.Book
import com.example.myapplication.data.model.QuizQuestion
import com.example.myapplication.onboarding.customTextFieldColors
import com.example.myapplication.ui.components.LabeledProgressBar
import com.example.myapplication.ui.components.ModuleHeader
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.components.ProgressFab
import com.example.myapplication.ui.components.SelectableChip
import com.example.myapplication.ui.theme.*

@Composable
fun CultureScreen(
    onBack: () -> Unit,
    viewModel: CultureViewModel = viewModel()
) {
    val data by viewModel.data.collectAsState()
    val isPlayingTts by viewModel.isPlayingTts.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: PDF / Audiolibri, 1: Quiz Sophia
    var showAddBookDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                ProgressFab(
                    onClick = { showAddBookDialog = true },
                    icon = Icons.Default.Add,
                    contentDescription = "Carica Libro"
                )
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            ModuleHeader(
                categoryLabel = "Culture & Philosophy",
                title = "Cultura",
                subtitle = "Audiolibri PDF con sintesi vocale e quiz filosofici Sophia",
                onBack = onBack
            )
            // Header card
            ProgressCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Area Cultura & Mente", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                        Text(
                            text = if (data.dailyQuizCompleted == viewModel.getToday()) "Quiz Fatto ✓" else "Quiz Oggi Pronto",
                            style = MaterialTheme.typography.titleLarge,
                            color = Mint,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ascolta audiolibri anche a schermo spento e allenati con i quiz stile Sophia",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = Mint,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Tab Selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableChip(label = "Libri & TTS (${data.books.size})", selected = selectedTab == 0, onClick = { selectedTab = 0 })
                SelectableChip(label = "Quiz Sophia (Filosofia)", selected = selectedTab == 1, onClick = { selectedTab = 1 })
            }

            when (selectedTab) {
                0 -> BooksSection(
                    books = data.books,
                    isPlaying = isPlayingTts,
                    onStartReading = { book ->
                        val sampleText = "Benvenuto nella lettura vocale di ${book.title}. Questo testo viene letto tramite la sintesi vocale Android e proseguirà in background anche qualora il display del dispositivo venga spento."
                        viewModel.startTtsReading(book.title, sampleText)
                    },
                    onStopReading = viewModel::stopTtsReading,
                    onDelete = viewModel::deleteBook
                )
                1 -> QuizSophiaSection(
                    questions = viewModel.getSophiaQuestions(),
                    onComplete = viewModel::recordQuizResult
                )
            }
        }
    }

    if (showAddBookDialog) {
        AddBookDialog(
            onDismiss = { showAddBookDialog = false },
            onConfirm = { title, pages, uri ->
                viewModel.addBook(title, pages, uri)
                showAddBookDialog = false
            }
        )
    }
}

@Composable
fun BooksSection(
    books: List<Book>,
    isPlaying: Boolean,
    onStartReading: (Book) -> Unit,
    onStopReading: () -> Unit,
    onDelete: (String) -> Unit
) {
    if (books.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nessun libro caricato.\nTocca + per inserire o caricare un PDF!", color = TextSecondary)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(books, key = { it.id }) { book ->
                ProgressCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(book.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text("Pagina ${book.currentPage} di ${book.totalPages} • Letti: ${book.readingTimeMinutes} min", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            IconButton(onClick = { onDelete(book.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = TextTertiary)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        LabeledProgressBar(
                            label = "Progresso Lettura",
                            value = book.currentPage.toFloat(),
                            maxValue = if (book.totalPages > 0) book.totalPages.toFloat() else 1f
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = { if (isPlaying) onStopReading() else onStartReading(book) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPlaying) ScoreRed else Mint,
                                    contentColor = DarkBackground
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (isPlaying) "Ferma Lettura Vocale" else "Ascolta con TTS (Background)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizSophiaSection(
    questions: List<QuizQuestion>,
    onComplete: (Int, Int, String) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var showExplanation by remember { mutableStateOf(false) }
    var correctCount by remember { mutableStateOf(0) }
    var quizCompleted by remember { mutableStateOf(false) }

    if (quizCompleted) {
        ProgressCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("Quiz Sophia Completato!", style = MaterialTheme.typography.titleLarge, color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Punteggio finale: $correctCount su ${questions.size}", style = MaterialTheme.typography.displayMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Hai incrementato la tua percentuale di crescita per oggi!", color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        currentIndex = 0
                        selectedOption = null
                        showExplanation = false
                        correctCount = 0
                        quizCompleted = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
                ) {
                    Text("Riprova il Quiz")
                }
            }
        }
    } else {
        val currentQ = questions[currentIndex]
        ProgressCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Categoria: ${currentQ.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mint,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Domanda ${currentIndex + 1} / ${questions.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = currentQ.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(16.dp))

                currentQ.options.forEachIndexed { idx, opt ->
                    val isSelected = selectedOption == idx
                    val isCorrect = idx == currentQ.correctIndex

                    val borderColor = when {
                        showExplanation && isCorrect -> Mint
                        showExplanation && isSelected && !isCorrect -> ScoreRed
                        isSelected -> Mint
                        else -> CardBorder
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable(enabled = !showExplanation) {
                                selectedOption = idx
                            }
                            .padding(12.dp)
                    ) {
                        Text(opt, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (showExplanation) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Spiegazione: ${currentQ.explanation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .background(DarkBackground)
                            .clip(RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!showExplanation) {
                            if (selectedOption != null) {
                                if (selectedOption == currentQ.correctIndex) {
                                    correctCount++
                                }
                                showExplanation = true
                            }
                        } else {
                            if (currentIndex + 1 < questions.size) {
                                currentIndex++
                                selectedOption = null
                                showExplanation = false
                            } else {
                                quizCompleted = true
                                onComplete(correctCount, questions.size, "Sophia General Knowledge")
                            }
                        }
                    },
                    enabled = selectedOption != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (!showExplanation) "Verifica Risposta" else "Prossima Domanda", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddBookDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, android.net.Uri?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var pagesText by remember { mutableStateOf("150") }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        if (title.isBlank() && uri != null) {
            title = uri.lastPathSegment?.substringAfterLast("/") ?: "Documento PDF"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Carica o Aggiungi Libro/PDF", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titolo libro / opera") },
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pagesText,
                    onValueChange = { pagesText = it },
                    label = { Text("Numero pagine stimate") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = customTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { pdfPicker.launch("application/pdf") },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = Mint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri != null) "PDF Selezionato ✓" else "Seleziona file PDF dallo storage")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val p = pagesText.toIntOrNull() ?: 100
                        onConfirm(title.trim(), p, selectedUri)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = DarkBackground)
            ) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = TextSecondary) }
        }
    )
}
