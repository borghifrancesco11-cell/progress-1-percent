package com.progressone.nativeapp.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import com.progressone.nativeapp.data.OutfitLogic
import org.json.JSONArray
import org.json.JSONObject

/**
 * A private, offline outfit mixer. The first candidate in each category is selected by default.
 * Nothing is written to the database until the user explicitly saves the combination.
 * Locks are scoped to the current mixer session.
 */
@Composable
fun MixMatchScreen(
    store: LocalStore,
    clothes: List<JSONObject>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val categories = listOf("Top", "Bottom", "Shoes", "Outerwear", "Accessories")
    val groups = remember(clothes) {
        categories.associateWith { category ->
            clothes.filter { it.optString("category").equals(category, ignoreCase = true) }
                .sortedBy { it.optString("name").lowercase() }
        }
    }
    val active = categories.filter { !groups[it].isNullOrEmpty() }
    val positions = remember(clothes) { mutableStateMapOf<String, Int>() }
    val locks = remember { mutableStateListOf<String>() }
    var outfitName by remember { mutableStateOf("") }
    var occasion by remember { mutableStateOf("Everyday") }
    var error by remember { mutableStateOf("") }

    fun selected(category: String): JSONObject? {
        val candidates = groups[category].orEmpty()
        return if (candidates.isEmpty()) null else candidates[positions.getOrDefault(category, 0)
            .mod(candidates.size)]
    }

    fun move(category: String, delta: Int) {
        if (category in locks) return
        val candidates = groups[category].orEmpty()
        if (candidates.size <= 1) return
        val current = positions.getOrDefault(category, 0)
        positions[category] = OutfitLogic.nextIndex(current, candidates.size, delta)
    }

    fun shuffle() {
        active.forEach { category ->
            if (category !in locks) {
                val candidates = groups[category].orEmpty()
                if (candidates.size > 1) {
                    val current = positions.getOrDefault(category, 0)
                    positions[category] = OutfitLogic.shuffleIndex(current, candidates.size)
                }
            }
        }
    }

    BottomSheetFrame(tr("Mix & Match"), onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                tr("Swipe through your clothes, lock favorites, then save your look."),
                color = Mist,
                style = MaterialTheme.typography.bodySmall
            )
            if (active.isEmpty()) {
                EmptyPanel(tr("Your wardrobe is empty"), tr("Add garments first, then return here to mix outfits."))
            } else {
                val chosen = active.mapNotNull(::selected)
                OutfitCollage(store, chosen, Modifier.fillMaxWidth().height(205.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${chosen.size} ${tr("garments selected")}", Modifier.weight(1f), color = Mist,
                        style = MaterialTheme.typography.labelMedium)
                    FilledTonalButton(onClick = ::shuffle) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null,
                            modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Shuffle unlocked"))
                    }
                }
                active.forEach { category ->
                    val current = selected(category) ?: return@forEach
                    val candidateCount = groups[category].orEmpty().size
                    val locked = category in locks
                    FrostCard(Modifier.fillMaxWidth().pointerInput(category, locked, candidateCount) {
                        var totalDx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDx = 0f },
                            onHorizontalDrag = { change, distance ->
                                totalDx += distance
                                change.consume()
                            },
                            onDragEnd = {
                                if (!locked && abs(totalDx) > 55f) move(category, if (totalDx < 0f) 1 else -1)
                                totalDx = 0f
                            }
                        )
                    }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Eyebrow(tr(category))
                                Text(current.optString("name", category),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("${positions.getOrDefault(category, 0) + 1} / $candidateCount",
                                    color = Mist, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { move(category, -1) }, enabled = !locked && candidateCount > 1) {
                                Icon(Icons.Rounded.ChevronLeft, tr("Previous garment"))
                            }
                            IconButton(onClick = { move(category, 1) }, enabled = !locked && candidateCount > 1) {
                                Icon(Icons.Rounded.ChevronRight, tr("Next garment"))
                            }
                            IconButton(onClick = {
                                if (locked) locks.remove(category) else locks.add(category)
                            }) {
                                Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    if (locked) tr("Unlock item") else tr("Lock item"),
                                    tint = if (locked) MaterialTheme.colorScheme.primary else Mist)
                            }
                        }
                    }
                }
                SectionDivider()
                FormField(tr("Outfit name"), outfitName, { outfitName = it.take(80) })
                Eyebrow(tr("Occasion"))
                Chips(listOf("Everyday", "School", "Sport", "Evening", "Formal", "Other"), occasion) {
                    occasion = it
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        val ids = active.mapNotNull { selected(it)?.optString("id") }.filter { it.isNotBlank() }
                        if (ids.isEmpty()) {
                            error = "Select at least one garment."
                        } else {
                            val record = LocalStore.obj(
                                "name" to outfitName.trim().ifBlank { "Mix ${Metrics.day()}" },
                                "date" to Metrics.day(),
                                "items" to JSONArray(ids),
                                "season" to "All",
                                "occasion" to occasion,
                                "favorite" to false,
                                "hidden" to false,
                                "generator" to "mix_match"
                            )
                            store.put("outfit", record)
                            onSaved()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Save this outfit"))
                }
            }
        }
    }
}
