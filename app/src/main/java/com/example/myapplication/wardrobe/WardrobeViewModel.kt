package com.example.myapplication.wardrobe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.myapplication.data.JsonStorage
import com.example.myapplication.data.model.ClothingItem
import com.example.myapplication.data.model.Outfit
import com.example.myapplication.data.model.SuitcaseList
import com.example.myapplication.data.model.WardrobeData
import com.example.myapplication.data.model.WishlistItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class WardrobeViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _data = MutableStateFlow(loadData())
    val data: StateFlow<WardrobeData> = _data.asStateFlow()

    private fun loadData(): WardrobeData {
        return JsonStorage.load<WardrobeData>(context, "wardrobe_data") ?: WardrobeData(
            clothes = listOf(
                ClothingItem(id = "c1", name = "T-Shirt Bianca", category = "Top", color = "Bianco", seasons = listOf("Primavera", "Estate")),
                ClothingItem(id = "c2", name = "Jeans Slim Blu", category = "Bottom", color = "Blu", seasons = listOf("Primavera", "Autunno", "Inverno")),
                ClothingItem(id = "c3", name = "Sneakers Bianche", category = "Shoes", color = "Bianco", seasons = listOf("Primavera", "Estate", "Autunno")),
                ClothingItem(id = "c4", name = "Felpa con Cappuccio Nera", category = "Top", color = "Nero", seasons = listOf("Autunno", "Inverno")),
                ClothingItem(id = "c5", name = "Giacca a Vento", category = "Outerwear", color = "Verde", seasons = listOf("Autunno", "Primavera"))
            ),
            wishlist = listOf(
                WishlistItem(id = "w1", name = "Giacca di Pelle", description = "Taglia M nera", price = 120f)
            ),
            suitcases = listOf(
                SuitcaseList(
                    id = "s1",
                    name = "Weekend a Milano",
                    destination = "Milano",
                    itemIds = listOf("c1", "c2", "c3"),
                    packed = mapOf("c1" to true, "c2" to false, "c3" to false)
                )
            )
        )
    }

    private fun saveData(newData: WardrobeData) {
        _data.value = newData
        JsonStorage.save(context, "wardrobe_data", newData)
    }

    fun addClothing(name: String, category: String, color: String, seasons: List<String>) {
        val item = ClothingItem(
            id = UUID.randomUUID().toString(),
            name = name,
            category = category,
            color = color,
            seasons = seasons
        )
        saveData(_data.value.copy(clothes = _data.value.clothes + item))
    }

    fun deleteClothing(id: String) {
        saveData(_data.value.copy(clothes = _data.value.clothes.filter { it.id != id }))
    }

    /** Genera combinazione outfit per meteo (Sole/Caldo, Pioggia, Freddo) */
    fun generateWeatherOutfit(weatherCondition: String): List<ClothingItem> {
        val clothes = _data.value.clothes
        val targetSeason = when (weatherCondition) {
            "Caldo / Sole" -> "Estate"
            "Pioggia / Mite" -> "Autunno"
            "Freddo / Inverno" -> "Inverno"
            else -> "Primavera"
        }

        val suitable = clothes.filter { it.seasons.contains(targetSeason) || it.seasons.isEmpty() }
        val top = suitable.find { it.category == "Top" } ?: clothes.find { it.category == "Top" }
        val bottom = suitable.find { it.category == "Bottom" } ?: clothes.find { it.category == "Bottom" }
        val shoes = suitable.find { it.category == "Shoes" } ?: clothes.find { it.category == "Shoes" }
        val outer = if (weatherCondition != "Caldo / Sole") {
            suitable.find { it.category == "Outerwear" } ?: clothes.find { it.category == "Outerwear" }
        } else null

        return listOfNotNull(top, bottom, shoes, outer)
    }

    fun saveOutfit(name: String, items: List<ClothingItem>) {
        val outfit = Outfit(
            id = UUID.randomUUID().toString(),
            name = name,
            itemIds = items.map { it.id },
            saved = true
        )
        saveData(_data.value.copy(outfits = _data.value.outfits + outfit))
    }

    fun addWishlistItem(name: String, description: String, price: Float) {
        val item = WishlistItem(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            price = price
        )
        saveData(_data.value.copy(wishlist = _data.value.wishlist + item))
    }

    fun deleteWishlistItem(id: String) {
        saveData(_data.value.copy(wishlist = _data.value.wishlist.filter { it.id != id }))
    }

    fun addSuitcase(name: String, destination: String, itemIds: List<String>) {
        val suitcase = SuitcaseList(
            id = UUID.randomUUID().toString(),
            name = name,
            destination = destination,
            itemIds = itemIds,
            packed = itemIds.associateWith { false }
        )
        saveData(_data.value.copy(suitcases = _data.value.suitcases + suitcase))
    }

    fun toggleSuitcaseItem(suitcaseId: String, itemId: String) {
        val updated = _data.value.suitcases.map { s ->
            if (s.id == suitcaseId) {
                val current = s.packed[itemId] ?: false
                s.copy(packed = s.packed + (itemId to !current))
            } else s
        }
        saveData(_data.value.copy(suitcases = updated))
    }
}
