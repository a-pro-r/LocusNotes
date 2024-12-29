package com.beakoninc.locusnotes.data.model

data class Location(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)
