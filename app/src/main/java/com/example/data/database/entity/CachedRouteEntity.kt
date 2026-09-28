package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing cached turn-by-turn navigation routes and delivery points
 * to allow couriers to continue navigating without an internet connection.
 */
@Entity(
    tableName = "cached_routes",
    indices = [
        Index(value = ["destLatitude", "destLongitude"]),
        Index(value = ["cachedAt"])
    ]
)
data class CachedRouteEntity(
    @PrimaryKey
    val routeKey: String, // Unique key based on rounded origin & destination coords
    val originLatitude: Double,
    val originLongitude: Double,
    val destLatitude: Double,
    val destLongitude: Double,
    val destinationTitle: String,
    val destinationHouseNumber: String?,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val coordinatesJson: String, // GeoJSON coordinate array serialized to string
    val stepsJson: String, // Route steps with maneuver instructions serialized
    val deliveryPointsJson: String? = null, // Multi-stop delivery points if multi-stop plan
    val cachedAt: Long = System.currentTimeMillis()
)
