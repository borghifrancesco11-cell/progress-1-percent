package com.example.myapplication.score

/**
 * Aggrega gli score di tutti i moduli abilitati per calcolare la percentuale totale in home.
 * La media è pesata equamente tra i moduli attivi.
 */
class ScoreAggregator {

    private val providers = mutableListOf<ModuleScoreProvider>()

    fun register(provider: ModuleScoreProvider) {
        providers.removeAll { it.moduleId == provider.moduleId }
        providers.add(provider)
    }

    fun unregister(moduleId: String) {
        providers.removeAll { it.moduleId == moduleId }
    }

    /** Calcola lo score totale (0-100) come media dei moduli abilitati */
    fun getTotalScore(): Float {
        val enabled = providers.filter { it.isEnabled() }
        if (enabled.isEmpty()) return 0f
        return enabled.map { it.getCurrentScore() }.average().toFloat()
    }

    /** Restituisce lo score formattato su 100 (es. "45 / 100") */
    fun getScoreText(): String {
        val score = getTotalScore().toInt()
        return "$score / 100"
    }

    /** Restituisce la percentuale formattata (es. "45%") */
    fun getPercentageText(): String {
        return "${getTotalScore().toInt()}%"
    }

    /** Restituisce gli score individuali di tutti i moduli abilitati */
    fun getModuleScores(): List<Pair<String, Float>> {
        return providers
            .filter { it.isEnabled() }
            .map { it.displayName to it.getCurrentScore() }
    }

    /** Restituisce i nomi dei moduli che contribuiscono allo score */
    fun getContributingModuleNames(): String {
        return providers
            .filter { it.isEnabled() }
            .joinToString(", ") { it.displayName }
    }
}
