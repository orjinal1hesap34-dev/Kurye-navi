package com.example.data.model

data class RouteModel(
    val coordinates: List<NavLocation> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val totalDurationSeconds: Double = 0.0,
    val steps: List<RouteStep> = emptyList(),
    val destination: NavLocation = NavLocation(0.0, 0.0),
    val destinationTitle: String = "Hedef",
    val destinationHouseNumber: String? = null
) {
    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", totalDistanceMeters / 1000.0)
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
}

data class RouteStep(
    val instruction: String,
    val maneuverType: String,
    val modifier: String?,
    val location: NavLocation,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val streetName: String
) {
    val formattedDistance: String
        get() = if (distanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters.toInt()} m"
        }
}

enum class NavigationState {
    IDLE,
    DESTINATION_SELECTED,
    ROUTING,
    NAVIGATING,
    ARRIVED
}
