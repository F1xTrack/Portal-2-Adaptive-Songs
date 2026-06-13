package com.f1xtrack.portal2adaptivesongs.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PortalThemeTokensTest {

    @Test
    fun amoledBackground_isNearBlack() {
        assertEquals(0xFF020304.toInt(), PortalThemeTokens.colors.background.toArgb())
    }

    @Test
    fun accentTokens_remainDistinct() {
        assertNotEquals(PortalThemeTokens.colors.accentBlue, PortalThemeTokens.colors.accentOrange)
    }
}
