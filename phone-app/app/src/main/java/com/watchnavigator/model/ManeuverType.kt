package com.watchnavigator.model

enum class ManeuverType(
    val watchValue: String
) {
    TURN_LEFT("left"),
    TURN_RIGHT("right"),
    TURN_SLIGHT_LEFT("slight_left"),
    TURN_SLIGHT_RIGHT("slight_right"),
    TURN_SHARP_LEFT("sharp_left"),
    TURN_SHARP_RIGHT("sharp_right"),
    UTURN_LEFT("uturn"),
    UTURN_RIGHT("uturn"),
    STRAIGHT("straight"),
    RAMP_LEFT("slight_left"),
    RAMP_RIGHT("slight_right"),
    MERGE("straight"),
    FORK_LEFT("slight_left"),
    FORK_RIGHT("slight_right"),
    ROUNDABOUT_LEFT("roundabout"),
    ROUNDABOUT_RIGHT("roundabout"),
    DEPART("depart"),
    ARRIVE("arrive"),
    UNKNOWN("straight")
    ;

    companion object {
        fun fromApiString(
            rawManeuver: String?,
            instructionText: String? = null
        ): ManeuverType {
            if (!rawManeuver.isNullOrBlank()) {
                val normalized = rawManeuver.trim().lowercase().replace("_", "-")
                when (normalized) {
                    "turn-left" -> return TURN_LEFT
                    "turn-right" -> return TURN_RIGHT
                    "turn-slight-left" -> return TURN_SLIGHT_LEFT
                    "turn-slight-right" -> return TURN_SLIGHT_RIGHT
                    "turn-sharp-left" -> return TURN_SHARP_LEFT
                    "turn-sharp-right" -> return TURN_SHARP_RIGHT
                    "uturn-left" -> return UTURN_LEFT
                    "uturn-right" -> return UTURN_RIGHT
                    "straight", "continue" -> return STRAIGHT
                    "ramp-left" -> return RAMP_LEFT
                    "ramp-right" -> return RAMP_RIGHT
                    "merge" -> return MERGE
                    "fork-left" -> return FORK_LEFT
                    "fork-right" -> return FORK_RIGHT
                    "roundabout-left" -> return ROUNDABOUT_LEFT
                    "roundabout-right", "roundabout" -> return ROUNDABOUT_RIGHT
                    "depart" -> return DEPART
                    "arrive", "destination" -> return ARRIVE
                }
            }

            // Fallback: infer maneuver from instruction text if maneuver attribute is absent
            if (!instructionText.isNullOrBlank()) {
                val lower = instructionText.lowercase()
                return when {
                    lower.contains("u-turn") || lower.contains("quay đầu") -> UTURN_LEFT
                    lower.contains("sharp left") || lower.contains("rẽ gắt sang trái") -> TURN_SHARP_LEFT
                    lower.contains("sharp right") || lower.contains("rẽ gắt sang phải") -> TURN_SHARP_RIGHT
                    lower.contains("slight left") || lower.contains("chếch sang trái") || lower.contains("hơi rẽ trái") -> TURN_SLIGHT_LEFT
                    lower.contains(
                        "slight right"
                    ) ||
                        lower.contains("chếch sang phải") ||
                        lower.contains("hơi rẽ phải") -> TURN_SLIGHT_RIGHT
                    lower.contains("turn left") || lower.contains("rẽ trái") -> TURN_LEFT
                    lower.contains("turn right") || lower.contains("rẽ phải") -> TURN_RIGHT
                    lower.contains("roundabout") || lower.contains("vòng xuyến") || lower.contains("bùng binh") -> ROUNDABOUT_RIGHT
                    lower.contains("arrive") || lower.contains("đến") || lower.contains("đích") -> ARRIVE
                    lower.contains(
                        "head "
                    ) ||
                        lower.contains("đi về hướng") ||
                        lower.contains("đi thẳng") ||
                        lower.contains("continue") -> STRAIGHT
                    else -> STRAIGHT
                }
            }

            return UNKNOWN
        }
    }
}
