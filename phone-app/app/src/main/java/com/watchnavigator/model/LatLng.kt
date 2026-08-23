package com.watchnavigator.model

data class LatLng(
    val latitude: Double,
    val longitude: Double
) {
    fun toQueryString(): String = "$latitude,$longitude"
}
