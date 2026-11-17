package com.example.myapplication.school

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.Exam
import com.example.myapplication.data.model.Grade
import com.example.myapplication.data.model.SchoolData
import com.example.myapplication.data.model.Subject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SchoolViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<SchoolData> = _data.asStateFlow()

    private fun loadData(): SchoolData {
        return JsonStorage.load<SchoolData>(context, "school_data") ?: SchoolData(
            subjects = listOf(
                Subject(
                    id = "math",
                    name = "Matematica",
                    grades = listOf(Grade(value = 7.5f, date = "2026-11-01", description = "Test derivate"))
                ),
                Subject(
                    id = "history",
                    name = "Storia",
                    grades = listOf(Grade(value = 8.0f, date = "2026-11-05", description = "Interrogazione"))
                ),
                Subject(
                    id = "english",
                    name = "Inglese",
                    grades = listOf(Grade(value = 6.5f, date = "2026-11-10", description = "Writing test"))
                )
            ),
            exams = listOf(
                Exam(
                    id = UUID.randomUUID().toString(),
                    subjectId = "math",
                    subjectName = "Matematica",
                    date = "2026-11-25",
                    description = "Verifica integrali"
                )
            ),
            targetAverage = 7.0f
        )
    }

    private fun saveData(newData: SchoolData) {
        _data.value = newData
        JsonStorage.save(context, "school_data", newData)
    }

    fun addSubject(name: String) {
        val newSub = Subject(id = UUID.randomUUID().toString(), name = name, grades = emptyList())
        saveData(_data.value.copy(subjects = _data.value.subjects + newSub))
    }

    fun deleteSubject(subjectId: String) {
        saveData(
            _data.value.copy(
                subjects = _data.value.subjects.filter { it.id != subjectId },
                exams = _data.value.exams.filter { it.subjectId != subjectId }
            )
        )
    }

    fun addGrade(subjectId: String, grade: Float, description: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val newGrade = Grade(value = grade, date = today, description = description)
        val updatedSubjects = _data.value.subjects.map {
            if (it.id == subjectId) it.copy(grades = it.grades + newGrade) else it
        }
        saveData(_data.value.copy(subjects = updatedSubjects))
    }

    fun addExam(subjectId: String, date: String, description: String) {
        val subjectName = _data.value.subjects.find { it.id == subjectId }?.name ?: "Materia"
        val newExam = Exam(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            subjectName = subjectName,
            date = date,
            description = description
        )
        saveData(_data.value.copy(exams = _data.value.exams + newExam))
    }

    fun deleteExam(examId: String) {
        saveData(_data.value.copy(exams = _data.value.exams.filter { it.id != examId }))
    }

    fun setTargetAverage(target: Float) {
        saveData(_data.value.copy(targetAverage = target))
    }

    fun calculateSubjectAverage(subject: Subject): Float {
        if (subject.grades.isEmpty()) return 0f
        return subject.grades.map { it.value }.average().toFloat()
    }

    fun calculateOverallAverage(): Float {
        val allGrades = _data.value.subjects.flatMap { it.grades }
        if (allGrades.isEmpty()) return 0f
        return allGrades.map { it.value }.average().toFloat()
    }

    /** Simula l'impatto di un nuovo voto sulla media di materia e generale */
    fun simulateGrade(subjectId: String, simulatedGrade: Float): Pair<Float, Float> {
        val subjects = _data.value.subjects
        val subject = subjects.find { it.id == subjectId }
        val simulatedSubAvg = if (subject != null) {
            val grades = subject.grades.map { it.value } + simulatedGrade
            grades.average().toFloat()
        } else 0f

        val allGrades = subjects.flatMap { it.grades }.map { it.value } + simulatedGrade
        val simulatedOverallAvg = allGrades.average().toFloat()

        return Pair(simulatedSubAvg, simulatedOverallAvg)
    }
}
