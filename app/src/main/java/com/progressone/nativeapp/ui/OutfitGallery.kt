package com.progressone.nativeapp.ui

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local-only outfit library inspired by the gallery/details shown in the user reference video.
 * No social feed, network API, account, or changes to existing database tables.
 * Metadata is stored in each outfit's existing JSON payload for backup compatibility.
 */
fun outfitWearCount(look: JSONObject): Int = look.optJSONArray("wearHistory")?.length() ?: 0

fun outfitLastWorn(look: JSONObject): String {
    val history = look.optJSONArray("wearHistory") ?: return ""
    return (0 until history.length()).map { history.optString(it) }.maxOrNull() ?: ""
}

private fun outfitPieces(look: JSONObject, garments: List<JSONObject>): List<JSONObject> {
    val ids = look.optJSONArray("items") ?: return emptyList()
    val byId = garments.associateBy { it.str("id") }
    return (0 until ids.length()).mapNotNull { byId[ids.optString(it)] }
}

private fun orderedPieces(pieces: List<JSONObject>): List<JSONObject> {
    val priorities = mapOf("Outerwear" to 0, "Top" to 1, "Bottom" to 2, "Shoes" to 3, "Accessories" to 4)
    return pieces.sortedBy { priorities[it.str("category")] ?: 5 }
}

@Composable
private fun OutfitPhoto(store: LocalStore, photo: String, modifier: Modifier = Modifier) {
    val file = store.photoFile(photo)
    val image by produceState<android.graphics.Bitmap?>(initialValue = null, photo, file?.lastModified()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (file?.isFile != true) null else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                        val factor = (maxOf(bounds.outWidth, bounds.outHeight) / 420).coerceAtLeast(1)
                        BitmapFactory.decodeFile(file.absolutePath,
                            BitmapFactory.Options().apply { inSampleSize = factor })
                    }
                }
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image != null) Image(
            bitmap = image!!.asImageBitmap(),
            contentDescription = "Outfit garment",
            modifier = Modifier.fillMaxSize().padding(3.dp),
            contentScale = ContentScale.Fit
        ) else Icon(Icons.Rounded.Checkroom, "No garment photo",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
            modifier = Modifier.size(28.dp))
    }
}

