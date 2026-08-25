package com.watchnavigator.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchNavMessageTest {
    @Test
    fun toJsonString_producesCompactJson() {
        val message =
            WatchNavMessage(
                turn = "left",
                distanceMeters = 120,
                street = "Nguyen Trai"
            )
        val jsonStr = message.toJsonString()

        assertThat(jsonStr).isEqualTo("""{"turn":"left","distanceMeters":120,"street":"Nguyen Trai"}""")
    }

    @Test
    fun toJsonString_isUnder100Bytes() {
        val message =
            WatchNavMessage(
                turn = "turn-sharp-left",
                distanceMeters = 1500,
                street = "Duong Nguyen Trai Thanh Xuan"
            )
        val jsonBytes = message.toJsonString().toByteArray(Charsets.UTF_8)
        assertThat(jsonBytes.size).isLessThan(100)
    }

    @Test
    fun fromJson_parsesStandardJsonCorrectly() {
        val jsonStr = """{"turn":"right","distanceMeters":350,"street":"Tran Phu"}"""
        val parsed = WatchNavMessage.fromJson(jsonStr)

        assertThat(parsed).isNotNull()
        assertThat(parsed?.turn).isEqualTo("right")
        assertThat(parsed?.distanceMeters).isEqualTo(350)
        assertThat(parsed?.street).isEqualTo("Tran Phu")
    }

    @Test
    fun fromJson_supportsFallbackKeys_distance_m_and_streetName() {
        val jsonStr = """{"turn":"slight_left","distance_m":75,"streetName":"Le Loi"}"""
        val parsed = WatchNavMessage.fromJson(jsonStr)

        assertThat(parsed).isNotNull()
        assertThat(parsed?.turn).isEqualTo("slight_left")
        assertThat(parsed?.distanceMeters).isEqualTo(75)
        assertThat(parsed?.street).isEqualTo("Le Loi")
    }

    @Test
    fun fromJson_handlesMissingStreetGracefully() {
        val jsonStr = """{"turn":"straight","distanceMeters":500}"""
        val parsed = WatchNavMessage.fromJson(jsonStr)

        assertThat(parsed).isNotNull()
        assertThat(parsed?.turn).isEqualTo("straight")
        assertThat(parsed?.distanceMeters).isEqualTo(500)
        assertThat(parsed?.street).isEqualTo("")
    }

    @Test
    fun fromJson_returnsNullForInvalidJson() {
        assertThat(WatchNavMessage.fromJson("not-a-json")).isNull()
        assertThat(WatchNavMessage.fromJson("{}")).isNull() // missing turn
        assertThat(WatchNavMessage.fromJson("""{"distanceMeters":100}""")).isNull()
    }

    @Test
    fun fromNavStep_createsMessageFromStepModel() {
        val step =
            NavStep(
                instruction = "Turn left onto Nguyen Trai",
                streetName = "Nguyen Trai",
                maneuver = ManeuverType.TURN_LEFT,
                distanceMeters = 250,
                durationSeconds = 60,
                startLocation = LatLng(21.0, 105.8),
                endLocation = LatLng(21.01, 105.81),
                polylinePoints = emptyList()
            )

        val message = WatchNavMessage.fromNavStep(step)
        assertThat(message.turn).isEqualTo("left")
        assertThat(message.distanceMeters).isEqualTo(250)
        assertThat(message.street).isEqualTo("Nguyen Trai")
    }

    @Test
    fun fromNavStep_withCustomRemainingDistance() {
        val step =
            NavStep(
                instruction = "Turn right onto Le Van Luong",
                streetName = "Le Van Luong",
                maneuver = ManeuverType.TURN_RIGHT,
                distanceMeters = 500,
                durationSeconds = 120,
                startLocation = LatLng(21.0, 105.8),
                endLocation = LatLng(21.01, 105.81),
                polylinePoints = emptyList()
            )

        val message = WatchNavMessage.fromNavStep(step, remainingDistanceMeters = 80)
        assertThat(message.turn).isEqualTo("right")
        assertThat(message.distanceMeters).isEqualTo(80)
        assertThat(message.street).isEqualTo("Le Van Luong")
    }

    @Test
    fun arrival_createsArrivalMessage() {
        val arrival = WatchNavMessage.arrival("Grand Hotel")
        assertThat(arrival.turn).isEqualTo("arrive")
        assertThat(arrival.distanceMeters).isEqualTo(0)
        assertThat(arrival.street).isEqualTo("Grand Hotel")
    }
}
