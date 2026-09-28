package com.example.data.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NavLocation(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f,
    val speedKmh: Float = 0f,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Calculates distance to another location in meters using Haversine formula
     */
    fun distanceTo(other: NavLocation): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Calculates bearing from this location to another in degrees (0-360)
     */
    fun bearingTo(other: NavLocation): Float {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }
}
