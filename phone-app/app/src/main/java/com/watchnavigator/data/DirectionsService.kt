package com.watchnavigator.data

import com.watchnavigator.model.LatLng
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

interface DirectionsService {
    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode = TravelMode.DRIVING
    ): Result<NavRoute>

    suspend fun getDirectionsByPlaceId(
        origin: LatLng,
        destinationPlaceId: String,
        mode: TravelMode = TravelMode.DRIVING
    ): Result<NavRoute>
}

class GoogleDirectionsService(
    val apiKey: String = "",
    val serverUrl: String = "",
    val serverToken: String = "",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build(),
    private val parser: DirectionsResponseParser = DirectionsResponseParser()
) : DirectionsService {

    val isProxyConfigured: Boolean
        get() = serverUrl.isNotBlank()

    override suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode
    ): Result<NavRoute> {
        val originStr = origin.toQueryString()
        val destStr = destination.toQueryString()
        return fetchDirections(originStr, destStr, mode)
    }

    override suspend fun getDirectionsByPlaceId(
        origin: LatLng,
        destinationPlaceId: String,
        mode: TravelMode
    ): Result<NavRoute> {
        val originStr = origin.toQueryString()
        val destStr = "place_id:$destinationPlaceId"
        return fetchDirections(originStr, destStr, mode)
    }

    internal fun buildDirectionsRequest(
        originParam: String,
        destinationParam: String,
        mode: TravelMode
    ): Request {
        val isUsingProxy = serverUrl.isNotBlank()

        if (!isUsingProxy && apiKey.isBlank()) {
            throw IllegalStateException(
                "Neither Directions proxy (NAV_SERVER_URL) nor Google Maps API key (MAPS_API_KEY) is configured."
            )
        }

        val requestUrl = if (isUsingProxy) {
            val normalizedUrl = serverUrl.trim().trimEnd('/')
            val rawBase = when {
                normalizedUrl.endsWith("/api/v1/directions") -> normalizedUrl
                normalizedUrl.endsWith("/directions") -> "${normalizedUrl.removeSuffix("/directions")}/api/v1/directions"
                else -> "$normalizedUrl/api/v1/directions"
            }

            val parsedUrl = rawBase.toHttpUrlOrNull()
            if (parsedUrl == null || !parsedUrl.isHttps) {
                throw IllegalArgumentException("Invalid NAV_SERVER_URL: $serverUrl")
            }

            val urlBuilder = parsedUrl.newBuilder()

            urlBuilder
                .addQueryParameter("origin", originParam)
                .addQueryParameter("destination", destinationParam)
                .addQueryParameter("mode", mode.apiValue)
                .build()
        } else {
            val urlBuilder = "https://maps.googleapis.com/maps/api/directions/json"
                .toHttpUrlOrNull()?.newBuilder()
                ?: throw IllegalArgumentException("Invalid Google Maps Directions API URL")

            urlBuilder
                .addQueryParameter("origin", originParam)
                .addQueryParameter("destination", destinationParam)
                .addQueryParameter("mode", mode.apiValue)
                .addQueryParameter("key", apiKey.trim())
                .build()
        }

        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .get()

        if (isUsingProxy && serverToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${serverToken.trim()}")
        }

        return requestBuilder.build()
    }

    private suspend fun fetchDirections(
        originParam: String,
        destinationParam: String,
        mode: TravelMode
    ): Result<NavRoute> = withContext(Dispatchers.IO) {
        val request = try {
            buildDirectionsRequest(originParam, destinationParam, mode)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        try {
            val client = if (isProxyConfigured) {
                httpClient.newBuilder()
                    .followSslRedirects(false)
                    .addNetworkInterceptor { chain ->
                        if (!chain.request().isHttps) {
                            throw IOException("Cleartext HTTP traffic is not permitted: ${chain.request().url}")
                        }
                        chain.proceed(chain.request())
                    }
                    .build()
            } else {
                httpClient
            }

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()

                if (!response.isSuccessful) {
                    val errorMessage = extractErrorMessage(response.code, response.message, bodyString)
                    return@withContext Result.failure(IOException(errorMessage))
                }

                if (bodyString.isNullOrBlank()) {
                    return@withContext Result.failure(IOException("Empty response body from Directions API"))
                }

                parser.parse(bodyString, mode)
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("The directions request timed out. Check your connection and try again.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(responseCode: Int, responseMessage: String, bodyString: String?): String {
        if (!bodyString.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(bodyString)
                if (json.has("message") && !json.isNull("message")) {
                    val msg = json.getString("message").trim()
                    if (msg.isNotEmpty()) return formatHttpError(responseCode, msg)
                }
                if (json.has("error_message") && !json.isNull("error_message")) {
                    val msg = json.getString("error_message").trim()
                    if (msg.isNotEmpty()) return formatHttpError(responseCode, msg)
                }
                if (json.has("error") && !json.isNull("error")) {
                    val msg = json.getString("error").trim()
                    if (msg.isNotEmpty()) return formatHttpError(responseCode, msg)
                }
            } catch (_: Exception) {
                // Ignore JSON parsing failures on error bodies
            }
        }
        return formatHttpError(responseCode, responseMessage.ifBlank { "Request failed" })
    }

    private fun formatHttpError(code: Int, detail: String): String {
        return when (code) {
            400 -> "Bad Request (400): $detail"
            401 -> "Unauthorized (401): $detail"
            403 -> "Forbidden (403): $detail"
            404 -> "Not Found (404): $detail"
            500 -> "Server Error (500): $detail"
            502 -> "Bad Gateway (502): $detail"
            503 -> "Service Unavailable (503): $detail"
            504 -> "Gateway Timeout (504): $detail"
            else -> "HTTP $code error from Directions API: $detail"
        }
    }
}
