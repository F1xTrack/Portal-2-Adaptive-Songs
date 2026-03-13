package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import android.widget.ListView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RouteSettingsActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs("gps_prefs")
        clearPrefs("osmdroid")
    }

    @After
    fun tearDown() {
        clearPrefs("gps_prefs")
        clearPrefs("osmdroid")
    }

    @Test
    fun `loads saved gps and map preferences`() {
        context.getSharedPreferences("gps_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("record_routes", false)
            .putBoolean("use_network_location", false)
            .putLong("min_time_ms", 2500L)
            .putFloat("min_distance_m", 4.5f)
            .putInt("interval_sec", 7)
            .apply()
        context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
            .edit()
            .putString("tile_source", "USGS_TOPO")
            .apply()

        val activity = Robolectric.buildActivity(RouteSettingsActivity::class.java).setup().get()

        assertFalse(activity.findViewById<MaterialSwitch>(R.id.switchRecordRoutes).isChecked)
        assertFalse(activity.findViewById<MaterialSwitch>(R.id.switchUseNetworkLocation).isChecked)
        assertEquals("2500", activity.findViewById<TextInputEditText>(R.id.editMinTime).text.toString())
        assertEquals("4.5", activity.findViewById<TextInputEditText>(R.id.editMinDistance).text.toString())
        assertEquals(7f, activity.findViewById<Slider>(R.id.sliderRecordingInterval).value)
        assertEquals(
            activity.getString(R.string.osm_source_usgs_topo),
            activity.findViewById<MaterialAutoCompleteTextView>(R.id.autoCompleteTileSource).text.toString()
        )
    }

    @Test
    fun `persists updated route preferences`() {
        val activity = Robolectric.buildActivity(RouteSettingsActivity::class.java).setup().get()
        val gpsPrefs = context.getSharedPreferences("gps_prefs", Context.MODE_PRIVATE)
        val mapPrefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)

        activity.findViewById<MaterialSwitch>(R.id.switchRecordRoutes).performClick()
        activity.findViewById<MaterialSwitch>(R.id.switchUseNetworkLocation).performClick()
        activity.findViewById<TextInputEditText>(R.id.editMinTime).setText("3200")
        activity.findViewById<TextInputEditText>(R.id.editMinDistance).setText("6,5")

        val slider = activity.findViewById<Slider>(R.id.sliderRecordingInterval)
        slider.value = 9f

        val dropdown = activity.findViewById<MaterialAutoCompleteTextView>(R.id.autoCompleteTileSource)
        val parent = ListView(activity)
        val itemView = dropdown.adapter.getView(2, null, parent)
        dropdown.onItemClickListener.onItemClick(parent, itemView, 2, dropdown.adapter.getItemId(2))

        assertFalse(gpsPrefs.getBoolean("record_routes", true))
        assertFalse(gpsPrefs.getBoolean("use_network_location", true))
        assertEquals(3200L, gpsPrefs.getLong("min_time_ms", 0L))
        assertEquals(6.5f, gpsPrefs.getFloat("min_distance_m", 0f))
        assertEquals(9, gpsPrefs.getInt("interval_sec", 0))
        assertEquals("USGS_SAT", mapPrefs.getString("tile_source", null))
        assertEquals(
            activity.getString(R.string.recording_interval_value, 9),
            activity.findViewById<android.widget.TextView>(R.id.textRecordingInterval).text.toString()
        )
    }

    private fun clearPrefs(name: String) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
