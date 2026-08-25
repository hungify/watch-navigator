package com.watchnavigator.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeTest {
    @Test
    fun lightColorScheme_definesValidPrimaryAndSurfaceColors() {
        assertThat(LightColorScheme.primary).isEqualTo(Color(0xFF006494))
        assertThat(LightColorScheme.onPrimary).isEqualTo(Color(0xFFFFFFFF))
        assertThat(LightColorScheme.surface).isEqualTo(Color(0xFFFCFCFF))
        assertThat(LightColorScheme.tertiary).isEqualTo(Color(0xFF007A4D))
        assertThat(LightColorScheme.onTertiary).isEqualTo(Color(0xFFFFFFFF))
        assertThat(LightColorScheme.error).isEqualTo(Color(0xFFBA1A1A))
    }

    @Test
    fun darkColorScheme_definesValidPrimaryAndSurfaceColors() {
        assertThat(DarkColorScheme.primary).isEqualTo(Color(0xFF8FCDFF))
        assertThat(DarkColorScheme.onPrimary).isEqualTo(Color(0xFF003450))
        assertThat(DarkColorScheme.surface).isEqualTo(Color(0xFF101417))
        assertThat(DarkColorScheme.error).isEqualTo(Color(0xFFFFB4AB))
    }

    @Test
    fun typography_definesExpectedTextStyles() {
        assertThat(Typography.displayLarge.fontSize.value).isGreaterThan(0f)
        assertThat(Typography.headlineLarge.fontSize.value).isGreaterThan(0f)
        assertThat(Typography.titleLarge.fontSize.value).isGreaterThan(0f)
        assertThat(Typography.bodyLarge.fontSize.value).isGreaterThan(0f)
        assertThat(Typography.labelLarge.fontSize.value).isGreaterThan(0f)
    }
}
