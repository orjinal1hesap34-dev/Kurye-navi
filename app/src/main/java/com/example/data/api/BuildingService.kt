package com.example.data.api

import com.example.data.model.BuildingInfo
import com.example.data.model.NavLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class BuildingService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Queries real OpenStreetMap building footprints, door numbers and entrance nodes
     * around the target location.
     */
    suspend fun getBuildingAtLocation(target: NavLocation): BuildingInfo? = withContext(Dispatchers.IO) {
        try {
            // Overpass QL query around 120m
            val query = """
                [out:json][timeout:8];
                (
                  way["building"](around:120, ${target.latitude}, ${target.longitude});
                  node["addr:housenumber"](around:120, ${target.latitude}, ${target.longitude});
                  node["entrance"](around:120, ${target.latitude}, ${target.longitude});
                );
                out body;
                >;
                out skel qt;
            """.trimIndent()

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://overpass-api.de/api/interpreter?data=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KuryeNavigasyon/1.0 (Android Courier Delivery App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val elements = root.optJSONArray("elements") ?: return@withContext null

                // 1. Collect all node coordinates
                val nodeMap = mutableMapOf<Long, NavLocation>()
                val entranceNodes = mutableListOf<NavLocation>()
                val houseNumberNodes = mutableListOf<Pair<NavLocation, String>>()
                val buildingWays = mutableListOf<JSONObject>()

                for (i in 0 until elements.length()) {
                    val elem = elements.getJSONObject(i)
                    val type = elem.optString("type")
                    val id = elem.optLong("id")

                    if (type == "node") {
                        val lat = elem.optDouble("lat")
                        val lon = elem.optDouble("lon")
                        val loc = NavLocation(lat, lon)
                        nodeMap[id] = loc

                        val tags = elem.optJSONObject("tags")
                        if (tags != null) {
                            if (tags.has("entrance")) {
                                entranceNodes.add(loc)
                            }
                            val houseNum = tags.optString("addr:housenumber")
                            if (houseNum.isNotBlank()) {
                                houseNumberNodes.add(loc to houseNum)
                            }
                        }
                    } else if (type == "way") {
                        val tags = elem.optJSONObject("tags")
                        if (tags != null && tags.has("building")) {
                            buildingWays.add(elem)
                        }
                    }
                }

                // 2. Identify the building closest to or enclosing the target point
                var bestBuilding: BuildingInfo? = null
                var bestDistance = Double.MAX_VALUE

                for (way in buildingWays) {
                    val wayId = way.optLong("id")
                    val tags = way.optJSONObject("tags")
                    val nodesArray = way.optJSONArray("nodes") ?: continue

                    val polygonVertices = mutableListOf<NavLocation>()
                    var sumLat = 0.0
                    var sumLon = 0.0

                    for (n in 0 until nodesArray.length()) {
                        val nodeId = nodesArray.getLong(n)
                        nodeMap[nodeId]?.let { pt ->
                            polygonVertices.add(pt)
                            sumLat += pt.latitude
                            sumLon += pt.longitude
                        }
                    }

                    if (polygonVertices.size < 3) continue

                    val centroid = NavLocation(
                        latitude = sumLat / polygonVertices.size,
                        longitude = sumLon / polygonVertices.size
                    )

                    val isInside = isPointInPolygon(target, polygonVertices)
                    val dist = target.distanceTo(centroid)

                    if (isInside || dist < bestDistance) {
                        val houseNumTag = tags?.optString("addr:housenumber")?.takeIf { it.isNotBlank() }
                        val streetTag = tags?.optString("addr:street")?.takeIf { it.isNotBlank() }
                        val buildingType = tags?.optString("building") ?: "Bina"

                        // Find closest entrance node for this building
                        var matchingEntrance: NavLocation? = null
                        var minEntranceDist = Double.MAX_VALUE
                        for (entrance in entranceNodes) {
                            val entDist = entrance.distanceTo(centroid)
                            // If entrance is within 25m of building centroid or inside
                            if (entDist < 30.0 && entDist < minEntranceDist) {
                                minEntranceDist = entDist
                                matchingEntrance = entrance
                            }
                        }

                        // Also check nearby house number nodes if way has none
                        val detectedHouseNum = houseNumTag ?: houseNumberNodes.find {
                            it.first.distanceTo(centroid) < 20.0 || isPointInPolygon(it.first, polygonVertices)
                        }?.second

                        bestDistance = if (isInside) -1.0 else dist
                        bestBuilding = BuildingInfo(
                            id = wayId,
                            houseNumber = detectedHouseNum,
                            street = streetTag,
                            buildingType = buildingType,
                            polygon = polygonVertices,
                            entranceLocation = matchingEntrance,
                            centroid = centroid,
                            verified = true
                        )

                        if (isInside) break // Perfect match found!
                    }
                }

                bestBuilding
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Ray-casting algorithm to test if point is inside a polygon
     */
    private fun isPointInPolygon(point: NavLocation, polygon: List<NavLocation>): Boolean {
        var intersectCount = 0
        val n = polygon.size
        for (i in 0 until n) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % n]
            if ((p1.latitude > point.latitude) != (p2.latitude > point.latitude)) {
                val xIntersection = (point.latitude - p1.latitude) * (p2.longitude - p1.longitude) /
                        (p2.latitude - p1.latitude) + p1.longitude
                if (point.longitude < xIntersection) {
                    intersectCount++
                }
            }
        }
        return (intersectCount % 2) != 0
    }
}
