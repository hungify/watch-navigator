package com.watchnavigator.data

import com.watchnavigator.model.LatLng
import com.watchnavigator.model.ManeuverType
import com.watchnavigator.model.NavRoute
import com.watchnavigator.model.NavStep
import com.watchnavigator.model.TravelMode
import com.watchnavigator.util.HtmlHelper
import com.watchnavigator.util.PolylineDecoder
import org.json.JSONException
import org.json.JSONObject

class DirectionsResponseParser {
    fun parse(
        jsonString: String,
        travelMode: TravelMode = TravelMode.DRIVING
    ): Result<NavRoute> {
        return try {
            val root = JSONObject(jsonString)
            val status = root.optString("status", "UNKNOWN")

            if (status != "OK") {
                val errorMessage = root.optString("error_message", "Directions API returned status: $status")
                val userFriendlyMessage =
                    when (status) {
                        "ZERO_RESULTS" -> "No route found between the origin and destination."
                        "NOT_FOUND" -> "One or more locations could not be found."
                        "OVER_QUERY_LIMIT" -> "Directions API quota exceeded."
                        "REQUEST_DENIED" -> "Directions API request was denied: $errorMessage"
                        "INVALID_REQUEST" -> "Invalid directions request: $errorMessage"
                        else -> errorMessage
                    }
                return Result.failure(DirectionsApiException(status, userFriendlyMessage))
            }

            val routesArray = root.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) {
                return Result.failure(DirectionsApiException("ZERO_RESULTS", "No routes found in the response."))
            }

            val firstRoute = routesArray.getJSONObject(0)
            val overviewPolylineStr = firstRoute.optJSONObject("overview_polyline")?.optString("points", "") ?: ""
            val overviewPolyline = PolylineDecoder.decode(overviewPolylineStr)

            val legsArray = firstRoute.optJSONArray("legs")
            if (legsArray == null || legsArray.length() == 0) {
                return Result.failure(DirectionsApiException("INVALID_DATA", "No route legs found in response."))
            }

            var totalDistance = 0
            var totalDuration = 0
            val allSteps = ArrayList<NavStep>()
            var firstStartLocation: LatLng? = null
            var lastEndLocation: LatLng? = null
            var destinationAddress = ""

            for (i in 0 until legsArray.length()) {
                val leg = legsArray.getJSONObject(i)
                totalDistance += leg.optJSONObject("distance")?.optInt("value", 0) ?: 0
                totalDuration += leg.optJSONObject("duration")?.optInt("value", 0) ?: 0

                val startLocObj = leg.optJSONObject("start_location")
                if (firstStartLocation == null && startLocObj != null) {
                    firstStartLocation = LatLng(startLocObj.optDouble("lat", 0.0), startLocObj.optDouble("lng", 0.0))
                }

                val endLocObj = leg.optJSONObject("end_location")
                if (endLocObj != null) {
                    lastEndLocation = LatLng(endLocObj.optDouble("lat", 0.0), endLocObj.optDouble("lng", 0.0))
                }

                val legEndAddress = leg.optString("end_address", "")
                if (legEndAddress.isNotBlank()) {
                    destinationAddress = legEndAddress
                }

                val stepsArray = leg.optJSONArray("steps")
                if (stepsArray != null) {
                    for (j in 0 until stepsArray.length()) {
                        val stepObj = stepsArray.getJSONObject(j)
                        val stepDistance = stepObj.optJSONObject("distance")?.optInt("value", 0) ?: 0
                        val stepDuration = stepObj.optJSONObject("duration")?.optInt("value", 0) ?: 0
                        val htmlInstructions = stepObj.optString("html_instructions", "")
                        val rawManeuver = stepObj.optString("maneuver", "")

                        val cleanInstruction = HtmlHelper.stripHtml(htmlInstructions)
                        val streetName = HtmlHelper.extractStreetName(htmlInstructions, cleanInstruction)
                        val maneuver = ManeuverType.fromApiString(rawManeuver, cleanInstruction)

                        val sLoc = stepObj.optJSONObject("start_location")
                        val startLatLng =
                            if (sLoc !=
                                null
                            ) {
                                LatLng(sLoc.optDouble("lat", 0.0), sLoc.optDouble("lng", 0.0))
                            } else {
                                LatLng(0.0, 0.0)
                            }

                        val eLoc = stepObj.optJSONObject("end_location")
                        val endLatLng =
                            if (eLoc !=
                                null
                            ) {
                                LatLng(eLoc.optDouble("lat", 0.0), eLoc.optDouble("lng", 0.0))
                            } else {
                                LatLng(0.0, 0.0)
                            }

                        val stepPolyStr = stepObj.optJSONObject("polyline")?.optString("points", "") ?: ""
                        val stepPolyline = PolylineDecoder.decode(stepPolyStr)

                        allSteps.add(
                            NavStep(
                                instruction = cleanInstruction,
                                streetName = streetName,
                                maneuver = maneuver,
                                distanceMeters = stepDistance,
                                durationSeconds = stepDuration,
                                startLocation = startLatLng,
                                endLocation = endLatLng,
                                polylinePoints = stepPolyline
                            )
                        )
                    }
                }
            }

            val navRoute =
                NavRoute(
                    origin = firstStartLocation,
                    destination = lastEndLocation,
                    destinationAddress = destinationAddress,
                    totalDistanceMeters = totalDistance,
                    totalDurationSeconds = totalDuration,
                    travelMode = travelMode,
                    overviewPolyline = overviewPolyline,
                    steps = allSteps
                )

            Result.success(navRoute)
        } catch (e: JSONException) {
            Result.failure(DirectionsApiException("JSON_PARSE_ERROR", "Failed to parse Directions API response: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class DirectionsApiException(
    val status: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
