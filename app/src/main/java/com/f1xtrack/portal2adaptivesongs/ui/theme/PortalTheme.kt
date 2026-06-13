package com.f1xtrack.portal2adaptivesongs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun PortalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    animationIntensity: AnimationIntensity = AnimationIntensity.Moderate,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        portalDarkColorScheme()
    } else {
        portalDarkColorScheme()
    }

    CompositionLocalProvider(
        LocalAnimationIntensity provides animationIntensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PortalTypography,
            content = content,
        )
    }
}
