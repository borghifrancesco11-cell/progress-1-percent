package com.progressone.nativeapp.weather

import com.progressone.nativeapp.data.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Optional online forecast; ALL user data and images remain on the device. */
object WeatherService {
    data class City(val name: String, val lat: Double, val lon: Double, val country: String)
    data class Weather(val temp: Double, val feels: Double, val rain: Double,
                       val wind: Double, val code: Int, val updated: String)

    private fun get(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode == 200) { "Weather service returned ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }

    suspend fun findCities(query: String): List<City> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val root = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=en&format=json")
        val rows = root.optJSONArray("results") ?: JSONArray()
        (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            City(row.optString("name"), row.optDouble("latitude"), row.optDouble("longitude"),
                row.optString("country"))
        }
    }

    suspend fun forecast(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,precipitation,wind_speed_10m,weather_code" +
            "&timezone=auto"
        val now = get(url).getJSONObject("current")
        Weather(now.optDouble("temperature_2m"), now.optDouble("apparent_temperature"),
            now.optDouble("precipitation"), now.optDouble("wind_speed_10m"),
            now.optInt("weather_code"), now.optString("time"))
    }

    fun saved(store: LocalStore): Weather? = runCatching {
        val raw = store.setting("weatherJson")
        if (raw.isBlank()) return null
        val x = JSONObject(raw)
        Weather(x.getDouble("temp"), x.getDouble("feels"), x.getDouble("rain"),
            x.getDouble("wind"), x.getInt("code"), x.getString("updated"))
    }.getOrNull()

    fun cache(store: LocalStore, info: Weather) {
        store.setSetting("weatherJson", JSONObject().apply {
            put("temp", info.temp); put("feels", info.feels); put("rain", info.rain)
            put("wind", info.wind); put("code", info.code); put("updated", info.updated)
        }.toString())
    }
}
