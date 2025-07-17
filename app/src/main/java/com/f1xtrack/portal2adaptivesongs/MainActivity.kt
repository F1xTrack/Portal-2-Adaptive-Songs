package com.f1xtrack.portal2adaptivesongs

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.f1xtrack.portal2adaptivesongs.databinding.ActivityMainBinding
import android.app.ProgressDialog
import android.view.Menu
import android.view.MenuItem
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.app.AlertDialog
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.io.File
import android.provider.DocumentsContract
import android.media.MediaMetadataRetriever
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * Главная активность приложения Portal 2 Adaptive Songs
 * 
 * Основные функции:
 * - Отслеживание скорости движения пользователя через GPS
 * - Адаптивное воспроизведение музыки в зависимости от скорости
 * - Управление списком треков с различными режимами сортировки
 * - Визуализация спектра музыки в реальном времени
 * - Интеграция с настройками приложения
 */
class MainActivity : AppCompatActivity() {
    
    // View Binding для доступа к элементам интерфейса
    private lateinit var binding: ActivityMainBinding
    
    // Основные компоненты приложения
    private lateinit var tracker: SpeedTracker      // Отслеживание скорости движения
    private lateinit var player: SoundPlayer        // Воспроизведение звука
    private lateinit var tracksAdapter: TracksAdapter // Адаптер для списка треков
    
    // Состояние воспроизведения
    private var isSuperSpeed = false    // Флаг высокоскоростного режима
    private var lastTrack: String? = null // Последний воспроизведенный трек
    private var selectedTrack: String? = null // Выбранный трек (для landscape режима)
    
    // Константы для запросов разрешений
    private val IMPORT_ZIP_REQUEST_CODE = 101
    private val IMPORT_PACK_REQUEST_CODE = 102
    
    // Список пользовательских треков
    private var userTracks: List<String> = emptyList()
    
    // Настройки гистерезиса для предотвращения мигания между режимами
    private var hysteresis = 3f
    
    // Настройки сортировки треков
    private val PREFS_NAME = "track_sort_prefs"
    private val PREF_SORT = "sort_type"
    private enum class SortType { ALPHA, FREQ, DURATION } // Типы сортировки: по алфавиту, частоте, длительности
    private var sortType: SortType = SortType.ALPHA
    
    // Статистика воспроизведения треков
    private val trackPlayCount = mutableMapOf<String, Int>() // Количество воспроизведений каждого трека
    private val trackDuration = mutableMapOf<String, Int>()  // Длительность каждого трека
    private val PREFS_STATS = "track_stats"
    
