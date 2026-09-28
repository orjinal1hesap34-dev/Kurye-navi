package com.example.data.model

data class BuildingInfo(
    val id: Long = 0L,
    val houseNumber: String? = null,
    val street: String? = null,
    val buildingType: String = "Bina",
    val polygon: List<NavLocation> = emptyList(),
    val entranceLocation: NavLocation? = null,
    val centroid: NavLocation = NavLocation(0.0, 0.0),
    val verified: Boolean = false
) {
    val displayTitle: String
        get() = when {
            !houseNumber.isNullOrBlank() && !street.isNullOrBlank() -> "$street No: $houseNumber"
            !houseNumber.isNullOrBlank() -> "Kapı No: $houseNumber"
            !street.isNullOrBlank() -> street
            else -> "Hedef Konum"
        }

    val displaySubtitle: String
        get() = when {
            !houseNumber.isNullOrBlank() && entranceLocation != null -> "Doğrulanmış Bina & Kapı Girişi Belirlendi"
            !houseNumber.isNullOrBlank() -> "Doğrulanmış Dış Kapı Numarası: $houseNumber"
            else -> "Kayıtlı kapı numarası bulunamadı"
        }
}
