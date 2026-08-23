package com.watchnavigator.model

data class NavStep(
    val instruction: String,
    val streetName: String,
    val maneuver: ManeuverType,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val startLocation: LatLng,
    val endLocation: LatLng,
    val polylinePoints: List<LatLng>
)
