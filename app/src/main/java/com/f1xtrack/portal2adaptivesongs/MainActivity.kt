package com.f1xtrack.portal2adaptivesongs

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.f1xtrack.portal2adaptivesongs.databinding.ActivityMainBinding
import android.app.ProgressDialog
import android.view.Menu
import android.view.MenuItem
import android.content.Intent
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.io.File
import android.provider.DocumentsContract
import android.media.MediaMetadataRetriever
import android.location.Location
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.GravityCompat
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import android.os.Handler
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import android.content.Context
import kotlin.random.Random
import androidx.core.widget.doOnTextChanged
import android.widget.TextView
import android.text.method.LinkMovementMethod
import androidx.core.text.HtmlCompat

class MainActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityMainBinding
    private lateinit var tracker: SpeedTracker
    internal lateinit var player: ExoSoundPlayer
    private var isSuperSpeed = false
    private var lastTrack: String? = null
    private val IMPORT_ZIP_REQUEST_CODE = 101
    private val IMPORT_PACK_REQUEST_CODE = 102
    internal var userTracks: List<String> = emptyList()
    private var hysteresis = 3f // Гистерезис для предотвращения мигания
    internal var selectedTrack: String? = null // Для landscape режима
    private val PREFS_NAME = "track_sort_prefs"
    private val PREF_SORT = "sort_type"
    private enum class SortType { ALPHA, FREQ, DURATION }
    private var sortType: SortType = SortType.ALPHA
    private val trackPlayCount = mutableMapOf<String, Int>() // Для сортировки по частоте
    private val trackDuration = mutableMapOf<String, Int>() // Для сортировки по длительности
    private val PREFS_STATS = "track_stats"
    internal lateinit var tracksAdapter: TracksAdapter
    private var superSpeedTimer: Handler? = null
    private var distanceMetersAccum = 0f
    private var timeAttackManager: TimeAttackManager? = null
    private var routeRecorder: RouteRecorder? = null
    private var searchQuery: String = ""

    private fun getHiddenAssets(): Set<String> {
        val prefs = getSharedPreferences("storage_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("hidden_assets", emptySet()) ?: emptySet()
    }

    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        val appName = getString(R.string.app_name)
        val github = getString(R.string.about_github_url)
        val donate = getString(R.string.about_donate_url)
        val html = getString(R.string.about_message_html, appName, version, github, donate)
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.about_title)
            .setMessage(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.action_got_it, null)
            .show()
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }

    // Одноразовая миграция: переносит скрытые треки со старых имён папок [PSM]/[Rev]
    // на новые официальные названия OST, чтобы настройки пользователя сохранились
    private fun migrateHiddenAssetsIfNeeded() {
        val prefs = getSharedPreferences("storage_prefs", Context.MODE_PRIVATE)
        val migratedFlag = "hidden_assets_migrated_psm_rev"
        if (prefs.getBoolean(migratedFlag, false)) return

        val current = prefs.getStringSet("hidden_assets", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.isEmpty()) {
            prefs.edit().putBoolean(migratedFlag, true).apply()
            return
        }

        // Маппинг старое имя папки -> новое официальное название трека
        val map = mapOf(
            // Portal Stories: Mel
            "[PSM] sp_a3_concepts" to "Welcome To The Future",
            "[PSM] sp_a3_faith_plate" to "Long Way Down",
            "[PSM] sp_a3_paint_fling" to "Obscuration",
            "[PSM] sp_a3_transition" to "Transitional Period",
            "[PSM] sp_a4_destroyed" to "Natural Light",
            "[PSM] sp_a4_overgrown" to "Troubled Water",
            "[PSM] sp_a4_tb_over_goo" to "Waking Up to Science",
            "[PSM] sp_a4_two_of_a_kind" to "Testing with Nature",
            "[PSM] Финал" to "Live Fire Exercise",

            // Portal: Revolution
            "[Rev] sp_a1_catapult_intro" to "Arbitrary Directions",
            "[Rev] sp_a1_cube_fling_intro_ch2" to "Music Cube",
            "[Rev] sp_a1_final_test_ch2_a0" to "Hard Light Bridge",
            "[Rev] sp_a3_vactube_gelbending" to "Almost Laminar",
            "[Rev] sp_a3_vactube_hanging" to "Almost Laminar 2",
            "[Rev] sp_a3_vactube_pipes" to "Almost Laminar 3",
            "[Rev] sp_a4_finale" to "Shutting Down the Spire"
        )

        var changed = false
        for ((oldName, newName) in map) {
            if (current.remove(oldName)) {
                current.add(newName)
                changed = true
            }
        }

        val editor = prefs.edit().putBoolean(migratedFlag, true)
        if (changed) editor.putStringSet("hidden_assets", current)
        editor.apply()
    }

    /**
     * Одноразовая миграция для переименований Portal 2:
     * - sp_a2_ricochet -> I Saw a Deer Today
     * - sp_a2_trust_fling -> 15 Acres of Broken Glass
     * - Побег -> An Accent Beyond
     * Используем отдельный флаг, чтобы не зависеть от предыдущей миграции PSM/Rev.
     */
    private fun migrateHiddenAssetsPortal2IfNeeded() {
        val prefs = getSharedPreferences("storage_prefs", Context.MODE_PRIVATE)
        val migratedFlag = "hidden_assets_migrated_portal2"
        if (prefs.getBoolean(migratedFlag, false)) return

        val current = prefs.getStringSet("hidden_assets", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.isEmpty()) {
            prefs.edit().putBoolean(migratedFlag, true).apply()
            return
        }

        val map = mapOf(
            "sp_a2_ricochet" to "I Saw a Deer Today",
            "sp_a2_trust_fling" to "15 Acres of Broken Glass",
            "Побег" to "An Accent Beyond"
        )

        var changed = false
        for ((oldName, newName) in map) {
            if (current.remove(oldName)) {
                current.add(newName)
                changed = true
            }
        }

        val editor = prefs.edit().putBoolean(migratedFlag, true)
        if (changed) editor.putStringSet("hidden_assets", current)
        editor.apply()
    }

    private fun assetFolderHasAnyVariant(folder: String, base: String): Boolean {
        return try {
            val files = assets.list(folder) ?: return false
            val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
            files.any { regex.matches(it) }
        } catch (_: Exception) { false }
    }

    private fun userFolderHasAnyVariant(dir: File, base: String): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return dir.listFiles()?.any { file -> regex.matches(file.name) } == true
    }

    private fun findFirstVariantPath(track: String, base: String, isUser: Boolean): File? {
        val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return try {
            if (isUser) {
                val dir = File(filesDir, "soundtracks/$track")
                val candidate = dir.listFiles()?.firstOrNull { f -> regex.matches(f.name) }
                candidate
            } else {
                val files = assets.list(track) ?: return null
                val candidate = files.firstOrNull { name -> regex.matches(name) }
                if (candidate != null) {
                    val tmp = File(cacheDir, "tmp_${track}_${candidate}")
                    if (!tmp.exists()) {
                        assets.open("${track}/${candidate}").use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    tmp
                } else null
            }
        } catch (_: Exception) { null }
    }

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) startTracking()
            else Toast.makeText(this, "Разрешите местоположение", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Применяем тему до создания вью
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)

        // Онбординг: показываем один раз при первом запуске
        run {
            val ob = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
            val completed = ob.getBoolean("onboarding_completed", false)
            if (!completed) {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Мигрируем скрытые треки после переименования папок [PSM]/[Rev]
        migrateHiddenAssetsIfNeeded()
        // И дополнительная миграция для переименований Portal 2
        migrateHiddenAssetsPortal2IfNeeded()

        // Тулбара больше нет; дровер открывается свайпом от левого края
        val navigationView = binding.root.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
        navigationView?.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Уже на главном экране
                }
                R.id.nav_import_zip -> {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "application/zip"
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    startActivityForResult(intent, IMPORT_ZIP_REQUEST_CODE)
                }
                R.id.nav_import_folder -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    startActivityForResult(intent, IMPORT_PACK_REQUEST_CODE)
                }
                R.id.nav_track_prefs -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_storage -> startActivity(Intent(this, StorageActivity::class.java))
                R.id.nav_history -> startActivity(Intent(this, HistoryActivity::class.java))
                R.id.nav_achievements -> startActivity(Intent(this, AchievementsActivity::class.java))
                R.id.nav_language -> {
                    showLanguageDialog()
                }
                R.id.nav_theme -> {
                    showThemeDialog()
                }
                R.id.nav_time_attack_settings -> {
                    startActivity(Intent(this, TimeAttackSettingsActivity::class.java))
                }
                R.id.nav_route_settings -> {
                    startActivity(Intent(this, RouteSettingsActivity::class.java))
                }
                R.id.nav_about -> {
                    showAboutDialog()
                }
            }
            val v: View? = binding.root.findViewById(R.id.drawerLayout)
            if (v is DrawerLayout) v.closeDrawer(GravityCompat.START)
            true
        }
        // Подсветить пункт «Главное меню» как активный на главном экране
        navigationView?.setCheckedItem(R.id.nav_home)

        player = ExoSoundPlayer(this)
        routeRecorder = RouteRecorder(this)

        // Функция для получения текущего выбранного трека
        fun getCurrentTrack(): String {
            return selectedTrack ?: ""
        }

        // Инициализация трекера: убираем моментальный переход в onSpeedBurst,
        // оставляем только гистерезисную логику ниже
        tracker = SpeedTracker(this, { speed ->
            runOnUiThread {
                Log.d("MainActivity", "Triggered speed=$speed")
                // Раньше здесь был мгновенный переход в супер-режим — убран
            }
        }, { speed ->
            runOnUiThread {
                val threshold = tracker.getThreshold()
                val track = selectedTrack ?: ""
                val isUser = userTracks.contains(track)
                if (lastTrack != track) {
                    isSuperSpeed = false
                    lastTrack = track
                    player.crossfadeTo(track, false, isUser)
                }
                if (!isSuperSpeed && speed >= threshold) {
                    isSuperSpeed = true
                    player.crossfadeTo(track, true, isUser)
                    startSuperSpeedTimer()
                } else if (isSuperSpeed && speed < (threshold - hysteresis)) {
                    isSuperSpeed = false
                    player.crossfadeTo(track, false, isUser)
                }
                // Обновляем логику тайм-атаки текущей скоростью
                timeAttackManager?.onSpeed(speed)
                // График скорости (отключено)
                // binding.speedGraph?.addValue(speed)
            }
        }, { deltaMeters ->
            distanceMetersAccum += deltaMeters
            if (distanceMetersAccum >= 1000f) {
                val km = (distanceMetersAccum / 1000f).toInt()
                reportDistanceKmDelta(km)
                distanceMetersAccum -= km * 1000f
            }
        }, { loc: Location ->
            // Подаём данные в запись маршрута и граф высоты
            val speedKmh = loc.speed * 3.6f
            val mode = if (isSuperSpeed) "superspeed" else "normal"
            val track = selectedTrack ?: ""
            routeRecorder?.addPoint(loc, track, mode, speedKmh)
            runOnUiThread {
                val alt = if (loc.hasAltitude()) loc.altitude.toFloat() else 0f
                // binding.elevationGraph?.addValue(alt) // График высоты (отключено)
            }
        })

        tracker.setThreshold(binding.seekBurst.value)
        binding.textSpeed.text = getString(R.string.faith_plate_threshold, binding.seekBurst.value.toInt())
        // Устанавливаем hysteresis из seekCooldown (Slider)
        hysteresis = binding.seekCooldown.value
        binding.textHysteresis.text = getString(R.string.hysteresis_value, binding.seekCooldown.value.toInt())

        binding.seekCooldown.addOnChangeListener { _, value, _ ->
            hysteresis = value
            binding.textHysteresis.text = getString(R.string.hysteresis_value, value.toInt())
        }

        binding.seekBurst.addOnChangeListener { _, value, _ ->
            tracker.setThreshold(value)
            binding.textSpeed.text = getString(R.string.faith_plate_threshold, value.toInt())
        }

        // Показываем подсказку про боковое меню (не более 2 раз за всё время)
        maybeShowDrawerHint()

        // --- Инициализация RecyclerView ---
        tracksAdapter = TracksAdapter(emptyList(), selectedTrack) { trackInfo ->
            // Если кликнули на тот же трек - ставим на паузу или возобновляем
            if (selectedTrack == trackInfo.name) {
                player.togglePause()
            } else {
                // Полностью останавливаем все потоки предыдущего трека
                player.releaseAll()
                stopSuperSpeedTimer()
                isSuperSpeed = false
                lastTrack = null

                selectedTrack = trackInfo.name
                tracksAdapter.updateData(getTrackInfoList(), selectedTrack)
                val isUser = userTracks.contains(trackInfo.name)
                player.playBoth(trackInfo.name, isUser)
                incTrackPlayCount(trackInfo.name)
            }
        }
        binding.recyclerTracks?.layoutManager = LinearLayoutManager(this)
        binding.recyclerTracks?.adapter = tracksAdapter

        // --- SegmentedButton для сортировки ---
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sortType = when (prefs.getString(PREF_SORT, "ALPHA")) {
            "FREQ" -> SortType.FREQ
            "DURATION" -> SortType.DURATION
            else -> SortType.ALPHA
        }

        when (sortType) {
            SortType.ALPHA -> binding.btnSortAlpha?.isChecked = true
            SortType.FREQ -> binding.btnSortFreq?.isChecked = true
            SortType.DURATION -> binding.btnSortDuration?.isChecked = true
        }

        binding.sortSegmented?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                sortType = when (checkedId) {
                    R.id.btnSortAlpha -> SortType.ALPHA
                    R.id.btnSortFreq -> SortType.FREQ
                    R.id.btnSortDuration -> SortType.DURATION
                    else -> SortType.ALPHA
                }
                prefs.edit().putString(PREF_SORT, sortType.name).apply()
                updateTracksList(selectedTrack)
            }
        }

        // Поиск по списку треков
        binding.editSearch?.doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString()?.trim().orEmpty()
            updateTracksList(selectedTrack)
        }

        // Переключатель режимов просмотра (удалён из UI)

        // Запрос разрешений
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        // --- Загрузка статистики ---
        val statsPrefs = getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)
        statsPrefs.getStringSet("play_counts", null)?.forEach {
            val (track, count) = it.split("|", limit = 2)
            trackPlayCount[track] = count.toIntOrNull() ?: 0
        }
        statsPrefs.getStringSet("durations", null)?.forEach {
            val (track, dur) = it.split("|", limit = 2)
            trackDuration[track] = dur.toIntOrNull() ?: 0
        }
        updateTracksList(selectedTrack)

        // Инициализация менеджера тайм-атаки
        timeAttackManager = TimeAttackManager(
            host = this,
            onStartRandomTrack = { startRandomTrackForTimeAttack() },
            thresholdProvider = { tracker.getThreshold().toInt() }
        )

        // Запланировать случайный запуск тайм-атаки не ранее чем через 10 минут
        timeAttackManager?.scheduleRandomAfterMinutes(minMinutes = 10)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Не показываем пункт "Настройки"
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_ZIP_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            showNameInputDialog(uri)
        } else if (requestCode == IMPORT_PACK_REQUEST_CODE && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            importPackFromFolder(treeUri)
        }
    }

    private fun showNameInputDialog(zipUri: Uri) {
        val input = android.widget.EditText(this)
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Введите название саундтрека")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    importZipToSoundtracks(zipUri, name)
                } else {
                    Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun importZipToSoundtracks(zipUri: Uri, name: String) {
        val dialog = ProgressDialog(this)
        dialog.setMessage("Импортируем саундтрек...")
        dialog.setCancelable(false)
        dialog.show()
        Thread {
            try {
                val dir = File(filesDir, "soundtracks/$name")
                dir.mkdirs()
                val inputStream = contentResolver.openInputStream(zipUri) ?: throw Exception("Не удалось открыть ZIP")
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                var foundNormal = false
                var foundSuper = false
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = when (entry.name) {
                            "normal.wav" -> File(dir, "normal.wav").also { foundNormal = true }
                            "superspeed.wav" -> File(dir, "superspeed.wav").also { foundSuper = true }
                            else -> null
                        }
                        if (outFile != null) {
                            FileOutputStream(outFile).use { out ->
                                zis.copyTo(out)
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
                zis.close()
                inputStream.close()
                runOnUiThread {
                    dialog.dismiss()
                    if (foundNormal && foundSuper) {
                        Toast.makeText(this, "Саундтрек импортирован!", Toast.LENGTH_SHORT).show()
                        updateTracksList(name)
                    } else {
                        dir.deleteRecursively()
                        Toast.makeText(this, "В ZIP должны быть normal.wav и superspeed.wav", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun getSortedTracks(assetTracks: List<String>, userTracks: List<String>): List<String> {
        val all = assetTracks + userTracks
        return when (sortType) {
            SortType.ALPHA -> all.sortedBy { it.lowercase() }
            SortType.FREQ -> all.sortedByDescending { trackPlayCount[it] ?: 0 }
            SortType.DURATION -> all.sortedByDescending { trackDuration[it] ?: 0 }
        }
    }

    internal fun getTrackInfoList(): List<TracksAdapter.TrackInfo> {
        val hidden = getHiddenAssets()
        val assetTracks = assets.list("")?.filter { name ->
            name !in hidden && assetFolderHasAnyVariant(name, "normal") && assetFolderHasAnyVariant(name, "superspeed")
        } ?: emptyList()
        val userDir = File(filesDir, "soundtracks")
        userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && userFolderHasAnyVariant(dir, "normal") && userFolderHasAnyVariant(dir, "superspeed")
        }?.map { it.name } ?: emptyList()
        val allTracks = getSortedTracks(assetTracks, userTracks)
        return allTracks.map { name ->
            TracksAdapter.TrackInfo(
                name = name,
                duration = trackDuration[name] ?: 0,
                plays = trackPlayCount[name] ?: 0
            )
        }
    }

    private fun updateTracksList(selectedTrack: String? = null) {
        // Стандартные треки из assets
        val hidden = getHiddenAssets()
        val assetTracks = assets.list("")?.filter { name ->
            name !in hidden && assetFolderHasAnyVariant(name, "normal") && assetFolderHasAnyVariant(name, "superspeed")
        } ?: emptyList()
        // Пользовательские треки из filesDir/soundtracks
        val userDir = File(filesDir, "soundtracks")
        userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && userFolderHasAnyVariant(dir, "normal") && userFolderHasAnyVariant(dir, "superspeed")
        }?.map { it.name } ?: emptyList()
        // --- Обновляем длительности ---
        (assetTracks + userTracks).forEach { track ->
            if (!trackDuration.containsKey(track)) {
                val file = if (userTracks.contains(track)) {
                    // Берём первую доступную вариацию normal*
                    findFirstVariantPath(track, "normal", isUser = true)
                } else {
                    // Берём первую доступную вариацию из assets, копируем во временный файл
                    findFirstVariantPath(track, "normal", isUser = false)
                }
                if (file != null) {
                    trackDuration[track] = getTrackDuration(file.absolutePath)
                } else {
                    trackDuration[track] = 0
                }
            }
        }
        saveStats()
        val all = getTrackInfoList()
        val filtered = if (searchQuery.isBlank()) all else all.filter { it.name.contains(searchQuery, ignoreCase = true) }
        tracksAdapter.updateData(filtered, selectedTrack)
    }

    private fun startTracking() {
        // Настройки GPS: использование сетей и интервал записи
        val gp = getSharedPreferences("gps_prefs", Context.MODE_PRIVATE)
        val useNet = gp.getBoolean("use_network_location", true)
        val intervalSec = gp.getInt("interval_sec", 2)
        tracker.setUseNetworkLocation(useNet)
        tracker.setUpdateIntervalSeconds(intervalSec)
        routeRecorder?.startSession()
        tracker.start()
        Toast.makeText(this, "Трекинг запущен", Toast.LENGTH_SHORT).show()
    }

    private fun importPackFromFolder(treeUri: Uri) {
        val dialog = ProgressDialog(this)
        dialog.setMessage("Импортируем пак саундтреков...")
        dialog.setCancelable(false)
        dialog.show()
        Thread {
            var imported = 0
            var failed = 0
            try {
                val children = contentResolver.query(
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null
                )
                if (children != null) {
                    while (children.moveToNext()) {
                        val name = children.getString(0)
                        val docId = children.getString(1)
                        val mime = children.getString(2)
                        if (mime == "application/zip" || name.endsWith(".zip", true)) {
                            val zipUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val trackName = name.removeSuffix(".zip").removeSuffix(".ZIP")
                            val result = importZipToSoundtracksSync(zipUri, trackName)
                            if (result) imported++ else failed++
                        }
                    }
                    children.close()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Ошибка импорта пака: ${e.message}", Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            runOnUiThread {
                dialog.dismiss()
                Toast.makeText(this, "Импортировано: $imported, ошибок: $failed", Toast.LENGTH_LONG).show()
                updateTracksList()
            }
        }.start()
    }

    // Синхронный импорт ZIP без диалогов, возвращает true/false
    private fun importZipToSoundtracksSync(zipUri: Uri, name: String): Boolean {
        return try {
            val dir = File(filesDir, "soundtracks/$name")
            dir.mkdirs()
            val inputStream = contentResolver.openInputStream(zipUri) ?: throw Exception("Не удалось открыть ZIP")
            val zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            var foundNormal = false
            var foundSuper = false
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = when (entry.name) {
                        "normal.wav" -> File(dir, "normal.wav").also { foundNormal = true }
                        "superspeed.wav" -> File(dir, "superspeed.wav").also { foundSuper = true }
                        else -> null
                    }
                    if (outFile != null) {
                        FileOutputStream(outFile).use { out ->
                            zis.copyTo(out)
                        }
                    }
                }
                entry = zis.nextEntry
            }
            zis.close()
            inputStream.close()
            if (!(foundNormal && foundSuper)) {
                dir.deleteRecursively()
                false
            } else true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveStats() {
        val statsPrefs = getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)
        val playCounts = trackPlayCount.map { "${it.key}|${it.value}" }.toSet()
        val durations = trackDuration.map { "${it.key}|${it.value}" }.toSet()
        statsPrefs.edit().putStringSet("play_counts", playCounts).putStringSet("durations", durations).apply()
    }

    private fun incTrackPlayCount(track: String) {
        trackPlayCount[track] = (trackPlayCount[track] ?: 0) + 1
        saveStats()
    }

    private fun getTrackDuration(path: String): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            dur?.toIntOrNull() ?: 0
        } catch (_: Exception) { 0 } finally { retriever.release() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::player.isInitialized) {
            player.releaseAll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::player.isInitialized) {
            player.releaseAll()
        }
        timeAttackManager?.dispose()
    }

    override fun onResume() {
        super.onResume()
        // Обновить список на случай изменений в Хранилище
        updateTracksList(selectedTrack)
        val taPrefs = getSharedPreferences("time_attack_prefs", Context.MODE_PRIVATE)
        if (taPrefs.getBoolean("start_now", false)) {
            taPrefs.edit().putBoolean("start_now", false).apply()
            timeAttackManager?.startNow()
        }
    }

    private fun startSuperSpeedTimer() {
        if (superSpeedTimer != null) return
        superSpeedTimer = Handler(mainLooper)
        val repo = AchievementRepository(this)
        val runnable = object : Runnable {
            override fun run() {
                if (isSuperSpeed) {
                    repo.addSuperSpeedMinutes(1)
                }
                superSpeedTimer?.postDelayed(this, 60_000L)
            }
        }
        superSpeedTimer?.postDelayed(runnable, 60_000L)
    }

    private fun stopSuperSpeedTimer() {
        superSpeedTimer?.removeCallbacksAndMessages(null)
        superSpeedTimer = null
    }

    private fun showAchievementBanner(title: String, desc: String, spriteRow: Int, spriteCol: Int) {
        val banner = findViewById<com.google.android.material.card.MaterialCardView>(R.id.bannerAchievement)
        val icon = findViewById<android.widget.ImageView>(R.id.bannerIcon)
        val t = findViewById<android.widget.TextView>(R.id.bannerTitle)
        val d = findViewById<android.widget.TextView>(R.id.bannerDesc)
        val bmp = AchievementsSprites.getSprite(this, spriteRow, spriteCol)
        if (bmp != null) icon.setImageBitmap(bmp)
        t.text = title
        d.text = desc
        banner.visibility = View.VISIBLE
        banner.translationY = -banner.height.toFloat()
        banner.alpha = 0f
        banner.animate().cancel()
        banner.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                banner.postDelayed({
                    banner.animate()
                        .translationY(-banner.height.toFloat())
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction { banner.visibility = View.GONE }
                        .start()
                }, 2000)
            }
            .start()
    }

    private fun onKmAchieved(totalKm: Int) {
        when (totalKm) {
            1 -> showAchievementBanner("1 км", "Пройдено 1 км", 0, 0)
            10 -> showAchievementBanner("10 км", "Пройдено 10 км", 0, 1)
            100 -> showAchievementBanner("100 км", "Пройдено 100 км", 0, 2)
        }
    }

    private fun onImportAchieved(total: Int) {
        when (total) {
            1 -> showAchievementBanner("1 трек", "Импортирован 1 трек", 1, 0)
            10 -> showAchievementBanner("10 треков", "Импортировано 10 треков", 1, 1)
            100 -> showAchievementBanner("100 треков", "Импортировано 100 треков", 1, 2)
        }
    }

    private fun onSuperMinutesAchieved(totalMin: Int) {
        when (totalMin) {
            10 -> showAchievementBanner(
                getString(R.string.achievement_super_10_title),
                getString(R.string.achievement_super_10_desc),
                2,
                0
            )
            60 -> showAchievementBanner(
                getString(R.string.achievement_super_60_title),
                getString(R.string.achievement_super_60_desc),
                2,
                1
            )
            600 -> showAchievementBanner(
                getString(R.string.achievement_super_600_title),
                getString(R.string.achievement_super_600_desc),
                2,
                2
            )
        }
    }

    // В местах, где мы обновляем значения — проверяем достижения и показываем баннер
    private fun reportDistanceKmDelta(km: Int) {
        val repo = AchievementRepository(this)
        val before = getSharedPreferences("achievements", Context.MODE_PRIVATE).getInt("distance_km", 0)
        repo.addDistanceKm(km)
        val after = getSharedPreferences("achievements", Context.MODE_PRIVATE).getInt("distance_km", 0)
        listOf(1, 10, 100).filter { it in (before + 1)..after }.forEach { onKmAchieved(it) }
    }

    private fun reportSuperMinutesDelta(min: Int) {
        val prefs = getSharedPreferences("achievements", Context.MODE_PRIVATE)
        val before = prefs.getInt("superspeed_minutes", 0)
        AchievementRepository(this).addSuperSpeedMinutes(min)
        val after = prefs.getInt("superspeed_minutes", 0)
        listOf(10, 60, 600).filter { it in (before + 1)..after }.forEach { onSuperMinutesAchieved(it) }
    }

    // В колбэке distanceMeters заменим на использование reportDistanceKmDelta
    // и в таймере superspeed — reportSuperMinutesDelta(1)
}

// -------------------------
// Дополнительные функции MainActivity
// -------------------------

private fun MainActivity.applyThemeFromPrefs() {
    val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    when (prefs.getString("app_theme", "portal2")) {
        "asi" -> setTheme(R.style.Theme_Portal_ASI)
        "portal2_overgrowth" -> setTheme(R.style.Theme_Portal2_Overgrowth)
        "portal1" -> setTheme(R.style.Theme_Portal1)
        else -> setTheme(R.style.Theme_Portal2AdaptiveSongs)
    }
    // Применяем сохранённый язык
    val lang = prefs.getString("app_lang", "system")
    val locales = if (lang == null || lang == "system" || lang.isBlank()) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(lang)
    }
    AppCompatDelegate.setApplicationLocales(locales)
}

private fun MainActivity.maybeShowDrawerHint() {
    val prefs = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
    val shown = prefs.getInt("drawer_hint_shown_count", 0)
    if (shown < 2) {
        Snackbar.make(binding.root, getString(R.string.hint_open_drawer), Snackbar.LENGTH_LONG)
            .setAction(R.string.action_got_it) {
                // no-op
            }
            .show()
        prefs.edit().putInt("drawer_hint_shown_count", shown + 1).apply()
    }
}

private fun MainActivity.showLanguageDialog() {
    val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    val languages = listOf(
        "system" to getString(R.string.language_system),
        "ar" to "العربية",
        "de" to "Deutsch",
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "hi" to "हिन्दी",
        "it" to "Italiano",
        "ja" to "日本語",
        "ko" to "한국어",
        "pl" to "Polski",
        "pt" to "Português",
        "ru" to "Русский",
        "tr" to "Türkçe",
        "zh" to "中文"
    )

    val options = languages.map { it.second }.toTypedArray()
    val currentLangCode = prefs.getString("app_lang", "system")
    val current = languages.indexOfFirst { it.first == currentLangCode }.coerceAtLeast(0)

    MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
        .setTitle(R.string.dialog_language_title)
        .setSingleChoiceItems(options, current) { dialog, which ->
            val code = languages[which].first
            prefs.edit().putString("app_lang", code).apply()
            val locales = if (code == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(code)
            }
            AppCompatDelegate.setApplicationLocales(locales)
            dialog.dismiss()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun MainActivity.showThemeDialog() {
    val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    val options = arrayOf(
        getString(R.string.theme_portal2_default),
        getString(R.string.theme_asi),
        getString(R.string.theme_portal2_overgrowth),
        getString(R.string.theme_portal1)
    )
    val current = when (prefs.getString("app_theme", "portal2")) {
        "asi" -> 1
        "portal2_overgrowth" -> 2
        "portal1" -> 3
        else -> 0
    }
    MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
        .setTitle(R.string.menu_theme)
        .setSingleChoiceItems(options, current) { dialog, which ->
            val key = when (which) {
                1 -> "asi"
                2 -> "portal2_overgrowth"
                3 -> "portal1"
                else -> "portal2"
            }
            prefs.edit().putString("app_theme", key).apply()
            dialog.dismiss()
            recreate()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun MainActivity.startRandomTrackForTimeAttack() {
    val all = getTrackInfoList().map { it.name }
    if (all.isEmpty()) return
    val name = all.random()
    val isUser = userTracks.contains(name)
    player.playBoth(name, isUser)
    selectedTrack = name
    tracksAdapter.updateData(getTrackInfoList(), selectedTrack)
}