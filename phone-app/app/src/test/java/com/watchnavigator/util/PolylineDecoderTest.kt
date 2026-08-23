package com.watchnavigator.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun decode_nullOrEmpty_returnsEmptyList() {
        assertThat(PolylineDecoder.decode(null)).isEmpty()
        assertThat(PolylineDecoder.decode("")).isEmpty()
    }

    @Test
    fun decode_validPolyline_returnsAccurateCoordinates() {
        // Points: (38.5, -120.2), (40.7, -120.95)
        val polyline = "_p~iF~ps|U_ulLnnqC"
        val points = PolylineDecoder.decode(polyline)

        assertThat(points).hasSize(2)
        assertThat(points[0].latitude).isWithin(0.001).of(38.5)
        assertThat(points[0].longitude).isWithin(0.001).of(-120.2)
        assertThat(points[1].latitude).isWithin(0.001).of(40.7)
        assertThat(points[1].longitude).isWithin(0.001).of(-120.95)
    }

    @Test
    fun decode_singlePointPolyline_decodesCorrectly() {
        val polyline = "_p~iF~ps|U"
        val points = PolylineDecoder.decode(polyline)

        assertThat(points).hasSize(1)
        assertThat(points[0].latitude).isWithin(0.001).of(38.5)
        assertThat(points[0].longitude).isWithin(0.001).of(-120.2)
    }
}
