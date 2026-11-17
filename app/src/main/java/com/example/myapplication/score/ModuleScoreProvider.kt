package com.example.myapplication.score

/**
 * Interfaccia che ogni modulo che contribuisce alla percentuale in home deve implementare.
 * Lo score è un valore da 0 a 100 che rappresenta il progresso giornaliero del modulo.
 */
interface ModuleScoreProvider {
    /** Identificativo univoco del modulo (es. "tracker", "food", "money") */
    val moduleId: String

    /** Nome visualizzato del modulo */
    val displayName: String

    /** Score corrente del modulo (0-100) */
    fun getCurrentScore(): Float

    /** Indica se il modulo è abilitato dall'utente */
    fun isEnabled(): Boolean
}
