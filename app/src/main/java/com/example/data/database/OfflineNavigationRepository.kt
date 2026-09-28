package com.example.data.database

import android.content.Context
import com.example.data.database.entity.CachedRouteEntity
import com.example.data.database.entity.CachedTileEntity
import com.example.data.model.DeliveryPoint
import com.example.data.model.NavLocation
import com.example.data.model.RouteModel
import com.example.data.model.RouteStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.round

/**
 * Repository providing offline caching and retrieval of map tiles and courier routes.
 */
class OfflineNavigationRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val routeDao = db.routeCacheDao()
    private val tileDao = db.tileCacheDao()

    val recentRoutes: Flow<List<CachedRouteEntity>> = routeDao.getAllRecentRoutes()

    /**
     * Persists a calculated route into Room database for offline navigation.
     */
    suspend fun saveRoute(
        origin: NavLocation,
        destination: NavLocation,
        route: RouteModel,
        deliveryPoints: List<DeliveryPoint>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val key = generateRouteKey(origin, destination)
            val coordsJson = serializeCoordinates(route.coordinates)
            val stepsJson = serializeSteps(route.steps)
            val deliveryPointsJson = deliveryPoints?.let { serializeDeliveryPoints(it) }

            val entity = CachedRouteEntity(
                routeKey = key,
                originLatitude = origin.latitude,
                originLongitude = origin.longitude,
                destLatitude = destination.latitude,
                destLongitude = destination.longitude,
                destinationTitle = route.destinationTitle,
                destinationHouseNumber = route.destinationHouseNumber,
                totalDistanceMeters = route.totalDistanceMeters,
                totalDurationSeconds = route.totalDurationSeconds,
                coordinatesJson = coordsJson,
                stepsJson = stepsJson,
                deliveryPointsJson = deliveryPointsJson,
                cachedAt = System.currentTimeMillis()
            )

            routeDao.insertRoute(entity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Finds a matching cached route for the destination, enabling navigation without internet.
     */
    suspend fun findCachedRoute(
        origin: NavLocation,
        destination: NavLocation
    ): RouteModel? = withContext(Dispatchers.IO) {
        try {
            // 1. Exact match lookup
            val exactKey = generateRouteKey(origin, destination)
            val exactEntity = routeDao.getRouteByKey(exactKey)
            if (exactEntity != null) {
                return@withContext deserializeRoute(exactEntity)
            }

            // 2. Spatial proximity lookup for the destination (~120 meter tolerance bounding box)
            val delta = 0.0011 // approx 120m in latitude/longitude
            val nearbyEntity = routeDao.findNearbyCachedRoute(
                destLatMin = destination.latitude - delta,
                destLatMax = destination.latitude + delta,
                destLonMin = destination.longitude - delta,
                destLonMax = destination.longitude + delta
            )

            if (nearbyEntity != null) {
                return@withContext deserializeRoute(nearbyEntity)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Saves a map tile to Room cache.
     */
    suspend fun cacheTile(
        url: String,
        data: ByteArray,
        contentType: String,
        etag: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val key = normalizeTileKey(url)
            val entity = CachedTileEntity(
                tileKey = key,
                contentType = contentType,
                data = data,
                etag = etag,
                contentLength = data.size.toLong(),
                lastAccessedAt = System.currentTimeMillis()
            )
            tileDao.insertTile(entity)

            // Cache size management: Keep cache within reasonable bounds (e.g. 1500 tiles)
            val count = tileDao.getTileCount()
            if (count > 1500) {
                tileDao.evictOldestTiles(150)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves a cached map tile from Room database.
     */
    suspend fun getCachedTile(url: String): CachedTileEntity? = withContext(Dispatchers.IO) {
        try {
            val key = normalizeTileKey(url)
            val tile = tileDao.getTile(key)
            if (tile != null) {
                tileDao.updateAccessTime(key)
            }
            tile
        } catch (e: Exception) {
            null
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        routeDao.clearAll()
        tileDao.clearAll()
    }

    private fun generateRouteKey(origin: NavLocation, dest: NavLocation): String {
        // Round to 3 decimal places (~100m grid) for key grouping
        val origLat = String.format(Locale.US, "%.3f", origin.latitude)
        val origLon = String.format(Locale.US, "%.3f", origin.longitude)
        val destLat = String.format(Locale.US, "%.3f", dest.latitude)
        val destLon = String.format(Locale.US, "%.3f", dest.longitude)
        return "$origLat,$origLon->$destLat,$destLon"
    }

    private fun normalizeTileKey(url: String): String {
        // Remove access token from URL so cache hits work regardless of query params
        val cleanUrl = url.substringBefore("?access_token=").substringBefore("&access_token=")
        return cleanUrl
    }

    private fun serializeCoordinates(coords: List<NavLocation>): String {
        val array = JSONArray()
        coords.forEach {
            val pt = JSONArray().apply {
                put(it.longitude)
                put(it.latitude)
            }
            array.put(pt)
        }
        return array.toString()
    }

    private fun serializeSteps(steps: List<RouteStep>): String {
        val array = JSONArray()
        steps.forEach { step ->
            val obj = JSONObject().apply {
                put("instruction", step.instruction)
                put("maneuverType", step.maneuverType)
                put("modifier", step.modifier ?: "")
                put("lat", step.location.latitude)
                put("lon", step.location.longitude)
                put("dist", step.distanceMeters)
                put("dur", step.durationSeconds)
                put("street", step.streetName)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun serializeDeliveryPoints(points: List<DeliveryPoint>): String {
        val array = JSONArray()
        points.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("address", p.address)
                put("houseNumber", p.houseNumber ?: "")
                put("lat", p.location.latitude)
                put("lon", p.location.longitude)
                put("recipient", p.recipientName ?: "")
                put("packages", p.packageCount)
                put("completed", p.isCompleted)
                put("order", p.stopOrder)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeRoute(entity: CachedRouteEntity): RouteModel {
        val coordinates = mutableListOf<NavLocation>()
        try {
            val coordsArray = JSONArray(entity.coordinatesJson)
            for (i in 0 until coordsArray.length()) {
                val pt = coordsArray.getJSONArray(i)
                coordinates.add(NavLocation(latitude = pt.getDouble(1), longitude = pt.getDouble(0)))
            }
        } catch (e: Exception) {
            coordinates.add(NavLocation(entity.originLatitude, entity.originLongitude))
            coordinates.add(NavLocation(entity.destLatitude, entity.destLongitude))
        }

        val steps = mutableListOf<RouteStep>()
        try {
            val stepsArray = JSONArray(entity.stepsJson)
            for (i in 0 until stepsArray.length()) {
                val obj = stepsArray.getJSONObject(i)
                steps.add(
                    RouteStep(
                        instruction = obj.optString("instruction", "İlerleyin"),
                        maneuverType = obj.optString("maneuverType", "turn"),
                        modifier = obj.optString("modifier").takeIf { it.isNotBlank() },
                        location = NavLocation(obj.optDouble("lat", 0.0), obj.optDouble("lon", 0.0)),
                        distanceMeters = obj.optDouble("dist", 0.0),
                        durationSeconds = obj.optDouble("dur", 0.0),
                        streetName = obj.optString("street", "Cadde/Sokak")
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore
        }

        return RouteModel(
            coordinates = coordinates,
            totalDistanceMeters = entity.totalDistanceMeters,
            totalDurationSeconds = entity.totalDurationSeconds,
            steps = steps,
            destination = NavLocation(entity.destLatitude, entity.destLongitude),
            destinationTitle = entity.destinationTitle + " (Çevrimdışı)",
            destinationHouseNumber = entity.destinationHouseNumber
        )
    }
}
