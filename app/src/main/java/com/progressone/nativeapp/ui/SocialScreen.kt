package com.progressone.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.SocialLimit
import com.progressone.nativeapp.data.SocialUtils
import kotlin.math.roundToInt

/** Social detox — local daily limits read from Android Digital Wellbeing usage stats. */
@Composable
fun SocialScreen(store: LocalStore) {
    val context = LocalContext.current
    val revision = store.revision
    val masterOn = remember(revision) { store.setting("social_master", "true") == "true" }
    var apps by remember(revision) { mutableStateOf(SocialUtils.loadApps(store)) }
    var permission by remember { mutableStateOf(SocialUtils.hasUsagePermission(context)) }

    val activeApps = apps.filter { it.enabled && it.limitMinutes > 0 }
    val usage = remember(apps, permission, revision) {
        if (!permission) emptyMap()
        else activeApps.associate { it.packageName to SocialUtils.usageMinutesToday(context, it.packageName) }
    }
    val withinLimits = activeApps.count { (usage[it.packageName] ?: 0) <= it.limitMinutes }
    val score = remember(apps, usage, permission, masterOn) {
        if (!masterOn || !permission || activeApps.isEmpty()) null
        else activeApps.map { app ->
            val used = usage[app.packageName] ?: 0
            if (used <= app.limitMinutes) 100.0
            else (100.0 - (used - app.limitMinutes).toDouble() / app.limitMinutes * 100.0).coerceAtLeast(0.0)
        }.average().roundToInt()
    }

    fun persist(updated: List<SocialLimit>) {
        apps = updated
        SocialUtils.saveApps(store, updated)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeading("DIGITAL WELLBEING", "Social detox", "Set daily limits and protect your attention")
        }

        item {
            FrostCard(Modifier.fillMaxWidth(), highlighted = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(if (masterOn) "LIMITS ACTIVE" else "DETOX PAUSED", Mint)
                        Text(
                            text = when {
                                !masterOn -> "Paused"
                                !permission -> "Permission needed"
                                activeApps.isEmpty() -> "No limits set"
                                else -> "$withinLimits / ${activeApps.size} within limits"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Ice
                        )
                        Text(
                            text = "Usage is read on this device only. Nothing is uploaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mist
                        )
                    }
                    if (score != null) ProgressRing(score / 100f, Modifier.size(92.dp), caption = "detox", sizeDp = 92.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable social detox", style = MaterialTheme.typography.titleSmall)
                        Text("Turn all limits on or off with one switch.", style = MaterialTheme.typography.bodySmall, color = Mist)
                    }
                    Switch(checked = masterOn, onCheckedChange = {
                        store.setSetting("social_master", it.toString())
                    })
                }
            }
        }

        if (!permission) {
            item {
                FrostCard(Modifier.fillMaxWidth()) {
                    Eyebrow("ONE-TIME SETUP", Mint)
                    Text("Allow usage access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android needs Digital Wellbeing access to show how long each app has been used today. The app only reads totals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist
                    )
                    Button(
                        onClick = { SocialUtils.openUsageSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open usage access settings")
                    }
                    OutlinedButton(
                        onClick = { permission = SocialUtils.hasUsagePermission(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("I granted access · Check again") }
                }
            }
        }

        item {
            SectionHeading("Daily limits", "Tap + or − to adjust by five minutes")
        }

        items(apps, key = { it.packageName }) { app ->
            val used = usage[app.packageName] ?: 0
            val over = permission && app.enabled && used > app.limitMinutes
            FrostCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (!permission) "Limit ${app.limitMinutes} min"
                            else "${used} min today · limit ${app.limitMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (over) Color(0xFFFF6B6B) else Mist
                        )
                        if (over) {
                            Text(
                                "Limit exceeded by ${used - app.limitMinutes} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF6B6B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Switch(
                        checked = app.enabled,
                        onCheckedChange = { on ->
                            persist(apps.map { if (it.packageName == app.packageName) it.copy(enabled = on) else it })
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            persist(apps.map {
                                if (it.packageName == app.packageName) it.copy(limitMinutes = (it.limitMinutes - 5).coerceAtLeast(5)) else it
                            })
                        },
                        enabled = app.enabled,
                        modifier = Modifier.weight(1f)
                    ) { Text("− 5 min") }
                    Text(
                        "${app.limitMinutes} min",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (app.enabled) Ice else Mist
                    )
                    OutlinedButton(
                        onClick = {
                            persist(apps.map {
                                if (it.packageName == app.packageName) it.copy(limitMinutes = (it.limitMinutes + 5).coerceAtMost(600)) else it
                            })
                        },
                        enabled = app.enabled,
                        modifier = Modifier.weight(1f)
                    ) { Text("+ 5 min") }
                }
            }
        }

        item {
            FrostCard(Modifier.fillMaxWidth()) {
                Eyebrow("HOW IT WORKS")
                Text(
                    "Progress 1% compares today's foreground time with your limit. Stay under it and the module keeps the weekly score high; exceed it and the score drops proportionally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