    /**
     * Обработчик запроса разрешений на местоположение
     * Запускается при первом запуске приложения
     */
    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) startTracking()
            else Toast.makeText(this, "Разрешите местоположение", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка панели инструментов
        setSupportActionBar(binding.topAppBar)

        // Инициализация плеера
        player = SoundPlayer(this)
        
        // Настройка визуализации спектра музыки
        player.setSpectrumListener { amplitudes ->
            runOnUiThread {
                // Обрезаем/нормализуем массив до 64 значений для плавности отображения
                val n = 64
                val arr = if (amplitudes.size > n) {
                    FloatArray(n) { i -> amplitudes[i * amplitudes.size / n].coerceIn(0f, 1f) }
                } else amplitudes.map { it.coerceIn(0f, 1f) }.toFloatArray()
                binding.spectrumView?.updateSpectrum(arr)
            }
        }

        /**
         * Функция для получения текущего выбранного трека
         * Используется в колбэках отслеживания скорости
         */
        fun getCurrentTrack(): String {
            return selectedTrack ?: ""
        }

        // Инициализация отслеживания скорости
        tracker = SpeedTracker(this, 
            // Колбэк при превышении порога скорости (включение superspeed)
            { speed ->
                runOnUiThread {
                    Log.d("MainActivity", "Triggered speed=$speed")
                    val track = getCurrentTrack()
                    val isUser = userTracks.contains(track)
                    player.crossfadeTo(track, true, isUser) // Переключение на быструю версию
                }
            }, 
            // Колбэк при изменении скорости (основная логика)
            { speed ->
                runOnUiThread {
                    val threshold = tracker.getThreshold()
                    val track = getCurrentTrack()
                    val isUser = userTracks.contains(track)
                    
                    // При смене трека всегда сбрасываем состояние
                    if (lastTrack != track) {
                        isSuperSpeed = false
                        lastTrack = track
                        player.crossfadeTo(track, false, isUser)
                    }
                    
                    // Логика гистерезиса: предотвращает мигание между режимами
                    // Включаем superspeed при speed >= threshold
                    // Выключаем при speed < (threshold - hysteresis)
                    if (!isSuperSpeed && speed >= threshold) {
                        isSuperSpeed = true
                        player.crossfadeTo(track, true, isUser)
                    } else if (isSuperSpeed && speed < (threshold - hysteresis)) {
                        isSuperSpeed = false
                        player.crossfadeTo(track, false, isUser)
                    }
                }
            }
        )
        
        // Настройка UI элементов управления
        setupUI()
        
        // Инициализация списка треков
        setupTracksList()
        
        // Настройка сортировки
        setupSorting()
        
        // Запрос разрешений на местоположение
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        // Загрузка статистики воспроизведения
        loadStats()
        
        // Обновление списка треков после всей инициализации
        updateTracksList(selectedTrack)
    }

    /**
     * Настройка элементов пользовательского интерфейса
     */
    private fun setupUI() {
        // Устанавливаем начальные значения из слайдеров
        tracker.setThreshold(binding.seekBurst.value)
        binding.textSpeed.text = "Порог Faith Plate: ${binding.seekBurst.value.toInt()} км/ч"
        
        hysteresis = binding.seekCooldown.value
        binding.seekCooldown.setLabelFormatter { value -> "Гистерезис: ${value.toInt()} км/ч" }
        
        // Слушатели изменений слайдеров
        binding.seekCooldown.addOnChangeListener { slider, value, fromUser ->
            hysteresis = value
            binding.textHysteresis.text = "Гистерезис: ${value.toInt()} км/ч"
        }
        
        binding.seekBurst.addOnChangeListener { slider, value, fromUser ->
            tracker.setThreshold(value)
            binding.textSpeed.text = "Порог Faith Plate: ${value.toInt()} км/ч"
        }
        
        // Инициализация подписи гистерезиса
        binding.textHysteresis.text = "Гистерезис: ${binding.seekCooldown.value.toInt()} км/ч"
    }

    /**
     * Настройка списка треков с RecyclerView
     */
    private fun setupTracksList() {
        tracksAdapter = TracksAdapter(emptyList(), selectedTrack) { trackInfo ->
            selectedTrack = trackInfo.name
            tracksAdapter.updateData(getTrackInfoList(), selectedTrack)
            val isUser = userTracks.contains(trackInfo.name)
            
            // Проверяем наличие предрендеренной спектрограммы
            val dir = if (isUser) File(filesDir, "soundtracks/${trackInfo.name}") else cacheDir
            val spectrumFile = File(dir, "normal.spectrum")
            
            if (spectrumFile.exists()) {
                // Используем предрендеренную спектрограмму
                player.playBoth(trackInfo.name, isUser)
                player.playSpectrumForTrack(trackInfo.name, isUser) { arr ->
                    runOnUiThread { binding.spectrumView?.updateSpectrum(arr) }
                }
            } else {
                // Fallback на Visualizer (старый способ)
                player.playBoth(trackInfo.name, isUser)
                player.setSpectrumListener { amplitudes ->
                    runOnUiThread {
                        val n = 64
                        val arr = if (amplitudes.size > n) {
                            FloatArray(n) { i -> amplitudes[i * amplitudes.size / n].coerceIn(0f, 1f) }
                        } else amplitudes.map { it.coerceIn(0f, 1f) }.toFloatArray()
                        binding.spectrumView?.updateSpectrum(arr)
                    }
                }
            }
            
            // Увеличиваем счетчик воспроизведений
            incTrackPlayCount(trackInfo.name)
        }
        
        binding.recyclerTracks?.layoutManager = LinearLayoutManager(this)
        binding.recyclerTracks?.adapter = tracksAdapter
    }

    /**
     * Настройка системы сортировки треков
     */
    private fun setupSorting() {
        // Загружаем сохраненный тип сортировки
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sortType = when (prefs.getString(PREF_SORT, "ALPHA")) {
            "FREQ" -> SortType.FREQ
            "DURATION" -> SortType.DURATION
            else -> SortType.ALPHA
        }
        
        // Настройка кнопок сортировки
        val btnAlpha = binding.btnSortAlpha
        val btnFreq = binding.btnSortFreq
        val btnDuration = binding.btnSortDuration
        
        // Выделяем выбранную кнопку
        when (sortType) {
            SortType.ALPHA -> btnAlpha?.isChecked = true
            SortType.FREQ -> btnFreq?.isChecked = true
            SortType.DURATION -> btnDuration?.isChecked = true
        }
        
        // Слушатель изменений сортировки
        binding.sortSegmented?.addOnButtonCheckedListener { group, checkedId, isChecked ->
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
    }

    /**
     * Создание меню приложения
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /**
     * Обработка выбора пунктов меню
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Обработка результатов активности (импорт файлов)
     */
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

    /**
     * Диалог для ввода названия импортируемого саундтрека
     */
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

    /**
     * Импорт ZIP-архива с саундтреком
     * Ожидает файлы normal.wav и superspeed.wav в архиве
     */
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
                
                // Извлекаем файлы из архива
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
                
                // Генерируем спектрограммы для нового трека
                player.generateSpectraForTrack(name, true)
                
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

    /**
     * Сортировка треков по выбранному критерию
     */
    private fun getSortedTracks(assetTracks: List<String>, userTracks: List<String>): List<String> {
        val all = assetTracks + userTracks
        return when (sortType) {
            SortType.ALPHA -> all.sortedBy { it.lowercase() }
            SortType.FREQ -> all.sortedByDescending { trackPlayCount[it] ?: 0 }
            SortType.DURATION -> all.sortedByDescending { trackDuration[it] ?: 0 }
        }
    }

    /**
     * Получение списка информации о треках для адаптера
     */
    private fun getTrackInfoList(): List<TracksAdapter.TrackInfo> {
        // Получаем стандартные треки из assets
        val assetTracks = assets.list("")!!.filter { name ->
            assets.list(name)?.contains("superspeed.wav") == true
        }
        
        // Получаем пользовательские треки
        val userDir = File(filesDir, "soundtracks")
        userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && File(dir, "normal.wav").exists() && File(dir, "superspeed.wav").exists()
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

    /**
     * Обновление списка треков в UI
     */
    private fun updateTracksList(selectedTrack: String? = null) {
        // Получаем стандартные треки из assets
        val assetTracks = assets.list("")!!.filter { name ->
            assets.list(name)?.contains("superspeed.wav") == true
        }
        
        // Получаем пользовательские треки
        val userDir = File(filesDir, "soundtracks")
        userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && File(dir, "normal.wav").exists() && File(dir, "superspeed.wav").exists()
        }?.map { it.name } ?: emptyList()
        
        // Обновляем информацию о длительности треков
        (assetTracks + userTracks).forEach { track ->
            if (!trackDuration.containsKey(track)) {
                val file = if (userTracks.contains(track)) {
                    File(filesDir, "soundtracks/${track}/normal.wav")
                } else {
                    // Для asset треков создаем временный файл
                    val tmp = File(cacheDir, "tmp_${track}.wav")
                    if (!tmp.exists()) {
                        assets.open("${track}/normal.wav").use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    tmp
                }
                trackDuration[track] = getTrackDuration(file.absolutePath)
            }
        }
        
        saveStats()
        tracksAdapter.updateData(getTrackInfoList(), selectedTrack)
    }

    /**
     * Запуск отслеживания скорости
     */
    private fun startTracking() {
        tracker.start()
        Toast.makeText(this, "Трекинг запущен", Toast.LENGTH_SHORT).show()
    }

    /**
     * Импорт пака саундтреков из папки
     */
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
                        
                        // Обрабатываем только ZIP файлы
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

    /**
     * Синхронный импорт ZIP без диалогов
     * Возвращает true при успешном импорте, false при ошибке
     */
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

    /**
     * Сохранение статистики воспроизведения в SharedPreferences
     */
    private fun saveStats() {
        val statsPrefs = getSharedPreferences(PREFS_STATS, MODE_PRIVATE)
        val playCounts = trackPlayCount.map { "${it.key}|${it.value}" }.toSet()
        val durations = trackDuration.map { "${it.key}|${it.value}" }.toSet()
        statsPrefs.edit().putStringSet("play_counts", playCounts).putStringSet("durations", durations).apply()
    }

    /**
     * Загрузка статистики воспроизведения из SharedPreferences
     */
    private fun loadStats() {
        val statsPrefs = getSharedPreferences(PREFS_STATS, MODE_PRIVATE)
        statsPrefs.getStringSet("play_counts", null)?.forEach {
            val (track, count) = it.split("|", limit = 2)
            trackPlayCount[track] = count.toIntOrNull() ?: 0
        }
        statsPrefs.getStringSet("durations", null)?.forEach {
            val (track, dur) = it.split("|", limit = 2)
            trackDuration[track] = dur.toIntOrNull() ?: 0
        }
    }

    /**
     * Увеличение счетчика воспроизведений трека
     */
    private fun incTrackPlayCount(track: String) {
        trackPlayCount[track] = (trackPlayCount[track] ?: 0) + 1
        saveStats()
    }

    /**
     * Получение длительности аудиофайла в миллисекундах
     */
    private fun getTrackDuration(path: String): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            dur?.toIntOrNull() ?: 0
        } catch (_: Exception) { 0 } finally { retriever.release() }
    }

    /**
     * Сохранение состояния при повороте экрана
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player.releaseAll()
    }

    /**
     * Очистка ресурсов при уничтожении активности
     */
    override fun onDestroy() {
        super.onDestroy()
        player.releaseAll()
    }
}
