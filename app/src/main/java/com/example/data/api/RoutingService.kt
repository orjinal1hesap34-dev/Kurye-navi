package com.example.data.api

import com.example.data.MapboxConfig
import com.example.data.database.OfflineNavigationRepository
import com.example.data.model.DeliveryPoint
import com.example.data.model.MultiStopRoutePlan
import com.example.data.model.MultiStopRouteResult
import com.example.data.model.NavLocation
import com.example.data.model.RouteModel
import com.example.data.model.RouteStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class RouteResult {
    data class Success(val route: RouteModel) : RouteResult()
    data class Failure(val errorMessage: String) : RouteResult()
}

class RoutingService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val offlineRepository: OfflineNavigationRepository? = null
) {

    private val multiStopService = MultiStopRouteService(client, offlineRepository)

    /**
     * Calculates an optimized route passing through multiple delivery points.
     */
    suspend fun calculateMultiStopRoute(
        origin: NavLocation,
        deliveryPoints: List<DeliveryPoint>,
        optimizeOrder: Boolean = true
    ): MultiStopRouteResult {
        return multiStopService.calculateMultiStopRoute(origin, deliveryPoints, optimizeOrder)
    }

    /**
     * Converts a multi-stop plan into an active route model for immediate turn-by-turn guidance.
     */
    fun createActiveNavigationRoute(
        plan: MultiStopRoutePlan,
        currentLegIndex: Int = 0
    ): RouteModel? {
        return multiStopService.createActiveNavigationRoute(plan, currentLegIndex)
    }

    suspend fun calculateRoute(
        origin: NavLocation,
        destination: NavLocation,
        destinationTitle: String = "Hedef",
        destinationHouseNumber: String? = null
    ): RouteResult = withContext(Dispatchers.IO) {
        // Validate coordinates
        if (origin.latitude == 0.0 && origin.longitude == 0.0) {
            return@withContext RouteResult.Failure("Başlangıç GPS konumu henüz alınamadı.")
        }
        if (destination.latitude == 0.0 && destination.longitude == 0.0) {
            return@withContext RouteResult.Failure("Hedef konumu geçersiz.")
        }

        // Try Mapbox Directions if configured, otherwise OSRM
        if (MapboxConfig.isConfigured) {
            val mapboxUrl = "https://api.mapbox.com/directions/v5/mapbox/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?geometries=geojson&steps=true&overview=full&access_token=${MapboxConfig.accessToken}"
            val mapboxResult = executeRouteRequest(mapboxUrl, origin, destination, destinationTitle, destinationHouseNumber)
            if (mapboxResult is RouteResult.Success) {
                offlineRepository?.saveRoute(origin, destination, mapboxResult.route)
                return@withContext mapboxResult
            }
        }

        // Primary OSRM endpoint
        val osrmUrl = "https://router.project-osrm.org/route/v1/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson&steps=true"
        val osrmResult = executeRouteRequest(osrmUrl, origin, destination, destinationTitle, destinationHouseNumber)
        if (osrmResult is RouteResult.Success) {
            offlineRepository?.saveRoute(origin, destination, osrmResult.route)
            return@withContext osrmResult
        }

        // Backup OSM router endpoint
        val backupUrl = "https://routing.openstreetmap.de/routed-car/route/v1/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson&steps=true"
        val backupResult = executeRouteRequest(backupUrl, origin, destination, destinationTitle, destinationHouseNumber)
        if (backupResult is RouteResult.Success) {
            offlineRepository?.saveRoute(origin, destination, backupResult.route)
            return@withContext backupResult
        }

        // Fallback: check Room database for recently cached route
        val cachedRoute = offlineRepository?.findCachedRoute(origin, destination)
        if (cachedRoute != null) {
            return@withContext RouteResult.Success(cachedRoute)
        }

        return@withContext (osrmResult as? RouteResult.Failure)
            ?: RouteResult.Failure("Rota sunucusuna bağlanılamadı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.")
    }

    private fun executeRouteRequest(
        url: String,
        origin: NavLocation,
        destination: NavLocation,
        destinationTitle: String,
        destinationHouseNumber: String?
    ): RouteResult {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KuryeNavigasyon/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    return when (code) {
                        401, 403 -> RouteResult.Failure("Rota servisi yetkilendirme hatası (Kod: $code).")
                        400 -> RouteResult.Failure("Geçersiz koordinat veya rota noktası.")
                        else -> RouteResult.Failure("Rota servisi yanıt vermedi (Kod: $code).")
                    }
                }

                val body = response.body?.string() ?: return RouteResult.Failure("Boş rota yanıtı alındı.")
                val root = JSONObject(body)

                val code = root.optString("code", "")
                if (code.equals("NoRoute", ignoreCase = true)) {
                    return RouteResult.Failure("Başlangıç ve hedef arasında araçla gidilebilir yol bulunamadı.")
                }

                val routes = root.optJSONArray("routes")
                if (routes == null || routes.length() == 0) {
                    return RouteResult.Failure("Uygun sürüş rotası bulunamadı.")
                }

                val primaryRoute = routes.getJSONObject(0)
                val totalDistance = primaryRoute.optDouble("distance", 0.0)
                val totalDuration = primaryRoute.optDouble("duration", 0.0)

                // Parse Geometry coordinates
                val geometryObj = primaryRoute.optJSONObject("geometry")
                val coordsArray = geometryObj?.optJSONArray("coordinates")
                val coordinates = mutableListOf<NavLocation>()

                if (coordsArray != null) {
                    for (i in 0 until coordsArray.length()) {
                        val pt = coordsArray.getJSONArray(i)
                        coordinates.add(NavLocation(latitude = pt.getDouble(1), longitude = pt.getDouble(0)))
                    }
                }

                if (coordinates.isEmpty()) {
                    return RouteResult.Failure("Rota koordinat çizgisi boş döndü.")
                }

                // Parse Legs and Steps
                val steps = mutableListOf<RouteStep>()
                val legs = primaryRoute.optJSONArray("legs")
                if (legs != null && legs.length() > 0) {
                    val leg = legs.getJSONObject(0)
                    val stepsArray = leg.optJSONArray("steps")
                    if (stepsArray != null) {
                        for (s in 0 until stepsArray.length()) {
                            val stepObj = stepsArray.getJSONObject(s)
                            val stepDistance = stepObj.optDouble("distance", 0.0)
                            val stepDuration = stepObj.optDouble("duration", 0.0)
                            val streetName = stepObj.optString("name").trim()

                            val maneuverObj = stepObj.optJSONObject("maneuver")
                            val maneuverType = maneuverObj?.optString("type") ?: "turn"
                            val modifier = maneuverObj?.optString("modifier")
                            val locArray = maneuverObj?.optJSONArray("location")
                            val stepLocation = if (locArray != null && locArray.length() >= 2) {
                                NavLocation(latitude = locArray.getDouble(1), longitude = locArray.getDouble(0))
                            } else {
                                coordinates.getOrNull(s) ?: origin
                            }

                            val turkishInstruction = formatTurkishInstruction(
                                type = maneuverType,
                                modifier = modifier,
                                streetName = streetName,
                                houseNumber = destinationHouseNumber,
                                isLastStep = s == stepsArray.length() - 1
                            )

                            steps.add(
                                RouteStep(
                                    instruction = turkishInstruction,
                                    maneuverType = maneuverType,
                                    modifier = modifier,
                                    location = stepLocation,
                                    distanceMeters = stepDistance,
                                    durationSeconds = stepDuration,
                                    streetName = if (streetName.isNotBlank()) streetName else "Cadde/Sokak"
                                )
                            )
                        }
                    }
                }

                return RouteResult.Success(
                    RouteModel(
                        coordinates = coordinates,
                        totalDistanceMeters = totalDistance,
                        totalDurationSeconds = totalDuration,
                        steps = steps,
                        destination = destination,
                        destinationTitle = destinationTitle,
                        destinationHouseNumber = destinationHouseNumber
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return RouteResult.Failure("Bağlantı hatası: ${e.localizedMessage ?: "Sunucuya ulaşılamadı"}")
        }
    }

    private fun formatTurkishInstruction(
        type: String,
        modifier: String?,
        streetName: String,
        houseNumber: String?,
        isLastStep: Boolean
    ): String {
        val targetName = if (streetName.isNotBlank()) "$streetName üzerine " else ""
        return when (type) {
            "depart" -> "Rotaya başlayın, $streetName boyunca ilerleyin"
            "arrive" -> {
                if (!houseNumber.isNullOrBlank()) {
                    "Hedefe ulaştınız! Dış kapı no: $houseNumber"
                } else {
                    "Hedefe ulaştınız!"
                }
            }
            "roundabout", "rotary" -> "Dönel kavşağa girin ve uygun çıkıştan çıkın"
            "fork" -> when (modifier) {
                "left" -> "Çatalda sola yönelin"
                "right" -> "Çatalda sağa yönelin"
                else -> "İlerlemeye devam edin"
            }
            "end of road" -> when (modifier) {
                "left" -> "Yolun sonunda sola dönün"
                "right" -> "Yolun sonunda sağa dönün"
                else -> "Yolun sonunda devam edin"
            }
            else -> when (modifier) {
                "left" -> "${targetName}sola dönün"
                "right" -> "${targetName}sağa dönün"
                "slight left" -> "${targetName}hafif sola yönelin"
                "slight right" -> "${targetName}hafif sağa yönelin"
                "sharp left" -> "${targetName}keskin sola dönün"
                "sharp right" -> "${targetName}keskin sağa dönün"
                "straight" -> "Düz devam edin"
                "uturn" -> "U dönüşü yapın"
                else -> if (isLastStep) "Hedefe doğru ilerleyin" else "Düz devam edin"
            }
        }
    }
}
