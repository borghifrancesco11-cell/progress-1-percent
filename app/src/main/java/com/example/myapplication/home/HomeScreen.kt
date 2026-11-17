package com.example.myapplication.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
            Spacer(Modifier.height(38.dp))

            // Header originale: "Progress 1%" con "1%" in Mint e icona Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = TextPrimary)) {
                            append("Progress ")
                        }
                        withStyle(style = SpanStyle(color = Mint)) {
                            append("1%")
                        }
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { onNavigate(Screen.Settings.route) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Impostazioni",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Data odierna in grigio
            Text(
                text = state.todayFormatted,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Main Score Card (Riproduzione fedele)
            ProgressCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Score of the week",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.scoreText,
                            style = MaterialTheme.typography.displayMedium,
                            color = Mint,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Based on ${state.contributingModules}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            lineHeight = 16.sp
                        )
                    }

                    CircularProgress(
                        percentage = state.totalScore,
                        size = 100.dp,
                        strokeWidth = 9.dp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Last 7 days consistency (Barrette orizzontali originali)
            ProgressCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last 7 days consistency",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "0-100",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }

                Spacer(Modifier.height(20.dp))

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

            Spacer(Modifier.height(28.dp))

            // YOUR SPACE Header
            Text(
                text = "YOUR SPACE",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(Modifier.height(14.dp))

            // Tracker Tile Full Width
            MinimalFullTile(
                icon = Icons.Default.TrackChanges,
                title = "Tracker",
                subtitle = "Abitudini & Goal",
                onClick = { onNavigate(Screen.Tracker.route) }
            )

            Spacer(Modifier.height(12.dp))

            // Griglia a 2 colonne pulita
            val enabled = state.enabledModules
            val tiles = mutableListOf<TileData>()

            if (enabled.contains("food")) {
                tiles.add(TileData("Food", "Diario & Macro AI", Icons.Default.Restaurant, Screen.Food.route))
            }
            if (enabled.contains("wardrobe")) {
                tiles.add(TileData("Wardrobe", "Outfit check", Icons.Default.Checkroom, Screen.Wardrobe.route))
            }
            if (enabled.contains("gym")) {
                tiles.add(TileData("Palestra", "OpenGym", Icons.Default.FitnessCenter, Screen.Gym.route))
            }
            if (enabled.contains("school")) {
                tiles.add(TileData("School", "Medie & Verifiche", Icons.Default.School, Screen.School.route))
            }
            if (enabled.contains("money")) {
                tiles.add(TileData("Money", "Budget & Spese", Icons.Default.AccountBalanceWallet, Screen.Money.route))
            }
            if (enabled.contains("tasks")) {
                tiles.add(TileData("Tasks", "Promemoria oggi", Icons.Default.Checklist, Screen.Tasks.route))
            }
            if (enabled.contains("challenges")) {
                tiles.add(TileData("Challenges", "Sfide offline", Icons.Default.EmojiEvents, Screen.Challenges.route))
            }
            if (enabled.contains("culture")) {
                tiles.add(TileData("Cultura", "Audiolibri & Quiz", Icons.AutoMirrored.Filled.MenuBook, Screen.Culture.route))
            }
            if (enabled.contains("social_blocker")) {
                tiles.add(TileData("Blocco Social", "Detox & Limiti", Icons.Default.PhonelinkLock, Screen.SocialBlocker.route))
            }

            tiles.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MinimalHalfTile(
                        data = pair[0],
                        onClick = { onNavigate(pair[0].route) },
                        modifier = Modifier.weight(1f)
                    )
                    if (pair.size > 1) {
                        MinimalHalfTile(
                            data = pair[1],
                            onClick = { onNavigate(pair[1].route) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Spazio per non coprire il contenuto con la dock bar in basso
            Spacer(Modifier.height(90.dp))
        }

        // ── Floating Dock Navigation Bar Minimal ──
        // Rende la navigazione tra le funzioni principali immediata e chiarissima
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(DarkCard)
                .border(1.dp, CardBorder, RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Icon attiva
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Mint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = Mint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Tracker Quick Nav
                IconButton(
                    onClick = { onNavigate(Screen.Tracker.route) },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrackChanges,
                        contentDescription = "Tracker",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Gym Quick Nav
                IconButton(
                    onClick = { onNavigate(Screen.Gym.route) },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = "Palestra",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Food Quick Nav
                IconButton(
                    onClick = { onNavigate(Screen.Food.route) },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = "Cibo",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Settings Quick Nav
                IconButton(
                    onClick = { onNavigate(Screen.Settings.route) },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Impostazioni",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
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
    val route: String
)

@Composable
fun MinimalFullTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ProgressCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Mint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
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
    }
}

@Composable
fun MinimalHalfTile(
    data: TileData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProgressCard(
        modifier = modifier
            .height(106.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = Mint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = data.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
