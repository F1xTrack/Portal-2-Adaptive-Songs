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
import android.app.AlertDialog
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.io.File
import android.provider.DocumentsContract
import android.media.MediaMetadataRetriever
import androidx.recyclerview.widget.LinearLayoutManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var tracker: SpeedTracker
    private lateinit var player: SoundPlayer
    private var isSuperSpeed = false
    private var lastTrack: String? = null
    private val IMPORT_ZIP_REQUEST_CODE = 101
    private val IMPORT_PACK_REQUEST_CODE = 102
    private var userTracks: List<String> = emptyList()
    private var hysteresis = 3f // Гистерезис для предотвращения мигания
    private var selectedTrack: String? = null // Для landscape режима
    private val PREFS_NAME = "track_sort_prefs"
    private val PREF_SORT = "sort_type"
    private enum class SortType { ALPHA, FREQ, DURATION }
    private var sortType: SortType = SortType.ALPHA
    private val trackPlayCount = mutableMapOf<String, Int>() // Для сортировки по частоте
    private val trackDuration = mutableMapOf<String, Int>() // Для сортировки по длительности
    private val PREFS_STATS = "track_stats"
    private lateinit var tracksAdapter: TracksAdapter

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
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        player = SoundPlayer(this)

        // Функция для получения текущего выбранного трека
        fun getCurrentTrack(): String {
            return selectedTrack ?: ""
        }

        tracker = SpeedTracker(this, { speed ->
            runOnUiThread {
                Log.d("MainActivity", "Triggered speed=$speed")
                val track = getCurrentTrack()
                val isUser = userTracks.contains(track)
                player.crossfadeTo(track, true, isUser)
            }
        }, { speed ->
            runOnUiThread {
                val threshold = tracker.getThreshold()
                val track = getCurrentTrack()
                val isUser = userTracks.contains(track)
                if (lastTrack != track) {
                    // При смене трека всегда сбрасываем состояние
                    isSuperSpeed = false
                    lastTrack = track
                    player.crossfadeTo(track, false, isUser)
                }
                // --- Гистерезис: включаем superspeed при speed >= threshold, выключаем при speed < (threshold - hysteresis) ---
                if (!isSuperSpeed && speed >= threshold) {
                    isSuperSpeed = true
                    player.crossfadeTo(track, true, isUser)
                } else if (isSuperSpeed && speed < (threshold - hysteresis)) {
                    isSuperSpeed = false
                    player.crossfadeTo(track, false, isUser)
                }
            }
        })
        // Устанавливаем threshold из seekBurst (Slider)
        tracker.setThreshold(binding.seekBurst.value)
        binding.textSpeed.text = "Порог Faith Plate: ${binding.seekBurst.value.toInt()} км/ч"
        // Устанавливаем hysteresis из seekCooldown (Slider)
        hysteresis = binding.seekCooldown.value
        binding.textHysteresis.text = "Гистерезис: ${binding.seekCooldown.value.toInt()} км/ч"

        binding.seekCooldown.addOnChangeListener { _, value, _ ->
            hysteresis = value
            binding.textHysteresis.text = "Гистерезис: ${value.toInt()} км/ч"
        }

        binding.seekBurst.addOnChangeListener { _, value, _ ->
            tracker.setThreshold(value)
            binding.textSpeed.text = "Порог Faith Plate: ${value.toInt()} км/ч"
        }

        // --- Инициализация RecyclerView ---
        tracksAdapter = TracksAdapter(emptyList(), selectedTrack) { trackInfo ->
            selectedTrack = trackInfo.name
            tracksAdapter.updateData(getTrackInfoList(), selectedTrack)
            val isUser = userTracks.contains(trackInfo.name)
            player.playBoth(trackInfo.name, isUser)
            incTrackPlayCount(trackInfo.name)
        }
        binding.recyclerTracks?.layoutManager = LinearLayoutManager(this)
        binding.recyclerTracks?.adapter = tracksAdapter

        // --- SegmentedButton для сортировки ---
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
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

        // Запрос разрешений
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        // --- Загрузка статистики ---
        val statsPrefs = getSharedPreferences(PREFS_STATS, MODE_PRIVATE)
        statsPrefs.getStringSet("play_counts", null)?.forEach {
            val (track, count) = it.split("|", limit = 2)
            trackPlayCount[track] = count.toIntOrNull() ?: 0
        }
        statsPrefs.getStringSet("durations", null)?.forEach {
            val (track, dur) = it.split("|", limit = 2)
            trackDuration[track] = dur.toIntOrNull() ?: 0
        }
        updateTracksList(selectedTrack)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
        AlertDialog.Builder(this)
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

    private fun getTrackInfoList(): List<TracksAdapter.TrackInfo> {
        val assetTracks = assets.list("")?.filter { name ->
            assetFolderHasAnyVariant(name, "normal") && assetFolderHasAnyVariant(name, "superspeed")
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
        val assetTracks = assets.list("")?.filter { name ->
            assetFolderHasAnyVariant(name, "normal") && assetFolderHasAnyVariant(name, "superspeed")
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
        tracksAdapter.updateData(getTrackInfoList(), selectedTrack)
    }

    private fun startTracking() {
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
        val statsPrefs = getSharedPreferences(PREFS_STATS, MODE_PRIVATE)
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
        player.releaseAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.releaseAll()
    }
}