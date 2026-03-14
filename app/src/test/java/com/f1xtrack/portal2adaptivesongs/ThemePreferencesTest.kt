package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.f1xtrack.portal2adaptivesongs.ui.theme.AnimationIntensity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemePreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun readAnimationIntensityPreference_readsRelaxedValue() {
        context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("animation_intensity", "relaxed")
            .commit()

        assertEquals(AnimationIntensity.Relaxed, readAnimationIntensityPreference(context))
    }

    @Test
    fun readAnimationIntensityPreference_fallsBackToModerateForUnknownValue() {
        context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("animation_intensity", "experimental")
            .commit()

        assertEquals(AnimationIntensity.Moderate, readAnimationIntensityPreference(context))
    }
}
