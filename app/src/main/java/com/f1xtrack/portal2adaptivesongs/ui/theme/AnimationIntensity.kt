package com.f1xtrack.portal2adaptivesongs.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class AnimationIntensity {
    Relaxed,
    Moderate,
    Expressive,
}

val LocalAnimationIntensity = staticCompositionLocalOf { AnimationIntensity.Moderate }
