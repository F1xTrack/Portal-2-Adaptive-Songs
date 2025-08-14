package com.f1xtrack.portal2adaptivesongs

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View

class RouteSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_settings)

        // Material 3: no top app bar on this screen; rely on system back navigation

        val settingsContainer = findViewById<ViewGroup>(R.id.settings_container)
        setupRouteRecordingSettings(settingsContainer)
    }

    private fun setupRouteRecordingSettings(container: ViewGroup) {
        val prefs = getSharedPreferences("gps_prefs", MODE_PRIVATE)

        // --- Секция записи маршрутов ---
        val routeRecordingTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_routes)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 16, 0, 8)
        }

        val switchRecord = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
            text = getString(R.string.settings_record_routes)
            isChecked = prefs.getBoolean("record_routes", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("record_routes", isChecked).apply()
            }
        }

        val minTimeLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = getString(R.string.settings_min_time_s)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val minTimeEdit = com.google.android.material.textfield.TextInputEditText(this).apply {
            setText(prefs.getLong("min_time_ms", 1000L).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().toLongOrNull() ?: 1000L
                    prefs.edit().putLong("min_time_ms", value).apply()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        minTimeLayout.addView(minTimeEdit)

        val minDistanceLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = getString(R.string.settings_min_distance_m)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val minDistanceEdit = com.google.android.material.textfield.TextInputEditText(this).apply {
            setText(prefs.getFloat("min_distance_m", 2f).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val value = s.toString().toFloatOrNull() ?: 2f
                    prefs.edit().putFloat("min_distance_m", value).apply()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        minDistanceLayout.addView(minDistanceEdit)

        container.addView(routeRecordingTitle)
        container.addView(switchRecord)
        container.addView(minTimeLayout)
        container.addView(minDistanceLayout)

        // --- Секция карты и GPS ---
        val mapPrefs = getSharedPreferences("osmdroid", MODE_PRIVATE)

        val mapSectionTitle = TextView(this).apply {
            text = getString(R.string.settings_section_map_gps)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 24, 0, 8)
        }

        // Используется только OSM; выбор провайдера удалён

        // Источник тайлов для OSM
        val osmSourceLabel = TextView(this).apply {
            text = getString(R.string.osm_tile_source_label)
            setPadding(0, 16, 0, 4)
        }
        val osmSourceSpinner = Spinner(this)
        val osmSources = listOf(
            org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK,
            org.osmdroid.tileprovider.tilesource.TileSourceFactory.USGS_TOPO,
            org.osmdroid.tileprovider.tilesource.TileSourceFactory.USGS_SAT
        )
        val osmSourceNames = arrayOf(
            getString(R.string.osm_source_mapnik),
            getString(R.string.osm_source_usgs_topo),
            getString(R.string.osm_source_usgs_sat)
        )
        val osmAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, osmSourceNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        osmSourceSpinner.adapter = osmAdapter

        val currentTileSource = mapPrefs.getString("tile_source", org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK.name())
        val currentTileIndex = osmSources.indexOfFirst { it.name() == currentTileSource }.coerceAtLeast(0)
        osmSourceSpinner.setSelection(currentTileIndex)

        // Настройки OSM всегда отображаются

        osmSourceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = osmSources[position]
                mapPrefs.edit().putString("tile_source", selected.name()).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        container.addView(mapSectionTitle)
        container.addView(osmSourceLabel)
        container.addView(osmSourceSpinner)
    }
}
