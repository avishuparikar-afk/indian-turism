package com.example.data.api

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GeoapifyAddressResult(
    val name: String,
    val formattedAddress: String,
    val latLng: LatLng,
    val city: String = "",
    val state: String = ""
)

class GeoapifyService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchAddress(query: String, apiKey: String): List<GeoapifyAddressResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "MY_GEOAPIFY_API_KEY") {
            return@withContext emptyList()
        }
        if (query.trim().length < 2) {
            return@withContext emptyList()
        }

        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api.geoapify.com/v1/geocode/autocomplete?text=$encoded&filter=countrycode:in&limit=5&apiKey=$cleanKey"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            if (!response.isSuccessful) {
                Log.w("GeoapifyService", "Geoapify autocomplete response not successful: ${response.code} - $body")
                return@withContext emptyList()
            }

            val json = JSONObject(body)
            val features = json.optJSONArray("features") ?: return@withContext emptyList()
            val list = mutableListOf<GeoapifyAddressResult>()

            for (i in 0 until features.length()) {
                val item = features.optJSONObject(i) ?: continue
                val properties = item.optJSONObject("properties") ?: continue
                val geometry = item.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")

                val lon = coordinates?.optDouble(0) ?: properties.optDouble("lon")
                val lat = coordinates?.optDouble(1) ?: properties.optDouble("lat")

                val name = properties.optString("name").ifBlank {
                    properties.optString("address_line1").ifBlank { query }
                }
                val formatted = properties.optString("formatted").ifBlank {
                    val l1 = properties.optString("address_line1")
                    val l2 = properties.optString("address_line2")
                    if (l1.isNotBlank() && l2.isNotBlank()) "$l1, $l2" else l1.ifBlank { name }
                }
                val city = properties.optString("city", "")
                val state = properties.optString("state", "")

                if (lat != 0.0 && lon != 0.0) {
                    list.add(
                        GeoapifyAddressResult(
                            name = name,
                            formattedAddress = formatted,
                            latLng = LatLng(lat, lon),
                            city = city,
                            state = state
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e("GeoapifyService", "Geoapify autocomplete error", e)
            emptyList()
        }
    }
}
