package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class TimeAttackSettingsActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
    }

    @After
    fun tearDown() {
        clearPrefs()
    }

    @Test
    fun `loads selected difficulty from preferences`() {
        prefs().edit().putString("difficulty", "hard").apply()

        val activity = Robolectric.buildActivity(TimeAttackSettingsActivity::class.java).setup().get()

        assertEquals(
            activity.findViewById<MaterialButton>(R.id.buttonDifficultyHard).id,
            activity.findViewById<MaterialButtonToggleGroup>(R.id.groupDifficulty).checkedButtonId
        )
    }

    @Test
    fun `updates difficulty when another preset is selected`() {
        val activity = Robolectric.buildActivity(TimeAttackSettingsActivity::class.java).setup().get()

        activity.findViewById<MaterialButton>(R.id.buttonDifficultyExtreme).performClick()

        assertEquals("extreme", prefs().getString("difficulty", null))
    }

    @Test
    fun `marks start now and finishes activity`() {
        val activity = Robolectric.buildActivity(TimeAttackSettingsActivity::class.java).setup().get()

        activity.findViewById<android.view.View>(R.id.buttonStartTimeAttackNow).performClick()

        assertTrue(prefs().getBoolean("start_now", false))
        assertTrue(activity.isFinishing)
        assertEquals(activity.getString(R.string.menu_time_attack), ShadowToast.getTextOfLatestToast())
    }

    private fun prefs() = context.getSharedPreferences("time_attack_prefs", Context.MODE_PRIVATE)

    private fun clearPrefs() {
        prefs().edit().clear().commit()
    }
}
