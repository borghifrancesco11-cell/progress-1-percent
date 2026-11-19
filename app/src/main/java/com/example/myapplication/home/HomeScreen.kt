package com.example.myapplication.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.navigation.Screen
import com.example.myapplication.ui.components.CircularProgress
import com.example.myapplication.ui.components.ConsistencyBar
import com.example.myapplication.ui.components.ProgressCard
import com.example.myapplication.ui.theme.*

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.refreshHome()
    }

    // Animated score percentage for Android 17 feel
    val animatedScoreInt by animateIntAsState(
        targetValue = state.totalScore.toInt(),
        animationSpec = tween(durationMillis = 900),
        label = "scoreCounter"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(36.dp))

            // ── Top Bar: Brand, Data, Theme Toggle & Settings ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = TextPrimary)) {
                                append("Progress ")
                            }
                            withStyle(style = SpanStyle(color = Mint)) {
                                append("1%")
                            }
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Mint)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = state.todayFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Theme Switcher (OLED Black / Clean Light)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DarkCard)
                            .border(1.dp, CardBorder, CircleShape)
                            .clickable { viewModel.toggleTheme() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.themeMode == "light") Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Cambia tema",
                            tint = if (state.themeMode == "light") TextPrimary else ScoreYellow,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Settings Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DarkCard)
                            .border(1.dp, CardBorder, CircleShape)
                            .clickable { onNavigate(Screen.Settings.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Impostazioni",
                            tint = TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Hero Score Card (Android 17 Expressive) ──
            val heroBorderColor = if (state.totalScore > 0f) CardBorderActive else CardBorder
            ProgressCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, heroBorderColor, RoundedCornerShape(28.dp)),
                cornerRadius = 28.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Mint)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "OGGI • FOCUS 1%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mint,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "$animatedScoreInt%",
                            style = MaterialTheme.typography.displayLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp,
                            fontSize = 46.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        val motivationalMsg = when {
                            state.totalScore >= 100f -> "Giorno perfetto: 100% completato! 🌟"
                            state.totalScore >= 50f -> "Oltre la metà. Sei in piena zona 1%!"
                            state.totalScore > 0f -> "Buon ritmo: continua a costruire il tuo slancio."
                            else -> "Inizia ora: il primo 1% di oggi ti aspetta."
                        }

                        Text(
                            text = motivationalMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${state.scoreText} aggregato",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Mint.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🔥 ${state.streakDays}g Streak",
                                    fontSize = 11.sp,
                                    color = Mint,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    CircularProgress(
                        percentage = state.totalScore,
                        size = 100.dp,
                        strokeWidth = 10.dp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Android 17 Quick Glance Row (Dynamic Pills) ──
            val quickScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(quickScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Gym/Cali Shortcut
                val gymLabel = if (state.workoutType == "calisthenics") "Calisthenics" else if (state.workoutType == "hybrid") "Workout Ibrido" else "Palestra"
                ExpressiveActionChip(
                    icon = Icons.Default.FitnessCenter,
                    label = gymLabel,
                    badge = if (state.todayWorkoutsCount > 0) "${state.todayWorkoutsCount} Fatto" else "Allenati",
                    accent = CategoryCyan,
                    onClick = { onNavigate(Screen.Gym.route) }
                )

                // Food AI Shortcut
                ExpressiveActionChip(
                    icon = Icons.Default.Restaurant,
                    label = "Food AI",
                    badge = "${state.todayCalories} kcal",
                    accent = CategoryOrange,
                    onClick = { onNavigate(Screen.Food.route) }
                )

                // Tracker Shortcut
                val habitsBadge = if (state.totalHabitsCount > 0) "${state.completedHabitsCount}/${state.totalHabitsCount}" else "Check"
                ExpressiveActionChip(
                    icon = Icons.Default.TrackChanges,
                    label = "Abitudini",
                    badge = habitsBadge,
                    accent = Mint,
                    onClick = { onNavigate(Screen.Tracker.route) }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Daily Stoic & Kaizen Wisdom Surface ──
            ProgressCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Mint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = Mint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "\"${state.dailyQuote}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 21.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "— ${state.dailyQuoteAuthor}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mint,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Interactive quote shuffle icon
                    IconButton(
                        onClick = { viewModel.nextQuote() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "Altra citazione",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 7 Days Consistency Matrix ──
            ProgressCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Costanza ultimi 7 giorni",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Score 0–100",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    state.consistencyDays.forEach { day ->
                        ConsistencyBar(
                            value = day.score,
                            dayLabel = day.dayName,
                            isToday = day.isToday
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Modular Grid Section Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "I TUOI SPAZI & MODULI",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )

                Text(
                    text = "${state.enabledModules.size} Attivi",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mint,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            // Tracker Full Width Hero Card
            val habitsSub = if (state.totalHabitsCount > 0) {
                "${state.completedHabitsCount} di ${state.totalHabitsCount} abitudini completate oggi"
            } else {
                "Abitudini quotidiane & Obiettivi a lungo termine"
            }
            ExpressiveFullCard(
                icon = Icons.Default.TrackChanges,
                title = "Tracker Abitudini",
                subtitle = habitsSub,
                tag = "CORE",
                accentColor = Mint,
                onClick = { onNavigate(Screen.Tracker.route) }
            )

            Spacer(Modifier.height(10.dp))

            // Dynamic 2-Column Expressive Grid
            val enabled = state.enabledModules
            val tiles = mutableListOf<TileData>()

            // 1. Food & Macro (Dual AI Model verification)
            if (enabled.contains("food")) {
                val foodSub = if (state.todayCalories > 0) "${state.todayCalories}/${state.targetCalories} kcal" else "Vision AI & Macro"
                tiles.add(TileData("Food & Macro", foodSub, Icons.Default.Restaurant, Screen.Food.route, CategoryOrange, tag = "AI"))
            }

            // 2. Gym / Calisthenics (Tailored routines)
            if (enabled.contains("gym")) {
                val gymTitle = if (state.workoutType == "calisthenics") "Calisthenics" else if (state.workoutType == "hybrid") "Workout Ibrido" else "Palestra"
                val gymSub = if (state.todayWorkoutsCount > 0) "${state.todayWorkoutsCount} completato" else "Schede & PR"
                tiles.add(TileData(gymTitle, gymSub, Icons.Default.FitnessCenter, Screen.Gym.route, CategoryCyan, tag = state.workoutType.take(4).uppercase()))
            }

            // 3. Blocco Social (Detox & limiti sicuri)
            if (enabled.contains("social_blocker")) {
                tiles.add(TileData("Blocco Social", "Detox & Limiti", Icons.Default.PhonelinkLock, Screen.SocialBlocker.route, ScoreRed, tag = "DETOX"))
            }

            // 4. Tasks (Daily checklist)
            if (enabled.contains("tasks")) {
                val tasksSub = if (state.tasksPendingCount > 0) "${state.tasksPendingCount} da completare" else "Tutto completato"
                tiles.add(TileData("Tasks", tasksSub, Icons.Default.Checklist, Screen.Tasks.route, CategoryAmber, tag = "TODO"))
            }

            // 5. School (Media & Simulatore)
            if (enabled.contains("school")) {
                tiles.add(TileData("School", "Medie & Esami", Icons.Default.School, Screen.School.route, CategoryBlue, tag = "STUDIO"))
            }

            // 6. Money (Budget & Finanze)
            if (enabled.contains("money")) {
                tiles.add(TileData("Money", "Budget & Spese", Icons.Default.AccountBalanceWallet, Screen.Money.route, ScoreGreen, tag = "CASH"))
            }

            // 7. Challenges (Sfide offline)
            if (enabled.contains("challenges")) {
                tiles.add(TileData("Sfide 1%", "Costanza & Sfide", Icons.Default.EmojiEvents, Screen.Challenges.route, ScoreYellow, tag = "GOAL"))
            }

            // 8. Wardrobe (Meteo & Outfit)
            if (enabled.contains("wardrobe")) {
                tiles.add(TileData("Wardrobe", "Meteo & Capsule", Icons.Default.Checkroom, Screen.Wardrobe.route, CategoryBlue, tag = "LOOK"))
            }

            tiles.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveHalfCard(
                        data = pair[0],
                        onClick = { onNavigate(pair[0].route) },
                        modifier = Modifier.weight(1f)
                    )
                    if (pair.size > 1) {
                        ExpressiveHalfCard(
                            data = pair[1],
                            onClick = { onNavigate(pair[1].route) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Spazio inferiore per evitare sovrapposizione con floating pill bar
            Spacer(Modifier.height(110.dp))
        }

        // ── Floating Dock Navigation Bar (Android 17 Pill) ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(DarkCard.copy(alpha = 0.95f))
                .border(1.dp, CardBorderActive, RoundedCornerShape(38.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home (Active Pill)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Mint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = Mint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Tracker
                IconButton(
                    onClick = { onNavigate(Screen.Tracker.route) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrackChanges,
                        contentDescription = "Tracker",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Palestra / Calisthenics
                IconButton(
                    onClick = { onNavigate(Screen.Gym.route) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = "Allenamento",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Food AI
                IconButton(
                    onClick = { onNavigate(Screen.Food.route) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = "Food",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings
                IconButton(
                    onClick = { onNavigate(Screen.Settings.route) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Impostazioni",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

data class TileData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val accentColor: Color = Mint,
    val tag: String = ""
)

@Composable
fun ExpressiveActionChip(
    icon: ImageVector,
    label: String,
    badge: String,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
            }

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ExpressiveFullCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    ProgressCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 22.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = tag,
                    fontSize = 11.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ExpressiveHalfCard(
    data: TileData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProgressCard(
        modifier = modifier
            .height(112.dp)
            .clickable(onClick = onClick),
        cornerRadius = 22.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(data.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = data.icon,
                        contentDescription = null,
                        tint = data.accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (data.tag.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = data.tag,
                            fontSize = 9.sp,
                            color = data.accentColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Column {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = data.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
