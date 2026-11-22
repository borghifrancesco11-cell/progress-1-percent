package com.progressone.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.data.Metrics
import com.progressone.nativeapp.data.moduleEnabled
import com.progressone.nativeapp.media.ImageProcessor
import com.progressone.nativeapp.weather.WeatherService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

private val garmentCategories = listOf("Top", "Bottom", "Outerwear", "Shoes", "Accessories")
private val seasons = listOf("All", "Spring", "Summer", "Autumn", "Winter")

@Composable
fun WardrobeScreen(store: LocalStore, snapshot: AppSnapshot) {
    var section by remember { mutableStateOf("Garments") }
    val revision = store.revision
    val showOutfits = remember(revision) { store.moduleEnabled("outfits") }
    val showMixMatch = remember(revision) { store.moduleEnabled("mixmatch") }
    val showLists = remember(revision) { store.moduleEnabled("lists") }
    val showWeather = remember(revision) { store.moduleEnabled("weather") }
    val sections = listOf("Garments") + (if (showOutfits) listOf("Outfits") else emptyList()) +
        (if (showLists) listOf("Lists") else emptyList())
    LaunchedEffect(sections) { if (section !in sections) section = "Garments" }
    var filter by remember { mutableStateOf("All") }
    var outfitQuery by remember { mutableStateOf("") }
    var outfitFilter by remember { mutableStateOf("All") }
    var outfitSort by remember { mutableStateOf("Newest") }
    var garmentEditor by remember { mutableStateOf<JSONObject?>(null) }
    var createGarment by remember { mutableStateOf(false) }
    var outfitEditor by remember { mutableStateOf(false) }
    var mixing by remember { mutableStateOf(false) }
    var editingOutfit by remember { mutableStateOf<JSONObject?>(null) }
    var selectedOutfit by remember { mutableStateOf<JSONObject?>(null) }
    var selectedGarment by remember { mutableStateOf<JSONObject?>(null) }
    var listEditor by remember { mutableStateOf("") }
    val categories = listOf("All") + garmentCategories
    val wardrobe = snapshot.garments.filter { filter == "All" || it.str("category") == filter }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("YOUR STYLE · YOUR DEVICE", "Wardrobe", "Clothes, outfits and lists. No uploads.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("GARMENTS", snapshot.garments.size.toString(), Modifier.weight(1f))
                if (showOutfits) SmallStat("OUTFITS", snapshot.outfits.size.toString(), Modifier.weight(1f), Mint)
            }
        }
        if (showWeather) item { WeatherCard(store) }
        if (showWeather) item {
            val forecast = WeatherService.saved(store)
            if (forecast != null && snapshot.garments.isNotEmpty()) {
                val suggestedSeason = when {
                    forecast.temp >= 24.0 -> "Summer"
                    forecast.temp <= 8.0 -> "Winter"
                    else -> "Autumn"
                }
                FrostCard(Modifier.fillMaxWidth()) {
                    Eyebrow("OUTFIT METEO", Mint)
                    Text("${forecast.temp.roundToInt()}°C · suggerimento stagione $suggestedSeason",
                        color = Mist, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        val suitable = snapshot.garments.filter {
                            it.str("season", "All").equals("All", true) ||
                                it.str("season", "All").equals(suggestedSeason, true)
                        }
                        fun pick(category: String) = (suitable.firstOrNull { it.str("category") == category }
                            ?: snapshot.garments.firstOrNull { it.str("category") == category })
                        val chosen = listOfNotNull(
                            pick("Top"), pick("Bottom"), pick("Shoes"),
                            if (forecast.temp <= 18.0) pick("Outerwear") else null
                        )
                        if (chosen.isNotEmpty()) {
                            store.put("outfit", LocalStore.obj(
                                "name" to "Outfit meteo ${forecast.temp.roundToInt()}°C",
                                "date" to Metrics.day(),
                                "items" to JSONArray(chosen.map { it.str("id") }),
                                "season" to suggestedSeason,
                                "occasion" to "Weather",
                                "favorite" to false,
                                "hidden" to false,
                                "generator" to "weather"
                            ))
                        }
                    }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Genera outfit per questo meteo")
                    }
                }
            }
        }
        item { Chips(sections, section) { section = it } }
        when (section) {
            "Garments" -> {
                item { SectionHeading("My clothes", "Tap a piece to log a wear") {
                    Button(onClick = { garmentEditor = null; createGarment = true }) {
                        Icon(Icons.Rounded.Add, null); Text(tr("Add"))
                    }
                } }
                item { Chips(categories, filter) { filter = it } }
                if (wardrobe.isEmpty()) item {
                    EmptyPanel("Your wardrobe is empty", "Add a garment and create a transparent PNG cutout.") {
                        createGarment = true
                    }
                }
                // Fixed grid cell rendering; no HTML sticky headers or browser image loading.
                items(wardrobe.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { garment ->
                            GarmentTile(store, garment, snapshot.wears.count { it.str("item") == garment.str("id") },
                                Modifier.weight(1f)) { selectedGarment = garment }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            "Outfits" -> if (showOutfits) {
                item {
                    SectionHeading("Outfits", "Your saved combinations · ${snapshot.outfits.size} total") {
                        Button(onClick = { editingOutfit = null; outfitEditor = true }) {
                            Icon(Icons.Rounded.Add, null); Text(tr("Create"))
                        }
                    }
                }
                if (showMixMatch) item {
                    FilledTonalButton(
                        onClick = { mixing = true },
                        enabled = snapshot.garments.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Mix & Match"))
                    }
                }
                item {
                    OutlinedTextField(
                        value = outfitQuery,
                        onValueChange = { outfitQuery = it.take(80) },
                        label = { Text(tr("Search outfits")) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (outfitQuery.isNotBlank()) IconButton(onClick = { outfitQuery = "" }) {
                                Icon(Icons.Rounded.Close, "Clear search")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                item { Chips(listOf("All", "Favorites", "Hidden", "Spring", "Summer", "Autumn", "Winter"), outfitFilter) { outfitFilter = it } }
                item { Chips(listOf("Newest", "Recently worn", "Most worn", "Name"), outfitSort) { outfitSort = it } }
                val looks = snapshot.outfits.filter { look ->
                    val matchesFilter = when (outfitFilter) {
                        "Favorites" -> look.optBoolean("favorite") && !look.optBoolean("hidden")
                        "Hidden" -> look.optBoolean("hidden")
                        "All" -> !look.optBoolean("hidden")
                        else -> !look.optBoolean("hidden") &&
                            look.str("season", "All").equals(outfitFilter, ignoreCase = true)
                    }
                    val q = outfitQuery.trim()
                    val tags = look.optJSONArray("tags")
                    val tagText = if (tags == null) "" else (0 until tags.length()).joinToString(" ") { tags.optString(it) }
                    matchesFilter && (q.isEmpty() || listOf(
                        look.str("name"), look.str("season"), look.str("occasion"), look.str("note"), tagText
                    ).any { it.contains(q, ignoreCase = true) })
                }.let { filtered ->
                    when (outfitSort) {
                        "Name" -> filtered.sortedBy { it.str("name").lowercase() }
                        "Recently worn" -> filtered.sortedWith(compareByDescending<JSONObject> {
                            outfitLastWorn(it)
                        }.thenBy { it.str("name").lowercase() })
                        "Most worn" -> filtered.sortedWith(compareByDescending<JSONObject> {
                            outfitWearCount(it)
                        }.thenBy { it.str("name").lowercase() })
                        else -> filtered.sortedByDescending { it.str("date") }
                    }
                }
                if (looks.isEmpty()) item {
                    EmptyPanel(
                        if (snapshot.outfits.isEmpty()) "No outfits yet" else "No matching outfits",
                        if (snapshot.outfits.isEmpty()) "Create a look with the garments you have saved."
                        else "Try a different filter or search."
                    ) { if (snapshot.outfits.isEmpty()) { editingOutfit = null; outfitEditor = true }
                        else { outfitQuery = ""; outfitFilter = "All" } }
                }
                items(looks.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { look ->
                            OutfitGridTile(
                                store = store,
                                look = look,
                                garments = snapshot.garments,
                                modifier = Modifier.weight(1f),
                                onOpen = { selectedOutfit = look },
                                onToggleFavorite = {
                                    val updated = JSONObject(look.toString())
                                    updated.put("favorite", !look.optBoolean("favorite"))
                                    store.put("outfit", updated)
                                    if (selectedOutfit?.str("id") == updated.str("id")) selectedOutfit = updated
                                }
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Text("Your outfit photos, favorites and plans stay on this device.",
                        color = Mist, style = MaterialTheme.typography.labelSmall)
                }
            }
            "Lists" -> if (showLists) {
                item { SectionHeading("Wish list", "Items you may want to buy") {
                    TextButton(onClick = { listEditor = "wishlist" }) { Text(tr("Add")) }
                } }
                if (snapshot.wishlist.isEmpty()) item { EmptyPanel("No wishlist items", "Keep links or notes privately.") }
                items(snapshot.wishlist) { entry -> ListEntry(store, "wishlist", entry) }
                item { SectionHeading("Packing list", "Remember what to bring") {
                    TextButton(onClick = { listEditor = "packing" }) { Text(tr("Add")) }
                } }
                if (snapshot.packing.isEmpty()) item { EmptyPanel("No packing items", "Build a travel checklist.") }
                items(snapshot.packing) { entry -> ListEntry(store, "packing", entry) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    if (createGarment) GarmentEditor(store, garmentEditor, onDismiss = { createGarment = false })
    if (selectedGarment != null) {
        val g = selectedGarment!!
        BottomSheetFrame(g.str("name"), { selectedGarment = null }) {
            PhotoDisplay(store, g.str("photo"), modifier = Modifier.fillMaxWidth().height(235.dp))
            Text("${g.str("category")} · ${g.str("season", "All")}", color = Mist)
            Text("${g.str("brand")} · ${g.str("color")}", color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    store.put("wear", LocalStore.obj("item" to g.str("id"), "date" to com.progressone.nativeapp.data.Metrics.day()))
                    selectedGarment = null
                }, Modifier.weight(1f)) { Text(tr("Log wear")) }
                OutlinedButton(onClick = {
                    garmentEditor = g; selectedGarment = null; createGarment = true
                }, Modifier.weight(1f)) { Text(tr("Edit")) }
            }
            var confirm by remember { mutableStateOf(false) }
            if (confirm) TextButton(onClick = { store.remove("garment", g.str("id")); selectedGarment = null }) {
                Text(tr("Confirm deletion"), color = MaterialTheme.colorScheme.error)
            } else TextButton(onClick = { confirm = true }) { Text(tr("Delete garment")) }
        }
    }
    if (mixing && showMixMatch) MixMatchScreen(store, snapshot.garments,
        onDismiss = { mixing = false },
        onSaved = { mixing = false; section = "Outfits"; outfitFilter = "All" })
    if (outfitEditor) OutfitEditor(store, snapshot.garments, editingOutfit,
        onDismiss = { outfitEditor = false; editingOutfit = null })
    if (selectedOutfit != null) {
        OutfitDetailsSheet(
            store = store,
            look = selectedOutfit!!,
            garments = snapshot.garments,
            onEdit = { look ->
                editingOutfit = look
                selectedOutfit = null
                outfitEditor = true
            },
            onChanged = { changed -> selectedOutfit = changed },
            onDismiss = { selectedOutfit = null }
        )
    }
    if (listEditor.isNotBlank()) ListEditor(store, listEditor, onDismiss = { listEditor = "" })
}

@Composable
private fun PhotoDisplay(store: LocalStore, name: String, modifier: Modifier = Modifier) {
    val file = store.photoFile(name)
    val image by produceState<Bitmap?>(initialValue = null, name, file?.lastModified()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (file?.isFile == true) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                        val factor = max(1, max(bounds.outWidth, bounds.outHeight) / 530)
                        BitmapFactory.decodeFile(file.absolutePath,
                            BitmapFactory.Options().apply { inSampleSize = factor })
                    }
                } else null
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        if (image == null) Icon(Icons.Rounded.Checkroom, "No photo", tint = Mist, modifier = Modifier.size(45.dp))
        else Image(bitmap = image!!.asImageBitmap(), contentDescription = "Garment photo",
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun GarmentTile(store: LocalStore, item: JSONObject, wears: Int, modifier: Modifier, onClick: () -> Unit) {
    FrostCard(modifier, onClick = onClick) {
        PhotoDisplay(store, item.str("photo"), Modifier.fillMaxWidth().aspectRatio(1.0f))
        Text(item.str("name"), fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text("${item.str("category")} · ${item.str("season", "All")}",
            style = MaterialTheme.typography.labelSmall, color = Mist)
        Text("$wears wears", color = Ice, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun GarmentEditor(store: LocalStore, previous: JSONObject?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(previous?.str("name") ?: "") }
    var brand by remember { mutableStateOf(previous?.str("brand") ?: "") }
    var color by remember { mutableStateOf(previous?.str("color") ?: "") }
    var price by remember { mutableStateOf(previous?.num("price")?.toString() ?: "") }
    var category by remember { mutableStateOf(previous?.str("category", "Top") ?: "Top") }
    var season by remember { mutableStateOf(previous?.str("season", "All") ?: "All") }
    var finalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var finalSource by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                error = ""
                // Segmentation starts automatically after photo selection, never presents manual tools.
                // The first ML Kit run may download the model from Google Play services.
                val source = ImageProcessor.decode(ctx, uri)
                val cut = ImageProcessor.aiCutout(source)
                val (original, foreground) = ImageProcessor.trimTransparentPair(source, cut)
                finalSource = original
                finalBitmap = foreground
            }
            catch (e: Exception) {
                finalBitmap = null; finalSource = null
                error = "Automatic cutout unavailable: ${e.message ?: "try again when the ML Kit model is ready"}"
            }
            catch (_: OutOfMemoryError) { error = "Image is too large" }
            finally { busy = false }
        }
    }
    BottomSheetFrame(if (previous == null) "Add garment" else "Edit garment", onDismiss) {
        Column(Modifier.heightIn(max = 575.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            FormField("Garment name", name, { name = it.take(80) })
            Eyebrow("CATEGORY")
            Chips(garmentCategories, category) { category = it }
            Eyebrow("SEASON")
            Chips(seasons, season) { season = it }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FormField("Brand", brand, { brand = it }, Modifier.weight(1f))
                FormField("Color", color, { color = it }, Modifier.weight(1f))
            }
            FormField("Price (€ · optional)", price, { price = it })
            if (finalBitmap != null) {
                Image(bitmap = finalBitmap!!.asImageBitmap(), contentDescription = "Cutout preview",
                    modifier = Modifier.fillMaxWidth().height(178.dp), contentScale = ContentScale.Fit)
            } else if (previous != null && previous.str("photo").isNotBlank()) {
                PhotoDisplay(store, previous.str("photo"), Modifier.fillMaxWidth().height(178.dp))
            }
            OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth(), enabled = !busy) {
                Icon(Icons.Rounded.AddPhotoAlternate, null)
                Spacer(Modifier.width(8.dp)); Text(if (busy) "Removing background…" else "Choose photo")
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text(if (busy) "Processing cutout on-device…" else
                "Photo backgrounds are removed automatically using ML Kit. First use may need a model download.",
                color = Mist, style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                scope.launch {
                    busy = true
                    try {
                        // Save the processed image and its aligned restoration source independently.
                        // The source lets Erase/Restore recover removed pixels when re-editing later.
                        val oldPhoto = previous?.str("photo")
                        val oldSource = previous?.str("sourcePhoto")
                        var imageName: String? = null
                        var sourceName: String? = null
                        try {
                            imageName = if (finalBitmap != null) ImageProcessor.savePng(store, finalBitmap!!)
                                else oldPhoto ?: ""
                            sourceName = if (finalSource != null) ImageProcessor.saveRestorationSource(store, finalSource!!)
                                else oldSource ?: ""
                            val item = previous ?: LocalStore.obj("added" to com.progressone.nativeapp.data.Metrics.day())
                            item.put("name", name.trim())
                            item.put("category", category); item.put("season", season)
                            item.put("brand", brand.trim()); item.put("color", color.trim())
                            item.put("price", price.cleanNumber())
                            item.put("photo", imageName)
                            if (!sourceName.isNullOrBlank()) item.put("sourcePhoto", sourceName)
                            store.put("garment", item)
                            store.cleanupUnreferencedPhoto(oldPhoto)
                            store.cleanupUnreferencedPhoto(oldSource)
                            onDismiss()
                        } catch (e: Exception) {
                            // Failed writes must not leak unreferenced partially saved images.
                            if (finalBitmap != null) store.cleanupUnreferencedPhoto(imageName)
                            if (finalSource != null) store.cleanupUnreferencedPhoto(sourceName)
                            throw e
                        }
                    } catch (e: Exception) { error = e.message ?: "Photo could not be saved" }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank() && (previous != null || finalBitmap != null)) {
                Text(if (busy) "Saving…" else "Save garment")
            }
        }
    }

}

@Composable
private fun OutfitEditor(store: LocalStore, clothes: List<JSONObject>, previous: JSONObject?, onDismiss: () -> Unit) {
    var name by remember(previous) { mutableStateOf(previous?.str("name") ?: "") }
    var season by remember(previous) { mutableStateOf(previous?.str("season", "All") ?: "All") }
    var occasion by remember(previous) { mutableStateOf(previous?.str("occasion", "Everyday") ?: "Everyday") }
    var note by remember(previous) { mutableStateOf(previous?.str("note") ?: "") }
    var tags by remember(previous) {
        val tagList = previous?.optJSONArray("tags")
        mutableStateOf(if (tagList == null) "" else
            (0 until tagList.length()).joinToString(", ") { tagList.optString(it) })
    }
    var category by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    val selected = remember(previous) {
        mutableStateListOf<String>().apply {
            val arr = previous?.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) add(arr.optString(i))
        }
    }
    BottomSheetFrame(if (previous == null) "Create outfit" else "Edit outfit", onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 605.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            FormField("Outfit name", name, { name = it.take(80) })
            Eyebrow("SEASON")
            Chips(seasons, season) { season = it }
            Eyebrow("OCCASION")
            Chips(listOf("Everyday", "School", "Sport", "Evening", "Formal", "Other"), occasion) { occasion = it }
            FormField("Style tags (comma separated)", tags, { tags = it.take(120) })
            FormField("Notes (optional)", note, { note = it.take(350) }, singleLine = false)
            SectionHeading("Choose garments", "${selected.size} selected")
            FormField("Search clothes", search, { search = it })
            Chips(listOf("All") + garmentCategories, category) { category = it }
            val matches = clothes.filter {
                (category == "All" || it.str("category") == category) &&
                    (search.isBlank() || it.str("name").contains(search, true) || it.str("brand").contains(search, true))
            }
            if (matches.isEmpty()) Text(tr("No matching garments"), color = Mist)
            matches.forEach { garment ->
                val id = garment.str("id")
                Row(Modifier.fillMaxWidth().clickable {
                    if (id in selected) selected.remove(id) else selected.add(id)
                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhotoDisplay(store, garment.str("photo"), Modifier.size(54.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(garment.str("name"), style = MaterialTheme.typography.bodyMedium)
                        Text(garment.str("category"), color = Mist, style = MaterialTheme.typography.labelSmall)
                    }
                    Checkbox(checked = id in selected, onCheckedChange = { checked ->
                        if (checked && id !in selected) selected.add(id)
                        else if (!checked) selected.remove(id)
                    })
                }
                SectionDivider()
            }
            Button(onClick = {
                val record = previous ?: LocalStore.obj("date" to com.progressone.nativeapp.data.Metrics.day())
                record.put("name", name.trim())
                record.put("items", JSONArray(selected))
                record.put("season", season)
                record.put("occasion", occasion)
                record.put("note", note.trim())
                record.put("tags", JSONArray(tags.split(",").map { it.trim() }
                    .filter { it.isNotBlank() }.distinct()))
                store.put("outfit", record)
                onDismiss()
            }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank() && selected.isNotEmpty()) {
                Text(tr("Save outfit"))
            }
        }
    }
}

@Composable
private fun ListEditor(store: LocalStore, kind: String, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    BottomSheetFrame(if (kind == "packing") "Packing item" else "Wish list item", onDismiss) {
        FormField("Name", name, { name = it })
        FormField("Notes / URL", note, { note = it }, singleLine = false)
        if (kind == "wishlist") FormField("Price (€)", price, { price = it })
        Button(onClick = {
            store.put(kind, LocalStore.obj("name" to name.trim(), "note" to note,
                "price" to price.cleanNumber())); onDismiss()
        }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(tr("Save")) }
    }
}

@Composable
private fun ListEntry(store: LocalStore, type: String, entry: JSONObject) {
    var confirm by remember { mutableStateOf(false) }
    FrostCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.str("name"), fontWeight = FontWeight.SemiBold)
                if (entry.str("note").isNotBlank()) Text(entry.str("note"), color = Mist)
            }
            IconButton(onClick = { if (confirm) store.remove(type, entry.str("id")) else confirm = true }) {
                Icon(if (confirm) Icons.Rounded.DeleteForever else Icons.Rounded.Delete, "Delete")
            }
        }
    }
}
