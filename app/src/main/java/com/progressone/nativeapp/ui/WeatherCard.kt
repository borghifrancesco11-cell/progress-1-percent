package com.progressone.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.progressone.nativeapp.data.LocalStore
import com.progressone.nativeapp.weather.WeatherService
import kotlinx.coroutines.launch

@Composable
fun WeatherCard(store: LocalStore) {
    val scope = rememberCoroutineScope()
    val rev = store.revision
    val city = remember(rev) { store.setting("city").trim() }
    val latitude = remember(rev) { store.setting("latitude").trim().toDoubleOrNull() }
    val longitude = remember(rev) { store.setting("longitude").trim().toDoubleOrNull() }
    val hasLocation = city.isNotBlank() && latitude != null && longitude != null &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
    val weather = remember(rev) { WeatherService.saved(store) }
    var search by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    FrostCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("WEATHER · OPTIONAL ONLINE", Mint)
                Text(city.ifBlank { "No city selected" }, style = MaterialTheme.typography.titleMedium)
                if (weather != null) {
                    Text("${weather.temp.toInt()}°C · feels ${weather.feels.toInt()}°C",
                        style = MaterialTheme.typography.headlineMedium)
                    Text("Rain ${weather.rain} mm · wind ${weather.wind} km/h", color = Mist)
                    Text("Forecast ${weather.updated} · Open-Meteo", color = Mist,
                        style = MaterialTheme.typography.labelSmall)
                } else Text("No forecast downloaded yet", color = Mist)
            }
            Icon(Icons.Rounded.Cloud, null, tint = Ice, modifier = Modifier.size(35.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(onClick = { search = true }, Modifier.weight(1f)) { Text("Set city") }
            Button(onClick = { scope.launch {
                busy = true
                try {
                    val lat = latitude
                    val lon = longitude
                    if (lat == null || lon == null) {
                        throw IllegalStateException("Select a city before refreshing the forecast")
                    }
                    WeatherService.cache(store, WeatherService.forecast(lat, lon))
                    error = ""
                }
                catch (e: Exception) { error = "Offline / weather unavailable: ${e.message}" }
                finally { busy = false }
            } }, enabled = !busy && hasLocation, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp)); Text("Refresh")
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = Mist, style = MaterialTheme.typography.bodySmall)
        Text("No location permission. Weather requests go to Open-Meteo; other data stays local.",
            color = Mist, style = MaterialTheme.typography.labelSmall)
    }
    if (search) {
        var cityQuery by remember { mutableStateOf("") }
        var locations by remember { mutableStateOf<List<WeatherService.City>>(emptyList()) }
        var cityError by remember { mutableStateOf("") }
        BottomSheetFrame("Find a city", onDismiss = { search = false }) {
            FormField("City name", cityQuery, { cityQuery = it })
            Button(onClick = { scope.launch {
                try { locations = WeatherService.findCities(cityQuery); cityError = "" }
                catch (e: Exception) { cityError = e.message ?: "No internet connection" }
            } }, enabled = cityQuery.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Search cities") }
            locations.forEach { result ->
                TextButton(onClick = {
                    store.setSetting("city", "${result.name}, ${result.country}")
                    store.setSetting("latitude", result.lat.toString())
                    store.setSetting("longitude", result.lon.toString())
                    store.setSetting("weatherJson", "")
                    search = false
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("${result.name}, ${result.country}")
                }
            }
            if (cityError.isNotBlank()) Text(cityError, color = Mist)
        }
    }
}
