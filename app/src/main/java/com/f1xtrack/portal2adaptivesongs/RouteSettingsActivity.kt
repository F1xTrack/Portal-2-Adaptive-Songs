package com.f1xtrack.portal2adaptivesongs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale
import android.widget.ArrayAdapter

class RouteSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_settings)

        findViewById<MaterialToolbar>(R.id.toolbarRouteSettings).setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("gps_prefs", MODE_PRIVATE)
        val mapPrefs = getSharedPreferences("osmdroid", MODE_PRIVATE)

        val switchRecordRoutes = findViewById<MaterialSwitch>(R.id.switchRecordRoutes)
        val switchUseNetwork = findViewById<MaterialSwitch>(R.id.switchUseNetworkLocation)
        val editMinTime = findViewById<TextInputEditText>(R.id.editMinTime)
        val editMinDistance = findViewById<TextInputEditText>(R.id.editMinDistance)
        val intervalSlider = findViewById<Slider>(R.id.sliderRecordingInterval)
        val intervalText = findViewById<android.widget.TextView>(R.id.textRecordingInterval)
        val tileSourceDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.autoCompleteTileSource)

        switchRecordRoutes.isChecked = prefs.getBoolean("record_routes", true)
        switchUseNetwork.isChecked = prefs.getBoolean("use_network_location", true)
        editMinTime.setText(prefs.getLong("min_time_ms", 1000L).toString())
        editMinDistance.setText(String.format(Locale.getDefault(), "%.1f", prefs.getFloat("min_distance_m", 2f)))

        val intervalSec = prefs.getInt("interval_sec", 2).coerceIn(1, 15)
        intervalSlider.value = intervalSec.toFloat()
        intervalText.text = getString(R.string.recording_interval_value, intervalSec)

        switchRecordRoutes.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("record_routes", checked).apply()
        }
        switchUseNetwork.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("use_network_location", checked).apply()
        }
        editMinTime.doAfterTextChangedCompat { value ->
            prefs.edit().putLong("min_time_ms", value.toLongOrNull() ?: 1000L).apply()
        }
        editMinDistance.doAfterTextChangedCompat { value ->
            prefs.edit().putFloat("min_distance_m", value.replace(',', '.').toFloatOrNull() ?: 2f).apply()
        }

        intervalSlider.addOnChangeListener { _, value, _ ->
            val seconds = value.toInt().coerceIn(1, 15)
            intervalText.text = getString(R.string.recording_interval_value, seconds)
            prefs.edit().putInt("interval_sec", seconds).apply()
        }

        val tileSourceLabels = arrayOf(
            getString(R.string.osm_source_mapnik),
            getString(R.string.osm_source_usgs_topo),
            getString(R.string.osm_source_usgs_sat)
        )
        val tileSourceValues = arrayOf("MAPNIK", "USGS_TOPO", "USGS_SAT")
        tileSourceDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, tileSourceLabels))
        val currentTileSource = mapPrefs.getString("tile_source", tileSourceValues.first()) ?: tileSourceValues.first()
        val currentIndex = tileSourceValues.indexOf(currentTileSource).coerceAtLeast(0)
        tileSourceDropdown.setText(tileSourceLabels[currentIndex], false)
        tileSourceDropdown.setOnItemClickListener { _, _, position, _ ->
            mapPrefs.edit().putString("tile_source", tileSourceValues[position]).apply()
        }
    }
}

private fun TextInputEditText.doAfterTextChangedCompat(action: (String) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) {
            action(s?.toString().orEmpty())
        }
    })
}

private fun RouteSettingsActivity.applyThemeFromPrefs() {
    val prefs = getSharedPreferences("ui_prefs", AppCompatActivity.MODE_PRIVATE)
    val amoled = prefs.getBoolean("amoled_mode", false)
    when {
        amoled -> setTheme(R.style.Theme_Portal2AdaptiveSongs_Amoled)
        prefs.getString("app_theme", "portal2") == "asi" -> setTheme(R.style.Theme_Portal_ASI)
        prefs.getString("app_theme", "portal2") == "portal2_overgrowth" -> setTheme(R.style.Theme_Portal2_Overgrowth)
        prefs.getString("app_theme", "portal2") == "portal1" -> setTheme(R.style.Theme_Portal1)
        else -> setTheme(R.style.Theme_Portal2AdaptiveSongs)
    }
}
