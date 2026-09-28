package com.example.data.model

data class SearchResult(
    val placeId: Long,
    val displayName: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val houseNumber: String? = null,
    val road: String? = null,
    val suburb: String? = null,
    val city: String? = null
) {
    val fullLabel: String
        get() = if (!houseNumber.isNullOrBlank()) {
            "$title No: $houseNumber, $subtitle"
        } else {
            "$title, $subtitle"
        }
}
