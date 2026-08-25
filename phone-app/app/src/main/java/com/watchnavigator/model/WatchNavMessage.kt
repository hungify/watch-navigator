package com.watchnavigator.model

import org.json.JSONObject

data class WatchNavMessage(
    val turn: String,
    val distanceMeters: Int,
    val street: String = ""
) {
    fun toJsonString(): String {
        val escapedTurn = escapeJson(turn)
        val escapedStreet = escapeJson(street)
        return """{"turn":"$escapedTurn","distanceMeters":$distanceMeters,"street":"$escapedStreet"}"""
    }

    private fun escapeJson(value: String): String {
        val builder = java.lang.StringBuilder()
        for (ch in value) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (ch < ' ') {
                        builder.append(String.format("\\u%04x", ch.code))
                    } else {
                        builder.append(ch)
                    }
                }
            }
        }
        return builder.toString()
    }

    companion object {
        const val TERMINAL_TURN = "stop"

        fun fromJson(jsonStr: String): WatchNavMessage? {
            return try {
                val json = JSONObject(jsonStr)
                if (!json.has("turn")) return null
                val turn = json.getString("turn")
                if (turn.isBlank()) return null

                val distance =
                    when {
                        json.has("distanceMeters") -> json.getInt("distanceMeters")
                        json.has("distance_m") -> json.getInt("distance_m")
                        else -> 0
                    }

                val street =
                    when {
                        json.has("street") -> json.optString("street", "")
                        json.has("streetName") -> json.optString("streetName", "")
                        else -> ""
                    }

                WatchNavMessage(
                    turn = turn,
                    distanceMeters = distance,
                    street = street
                )
            } catch (e: Exception) {
                null
            }
        }

        fun fromNavStep(
            step: NavStep,
            remainingDistanceMeters: Int = step.distanceMeters
        ): WatchNavMessage =
            WatchNavMessage(
                turn = step.maneuver.watchValue,
                distanceMeters = remainingDistanceMeters,
                street = step.streetName
            )

        fun arrival(destinationName: String = ""): WatchNavMessage =
            WatchNavMessage(
                turn = ManeuverType.ARRIVE.watchValue,
                distanceMeters = 0,
                street = destinationName
            )

        fun stop(): WatchNavMessage =
            WatchNavMessage(
                turn = TERMINAL_TURN,
                distanceMeters = 0,
                street = ""
            )
    }
}
