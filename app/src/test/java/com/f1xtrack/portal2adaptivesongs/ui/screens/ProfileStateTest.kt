package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.f1xtrack.portal2adaptivesongs.R
import com.f1xtrack.portal2adaptivesongs.ui.theme.AnimationIntensity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileStateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun resolveThemeLabel_prefersAmoledOverNamedThemes() {
        val label = resolveThemeLabel(
            context = context,
            themeKey = "portal1",
            isAmoledEnabled = true,
        )

        assertEquals(context.getString(R.string.settings_amoled_theme), label)
    }

    @Test
    fun resolveLanguageLabel_returnsNativeNameForKnownLocale() {
        val label = resolveLanguageLabel(context, "ja")

        assertEquals(context.getString(R.string.language_japanese_native), label)
    }

    @Test
    fun resolveLanguageLabel_fallsBackToSystemForUnknownLocale() {
        val label = resolveLanguageLabel(context, "xx")

        assertEquals(context.getString(R.string.language_system), label)
    }

    @Test
    fun buildProfileUiState_mapsSnapshotIntoUserFacingSummary() {
        val uiState = buildProfileUiState(
            context = context,
            snapshot = ProfileSnapshot(
                themeKey = "asi",
                languageCode = "ru",
                isAmoledEnabled = false,
                isKeepScreenOnEnabled = true,
                animationIntensity = AnimationIntensity.Expressive,
                onboardingCompleted = true,
                timeAttackDifficulty = "extreme",
                achievementsUnlocked = 3,
                achievementsTotal = 9,
                totalDistanceKm = 42,
                superSpeedMinutes = 17,
                importedTracks = 5,
            ),
        )

        assertEquals(context.getString(R.string.theme_asi), uiState.themeLabel)
        assertEquals(context.getString(R.string.language_russian), uiState.languageLabel)
        assertEquals(context.getString(R.string.profile_time_attack_difficulty_extreme), uiState.timeAttackDifficultyLabel)
        assertEquals(3, uiState.achievementsUnlocked)
        assertEquals(9, uiState.achievementsTotal)
        assertEquals(5, uiState.importedTracks)
        assertEquals(AnimationIntensity.Expressive, uiState.animationIntensity)
        assertEquals(3, uiState.quickActions.size)
    }

    @Test
    fun buildProfileUiState_reportsZeroProgressWhenThereAreNoAchievements() {
        val uiState = buildProfileUiState(
            context = context,
            snapshot = ProfileSnapshot(
                themeKey = "portal2",
                languageCode = "system",
                isAmoledEnabled = false,
                isKeepScreenOnEnabled = false,
                animationIntensity = AnimationIntensity.Moderate,
                onboardingCompleted = false,
                timeAttackDifficulty = "normal",
                achievementsUnlocked = 0,
                achievementsTotal = 0,
                totalDistanceKm = 0,
                superSpeedMinutes = 0,
                importedTracks = 0,
            ),
        )

        assertEquals(0f, uiState.achievementsProgress)
        assertEquals(context.getString(R.string.theme_portal2_default), uiState.themeLabel)
        assertEquals(context.getString(R.string.language_system), uiState.languageLabel)
    }
}
