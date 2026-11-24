package com.progressone.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.CoachInsight
import com.progressone.nativeapp.data.CoachTone

/** Compact, consistent presentation for every deterministic on-device Coach insight. */
@Composable
fun CoachCard(
    insight: CoachInsight,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit,
) {
    val accent = when (insight.tone) {
        CoachTone.POSITIVE -> Mint
        CoachTone.ACTION -> Ice
        CoachTone.RECOVERY -> Lavender
        CoachTone.NEUTRAL -> Mist
    }
    FrostCard(modifier.fillMaxWidth(), highlighted = true) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("${insight.eyebrow} · ON-DEVICE", accent)
                Text(
                    insight.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    insight.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist
                )
                if (insight.actionLabel.isNotBlank() && insight.actionRoute.isNotBlank()) {
                    TextButton(
                        onClick = { onAction(insight.actionRoute) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(insight.actionLabel, color = accent)
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = accent)
                    }
                }
            }
        }
    }
}
