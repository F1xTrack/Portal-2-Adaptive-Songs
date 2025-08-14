package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject
import android.location.Location

class AchievementsActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AchievementsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)
        supportActionBar?.title = getString(R.string.achievements_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recyclerAchievements)
        recycler.layoutManager = LinearLayoutManager(this)

        val achievements = AchievementRepository(this).getAll()
        adapter = AchievementsAdapter(this, achievements)
        recycler.adapter = adapter

        calculateAndDisplayStats()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun calculateAndDisplayStats() {
        val textStatDistance: TextView = findViewById(R.id.textStatDistance)
        val textStatTracks: TextView = findViewById(R.id.textStatTracks)
        val textStatSuperspeedTime: TextView = findViewById(R.id.textStatSuperspeedTime)

        val raw = RouteRecorder.readAll(this)
        if (raw.isEmpty()) {
            textStatDistance.text = "0.00 km"
            textStatTracks.text = "0"
            textStatSuperspeedTime.text = "0 min"
            return
        }

        val points = raw.mapNotNull { toPt(it) }.sortedBy { it.t }

        // Calculate total distance
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            val results = FloatArray(1)
            Location.distanceBetween(p1.lat, p1.lon, p2.lat, p2.lon, results)
            totalDistance += results[0]
        }
        textStatDistance.text = String.format("%.2f km", totalDistance / 1000.0)

        // Calculate unique tracks
        val uniqueTracks = points.map { it.track }.filter { it.isNotEmpty() }.toSet()
        textStatTracks.text = uniqueTracks.size.toString()

        // Calculate superspeed time
        var superspeedMillis = 0L
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            if (p1.superspeed) {
                superspeedMillis += (p2.t - p1.t)
            }
        }
        val superspeedMinutes = superspeedMillis / 1000 / 60
        textStatSuperspeedTime.text = "$superspeedMinutes min"
    }

    private fun toPt(obj: JSONObject): Pt? {
        return try {
            Pt(
                obj.getDouble("lat"),
                obj.getDouble("lon"),
                obj.getLong("t"),
                obj.optString("track", ""),
                obj.optBoolean("superspeed", false),
                obj.optDouble("speed", 0.0),
                obj.optDouble("alt", 0.0)
            )
        } catch (_: Exception) {
            null
        }
    }
}

data class Pt(
    val lat: Double,
    val lon: Double,
    val t: Long,
    val track: String,
    val superspeed: Boolean,
    val speed: Double,
    val alt: Double
)

data class Achievement(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val spriteRow: Int,
    val spriteCol: Int,
    val current: Int,
    val target: Int
)

class AchievementsAdapter(private val context: Context, private var items: List<Achievement>) : RecyclerView.Adapter<AchievementsAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.iconAchievement)
        val title: TextView = v.findViewById(R.id.textAchievementTitle)
        val desc: TextView = v.findViewById(R.id.textAchievementDesc)
        val progress: ProgressBar = v.findViewById(R.id.progressAchievement)
        val counter: TextView = v.findViewById(R.id.textAchievementCounter)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_achievement_card, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        val bmp = AchievementsSprites.getSprite(context, a.spriteRow, a.spriteCol)
        if (bmp != null) {
            holder.icon.setImageBitmap(bmp)
            holder.icon.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            holder.icon.setImageResource(R.drawable.aperture_logo)
        }
        holder.title.text = context.getString(a.titleRes)
        holder.desc.text = context.getString(a.descriptionRes)
        val pct = (100 * a.current) / (if (a.target == 0) 1 else a.target)
        holder.progress.progress = pct.coerceIn(0, 100)
        holder.counter.text = "${a.current}/${a.target}"
    }
}

object AchievementsSprites {
    private var sheet: Bitmap? = null
    private const val ROWS = 3
    private const val COLS = 3

    private fun ensureSheet(context: Context) {
        if (sheet == null) {
            try {
                sheet = BitmapFactory.decodeResource(context.resources, R.drawable.achievements_icons)
            } catch (_: Exception) {
                sheet = null
            }
        }
    }

