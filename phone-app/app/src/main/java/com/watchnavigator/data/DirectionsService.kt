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
    private val apiKey: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val parser: DirectionsResponseParser = DirectionsResponseParser()
) : DirectionsService {

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

    private suspend fun fetchDirections(
        originParam: String,
        destinationParam: String,
        mode: TravelMode
    ): Result<NavRoute> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Google Maps API key is missing. Please configure MAPS_API_KEY.")
            )
        }

        val url = "https://maps.googleapis.com/maps/api/directions/json"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("origin", originParam)
            ?.addQueryParameter("destination", destinationParam)
            ?.addQueryParameter("mode", mode.apiValue)
            ?.addQueryParameter("key", apiKey)
            ?.build()

        if (url == null) {
            return@withContext Result.failure(IllegalArgumentException("Failed to construct directions API URL"))
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code} error from Directions API: ${response.message}")
                    )
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body from Directions API"))

                parser.parse(bodyString, mode)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
