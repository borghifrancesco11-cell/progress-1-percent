package com.progressone.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.moduleEnabled
import com.progressone.nativeapp.reminders.ReminderScheduler
import com.progressone.nativeapp.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One home, no pager, no bottom bar, no background compositing of other pages. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val store = remember { LocalStore(applicationContext) }
            DisposableEffect(store) { onDispose { store.close() } }
            val revision = store.revision
            val theme = remember(revision) { store.setting("ui_theme", "oled") }
            ProgressTheme(theme = theme, language = "en") { NativeRoot(store) }
        }
    }
}

private val sectionTitles = mapOf(
    "track" to "Tracker", "wardrobe" to "Wardrobe", "food" to "Food",
    "school" to "School", "money" to "Money", "gym" to "Gym", "sleep" to "Sleep",
    "social" to "Social detox",
    "challenges" to "Challenges", "summary" to "Progress", "settings" to "Settings",
    "tasks" to "Tasks"
)

@Composable
fun NativeRoot(store: LocalStore) {
    val revision = store.revision
    // SQLite reading happens off the UI thread, preserving the last snapshot during refresh.
    val state = produceState<AppSnapshot?>(initialValue = null, store, revision) {
        value = withContext(Dispatchers.IO) { AppSnapshot.load(store) }
    }
    val snapshot = state.value
    val reduceMotion = remember(revision) { store.setting("reduce_motion", "false") == "true" }
    val enabled: (String) -> Boolean = { store.moduleEnabled(it) }
    var page by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (store.setting("onboarding_complete", "false") != "true") page = "onboarding"
        else ReminderScheduler.deliverDue(context, store)
    }
    LaunchedEffect(revision) {
        withContext(Dispatchers.IO) { ReminderScheduler.reschedule(context, store) }
    }
    BackHandler(page.isNotEmpty() && page != "onboarding") { page = "" }

    Scaffold(containerColor = Night, topBar = {
        Surface(color = Night) {
            Row(Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (page.isNotEmpty() && page != "onboarding") {
                    IconButton(onClick = { page = "" }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Home")
                    }
                }
                Text(if (page.isNotEmpty() && page != "onboarding") sectionTitles[page] ?: "Progress 1%"
                     else "Progress", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                if (page.isEmpty() || page == "onboarding") {
                    Text(" 1%", style = MaterialTheme.typography.titleLarge, color = Ice,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                if (page.isEmpty()) IconButton(onClick = { page = "settings" }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().background(Night).padding(padding)) {
            if (snapshot == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ice, modifier = Modifier.size(28.dp))
                }
            } else AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        val opening = targetState.isNotEmpty()
                        val enter = slideInHorizontally(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) { full -> if (opening) full / 10 else -full / 10 } + fadeIn(tween(220))
                        val exit = slideOutHorizontally(
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) { full -> if (opening) -full / 14 else full / 14 } + fadeOut(tween(170))
                        enter togetherWith exit
                    }
                },
                label = "open-section"
            ) { selected ->
                when (selected) {
                    "onboarding" -> FirstRunTutorial(store = store, onFinish = {
                        store.setSetting("onboarding_complete", "true")
                        page = ""
                    })
                    "settings" -> SettingsScreen(store, onBack = { page = "" },
                        onReplayTutorial = { page = "onboarding" })
                    "track" -> TrackScreen(store, snapshot,
                        onChallenges = { if (enabled("challenges")) page = "challenges" },
                        showChallenges = enabled("challenges"))
                    "wardrobe" -> WardrobeScreen(store, snapshot)
                    "food" -> FoodScreen(store)
                    "school" -> SchoolScreen(store, snapshot)
                    "money" -> FinanceScreen(store, snapshot)
                    "challenges" -> ChallengesScreen(store, snapshot, onBack = { page = "" })
                    "tasks" -> TasksScreen(store, snapshot, onBack = { page = "" })
                    "gym" -> GymScreen(store, snapshot, onBack = { page = "" })
                    "sleep" -> SleepScreen(store, snapshot, onBack = { page = "" })
                    "social" -> SocialScreen(store, onBack = { page = "" })
                    "summary" -> SummaryScreen(store, snapshot,
                        showSchool = enabled("school"), showWardrobe = enabled("wardrobe"),
                        showMoney = enabled("money"), showChallenges = enabled("challenges"),
                        showOutfits = enabled("outfits"), onBack = { page = "" })
                    else -> TodayScreen(store = store, snapshot = snapshot, isEnabled = enabled,
                        onOpen = { destination ->
                            if (destination in setOf("track", "settings", "summary", "challenges", "tasks") || enabled(destination))
                                page = destination
                        })
                }
            }
        }
    }
}
