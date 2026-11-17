package com.example.myapplication.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Gestisce la persistenza offline in file JSON nella directory interna dell'app.
 * Ogni modulo ha il proprio file JSON.
 */
object JsonStorage {

    @PublishedApi
    internal val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @PublishedApi
    internal fun getFile(context: Context, fileName: String): File {
        val dir = File(context.filesDir, "progress1percent")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$fileName.json")
    }

    /** Salva un oggetto serializzabile in un file JSON */
    inline fun <reified T> save(context: Context, fileName: String, data: T) {
        val file = getFile(context, fileName)
        file.writeText(json.encodeToString(data))
    }

    /** Carica un oggetto da un file JSON. Ritorna null se il file non esiste. */
    inline fun <reified T> load(context: Context, fileName: String): T? {
        val file = getFile(context, fileName)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<T>(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    /** Verifica se un file di dati esiste */
    fun exists(context: Context, fileName: String): Boolean {
        return getFile(context, fileName).exists()
    }

    /** Elimina un file di dati */
    fun delete(context: Context, fileName: String): Boolean {
        return getFile(context, fileName).delete()
    }
}
