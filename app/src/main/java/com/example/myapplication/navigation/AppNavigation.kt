package com.example.myapplication.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.challenges.ChallengesScreen
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.food.FoodScreen
import com.example.myapplication.gym.GymScreen
import com.example.myapplication.home.HomeScreen
import com.example.myapplication.money.MoneyScreen
import com.example.myapplication.onboarding.OnboardingScreen
import com.example.myapplication.school.SchoolScreen
import com.example.myapplication.settings.SettingsScreen
import com.example.myapplication.sleep.SleepScreen
import com.example.myapplication.socialblocker.SocialBlockerScreen
import com.example.myapplication.tasks.TasksScreen
import com.example.myapplication.tracker.TrackerScreen
import com.example.myapplication.wardrobe.WardrobeScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val settingsRepo = SettingsRepository(context)
    val settings by settingsRepo.settings.collectAsState()

    val startDestination = if (settings.onboardingCompleted) {
        Screen.Home.route
    } else {
        Screen.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Tracker.route) {
            TrackerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.School.route) {
            SchoolScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Money.route) {
            MoneyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Tasks.route) {
            TasksScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Challenges.route) {
            ChallengesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Food.route) {
            FoodScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Wardrobe.route) {
            WardrobeScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Gym.route) {
            GymScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SocialBlocker.route) {
            SocialBlockerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Sleep.route) {
            SleepScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
