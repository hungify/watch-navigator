package com.watchnavigator.data

import com.google.common.truth.Truth.assertThat
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.TravelMode
import org.junit.Before
import org.junit.Test

class DirectionsResponseParserTest {

    private lateinit var parser: DirectionsResponseParser

    @Before
    fun setUp() {
        parser = DirectionsResponseParser()
    }

    @Test
    fun parse_validDirectionsJsonResponse_returnsParsedNavRoute() {
        val json = """
        {
          "status": "OK",
          "routes": [
            {
              "summary": "Đ. Nguyễn Trãi",
              "overview_polyline": {
                "points": "_p~iF~ps|U_ulLnnqC_mqNvxqn`@"
              },
              "legs": [
                {
                  "distance": { "text": "7.5 km", "value": 7500 },
                  "duration": { "text": "22 mins", "value": 1320 },
                  "start_address": "Hoan Kiem, Hanoi, Vietnam",
                  "end_address": "Keangnam Landmark 72, Pham Hung, Hanoi",
                  "start_location": { "lat": 21.0285, "lng": 105.8542 },
                  "end_location": { "lat": 21.0175, "lng": 105.7842 },
                  "steps": [
                    {
                      "distance": { "text": "300 m", "value": 300 },
                      "duration": { "text": "1 min", "value": 60 },
                      "start_location": { "lat": 21.0285, "lng": 105.8542 },
                      "end_location": { "lat": 21.0290, "lng": 105.8520 },
                      "html_instructions": "Head <b>west</b> on <b>Hàng Khay</b> toward <b>Bà Triệu</b>",
                      "polyline": { "points": "_p~iF~ps|U" },
                      "travel_mode": "DRIVING"
                    },
                    {
                      "distance": { "text": "1.2 km", "value": 1200 },
                      "duration": { "text": "3 mins", "value": 180 },
                      "start_location": { "lat": 21.0290, "lng": 105.8520 },
                      "end_location": { "lat": 21.0250, "lng": 105.8450 },
                      "html_instructions": "Turn <b>left</b> onto <b>Tràng Thi</b>",
                      "maneuver": "turn-left",
                      "polyline": { "points": "_p~iF~ps|U" },
                      "travel_mode": "DRIVING"
                    },
                    {
                      "distance": { "text": "500 m", "value": 500 },
                      "duration": { "text": "1 min", "value": 80 },
                      "start_location": { "lat": 21.0250, "lng": 105.8450 },
                      "end_location": { "lat": 21.0175, "lng": 105.7842 },
                      "html_instructions": "Turn <b>right</b> onto <b>Điện Biên Phủ</b>",
                      "maneuver": "turn-right",
                      "polyline": { "points": "_p~iF~ps|U" },
                      "travel_mode": "DRIVING"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val result = parser.parse(json, TravelMode.DRIVING)

        assertThat(result.isSuccess).isTrue()
        val route = result.getOrThrow()
        assertThat(route.totalDistanceMeters).isEqualTo(7500)
        assertThat(route.totalDurationSeconds).isEqualTo(1320)
        assertThat(route.destinationAddress).isEqualTo("Keangnam Landmark 72, Pham Hung, Hanoi")
        assertThat(route.travelMode).isEqualTo(TravelMode.DRIVING)
        assertThat(route.overviewPolyline).isNotEmpty()
        assertThat(route.steps).hasSize(3)

        // Step 1: Head west on Hang Khay
        val step1 = route.steps[0]
        assertThat(step1.instruction).isEqualTo("Head west on Hàng Khay toward Bà Triệu")
        assertThat(step1.streetName).isEqualTo("Hàng Khay")
        assertThat(step1.maneuver).isEqualTo(ManeuverType.STRAIGHT)
        assertThat(step1.distanceMeters).isEqualTo(300)

        // Step 2: Turn left onto Trang Thi
        val step2 = route.steps[1]
        assertThat(step2.maneuver).isEqualTo(ManeuverType.TURN_LEFT)
        assertThat(step2.streetName).isEqualTo("Tràng Thi")
        assertThat(step2.distanceMeters).isEqualTo(1200)

        // Step 3: Turn right onto Dien Bien Phu
        val step3 = route.steps[2]
        assertThat(step3.maneuver).isEqualTo(ManeuverType.TURN_RIGHT)
        assertThat(step3.streetName).isEqualTo("Điện Biên Phủ")
    }

    @Test
    fun parse_zeroResults_returnsFailureWithAppropriateMessage() {
        val json = """
        {
          "status": "ZERO_RESULTS",
          "routes": [],
          "geocoded_waypoints": []
        }
        """.trimIndent()

        val result = parser.parse(json)
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(DirectionsApiException::class.java)
        val apiException = exception as DirectionsApiException
        assertThat(apiException.status).isEqualTo("ZERO_RESULTS")
        assertThat(apiException.message).contains("No route found")
    }

    @Test
    fun parse_requestDenied_returnsFailureWithErrorMessage() {
        val json = """
        {
          "status": "REQUEST_DENIED",
          "error_message": "The provided API key is invalid."
        }
        """.trimIndent()

        val result = parser.parse(json)
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as DirectionsApiException
        assertThat(exception.status).isEqualTo("REQUEST_DENIED")
        assertThat(exception.message).contains("The provided API key is invalid")
    }

    @Test
    fun parse_invalidJson_returnsFailureGracefully() {
        val result = parser.parse("Not a JSON string {")
        assertThat(result.isFailure).isTrue()
    }
}