@Composable
fun OutfitCollage(store: LocalStore, pieces: List<JSONObject>, modifier: Modifier = Modifier) {
    val ordered = orderedPieces(pieces)
    val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .37f)
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(surface), contentAlignment = Alignment.Center) {
        if (ordered.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Style, null, tint = Mist, modifier = Modifier.size(36.dp))
                Text("No garments", color = Mist, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val topCount = minOf(ordered.size, 2)
                Row(Modifier.fillMaxWidth().weight(if (ordered.size > 2) .64f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ordered.take(topCount).forEach { g ->
                        OutfitPhoto(store, g.str("photo"), Modifier.weight(1f).fillMaxHeight())
                    }
                }
                if (ordered.size > 2) {
                    Row(Modifier.fillMaxWidth().weight(.36f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ordered.drop(2).take(2).forEach { g ->
                            OutfitPhoto(store, g.str("photo"), Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            if (ordered.size > 4) Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .86f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp)
            ) { Text("+${ordered.size - 4}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun OutfitGridTile(
    store: LocalStore,
    look: JSONObject,
    garments: List<JSONObject>,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val pieces = outfitPieces(look, garments)
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clip(shape).background(scheme.surfaceVariant.copy(alpha = .48f))
            .border(1.dp, scheme.outline.copy(alpha = .25f), shape)
            .clickable(onClick = onOpen).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            OutfitCollage(store, pieces, Modifier.fillMaxWidth().aspectRatio(.86f))
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd).size(36.dp)
            ) {
                Icon(
                    if (look.optBoolean("favorite")) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (look.optBoolean("favorite")) "Remove favorite" else "Add favorite",
                    tint = if (look.optBoolean("favorite")) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        Text(look.str("name"), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${look.str("occasion", "Everyday")} · ${pieces.size} items",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val worn = outfitWearCount(look)
        val scheduled = look.str("plannedDate")
        if (scheduled.isNotBlank()) Text("Planned $scheduled", color = scheme.primary,
            style = MaterialTheme.typography.labelSmall)
        else Text(if (worn == 0) "Not worn yet" else "Worn $worn ${if (worn == 1) "time" else "times"}",
            color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun OutfitDetailsSheet(
    store: LocalStore,
    look: JSONObject,
    garments: List<JSONObject>,
    onEdit: (JSONObject) -> Unit,
    onChanged: (JSONObject) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val pieces = outfitPieces(look, garments)
    val count = outfitWearCount(look)
    val last = outfitLastWorn(look)
    var confirmDelete by remember(look.str("id")) { mutableStateOf(false) }
    fun update(change: (JSONObject) -> Unit) {
        val edited = JSONObject(look.toString())
        change(edited)
        store.put("outfit", edited)
        onChanged(edited)
    }
    BottomSheetFrame(look.str("name"), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutfitCollage(store, pieces, Modifier.fillMaxWidth().height(245.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(look.str("occasion", "Everyday"), style = MaterialTheme.typography.titleMedium)
                    Text("${pieces.size} garments · ${look.str("season", "All")}", color = Mist)
                }
                IconButton(onClick = {
                    update { it.put("favorite", !it.optBoolean("favorite")) }
                }) {
                    Icon(if (look.optBoolean("favorite")) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "Toggle favorite", tint = if (look.optBoolean("favorite")) MaterialTheme.colorScheme.error else Ice)
                }
            }
            val tags = look.optJSONArray("tags")
            if (tags != null && tags.length() > 0) {
                Text("Style: " + (0 until tags.length()).joinToString(", ") { tags.optString(it) }, color = Mist)
            }
            if (look.str("note").isNotBlank()) Text(look.str("note"), color = MaterialTheme.colorScheme.onSurface)
            SectionDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("TIMES WORN")
                    Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f)) {
                    Eyebrow("LAST WORN")
                    Text(if (last.isBlank()) "—" else last, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (look.str("plannedDate").isNotBlank()) Text(
                "Scheduled for ${look.str("plannedDate")}",
                color = MaterialTheme.colorScheme.primary
            )
            Button(onClick = {
                val day = Metrics.day()
                update { edited ->
                    val original = edited.optJSONArray("wearHistory") ?: JSONArray()
                    val extended = JSONArray(original.toString()).apply { put(day) }
                    edited.put("wearHistory", extended)
                    if (edited.str("plannedDate") == day) edited.remove("plannedDate")
                }
                // Keep garment wear counters compatible with the existing wardrobe logic.
                pieces.forEach { g -> store.put("wear", LocalStore.obj("item" to g.str("id"), "date" to day)) }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Wore this outfit")
            }
            OutlinedButton(onClick = {
                val initial = runCatching { LocalDate.parse(look.str("plannedDate")) }
                    .getOrElse { LocalDate.now() }
                DatePickerDialog(ctx, { _, year, month, day ->
                    val selected = LocalDate.of(year, month + 1, day).toString()
                    update { it.put("plannedDate", selected) }
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text(if (look.str("plannedDate").isNotBlank()) "Change planned date" else "Plan an outfit")
            }
            if (look.str("plannedDate").isNotBlank()) TextButton(onClick = {
                update { it.remove("plannedDate") }
            }) { Text("Clear planned date") }
            OutlinedButton(onClick = { onEdit(look) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Edit, null)
                Spacer(Modifier.width(8.dp))
                Text("Edit outfit")
            }
            TextButton(onClick = { update { it.put("hidden", !it.optBoolean("hidden")) } }) {
                Icon(if (look.optBoolean("hidden")) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, null)
                Spacer(Modifier.width(8.dp))
                Text(if (look.optBoolean("hidden")) "Show in outfit gallery" else "Hide from gallery")
            }
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Delete outfit", color = MaterialTheme.colorScheme.error)
            }
            Text("Planning a date does not schedule a phone notification.",
                style = MaterialTheme.typography.labelSmall, color = Mist)
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete outfit?") },
        text = { Text("The outfit will be removed. Its garments and their photos stay in your wardrobe.") },
        confirmButton = {
            TextButton(onClick = {
                store.remove("outfit", look.str("id"))
                confirmDelete = false
                onDismiss()
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}
