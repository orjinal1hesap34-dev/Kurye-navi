package com.example.data.model

import java.util.Locale
import java.util.UUID

/**
 * Represents a delivery stop along a courier's route.
 */
data class DeliveryPoint(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val address: String = "",
    val houseNumber: String? = null,
    val location: NavLocation,
    val recipientName: String? = null,
    val packageCount: Int = 1,
    val isCompleted: Boolean = false,
    val stopOrder: Int = 0
)

/**
 * A segment of a multi-stop route between two delivery points.
 */
data class DeliveryRouteLeg(
    val legIndex: Int,
    val fromLocation: NavLocation,
    val targetPoint: DeliveryPoint,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val coordinates: List<NavLocation>,
    val steps: List<RouteStep>
) {
    val formattedDistance: String
        get() = if (distanceMeters >= 1000) {
            String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.toInt()} m"
        }

    val formattedDuration: String
        get() {
            val minutes = (durationSeconds / 60).toInt()
            return if (minutes >= 60) {
                val hours = minutes / 60
                val remainingMins = minutes % 60
                "${hours} sa ${remainingMins} dk"
            } else {
                "${maxOf(1, minutes)} dk"
            }
        }
}

/**
 * Complete plan for multiple delivery stops with optimized sequence and path geometries.
 */
data class MultiStopRoutePlan(
    val origin: NavLocation,
    val deliveryPoints: List<DeliveryPoint>,
    val legs: List<DeliveryRouteLeg>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val allCoordinates: List<NavLocation>,
    val allSteps: List<RouteStep>
) {
    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000) {
            String.format(Locale.US, "%.1f km", totalDistanceMeters / 1000.0)
        } else {
            "${totalDistanceMeters.toInt()} m"
        }

    val formattedDuration: String
        get() {
            val minutes = (totalDurationSeconds / 60).toInt()
            return if (minutes >= 60) {
                val hours = minutes / 60
                val remainingMins = minutes % 60
                "${hours} sa ${remainingMins} dk"
            } else {
                "${maxOf(1, minutes)} dk"
            }
        }

    val completedCount: Int
        get() = deliveryPoints.count { it.isCompleted }

    val remainingPoints: List<DeliveryPoint>
        get() = deliveryPoints.filter { !it.isCompleted }
}

sealed class MultiStopRouteResult {
    data class Success(val plan: MultiStopRoutePlan) : MultiStopRouteResult()
    data class Failure(val errorMessage: String) : MultiStopRouteResult()
}
