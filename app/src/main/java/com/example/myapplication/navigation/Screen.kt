package com.example.myapplication.navigation

/** Rotte di navigazione dell'app */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object Onboarding : Screen("onboarding")

    // Moduli core
    data object Tracker : Screen("tracker")
    data object School : Screen("school")
    data object Money : Screen("money")
    data object Tasks : Screen("tasks")
    data object Challenges : Screen("challenges")
    data object Food : Screen("food")
    data object Wardrobe : Screen("wardrobe")

    // Nuovi moduli
    data object Gym : Screen("gym")
    data object SocialBlocker : Screen("social_blocker")
    data object Sleep : Screen("sleep")
}
