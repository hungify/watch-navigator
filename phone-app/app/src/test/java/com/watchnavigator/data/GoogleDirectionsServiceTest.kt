package com.watchnavigator.data

import com.google.common.truth.Truth.assertThat
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.TravelMode
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import java.io.IOException

class GoogleDirectionsServiceTest {

    @Test
    fun buildDirectionsRequest_withProxyUrlAndToken_constructsValidProxyRequest() {
        val service = GoogleDirectionsService(
            serverUrl = "https://watch-navigator-proxy.example.workers.dev",
            serverToken = "secret-token-123",
            apiKey = ""
        )

        val request = service.buildDirectionsRequest(
            originParam = "21.0285,105.8542",
            destinationParam = "21.0175,105.7842",
            mode = TravelMode.DRIVING
        )

        assertThat(request.url.toString()).isEqualTo(
            "https://watch-navigator-proxy.example.workers.dev/api/v1/directions?origin=21.0285%2C105.8542&destination=21.0175%2C105.7842&mode=driving"
        )
        assertThat(request.header("Authorization")).isEqualTo("Bearer secret-token-123")
        assertThat(request.url.queryParameter("key")).isNull()
    }

    @Test
    fun buildDirectionsRequest_withTrailingSlashOrExplicitEndpoint_formatsUrlCleanly() {
        val serviceTrailingSlash = GoogleDirectionsService(
            serverUrl = "https://watch-navigator-proxy.example.workers.dev/",
            serverToken = "token"
        )
        val req1 = serviceTrailingSlash.buildDirectionsRequest("0,0", "1,1", TravelMode.WALKING)
        assertThat(req1.url.encodedPath).isEqualTo("/api/v1/directions")

        val serviceExplicit = GoogleDirectionsService(
            serverUrl = "https://watch-navigator-proxy.example.workers.dev/api/v1/directions",
            serverToken = "token"
        )
        val req2 = serviceExplicit.buildDirectionsRequest("0,0", "1,1", TravelMode.WALKING)
        assertThat(req2.url.encodedPath).isEqualTo("/api/v1/directions")
    }

    @Test
    fun buildDirectionsRequest_whenProxyNotConfigured_fallsBackToDirectGoogleDirectionsApi() {
        val service = GoogleDirectionsService(
            apiKey = "AIzaSyDummyGoogleApiKey",
            serverUrl = "",
            serverToken = ""
        )

        val request = service.buildDirectionsRequest(
            originParam = "21.0285,105.8542",
            destinationParam = "place_id:ChIJ12345",
            mode = TravelMode.WALKING
        )

        assertThat(request.url.toString()).isEqualTo(
            "https://maps.googleapis.com/maps/api/directions/json?origin=21.0285%2C105.8542&destination=place_id%3AChIJ12345&mode=walking&key=AIzaSyDummyGoogleApiKey"
        )
        assertThat(request.header("Authorization")).isNull()
        assertThat(request.url.queryParameter("key")).isEqualTo("AIzaSyDummyGoogleApiKey")
    }

    @Test(expected = IllegalStateException::class)
    fun buildDirectionsRequest_whenNeitherProxyNorApiKeyConfigured_throwsIllegalStateException() {
        val service = GoogleDirectionsService(
            apiKey = "",
            serverUrl = "",
            serverToken = ""
        )

        service.buildDirectionsRequest("0,0", "1,1", TravelMode.DRIVING)
    }

    @Test
    fun getDirections_successfulProxyResponse_returnsParsedNavRoute() = runBlocking {
        val mockJson = """
        {
          "status": "OK",
          "routes": [
            {
              "summary": "Nguyen Trai",
              "overview_polyline": { "points": "points_poly" },
              "legs": [
                {
                  "distance": { "text": "5.0 km", "value": 5000 },
                  "duration": { "text": "15 mins", "value": 900 },
                  "start_address": "Origin St",
                  "end_address": "Dest St",
                  "start_location": { "lat": 21.0, "lng": 105.0 },
                  "end_location": { "lat": 21.1, "lng": 105.1 },
                  "steps": [
                    {
                      "distance": { "text": "200 m", "value": 200 },
                      "duration": { "text": "1 min", "value": 60 },
                      "start_location": { "lat": 21.0, "lng": 105.0 },
                      "end_location": { "lat": 21.01, "lng": 105.01 },
                      "html_instructions": "Head north",
                      "travel_mode": "DRIVING"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = GoogleDirectionsService(
            serverUrl = "https://watch-navigator-proxy.example.workers.dev",
            serverToken = "valid-token",
            httpClient = mockClient
        )

        val result = service.getDirections(LatLng(21.0, 105.0), LatLng(21.1, 105.1), TravelMode.DRIVING)

        assertThat(result.isSuccess).isTrue()
        val route = result.getOrThrow()
        assertThat(route.totalDistanceMeters).isEqualTo(5000)
        assertThat(route.steps).hasSize(1)
    }

    @Test
    fun getDirections_proxyReturns401Unauthorized_returnsHelpfulErrorMessage() = runBlocking {
        val errorJson = """
        {
          "error": "Unauthorized",
          "message": "Invalid or missing authentication token in Authorization or X-API-Key header.",
          "statusCode": 401
        }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(errorJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = GoogleDirectionsService(
            serverUrl = "https://watch-navigator-proxy.example.workers.dev",
            serverToken = "invalid-token",
            httpClient = mockClient
        )

        val result = service.getDirections(LatLng(21.0, 105.0), LatLng(21.1, 105.1))

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(IOException::class.java)
        assertThat(exception?.message).contains("Unauthorized (401)")
        assertThat(exception?.message).contains("Invalid or missing authentication token")
    }

    @Test
    fun getDirections_proxyReturns500ServerError_returnsHelpfulErrorMessage() = runBlocking {
        val errorJson = """
        {
          "error": "Internal Server Error",
          "message": "Server configuration error: GOOGLE_DIRECTIONS_API_KEY is not configured.",
          "statusCode": 500
        }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body(errorJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val service = GoogleDirectionsService(
            serverUrl = "https://watch-navigator-proxy.example.workers.dev",
            serverToken = "token",
            httpClient = mockClient
        )

        val result = service.getDirections(LatLng(21.0, 105.0), LatLng(21.1, 105.1))

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception?.message).contains("Server Error (500)")
        assertThat(exception?.message).contains("GOOGLE_DIRECTIONS_API_KEY is not configured")
    }

    @Test
    fun getDirections_networkFailure_returnsFailureResult() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor {
                throw IOException("Connection timed out to proxy worker")
            }
            .build()

        val service = GoogleDirectionsService(
            serverUrl = "https://watch-navigator-proxy.example.workers.dev",
            serverToken = "token",
            httpClient = mockClient
        )

        val result = service.getDirections(LatLng(21.0, 105.0), LatLng(21.1, 105.1))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Connection timed out")
    }
}
