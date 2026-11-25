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
                       val wind: Double, val code: Int, val updated: String,
                       val latitude: Double, val longitude: Double)

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
            val latitude = row.optDouble("latitude")
            val longitude = row.optDouble("longitude")
            if (!validCoordinates(latitude, longitude)) return@mapNotNull null
            City(row.optString("name"), latitude, longitude, row.optString("country"))
        }
    }

    suspend fun forecast(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        require(validCoordinates(lat, lon)) { "Invalid weather coordinates" }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,precipitation,wind_speed_10m,weather_code" +
            "&timezone=auto"
        val now = get(url).getJSONObject("current")
        Weather(now.optDouble("temperature_2m"), now.optDouble("apparent_temperature"),
            now.optDouble("precipitation"), now.optDouble("wind_speed_10m"),
            now.optInt("weather_code"), now.optString("time"), lat, lon)
    }

    /**
     * Returns a cached forecast only when it belongs to the currently selected
     * city and is still fresh. A cached payload from another location or an
     * expired payload is never presented as current weather.
     */
    fun saved(store: LocalStore): Weather? = runCatching {
        val city = store.setting("city").trim()
        val latitude = store.setting("latitude").trim().toDoubleOrNull() ?: return null
        val longitude = store.setting("longitude").trim().toDoubleOrNull() ?: return null
        if (city.isBlank() || !validCoordinates(latitude, longitude)) return null
        val raw = store.setting("weatherJson")
        if (raw.isBlank()) return null
        val x = JSONObject(raw)
        val cachedLatitude = x.optDouble("latitude", Double.NaN)
        val cachedLongitude = x.optDouble("longitude", Double.NaN)
        if (!cachedLatitude.isFinite() || !cachedLongitude.isFinite() ||
            kotlin.math.abs(cachedLatitude - latitude) > 0.000001 ||
            kotlin.math.abs(cachedLongitude - longitude) > 0.000001) return null
        if (!cacheIsFresh(x.optLong("cachedAt", Long.MIN_VALUE))) return null
        Weather(x.getDouble("temp"), x.getDouble("feels"), x.getDouble("rain"),
            x.getDouble("wind"), x.getInt("code"), x.getString("updated"),
            cachedLatitude, cachedLongitude)
    }.getOrNull()

    fun cache(store: LocalStore, info: Weather) {
        store.setSetting("weatherJson", JSONObject().apply {
            put("temp", info.temp); put("feels", info.feels); put("rain", info.rain)
            put("wind", info.wind); put("code", info.code); put("updated", info.updated)
            put("latitude", info.latitude); put("longitude", info.longitude)
            put("cachedAt", System.currentTimeMillis())
        }.toString())
    }

    internal fun cacheIsFresh(
        cachedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean = cachedAt in (now - CACHE_MAX_AGE_MILLIS)..(now + CLOCK_SKEW_MILLIS)

    private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    internal const val CACHE_MAX_AGE_MILLIS = 60 * 60 * 1000L
    internal const val CLOCK_SKEW_MILLIS = 5 * 60 * 1000L
}
