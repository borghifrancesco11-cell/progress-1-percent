package com.example.myapplication.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

/** Card con stile minimal originale di Progress 1% (angoli morbidi, bordo scuro tenue) */
@Composable
fun ProgressCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val mod = modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(DarkCard)
        .border(1.dp, CardBorder, RoundedCornerShape(cornerRadius))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(18.dp)

    Column(modifier = mod, content = content)
}

/** Header standard minimal per le schermate dei moduli (come negli screenshot originali) */
@Composable
fun ModuleHeader(
    categoryLabel: String,
    title: String,
    subtitle: String = "",
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tasto Indietro tondo minimal
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
                    .border(1.dp, CardBorder, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }

        Spacer(Modifier.height(14.dp))

        // Etichetta maiuscola verde/grigio (es. AI NUTRITION & FOOD)
        Text(
            text = categoryLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Mint,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        Spacer(Modifier.height(4.dp))

        // Titolo grande (es. Food, Tasks, Tracker)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}

/** Anello circolare di percentuale (come in Home) */
@Composable
fun CircularProgress(
    percentage: Float,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = DarkSurfaceVariant,
    progressColor: Color = Mint,
    showText: Boolean = true,
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 800),
        label = "circularProgress",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweep = 360f * (animatedPercentage / 100f)
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            val arcOffset = Offset(stroke.width / 2, stroke.width / 2)

            // Traccia di sfondo
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
                size = arcSize,
                topLeft = arcOffset,
            )
            // Barra di progresso
            if (sweep > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke,
                    size = arcSize,
                    topLeft = arcOffset,
                )
            }
        }
        if (showText) {
            Text(
                text = "${animatedPercentage.toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
    }
}

/** Barra del giorno per la consistency chart (riproduce esattamente l'aspetto orizzontale in home_final.png) */
@Composable
fun ConsistencyBar(
    value: Float,
    dayLabel: String,
    isToday: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Valore numerico
        Text(
            text = value.toInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) Mint else TextSecondary,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(8.dp))
        // Barretta orizzontale: pillola verde se oggi, lineetta scura altrimenti
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(if (isToday) 5.dp else 3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isToday) Mint else DarkSurfaceLight),
        )
        Spacer(Modifier.height(8.dp))
        // Lettera del giorno
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) Mint else TextTertiary,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Barra di progresso lineare con etichetta */
@Composable
fun LabeledProgressBar(
    label: String,
    value: Float,
    maxValue: Float,
    modifier: Modifier = Modifier,
    color: Color = Mint,
) {
    val progressRatio = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(
                "${value.toInt()} / ${maxValue.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progressRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = DarkSurfaceVariant,
        )
    }
}

/** Chip minimal selezionabile per filtri */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Mint.copy(alpha = 0.15f) else DarkCard)
            .border(1.dp, if (selected) Mint else CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) Mint else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** Pulsante FAB minimal */
@Composable
fun ProgressFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String = "",
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Mint,
        contentColor = DarkBackground,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(4.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}

/** Top bar compatibile se usata in scaffold */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkBackground,
            titleContentColor = TextPrimary,
            navigationIconContentColor = Mint,
            actionIconContentColor = Mint,
        ),
    )
}
