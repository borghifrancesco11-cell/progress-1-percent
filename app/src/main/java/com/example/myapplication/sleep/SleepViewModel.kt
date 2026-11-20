package com.example.myapplication.sleep

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.SleepData
import com.example.myapplication.data.model.SleepEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class SleepViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<SleepData> = _data.asStateFlow()

    private fun loadData(): SleepData =
        JsonStorage.load<SleepData>(context, "sleep_data") ?: SleepData()

    private fun saveData(newData: SleepData) {
        _data.value = newData
        JsonStorage.save(context, "sleep_data", newData)
    }

    fun addEntry(
        bedtime: String,
        wakeTime: String,
        quality: Int,
        note: String,
        date: String = todayIso(),
    ) {
        val entry = SleepEntry(
            id = UUID.randomUUID().toString(),
            date = date,
            bedtime = bedtime.trim(),
            wakeTime = wakeTime.trim(),
            durationMinutes = durationBetween(bedtime, wakeTime),
            quality = quality.coerceIn(1, 5),
            note = note.trim(),
        )
        // Una sola notte per data: un nuovo inserimento sostituisce quello esistente.
        val entries = _data.value.entries.filterNot { it.date == date } + entry
        saveData(_data.value.copy(entries = entries))
    }

    fun deleteEntry(id: String) {
        saveData(_data.value.copy(entries = _data.value.entries.filterNot { it.id == id }))
    }

    fun setTargetHours(hours: Float) {
        if (hours <= 0f) return
        saveData(_data.value.copy(targetHours = hours))
    }

    fun entryFor(date: String): SleepEntry? =
        _data.value.entries.firstOrNull { it.date == date }

    /** Media della durata (minuti) delle notti degli ultimi 7 giorni */
    fun lastSevenAverageMinutes(): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val lastWeek = (0 until 7).map {
            val day = fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -1)
            day
        }.toSet()
        val relevant = _data.value.entries.filter { it.date in lastWeek && it.durationMinutes > 0 }
        if (relevant.isEmpty()) return 0
        return relevant.sumOf { it.durationMinutes } / relevant.size
    }

    /** Media della qualità percepita (1–5) su tutte le notti registrate */
    fun averageQuality(): Float {
        val relevant = _data.value.entries.filter { it.quality in 1..5 }
        if (relevant.isEmpty()) return 0f
        return relevant.map { it.quality }.average().toFloat()
    }

    companion object {
        fun todayIso(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        /** Durata in minuti tra addormentamento e risveglio, gestendo la mezzanotte */
        fun durationBetween(bedtime: String, wakeTime: String): Int {
            val bed = parseMinutes(bedtime) ?: return 0
            val wake = parseMinutes(wakeTime) ?: return 0
            var diff = wake - bed
            if (diff <= 0) diff += 24 * 60
            return diff
        }

        private fun parseMinutes(value: String): Int? {
            val parts = value.trim().split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }

        fun formatDuration(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return if (m == 0) "${h}h" else "${h}h ${m}m"
        }
    }
}
