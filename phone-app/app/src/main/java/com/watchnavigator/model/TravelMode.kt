package com.watchnavigator.model

enum class TravelMode(val apiValue: String) {
    DRIVING("driving"),
    WALKING("walking");

    companion object {
        fun fromApiValue(value: String): TravelMode {
            return entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: DRIVING
        }
    }
}
