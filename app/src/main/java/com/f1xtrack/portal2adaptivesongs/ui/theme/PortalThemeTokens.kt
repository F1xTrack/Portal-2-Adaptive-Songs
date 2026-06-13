package com.f1xtrack.portal2adaptivesongs.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object PortalThemeTokens {
    val colors = PortalColors(
        background = Color(0xFF020304),
        surface = Color(0xFF090C11),
        surfaceElevated = Color(0xFF101720),
        surfaceHighlight = Color(0xFF162434),
        accentBlue = Color(0xFF7FD2FF),
        accentOrange = Color(0xFFFF9A3D),
        contentPrimary = Color(0xFFF4F7FB),
        contentSecondary = Color(0xFFA6B2C5),
        outline = Color(0xFF28435E),
        statusInfo = Color(0xFF7FD2FF),
        statusSuccess = Color(0xFF6DDBA7),
        statusWarning = Color(0xFFFFC469),
    )
}

data class PortalColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val accentBlue: Color,
    val accentOrange: Color,
    val contentPrimary: Color,
    val contentSecondary: Color,
    val outline: Color,
    val statusInfo: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
)

internal fun portalDarkColorScheme() = darkColorScheme(
    primary = PortalThemeTokens.colors.accentBlue,
    onPrimary = Color.Black,
    primaryContainer = PortalThemeTokens.colors.surfaceHighlight,
    onPrimaryContainer = PortalThemeTokens.colors.contentPrimary,
    secondary = PortalThemeTokens.colors.accentOrange,
    onSecondary = Color.Black,
    secondaryContainer = PortalThemeTokens.colors.surfaceElevated,
    onSecondaryContainer = PortalThemeTokens.colors.contentPrimary,
    tertiary = PortalThemeTokens.colors.statusInfo,
    onTertiary = Color.Black,
    background = PortalThemeTokens.colors.background,
    onBackground = PortalThemeTokens.colors.contentPrimary,
    surface = PortalThemeTokens.colors.surface,
    onSurface = PortalThemeTokens.colors.contentPrimary,
    surfaceVariant = PortalThemeTokens.colors.surfaceElevated,
    onSurfaceVariant = PortalThemeTokens.colors.contentSecondary,
    outline = PortalThemeTokens.colors.outline,
)
