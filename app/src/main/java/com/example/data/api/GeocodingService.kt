package com.example.data.api

import com.example.data.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class GeocodingService(private val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient()) {

    suspend fun searchAddress(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&countrycodes=tr&addressdetails=1&limit=8"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KuryeNavigasyon/1.0 (Android Courier App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                val results = mutableListOf<SearchResult>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val addressObj = obj.optJSONObject("address")
                    val houseNumber = addressObj?.optString("house_number")?.takeIf { it.isNotBlank() }
                    val road = addressObj?.optString("road") ?: addressObj?.optString("pedestrian")
                    val suburb = addressObj?.optString("suburb") ?: addressObj?.optString("neighbourhood")
                    val city = addressObj?.optString("city") ?: addressObj?.optString("town") ?: addressObj?.optString("province")

                    val title = when {
                        !road.isNullOrBlank() -> road
                        else -> obj.optString("display_name").split(",").firstOrNull() ?: "Konum"
                    }

                    val subtitle = listOfNotNull(suburb, city).filter { it.isNotBlank() }.joinToString(", ")

                    results.add(
                        SearchResult(
                            placeId = obj.optLong("place_id", i.toLong()),
                            displayName = obj.optString("display_name"),
                            title = title,
                            subtitle = if (subtitle.isNotBlank()) subtitle else "Türkiye",
                            latitude = obj.getDouble("lat"),
                            longitude = obj.getDouble("lon"),
                            houseNumber = houseNumber,
                            road = road,
                            suburb = suburb,
                            city = city
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): SearchResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KuryeNavigasyon/1.0 (Android Courier App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val obj = JSONObject(body)
                val addressObj = obj.optJSONObject("address")

                val houseNumber = addressObj?.optString("house_number")?.takeIf { it.isNotBlank() }
                val road = addressObj?.optString("road") ?: addressObj?.optString("pedestrian")
                val suburb = addressObj?.optString("suburb") ?: addressObj?.optString("neighbourhood")
                val city = addressObj?.optString("city") ?: addressObj?.optString("town") ?: addressObj?.optString("province")

                val title = when {
                    !road.isNullOrBlank() && !houseNumber.isNullOrBlank() -> "$road No: $houseNumber"
                    !road.isNullOrBlank() -> road
                    else -> obj.optString("display_name").split(",").firstOrNull() ?: "Seçilen Konum"
                }

                val subtitle = listOfNotNull(suburb, city).filter { it.isNotBlank() }.joinToString(", ")

                SearchResult(
                    placeId = obj.optLong("place_id", 0L),
                    displayName = obj.optString("display_name"),
                    title = title,
                    subtitle = if (subtitle.isNotBlank()) subtitle else "Nokta Konumu",
                    latitude = lat,
                    longitude = lon,
                    houseNumber = houseNumber,
                    road = road,
                    suburb = suburb,
                    city = city
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
