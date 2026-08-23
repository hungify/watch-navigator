package com.watchnavigator.model

data class NavRoute(
    val origin: LatLng?,
    val destination: LatLng?,
    val destinationAddress: String,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val travelMode: TravelMode,
    val overviewPolyline: List<LatLng>,
    val steps: List<NavStep>
)