    fun getSprite(context: Context, row: Int, col: Int): Bitmap? {
        ensureSheet(context)
        val s = sheet ?: return null
        if (row !in 0 until ROWS || col !in 0 until COLS) return null
        val tileW = s.width / COLS
        val tileH = s.height / ROWS
        val x = col * tileW
        val y = row * tileH
        return try { Bitmap.createBitmap(s, x, y, tileW, tileH) } catch (_: Exception) { null }
    }
}

class AchievementRepository(private val ctx: Context) {
    private val statsPrefs = ctx.getSharedPreferences("track_stats", AppCompatActivity.MODE_PRIVATE)
    
    // Helpers to count valid tracks in assets and user storage
    private fun assetFolderHasAnyVariant(folder: String, base: String): Boolean {
        return try {
            val files = ctx.assets.list(folder) ?: return false
            val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
            files.any { regex.matches(it) }
        } catch (_: Exception) { false }
    }
    private fun userFolderHasAnyVariant(dir: java.io.File, base: String): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return dir.listFiles()?.any { f -> regex.matches(f.name) } == true
    }

    fun getAll(): List<Achievement> {
        val km = getDistanceKm()
        val totalTracks = getImportedCount()
        val superMinutes = getSuperSpeedMinutes()
        return listOf(
            Achievement("km_1", R.string.ach_km_1_title, R.string.ach_km_1_desc, 0, 0, km, 1),
            Achievement("km_10", R.string.ach_km_10_title, R.string.ach_km_10_desc, 0, 1, km, 10),
            Achievement("km_100", R.string.ach_km_100_title, R.string.ach_km_100_desc, 0, 2, km, 100),
            Achievement("lib_1", R.string.ach_lib_1_title, R.string.ach_lib_1_desc, 1, 0, totalTracks, 1),
            Achievement("lib_10", R.string.ach_lib_10_title, R.string.ach_lib_10_desc, 1, 1, totalTracks, 10),
            Achievement("lib_100", R.string.ach_lib_100_title, R.string.ach_lib_100_desc, 1, 2, totalTracks, 100),
            Achievement("sup_10m", R.string.ach_sup_10m_title, R.string.ach_sup_10m_desc, 2, 0, superMinutes, 10),
            Achievement("sup_60m", R.string.ach_sup_60m_title, R.string.ach_sup_60m_desc, 2, 1, superMinutes, 60),
            Achievement("sup_600m", R.string.ach_sup_600m_title, R.string.ach_sup_600m_desc, 2, 2, superMinutes, 600),
        )
    }

    private fun getDistanceKm(): Int {
        return ctx.getSharedPreferences("achievements", AppCompatActivity.MODE_PRIVATE)
            .getInt("distance_km", 0)
    }

    private fun getImportedCount(): Int {
        // Count built-in asset tracks that have both normal and superspeed variants
        val assetTracks = ctx.assets.list("")?.filter { name ->
            assetFolderHasAnyVariant(name, "normal") && assetFolderHasAnyVariant(name, "superspeed")
        } ?: emptyList()
        // Count user tracks that have both variants
        val userDir = java.io.File(ctx.filesDir, "soundtracks")
        val userCount = userDir.listFiles()?.count { dir ->
            dir.isDirectory && userFolderHasAnyVariant(dir, "normal") && userFolderHasAnyVariant(dir, "superspeed")
        } ?: 0
        return assetTracks.size + userCount
    }

    private fun getSuperSpeedMinutes(): Int {
        return ctx.getSharedPreferences("achievements", AppCompatActivity.MODE_PRIVATE)
            .getInt("superspeed_minutes", 0)
    }

    fun addDistanceKm(delta: Int) {
        val prefs = ctx.getSharedPreferences("achievements", AppCompatActivity.MODE_PRIVATE)
        val v = prefs.getInt("distance_km", 0) + delta
        prefs.edit().putInt("distance_km", v).apply()
    }
    fun addSuperSpeedMinutes(delta: Int) {
        val prefs = ctx.getSharedPreferences("achievements", AppCompatActivity.MODE_PRIVATE)
        val v = prefs.getInt("superspeed_minutes", 0) + delta
        prefs.edit().putInt("superspeed_minutes", v).apply()
    }
}