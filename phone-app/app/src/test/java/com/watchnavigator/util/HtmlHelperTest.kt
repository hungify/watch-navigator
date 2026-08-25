package com.watchnavigator.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlHelperTest {
    @Test
    fun stripHtml_removesTagsAndDecodesEntities() {
        val html = "Head <b>north</b> on <b>Nguyễn Trãi</b> toward <b>Khuất Duy Tiến</b>"
        val plain = HtmlHelper.stripHtml(html)
        assertThat(plain).isEqualTo("Head north on Nguyễn Trãi toward Khuất Duy Tiến")
    }

    @Test
    fun stripHtml_handlesDivsAndEntities() {
        val html = "Turn <b>left</b> onto <b>Đường Láng &amp; Cầu Giấy</b><div style=\"font-size:0.9em\">Pass by petrol station</div>"
        val plain = HtmlHelper.stripHtml(html)
        assertThat(plain).isEqualTo("Turn left onto Đường Láng & Cầu Giấy Pass by petrol station")
    }

    @Test
    fun extractStreetName_extractsStreetFromOntoPattern() {
        val html = "Turn <b>right</b> onto <b>Nguyễn Xiển</b>"
        val street = HtmlHelper.extractStreetName(html)
        assertThat(street).isEqualTo("Nguyễn Xiển")
    }

    @Test
    fun extractStreetName_extractsVietnamesePattern() {
        val html = "Rẽ phải vào <b>Đường Giải Phóng</b>"
        val street = HtmlHelper.extractStreetName(html)
        assertThat(street).isEqualTo("Đường Giải Phóng")
    }

    @Test
    fun extractStreetName_fallbackWhenNoOnto() {
        val html = "Head <b>west</b> on <b>Hàng Khay</b>"
        val street = HtmlHelper.extractStreetName(html)
        assertThat(street).isEqualTo("Hàng Khay")
    }

    @Test
    fun distanceFormatter_formatsMetersCorrectly() {
        assertThat(DistanceFormatter.formatDistance(350)).isEqualTo("350 m")
        assertThat(DistanceFormatter.formatDistance(1200)).isEqualTo("1.2 km")
        assertThat(DistanceFormatter.formatDistance(10500)).isEqualTo("10.5 km")
    }

    @Test
    fun distanceFormatter_formatsDurationCorrectly() {
        assertThat(DistanceFormatter.formatDuration(20)).isEqualTo("< 1 min")
        assertThat(DistanceFormatter.formatDuration(60)).isEqualTo("1 min")
        assertThat(DistanceFormatter.formatDuration(120)).isEqualTo("2 mins")
        assertThat(DistanceFormatter.formatDuration(3600)).isEqualTo("1 hr")
        assertThat(DistanceFormatter.formatDuration(4500)).isEqualTo("1 hr 15 mins")
    }
}
