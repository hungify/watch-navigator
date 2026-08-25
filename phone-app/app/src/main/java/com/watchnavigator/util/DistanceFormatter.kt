package com.watchnavigator.util

import java.util.Locale

object DistanceFormatter {
    fun formatDistance(meters: Int): String =
        if (meters < 1000) {
            "$meters m"
        } else {
            val km = meters / 1000.0
            String.format(Locale.US, "%.1f km", km)
        }

    fun formatDuration(seconds: Int): String {
        val totalMinutes = (seconds + 30) / 60
        if (totalMinutes < 1) {
            return "< 1 min"
        }
        if (totalMinutes < 60) {
            return "$totalMinutes min" + if (totalMinutes > 1) "s" else ""
        }
        val hours = totalMinutes / 60
        val remainingMins = totalMinutes % 60
        return if (remainingMins == 0) {
            "$hours hr" + if (hours > 1) "s" else ""
        } else {
            "$hours hr" + (if (hours > 1) "s " else " ") + "$remainingMins min" + (if (remainingMins > 1) "s" else "")
        }
    }
}
