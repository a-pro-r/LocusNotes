package com.beakoninc.locusnotes.data.model

data class Location(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val placeId: String? = null,
    val streetNumber: String? = null,
    val route: String? = null,
    val locality: String? = null,
    val administrativeArea: String? = null,
    val country: String? = null,
    val postalCode: String? = null,
    val distanceMeters: Double? = null
)