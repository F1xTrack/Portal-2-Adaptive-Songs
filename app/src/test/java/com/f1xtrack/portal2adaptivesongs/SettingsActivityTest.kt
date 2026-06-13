package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.materialswitch.MaterialSwitch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs("ui_prefs")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @After
    fun tearDown() {
        clearPrefs("ui_prefs")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun `loads ui switches from shared preferences`() {
        context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("amoled_mode", true)
            .putBoolean("keep_screen_on", true)
            .apply()

        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        assertTrue(activity.findViewById<MaterialSwitch>(R.id.switchAmoled).isChecked)
        assertTrue(activity.findViewById<MaterialSwitch>(R.id.switchKeepScreenOn).isChecked)
    }

    @Test
    fun `updates keep screen on preference when toggled`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val switch = activity.findViewById<MaterialSwitch>(R.id.switchKeepScreenOn)

        assertFalse(readUiPrefs().getBoolean("keep_screen_on", false))

        switch.performClick()

        assertTrue(readUiPrefs().getBoolean("keep_screen_on", false))
    }

    @Test
    fun `updates amoled preference when toggled`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val switch = activity.findViewById<MaterialSwitch>(R.id.switchAmoled)

        switch.performClick()

        assertTrue(readUiPrefs().getBoolean("amoled_mode", false))
    }

    @Test
    fun `launches route settings from navigation button`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

        activity.findViewById<android.view.View>(R.id.buttonRoutes).performClick()

        val nextIntent = shadowOf(activity).nextStartedActivity

        assertEquals(RouteSettingsActivity::class.java.name, nextIntent.component?.className)
    }

    private fun readUiPrefs() = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    private fun clearPrefs(name: String) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
