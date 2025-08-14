package com.f1xtrack.portal2adaptivesongs

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import android.content.res.Configuration as AppConfiguration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import kotlin.math.abs

class HistoryActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var legendContainer: LinearLayout
    private lateinit var colorSchemeDropdown: MaterialAutoCompleteTextView
    private lateinit var timePeriodDropdown: MaterialAutoCompleteTextView

    private var colorScheme: String = "track"
    private var timePeriod: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // osmdroid requires a user agent; store preferences in osmdroid
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.menu_history)
        // Tint menu icons to match M3 colorOnSurface
        runCatching {
            val tint = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
            for (i in 0 until toolbar.menu.size()) {
                toolbar.menu.getItem(i).icon?.setTint(tint)
            }
            toolbar.overflowIcon?.setTint(tint)
        }
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_clear_routes -> { confirmClearHistory(); true }
                R.id.action_change_map_provider -> { handleMapProviderAction(); true }
                else -> false
            }
        }

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)

        // Load saved tile provider
        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        val tileSourceName = prefs.getString("tile_source", TileSourceFactory.MAPNIK.name())
        val tileSource = TileSourceFactory.getTileSource(tileSourceName)
        map.setTileSource(tileSource)

        // Apply dark theme to map if needed
        val nightModeFlags = resources.configuration.uiMode and AppConfiguration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == AppConfiguration.UI_MODE_NIGHT_YES) {
            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }

        legendContainer = findViewById(R.id.legendContainer)
        colorSchemeDropdown = findViewById(R.id.autoCompleteColorScheme)
        timePeriodDropdown = findViewById(R.id.autoCompleteTimePeriod)

        setupColorSchemeDropdown()
        setupTimePeriodDropdown()

        renderHistory()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(getString(R.string.storage_clear_route_history))
            .setMessage(getString(R.string.storage_confirm_clear_routes))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                RouteRecorder.clear(this)
                Toast.makeText(this, getString(R.string.storage_done), Toast.LENGTH_SHORT).show()
                map.overlays.clear()
                legendContainer.removeAllViews()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private data class Pt(
        val t: Long,
        val sid: String,
        val lat: Double,
        val lon: Double,
        val track: String,
        val mode: String,
        val speed: Double,
        val alt: Double
    )

    private fun setupTimePeriodDropdown() {
        val options = resources.getStringArray(R.array.history_time_period_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, options)
        timePeriodDropdown.setAdapter(adapter)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        timePeriod = prefs.getString("time_period", "all") ?: "all"
        val selection = when (timePeriod) {
            "24h" -> 0
            "7d" -> 1
            "30d" -> 2
            else -> 3
        }
        timePeriodDropdown.setText(options.getOrNull(selection) ?: options.last(), false)

        timePeriodDropdown.setOnItemClickListener { _, _, position, _ ->
            val selected = when (position) {
                0 -> "24h"
                1 -> "7d"
                2 -> "30d"
                else -> "all"
            }
            if (selected != timePeriod) {
                timePeriod = selected
                prefs.edit().putString("time_period", timePeriod).apply()
                renderHistory()
            }
        }
    }

    private fun setupColorSchemeDropdown() {
        val options = resources.getStringArray(R.array.history_color_scheme_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, options)
        colorSchemeDropdown.setAdapter(adapter)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        colorScheme = prefs.getString("color_scheme", "track") ?: "track"
        val selection = when (colorScheme) {
            "speed" -> 1
            "altitude" -> 2
            else -> 0
        }
        colorSchemeDropdown.setText(options.getOrNull(selection) ?: options.first(), false)

        colorSchemeDropdown.setOnItemClickListener { _, _, position, _ ->
            val selected = when (position) {
                1 -> "speed"
                2 -> "altitude"
                else -> "track"
            }
            if (selected != colorScheme) {
                colorScheme = selected
                prefs.edit().putString("color_scheme", colorScheme).apply()
                renderHistory()
            }
        }
    }

    private fun colorForTrack(track: String, mode: String): Int {
        val hue = (abs(track.hashCode()) % 360).toFloat()
        val isSuper = mode.equals("superspeed", ignoreCase = true)
        val saturation = if (isSuper) 1.0f else 0.6f
        val value = if (isSuper) 0.9f else 0.7f
        val hsv = floatArrayOf(hue, saturation, value)
        return Color.HSVToColor(200, hsv) // alpha 200
    }

    private fun renderHistory() {
        map.overlays.clear()
        legendContainer.removeAllViews()
        val raw = RouteRecorder.readAll(this)

        val cutoff = when (timePeriod) {
            "24h" -> System.currentTimeMillis() - 24 * 60 * 60 * 1000
            "7d" -> System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
            "30d" -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            else -> 0L
        }

        val filteredRaw = if (cutoff > 0) raw.filter { it.optLong("t", 0) >= cutoff } else raw

        if (filteredRaw.isEmpty()) {
            Toast.makeText(this, getString(R.string.history_no_routes), Toast.LENGTH_SHORT).show()
            return
        }
        val points = filteredRaw.mapNotNull { toPt(it) }.sortedBy { it.t }
        if (points.isEmpty()) {
            Toast.makeText(this, getString(R.string.history_no_routes), Toast.LENGTH_SHORT).show()
            return
        }

        // Build segments: new segment when track or mode or session changes
        var last: Pt? = null
        val current = mutableListOf<GeoPoint>()
        var currentTrack: String? = null
        var currentMode: String? = null
        var currentSid: String? = null
        val segments = mutableListOf<Triple<List<GeoPoint>, String, String>>() // pts, track, mode
        for (p in points) {
            if (last == null) {
                current.clear()
                current += GeoPoint(p.lat, p.lon)
                currentTrack = p.track
                currentMode = p.mode
                currentSid = p.sid
            } else {
                val changed = (p.track != currentTrack) || (p.mode != currentMode) || (p.sid != currentSid)
                if (changed) {
                    if (current.size >= 2 && currentTrack != null && currentMode != null) {
                        segments += Triple(current.toList(), currentTrack!!, currentMode!!)
                    }
                    current.clear()
                    currentTrack = p.track
                    currentMode = p.mode
                    currentSid = p.sid
                }
                current += GeoPoint(p.lat, p.lon)
            }
            last = p
        }
        if (current.size >= 2 && currentTrack != null && currentMode != null) {
            segments += Triple(current.toList(), currentTrack!!, currentMode!!)
        }

        // Render based on scheme
        when (colorScheme) {
            "track" -> renderByTrack(points)
            "speed" -> renderByGradient(points, "speed")
            "altitude" -> renderByGradient(points, "altitude")
        }
        map.invalidate()

        // Center map
        val controller = map.controller
        if (points.isNotEmpty()) {
            val lastGeoPoint = GeoPoint(points.last().lat, points.last().lon)
            controller.setZoom(15.0)
            controller.setCenter(lastGeoPoint)
        } else {
            controller.setZoom(3.0)
            controller.setCenter(GeoPoint(0.0, 0.0))
        }


    }

    private fun toPt(obj: JSONObject): Pt? {
        return try {
            val t = obj.optLong("t")
            val sid = obj.optString("sid")
            val lat = obj.optDouble("lat")
            val lon = obj.optDouble("lon")
            val track = obj.optString("track")
            val mode = obj.optString("mode")
            val speed = obj.optDouble("speed", -1.0)
            val alt = obj.optDouble("alt", -1.0)
            if (sid.isNullOrEmpty() || track.isNullOrEmpty()) null else Pt(t, sid, lat, lon, track, mode, speed, alt)
        } catch (_: Exception) { null }
    }

    private fun handleMapProviderAction() {
        showOsmTileSourceDialog()
    }

    private fun showOsmTileSourceDialog() {
        val providers = listOf(
            TileSourceFactory.MAPNIK,
            TileSourceFactory.USGS_TOPO,
            TileSourceFactory.USGS_SAT
        )
        val providerNames = providers.map { it.name() }.toTypedArray()

        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        val currentProviderName = prefs.getString("tile_source", TileSourceFactory.MAPNIK.name())
        val currentIndex = providers.indexOfFirst { it.name() == currentProviderName }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(getString(R.string.osm_tile_source_label))
            .setIcon(android.R.drawable.ic_menu_mapmode)
            .setSingleChoiceItems(providerNames, currentIndex) { dialog, which ->
                val selectedProvider = providers[which]
                map.setTileSource(selectedProvider)
                prefs.edit().putString("tile_source", selectedProvider.name()).apply()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderByTrack(points: List<Pt>) {
        val segments = buildSegments(points)
        val tracksInLegend = linkedSetOf<String>()

        segments.forEach { (geoPts, track, mode) ->
            val polyline = Polyline()
            polyline.setPoints(geoPts)
            polyline.color = colorForTrack(track, mode)
            polyline.width = if (mode.equals("superspeed", ignoreCase = true)) 10f else 5f
            map.overlays.add(polyline)
            tracksInLegend.add(track)
        }

        tracksInLegend.forEach { track ->
            legendContainer.addView(buildLegendRow(track, colorForTrack(track, "normal")))
        }
    }

    private fun renderByGradient(points: List<Pt>, type: String) {
        if (points.size < 2) return

        val values = points.mapNotNull {
            val v = if (type == "speed") it.speed else it.alt
            if (v >= 0) v else null
        }
        if (values.isEmpty()) return

        val minVal = values.minOrNull()!!
        val maxVal = values.maxOrNull()!!

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            val value = (if (type == "speed") p1.speed else p1.alt)

            if (value >= 0) {
                val polyline = Polyline()
                polyline.addPoint(GeoPoint(p1.lat, p1.lon))
                polyline.addPoint(GeoPoint(p2.lat, p2.lon))
                polyline.color = getColorForValue(value, minVal, maxVal)
                polyline.width = if (p1.mode.equals("superspeed", ignoreCase = true)) 10f else 5f
                map.overlays.add(polyline)
            }
        }

        buildGradientLegend(minVal, maxVal, type)
    }

    private fun getColorForValue(value: Double, min: Double, max: Double): Int {
        val range = max - min
        val normalized = if (range > 0) (value - min) / range else 0.0
        // Hue from Blue (240) to Red (0)
        val hue = (240 * (1 - normalized)).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 1.0f, 1.0f))
    }

    private fun buildGradientLegend(min: Double, max: Double, type: String) {
        val unit = if (type == "speed") getString(R.string.unit_speed_kmh) else getString(R.string.unit_alt_m)
        findViewById<TextView>(R.id.textLegendTitle).text = getString(R.string.history_legend)
        findViewById<TextView>(R.id.textLegendHint).visibility = View.GONE

        val gradientView = View(this)
        val colors = IntArray(100) {
            getColorForValue(min + (max - min) * it / 99.0, min, max)
        }
        val gradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
        gradient.cornerRadius = resources.getDimension(R.dimen.history_legend_gradient_radius)
        gradientView.background = gradient
        val legendHeight = resources.getDimensionPixelSize(R.dimen.history_legend_gradient_height)
        gradientView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, legendHeight).apply {
            topMargin = 8
        }

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val colorOnSurfaceVariant = MaterialColors.getColor(gradientView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val minLabel = TextView(this).apply {
            text = "%.1f %s".format(min, unit)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(colorOnSurfaceVariant)
            textSize = 12f
        }
        val maxLabel = TextView(this).apply {
            text = "%.1f %s".format(max, unit)
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(colorOnSurfaceVariant)
            textSize = 12f
        }
        labels.addView(minLabel)
        labels.addView(maxLabel)

        legendContainer.addView(gradientView)
        legendContainer.addView(labels)
    }

    private fun buildSegments(points: List<Pt>): List<Triple<List<GeoPoint>, String, String>> {
        var last: Pt? = null
        val current = mutableListOf<GeoPoint>()
        var currentTrack: String? = null
        var currentMode: String? = null
        var currentSid: String? = null
        val segments = mutableListOf<Triple<List<GeoPoint>, String, String>>() // pts, track, mode
        for (p in points) {
            if (last == null) {
                current.clear()
                current += GeoPoint(p.lat, p.lon)
                currentTrack = p.track
                currentMode = p.mode
                currentSid = p.sid
            } else {
                val changed = (p.track != currentTrack) || (p.mode != currentMode) || (p.sid != currentSid)
                if (changed) {
                    if (current.size >= 2 && currentTrack != null && currentMode != null) {
                        segments += Triple(current.toList(), currentTrack!!, currentMode!!)
                    }
                    current.clear()
                    currentTrack = p.track
                    currentMode = p.mode
                    currentSid = p.sid
                }
                current += GeoPoint(p.lat, p.lon)
            }
            last = p
        }
        if (current.size >= 2 && currentTrack != null && currentMode != null) {
            segments += Triple(current.toList(), currentTrack!!, currentMode!!)
        }
        return segments
    }

    private fun buildLegendRow(track: String, color: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        val swatch = View(this).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(32, 16).apply { setMargins(0, 4, 12, 4) }
        }
        val label = TextView(this).apply { text = track }
        row.addView(swatch)
        row.addView(label)
        return row
    }
}
