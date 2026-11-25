@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.progressone.nativeapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Central appearance tokens; screen code never writes theme-dependent hex values. */
data class VisualStyle(
    val key: String,
    val title: String,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val accent: Color,
    val second: Color,
    val third: Color,
    val text: Color,
    val muted: Color,
    val roundness: Int,
    val glass: Boolean = false
)

object ThemeCatalog {
    val themes = listOf(
        VisualStyle("oled", "OLED Dark", true, Color.Black, Color(0xFF0B0E0D),
            Color(0xFF121A18), Color(0xFF1F2E2B), Color(0xFF6EE7B7), Color(0xFF34D399),
            Color(0xFF10B981), Color(0xFFF0FDFA), Color(0xFF6B8A80), 16),
        VisualStyle("light", "Light", false, Color(0xFFF8FBF9), Color.White,
            Color(0xFFEAF2EE), Color(0xFFD9E8DF), Color(0xFF059669), Color(0xFF047857),
            Color(0xFF065F46), Color(0xFF0F1714), Color(0xFF475560), 16)
    )
    fun find(key: String): VisualStyle = if (key == "light" || key == "pixel_light" || key == "neutral")
        themes[1] else themes[0]
}

val LocalVisualStyle = staticCompositionLocalOf { ThemeCatalog.themes.first() }
val Night: Color @Composable get() = LocalVisualStyle.current.background
val Glass: Color @Composable get() = LocalVisualStyle.current.surface
val GlassLight: Color @Composable get() = LocalVisualStyle.current.raised
val Ice: Color @Composable get() = LocalVisualStyle.current.accent
val Mint: Color @Composable get() = LocalVisualStyle.current.second
val Lavender: Color @Composable get() = LocalVisualStyle.current.third
val Mist: Color @Composable get() = LocalVisualStyle.current.muted

@Composable
fun ProgressTheme(theme: String = "oled", language: String = "en", content: @Composable () -> Unit) {
    val style = ThemeCatalog.find(theme)
    val scheme = if (style.dark) darkColorScheme(
        primary = style.accent, onPrimary = style.background, secondary = style.second,
        tertiary = style.third, background = style.background, onBackground = style.text,
        surface = style.surface, onSurface = style.text,
        surfaceVariant = style.raised, onSurfaceVariant = style.muted, outline = style.border
    ) else lightColorScheme(
        primary = style.accent, onPrimary = Color.White, secondary = style.second,
        tertiary = style.third, background = style.background, onBackground = style.text,
        surface = style.surface, onSurface = style.text,
        surfaceVariant = style.raised, onSurfaceVariant = style.muted, outline = style.border
    )
    CompositionLocalProvider(LocalVisualStyle provides style, LocalAppLanguage provides language) {
        MaterialTheme(colorScheme = scheme, typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
        ), content = content)
    }
}

@Composable
fun FrostCard(modifier: Modifier = Modifier, highlighted: Boolean = false,
              onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val style = LocalVisualStyle.current
    val shape = RoundedCornerShape(style.roundness.dp)
    val colors = if (highlighted) listOf(style.raised, style.surface) else listOf(style.surface, style.surface)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "frostPress"
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(1.dp, style.border.copy(alpha = .72f), shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = onClick
                ) else Modifier
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

@Composable fun SectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.fillMaxWidth(), thickness = 1.dp,
        color = LocalVisualStyle.current.border.copy(alpha = .65f))
}

@Composable fun Eyebrow(text: String, color: Color = Mist) {
    Text(tr(text).uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium), color = color)
}

@Composable fun PageHeading(eyebrow: String, name: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(eyebrow)
        Text(tr(name), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(tr(detail), color = Mist, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun SectionHeading(title: String, caption: String = "", trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(tr(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (caption.isNotBlank()) Text(tr(caption), color = Mist, style = MaterialTheme.typography.bodySmall)
        }
        trailing?.invoke()
    }
}

/** The circle is the heart of the app — everything revolves around the percentage. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, caption: String = "", sizeDp: Dp = 132.dp) {
    val animated by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 260f),
        label = "ring")
    val style = LocalVisualStyle.current
    val background = GlassLight
    val foreground = style.accent
    val sweep = Brush.linearGradient(listOf(style.second, style.accent))
    Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (sizeDp.value * 0.07f).coerceAtLeast(6f).coerceAtMost(12f).dp.toPx()
            drawArc(background, 0f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(sweep, -90f, 360f * animated, false, style = Stroke(stroke, cap = StrokeCap.Round))
                val angle = Math.toRadians((-90f + 360f * animated).toDouble())
                val radius = (size.width / 2f) - stroke / 2f
                drawCircle(
                    color = foreground.copy(alpha = 0.55f),
                    radius = stroke * 0.42f,
                    center = androidx.compose.ui.geometry.Offset(
                        x = size.width / 2f + (radius * Math.cos(angle)).toFloat(),
                        y = size.height / 2f + (radius * Math.sin(angle)).toFloat()
                    )
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(animated * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1)
            if (caption.isNotBlank()) Text(tr(caption), color = Mist, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable fun SmallStat(label: String, value: String, modifier: Modifier = Modifier, tint: Color = Ice) {
    FrostCard(modifier) {
        Eyebrow(label)
        Text(value, color = tint, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun EmptyPanel(title: String, subtitle: String, onAdd: (() -> Unit)? = null) {
    FrostCard(Modifier.fillMaxWidth()) {
        Text(tr(title), fontWeight = FontWeight.Medium)
        Text(tr(subtitle), color = Mist)
        if (onAdd != null) TextButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("Add your first"))
        }
    }
}

@Composable fun Chips(values: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(values.size) { index ->
            val value = values[index]
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(tr(value)) })
        }
    }
}

@Composable fun FormField(label: String, value: String, onChange: (String) -> Unit,
              modifier: Modifier = Modifier, singleLine: Boolean = true,
              keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(tr(label)) }, singleLine = singleLine,
        keyboardOptions = keyboardOptions, shape = RoundedCornerShape(14.dp))
}

@Composable fun BottomSheetFrame(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Mist) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .navigationBarsPadding().imePadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr(title), style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

fun JSONObject.str(key: String, default: String = ""): String = optString(key, default).let { if (it == "null") default else it }
fun JSONObject.num(key: String, default: Double = 0.0): Double = optDouble(key, default)
/** Parse a user-entered number while rejecting overflow values such as `1e309`. */
fun String.cleanNumber(): Double = replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
fun euro(value: Double): String = "€%.2f".format(java.util.Locale.ITALY, value)
fun openSource(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
}
