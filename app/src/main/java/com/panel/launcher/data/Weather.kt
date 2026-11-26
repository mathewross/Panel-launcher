package com.panel.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class Weather(
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val maxC: Double,
    val minC: Double,
    val place: String,
    val fetchedAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put("t", tempC).put("c", code).put("d", isDay).put("hi", maxC).put("lo", minC)
        .put("p", place).put("at", fetchedAt).toString()

    companion object {
        fun fromJson(s: String): Weather? = runCatching {
            val o = JSONObject(s)
            Weather(o.getDouble("t"), o.getInt("c"), o.getBoolean("d"), o.getDouble("hi"),
                o.getDouble("lo"), o.optString("p"), o.getLong("at"))
        }.getOrNull()
    }
}

/** Weather from Open-Meteo (free, no account or API key). */
object WeatherRepository {
    data class Place(val name: String, val lat: Double, val lon: Double)

    fun hasLocationPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun fetch(lat: Double, lon: Double, place: String): Weather = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        val o = JSONObject(get(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        Weather(
            tempC = cur.getDouble("temperature_2m"),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            maxC = daily.getJSONArray("temperature_2m_max").getDouble(0),
            minC = daily.getJSONArray("temperature_2m_min").getDouble(0),
            place = place,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    suspend fun geocode(name: String): Place? = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" +
            URLEncoder.encode(name.trim(), "UTF-8")
        val results = JSONObject(get(url)).optJSONArray("results") ?: return@withContext null
        if (results.length() == 0) return@withContext null
        val r = results.getJSONObject(0)
        Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    @Suppress("DEPRECATION")
    suspend fun placeName(context: Context, loc: Location): String = withContext(Dispatchers.IO) {
        runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: ""
    }

    @SuppressLint("MissingPermission")
    suspend fun deviceLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 60 * 60 * 1000L) return last
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers ->
                    LocationManager.FUSED_PROVIDER
                else -> providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
            }
            if (provider != null) {
                val fresh = withTimeoutOrNull(15_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return last
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun emoji(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1, 2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77, 85, 86 -> "🌨️"
        in 80..82 -> "🌦️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        61, 80 -> "Light rain"
        63, 81 -> "Rain"
        65, 82 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 85 -> "Light snow"
        73, 75, 86 -> "Snow"
        77 -> "Snow grains"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }
}
