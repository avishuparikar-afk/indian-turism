package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LiveWeatherData(
    val tempC: Double,
    val condition: String,
    val description: String,
    val humidity: Int,
    val windSpeedKmh: Double,
    val feelsLikeC: Double,
    val iconCode: String,
    val cityName: String,
    val isLive: Boolean = true
)

class OpenWeatherService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeather(lat: Double, lon: Double, apiKey: String): LiveWeatherData? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "MY_OPENWEATHER_API_KEY") {
            return@withContext null
        }

        try {
            val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$cleanKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.w("OpenWeatherService", "Weather API response not successful: ${response.code} - $body")
                return@withContext null
            }

            val json = JSONObject(body)
            val main = json.getJSONObject("main")
            val temp = main.getDouble("temp")
            val feelsLike = main.optDouble("feels_like", temp)
            val humidity = main.optInt("humidity", 50)

            val weatherArray = json.optJSONArray("weather")
            val weatherObj = weatherArray?.optJSONObject(0)
            val condition = weatherObj?.optString("main", "Clear") ?: "Clear"
            val desc = weatherObj?.optString("description", condition)
                ?.split(" ")
                ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                ?: condition
            val icon = weatherObj?.optString("icon", "01d") ?: "01d"

            val wind = json.optJSONObject("wind")
            val windSpeedMps = wind?.optDouble("speed", 2.0) ?: 2.0
            val windKmh = (windSpeedMps * 3.6).let { Math.round(it * 10.0) / 10.0 }
            val cityName = json.optString("name", "")

            LiveWeatherData(
                tempC = Math.round(temp * 10.0) / 10.0,
                condition = condition,
                description = desc,
                humidity = humidity,
                windSpeedKmh = windKmh,
                feelsLikeC = Math.round(feelsLike * 10.0) / 10.0,
                iconCode = icon,
                cityName = cityName,
                isLive = true
            )
        } catch (e: Exception) {
            Log.e("OpenWeatherService", "Failed to query OpenWeatherMap", e)
            null
        }
    }
}
