package com.watchnavigator.data

import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.watchnavigator.model.LatLng
import com.watchnavigator.model.PlaceSuggestion
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface PlacesSearchService {
    suspend fun searchSuggestions(query: String): Result<List<PlaceSuggestion>>
    suspend fun fetchPlaceLocation(placeId: String): Result<LatLng>
    fun createNewSessionToken()
}

class GooglePlacesSearchService(
    private val placesClient: PlacesClient? = null
) : PlacesSearchService {

    private var sessionToken: AutocompleteSessionToken = AutocompleteSessionToken.newInstance()

    override fun createNewSessionToken() {
        sessionToken = AutocompleteSessionToken.newInstance()
    }

    override suspend fun searchSuggestions(query: String): Result<List<PlaceSuggestion>> {
        val client = placesClient
            ?: return Result.failure(IllegalStateException("Google Places SDK is not initialized. Please configure MAPS_API_KEY."))

        if (query.isBlank()) {
            return Result.success(emptyList())
        }

        return suspendCancellableCoroutine { continuation ->
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query)
                .build()

            client.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    val suggestions = response.autocompletePredictions.map { prediction ->
                        PlaceSuggestion(
                            placeId = prediction.placeId,
                            primaryText = prediction.getPrimaryText(null).toString(),
                            secondaryText = prediction.getSecondaryText(null)?.toString() ?: "",
                            fullText = prediction.getFullText(null).toString()
                        )
                    }
                    if (continuation.isActive) {
                        continuation.resume(Result.success(suggestions))
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exception))
                    }
                }
        }
    }

    override suspend fun fetchPlaceLocation(placeId: String): Result<LatLng> {
        val client = placesClient
            ?: return Result.failure(IllegalStateException("Google Places SDK is not initialized. Please configure MAPS_API_KEY."))

        return suspendCancellableCoroutine { continuation ->
            val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
            val request = FetchPlaceRequest.builder(placeId, placeFields)
                .setSessionToken(sessionToken)
                .build()

            client.fetchPlace(request)
                .addOnSuccessListener { response ->
                    val place = response.place
                    val gmsLatLng = place.latLng
                    if (gmsLatLng != null) {
                        val latLng = LatLng(gmsLatLng.latitude, gmsLatLng.longitude)
                        if (continuation.isActive) {
                            continuation.resume(Result.success(latLng))
                        }
                    } else {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(IllegalStateException("Place coordinates not found")))
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(exception))
                    }
                }
        }
    }
}
