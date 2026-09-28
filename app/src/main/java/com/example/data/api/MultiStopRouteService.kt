package com.example.data.api

import com.example.data.MapboxConfig
import com.example.data.database.OfflineNavigationRepository
import com.example.data.model.DeliveryPoint
import com.example.data.model.DeliveryRouteLeg
import com.example.data.model.MultiStopRoutePlan
import com.example.data.model.MultiStopRouteResult
import com.example.data.model.NavLocation
import com.example.data.model.RouteModel
import com.example.data.model.RouteStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Route calculation service utilizing MapLibre and Mapbox Directions & Optimization APIs
 * to plan efficient paths between multiple delivery points for a courier.
 */
class MultiStopRouteService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build(),
    private val offlineRepository: OfflineNavigationRepository? = null
) {

    /**
     * Calculates an efficient route visiting multiple delivery stops starting from courier origin.
     *
     * @param origin Current courier GPS location
     * @param deliveryPoints List of delivery points to visit
     * @param optimizeOrder If true, reorders delivery points to minimize total travel time and distance
     */
    suspend fun calculateMultiStopRoute(
        origin: NavLocation,
        deliveryPoints: List<DeliveryPoint>,
        optimizeOrder: Boolean = true
    ): MultiStopRouteResult = withContext(Dispatchers.IO) {
        if (origin.latitude == 0.0 && origin.longitude == 0.0) {
            return@withContext MultiStopRouteResult.Failure("Kurye başlangıç konumu henüz alınamadı.")
        }

        val activePoints = deliveryPoints.filter { !it.isCompleted }
        if (activePoints.isEmpty()) {
            return@withContext MultiStopRouteResult.Failure("Teslimat listesinde aktif teslimat noktası bulunmuyor.")
        }

        // If only 1 delivery point, order is trivial
        val orderedPoints = if (optimizeOrder && activePoints.size > 1) {
            optimizeDeliveryOrder(origin, activePoints)
        } else {
            activePoints.mapIndexed { idx, p -> p.copy(stopOrder = idx + 1) }
        }

        val successfulResult = when {
            optimizeOrder && MapboxConfig.isConfigured && activePoints.size in 2..12 -> {
                val optimized = tryMapboxOptimizedTrips(origin, activePoints)
                if (optimized is MultiStopRouteResult.Success) optimized else null
            }
            else -> null
        } ?: if (MapboxConfig.isConfigured) {
            val res = tryMapboxDirections(origin, orderedPoints)
            if (res is MultiStopRouteResult.Success) res else null
        } else null ?: run {
            val osrm = tryOsrmDirections(origin, orderedPoints)
            if (osrm is MultiStopRouteResult.Success) osrm else null
        } ?: run {
            val backup = tryBackupOsmDirections(origin, orderedPoints)
            if (backup is MultiStopRouteResult.Success) backup else null
        }

        if (successfulResult is MultiStopRouteResult.Success) {
            val plan = successfulResult.plan
            val firstLegRoute = createActiveNavigationRoute(plan, 0)
            if (firstLegRoute != null) {
                offlineRepository?.saveRoute(
                    origin = origin,
                    destination = plan.deliveryPoints.first().location,
                    route = firstLegRoute,
                    deliveryPoints = plan.deliveryPoints
                )
            }
            return@withContext successfulResult
        }

        // Offline Fallback: check if Room has cached route for the first delivery point
        val firstStop = orderedPoints.firstOrNull()
        if (firstStop != null) {
            val cachedRoute = offlineRepository?.findCachedRoute(origin, firstStop.location)
            if (cachedRoute != null) {
                val fallbackLeg = DeliveryRouteLeg(
                    legIndex = 0,
                    fromLocation = origin,
                    targetPoint = firstStop,
                    distanceMeters = cachedRoute.totalDistanceMeters,
                    durationSeconds = cachedRoute.totalDurationSeconds,
                    coordinates = cachedRoute.coordinates,
                    steps = cachedRoute.steps
                )
                val fallbackPlan = MultiStopRoutePlan(
                    origin = origin,
                    deliveryPoints = orderedPoints,
                    legs = listOf(fallbackLeg),
                    totalDistanceMeters = cachedRoute.totalDistanceMeters,
                    totalDurationSeconds = cachedRoute.totalDurationSeconds,
                    allCoordinates = cachedRoute.coordinates,
                    allSteps = cachedRoute.steps
                )
                return@withContext MultiStopRouteResult.Success(fallbackPlan)
            }
        }

        MultiStopRouteResult.Failure(
            "Çoklu teslimat rotası hesaplanamadı ve çevrimdışı önbellekte kayıtlı rota bulunamadı."
        )
    }

    /**
     * Converts a MultiStopRoutePlan into a single active RouteModel targeted at the next delivery stop,
     * while retaining the full route geometry for the courier map overview.
     */
    fun createActiveNavigationRoute(
        plan: MultiStopRoutePlan,
        currentLegIndex: Int = 0
    ): RouteModel? {
        if (plan.legs.isEmpty()) return null
        val activeLeg = plan.legs.getOrNull(currentLegIndex) ?: plan.legs.first()
        val targetPoint = activeLeg.targetPoint

        return RouteModel(
            coordinates = if (activeLeg.coordinates.isNotEmpty()) activeLeg.coordinates else plan.allCoordinates,
            totalDistanceMeters = activeLeg.distanceMeters,
            totalDurationSeconds = activeLeg.durationSeconds,
            steps = activeLeg.steps,
            destination = targetPoint.location,
            destinationTitle = "${targetPoint.stopOrder}. ${targetPoint.title}",
            destinationHouseNumber = targetPoint.houseNumber
        )
    }

    /**
     * Mapbox Optimized Trips API for multi-point Traveling Salesperson Problem (TSP) optimization.
     */
    private fun tryMapboxOptimizedTrips(
        origin: NavLocation,
        points: List<DeliveryPoint>
    ): MultiStopRouteResult {
        try {
            val coordsString = buildString {
                append("${origin.longitude},${origin.latitude}")
                points.forEach { pt ->
                    append(";${pt.location.longitude},${pt.location.latitude}")
                }
            }

            val url = "https://api.mapbox.com/optimized-trips/v1/mapbox/driving/$coordsString" +
                    "?geometries=geojson&steps=true&overview=full&roundtrip=false&source=first" +
                    "&access_token=${MapboxConfig.accessToken}"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KuryeNavigasyon/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return MultiStopRouteResult.Failure("Mapbox Trips HTTP ${response.code}")
                val body = response.body?.string() ?: return MultiStopRouteResult.Failure("Boş yanıt")
                val root = JSONObject(body)

                if (!root.optString("code", "").equals("Ok", ignoreCase = true)) {
                    return MultiStopRouteResult.Failure("Mapbox optimizasyon servisi rota üretemedi.")
                }

                val trips = root.optJSONArray("trips") ?: return MultiStopRouteResult.Failure("Trip bulunamadı")
                if (trips.length() == 0) return MultiStopRouteResult.Failure("Trip listesi boş")
                val primaryTrip = trips.getJSONObject(0)

                // Map waypoints to determine optimized stop sequence
                val waypointsArray = root.optJSONArray("waypoints")
                val orderedPoints = mutableListOf<DeliveryPoint>()
                if (waypointsArray != null) {
                    val waypointMap = mutableListOf<Pair<Int, Int>>() // trips_index to original waypoint_index
                    for (w in 0 until waypointsArray.length()) {
                        val wpObj = waypointsArray.getJSONObject(w)
                        val wpIdx = wpObj.getInt("waypoint_index")
                        val tripIdx = wpObj.getInt("trips_index")
                        if (wpIdx > 0) { // Skip origin (index 0)
                            waypointMap.add(tripIdx to (wpIdx - 1))
                        }
                    }
                    waypointMap.sortBy { it.first }
                    waypointMap.forEachIndexed { orderIdx, pair ->
                        val origPt = points.getOrNull(pair.second)
                        if (origPt != null) {
                            orderedPoints.add(origPt.copy(stopOrder = orderIdx + 1))
                        }
                    }
                }

                val finalPoints = if (orderedPoints.size == points.size) orderedPoints else points

                return parseMultiStopResponse(
                    routeJson = primaryTrip,
                    origin = origin,
                    orderedPoints = finalPoints
                )
            }
        } catch (e: Exception) {
            return MultiStopRouteResult.Failure(e.localizedMessage ?: "Optimizasyon hatası")
        }
    }

    /**
     * Mapbox Directions API for multi-stop routes.
     */
    private fun tryMapboxDirections(
        origin: NavLocation,
        orderedPoints: List<DeliveryPoint>
    ): MultiStopRouteResult {
        val coordsString = buildString {
            append("${origin.longitude},${origin.latitude}")
            orderedPoints.forEach { pt ->
                append(";${pt.location.longitude},${pt.location.latitude}")
            }
        }
        val url = "https://api.mapbox.com/directions/v5/mapbox/driving/$coordsString" +
                "?geometries=geojson&steps=true&overview=full&access_token=${MapboxConfig.accessToken}"

        return executeDirectionsRequest(url, origin, orderedPoints)
    }

    /**
     * OSRM Multi-coordinate Directions API.
     */
    private fun tryOsrmDirections(
        origin: NavLocation,
        orderedPoints: List<DeliveryPoint>
    ): MultiStopRouteResult {
        val coordsString = buildString {
            append("${origin.longitude},${origin.latitude}")
            orderedPoints.forEach { pt ->
                append(";${pt.location.longitude},${pt.location.latitude}")
            }
        }
        val url = "https://router.project-osrm.org/route/v1/driving/$coordsString?overview=full&geometries=geojson&steps=true"
        return executeDirectionsRequest(url, origin, orderedPoints)
    }

    /**
     * Backup OpenStreetMap Router.
     */
    private fun tryBackupOsmDirections(
        origin: NavLocation,
        orderedPoints: List<DeliveryPoint>
    ): MultiStopRouteResult {
        val coordsString = buildString {
            append("${origin.longitude},${origin.latitude}")
            orderedPoints.forEach { pt ->
                append(";${pt.location.longitude},${pt.location.latitude}")
            }
        }
        val url = "https://routing.openstreetmap.de/routed-car/route/v1/driving/$coordsString?overview=full&geometries=geojson&steps=true"
        return executeDirectionsRequest(url, origin, orderedPoints)
    }

    private fun executeDirectionsRequest(
        url: String,
        origin: NavLocation,
        orderedPoints: List<DeliveryPoint>
    ): MultiStopRouteResult {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KuryeNavigasyon/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return MultiStopRouteResult.Failure("Rota servisi HTTP ${response.code} hatası verdi.")
                }
                val body = response.body?.string() ?: return MultiStopRouteResult.Failure("Boş rota cevabı")
                val root = JSONObject(body)

                val routes = root.optJSONArray("routes")
                if (routes == null || routes.length() == 0) {
                    return MultiStopRouteResult.Failure("Teslimat noktaları arasında rota bulunamadı.")
                }

                return parseMultiStopResponse(routes.getJSONObject(0), origin, orderedPoints)
            }
        } catch (e: Exception) {
            return MultiStopRouteResult.Failure("Bağlantı hatası: ${e.localizedMessage ?: "Sunucuya ulaşılamadı"}")
        }
    }

    private fun parseMultiStopResponse(
        routeJson: JSONObject,
        origin: NavLocation,
        orderedPoints: List<DeliveryPoint>
    ): MultiStopRouteResult {
        val totalDistance = routeJson.optDouble("distance", 0.0)
        val totalDuration = routeJson.optDouble("duration", 0.0)

        // Parse overall GeoJSON geometry coordinates
        val allCoordinates = mutableListOf<NavLocation>()
        val geometryObj = routeJson.optJSONObject("geometry")
        val coordsArray = geometryObj?.optJSONArray("coordinates")
        if (coordsArray != null) {
            for (i in 0 until coordsArray.length()) {
                val pt = coordsArray.getJSONArray(i)
                allCoordinates.add(NavLocation(latitude = pt.getDouble(1), longitude = pt.getDouble(0)))
            }
        }

        // Parse individual legs between each stop
        val legsArray = routeJson.optJSONArray("legs")
        val legs = mutableListOf<DeliveryRouteLeg>()
        val allSteps = mutableListOf<RouteStep>()

        if (legsArray != null) {
            var currentFrom = origin

            for (l in 0 until legsArray.length()) {
                val legObj = legsArray.getJSONObject(l)
                val targetPoint = orderedPoints.getOrNull(l)
                    ?: DeliveryPoint(title = "Nokta ${l + 1}", location = currentFrom)

                val legDistance = legObj.optDouble("distance", 0.0)
                val legDuration = legObj.optDouble("duration", 0.0)

                val legSteps = mutableListOf<RouteStep>()
                val stepsArray = legObj.optJSONArray("steps")
                if (stepsArray != null) {
                    for (s in 0 until stepsArray.length()) {
                        val stepObj = stepsArray.getJSONObject(s)
                        val stepDist = stepObj.optDouble("distance", 0.0)
                        val stepDur = stepObj.optDouble("duration", 0.0)
                        val streetName = stepObj.optString("name").trim()

                        val maneuverObj = stepObj.optJSONObject("maneuver")
                        val maneuverType = maneuverObj?.optString("type") ?: "turn"
                        val modifier = maneuverObj?.optString("modifier")
                        val locArr = maneuverObj?.optJSONArray("location")

                        val stepLoc = if (locArr != null && locArr.length() >= 2) {
                            NavLocation(latitude = locArr.getDouble(1), longitude = locArr.getDouble(0))
                        } else {
                            targetPoint.location
                        }

                        val isLastInLeg = s == stepsArray.length() - 1
                        val instruction = if (isLastInLeg) {
                            if (!targetPoint.houseNumber.isNullOrBlank()) {
                                "${targetPoint.stopOrder}. Teslimat Noktasına Ulaştınız (No: ${targetPoint.houseNumber})"
                            } else {
                                "${targetPoint.stopOrder}. Teslimat Noktasına Ulaştınız (${targetPoint.title})"
                            }
                        } else {
                            formatTurkishInstruction(maneuverType, modifier, streetName)
                        }

                        val step = RouteStep(
                            instruction = instruction,
                            maneuverType = maneuverType,
                            modifier = modifier,
                            location = stepLoc,
                            distanceMeters = stepDist,
                            durationSeconds = stepDur,
                            streetName = if (streetName.isNotBlank()) streetName else "Cadde/Sokak"
                        )
                        legSteps.add(step)
                        allSteps.add(step)
                    }
                }

                // Extract leg coordinate slice if leg geometry is provided, or approximate from points
                val legCoords = mutableListOf<NavLocation>()
                val legGeom = legObj.optJSONObject("geometry")?.optJSONArray("coordinates")
                if (legGeom != null) {
                    for (c in 0 until legGeom.length()) {
                        val p = legGeom.getJSONArray(c)
                        legCoords.add(NavLocation(latitude = p.getDouble(1), longitude = p.getDouble(0)))
                    }
                }

                legs.add(
                    DeliveryRouteLeg(
                        legIndex = l,
                        fromLocation = currentFrom,
                        targetPoint = targetPoint,
                        distanceMeters = legDistance,
                        durationSeconds = legDuration,
                        coordinates = if (legCoords.isNotEmpty()) legCoords else allCoordinates,
                        steps = legSteps
                    )
                )

                currentFrom = targetPoint.location
            }
        }

        return MultiStopRouteResult.Success(
            MultiStopRoutePlan(
                origin = origin,
                deliveryPoints = orderedPoints,
                legs = legs,
                totalDistanceMeters = totalDistance,
                totalDurationSeconds = totalDuration,
                allCoordinates = allCoordinates,
                allSteps = allSteps
            )
        )
    }

    /**
     * Intelligent Traveling Salesperson Problem (TSP) heuristic:
     * Combines Nearest Neighbor with 2-Opt local search to find the shortest
     * travel sequence from the courier's current origin through all delivery points.
     */
    private fun optimizeDeliveryOrder(
        origin: NavLocation,
        points: List<DeliveryPoint>
    ): List<DeliveryPoint> {
        if (points.size <= 1) return points.mapIndexed { idx, p -> p.copy(stopOrder = idx + 1) }

        val remaining = points.toMutableList()
        val ordered = mutableListOf<DeliveryPoint>()
        var current = origin

        // Step 1: Nearest-Neighbor greedy sequence
        while (remaining.isNotEmpty()) {
            var nearestIdx = 0
            var minDist = Double.MAX_VALUE
            for (i in remaining.indices) {
                val dist = current.distanceTo(remaining[i].location)
                if (dist < minDist) {
                    minDist = dist
                    nearestIdx = i
                }
            }
            val nextPt = remaining.removeAt(nearestIdx)
            ordered.add(nextPt)
            current = nextPt.location
        }

        // Step 2: 2-Opt optimization to eliminate route criss-crosses
        var improved = true
        var passes = 0
        while (improved && passes < 30 && ordered.size >= 4) {
            improved = false
            passes++
            for (i in 0 until ordered.size - 1) {
                for (j in i + 1 until ordered.size) {
                    val pA = if (i == 0) origin else ordered[i - 1].location
                    val pB = ordered[i].location
                    val pC = ordered[j].location
                    val pD = if (j + 1 < ordered.size) ordered[j + 1].location else null

                    val currentDistance = pA.distanceTo(pB) + (pD?.let { pC.distanceTo(it) } ?: 0.0)
                    val newDistance = pA.distanceTo(pC) + (pD?.let { pB.distanceTo(it) } ?: 0.0)

                    if (newDistance < currentDistance - 15.0) { // Meaningful improvement threshold
                        // Reverse subsegment between i and j
                        ordered.subList(i, j + 1).reverse()
                        improved = true
                    }
                }
            }
        }

        return ordered.mapIndexed { index, point ->
            point.copy(stopOrder = index + 1)
        }
    }

    private fun formatTurkishInstruction(
        type: String,
        modifier: String?,
        streetName: String
    ): String {
        val targetName = if (streetName.isNotBlank()) "$streetName üzerine " else ""
        return when (type) {
            "depart" -> "Rotaya başlayın, $streetName boyunca ilerleyin"
            "arrive" -> "Hedefe ulaştınız!"
            "roundabout", "rotary" -> "Dönel kavşaktan uygun çıkıştan çıkın"
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
                else -> "Düz devam edin"
            }
        }
    }
}
