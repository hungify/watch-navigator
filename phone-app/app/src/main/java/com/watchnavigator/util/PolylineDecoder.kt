package com.watchnavigator.util

import com.watchnavigator.model.LatLng

object PolylineDecoder {

    /**
     * Decodes an encoded polyline string into a list of LatLng coordinates.
     * Compatible with Google Maps Directions API polyline encoding.
     */
    fun decode(encodedPath: String?): List<LatLng> {
        if (encodedPath.isNullOrEmpty()) {
            return emptyList()
        }

        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encodedPath.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encodedPath[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encodedPath[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val pLat = lat / 1E5
            val pLng = lng / 1E5
            poly.add(LatLng(pLat, pLng))
        }

        return poly
    }
}
