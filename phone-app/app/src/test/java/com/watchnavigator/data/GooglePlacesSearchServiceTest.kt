package com.watchnavigator.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GooglePlacesSearchServiceTest {
    @Test
    fun searchSuggestions_whenPlacesClientIsNull_returnsFailure() =
        runTest {
            val service = GooglePlacesSearchService(placesClient = null)

            val result = service.searchSuggestions("Keangnam")

            assertThat(result.isFailure).isTrue()
            val exception = result.exceptionOrNull()
            assertThat(exception).isInstanceOf(IllegalStateException::class.java)
            assertThat(exception?.message).contains("Google Places SDK is not initialized")
        }

    @Test
    fun fetchPlaceLocation_whenPlacesClientIsNull_returnsFailure() =
        runTest {
            val service = GooglePlacesSearchService(placesClient = null)

            val result = service.fetchPlaceLocation("place_id_123")

            assertThat(result.isFailure).isTrue()
            val exception = result.exceptionOrNull()
            assertThat(exception).isInstanceOf(IllegalStateException::class.java)
            assertThat(exception?.message).contains("Google Places SDK is not initialized")
        }

    @Test
    fun createNewSessionToken_whenPlacesClientIsNull_doesNotCrash() {
        val service = GooglePlacesSearchService(placesClient = null)
        service.createNewSessionToken()
    }
}
