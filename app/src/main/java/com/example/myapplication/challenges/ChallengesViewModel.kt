package com.example.myapplication.challenges

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.Challenge
import com.example.myapplication.data.model.ChallengesData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChallengesViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<ChallengesData> = _data.asStateFlow()

    private val defaultPresets = listOf(
        Challenge("preset_1", "Sveglia alle 6:00", "Alzati presto ogni giorno e inizia subito con energia", 7),
        Challenge("preset_2", "No Zuccheri Aggiunti", "Evita bibite zuccherate, snack industriali e dolci", 14),
        Challenge("preset_3", "100 Piegamenti al giorno", "Suddivisi in serie durante tutta la giornata", 30),
        Challenge("preset_4", "Lettura Quotidiana", "Leggi almeno 15 pagine di un libro al giorno", 21),
        Challenge("preset_5", "Doccia Fredda", "Termina ogni doccia con 2 minuti di acqua ghiacciata", 14),
        Challenge("preset_6", "Detox Schermi", "Nessun cellulare la prima e l'ultima ora del giorno", 7)
    )

    private fun loadData(): ChallengesData {
        val loaded = JsonStorage.load<ChallengesData>(context, "challenges_data")
        return if (loaded == null || loaded.availableChallenges.isEmpty()) {
            ChallengesData(
                challenges = loaded?.challenges ?: emptyList(),
                availableChallenges = defaultPresets
            )
        } else {
            loaded
        }
    }

    private fun saveData(newData: ChallengesData) {
        _data.value = newData
        JsonStorage.save(context, "challenges_data", newData)
    }

    fun getToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun startChallenge(challenge: Challenge) {
        val today = getToday()
        val started = challenge.copy(
            id = UUID.randomUUID().toString(),
            startDate = today,
            active = true,
            completed = false,
            completedDays = emptyList()
        )
        saveData(_data.value.copy(challenges = _data.value.challenges + started))
    }

    fun checkInToday(challengeId: String) {
        val today = getToday()
        val updated = _data.value.challenges.map { c ->
            if (c.id == challengeId && c.active) {
                if (!c.completedDays.contains(today)) {
                    val newDays = c.completedDays + today
                    val isDone = newDays.size >= c.durationDays
                    c.copy(completedDays = newDays, completed = isDone, active = !isDone)
                } else c
            } else c
        }
        saveData(_data.value.copy(challenges = updated))
    }

    fun abandonChallenge(challengeId: String) {
        saveData(_data.value.copy(challenges = _data.value.challenges.filter { it.id != challengeId }))
    }

    fun addCustomChallenge(title: String, description: String, days: Int) {
        val custom = Challenge(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            durationDays = days
        )
        saveData(_data.value.copy(availableChallenges = _data.value.availableChallenges + custom))
    }
}
