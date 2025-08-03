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
    companion object {
        private const val TAG = "MainActivity"
        private const val IMPORT_ZIP_REQUEST_CODE = 101
        private const val IMPORT_PACK_REQUEST_CODE = 102
        private const val PREFS_NAME = "track_sort_prefs"
        private const val PREF_SORT = "sort_type"
        private const val PREFS_STATS = "track_stats"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var speedTracker: SpeedTracker
    private lateinit var soundPlayer: SoundPlayer
    private lateinit var tracksAdapter: TracksAdapter
    private lateinit var trackManager: TrackManager

    // Состояние приложения
    private var currentTrack: String? = null
    private var isSuperSpeedMode = false
    private var speedThreshold = 10f
    private var hysteresisValue = 3f

    // Обработчик разрешений локации
    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            
            if (hasLocationPermission) {
                startSpeedTracking()
            } else {
                showToast("Разрешите доступ к местоположению для работы приложения")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeUI()
        initializeComponents()
        setupEventListeners()
        loadSavedState()
        requestLocationPermission()
    }

    private fun initializeUI() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
    }

    private fun initializeComponents() {
        soundPlayer = SoundPlayer(this)
        trackManager = TrackManager(this)
        tracksAdapter = TracksAdapter(emptyList(), null) { trackInfo ->
            onTrackSelected(trackInfo.name)
        }
        
        setupSpeedTracker()
        setupRecyclerView()
    }

    private fun setupSpeedTracker() {
        speedTracker = SpeedTracker(
            context = this,
            onSpeedBurst = { speed -> onSpeedBurst(speed) },
            onSpeedChange = { speed -> onSpeedChange(speed) }
        )
    }

    private fun setupRecyclerView() {
        binding.recyclerTracks?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = tracksAdapter
        }
    }

    private fun setupEventListeners() {
        // Слушатели для слайдеров
        binding.seekBurst.addOnChangeListener { _, value, _ ->
            speedThreshold = value
            updateSpeedThresholdDisplay()
            speedTracker.setThreshold(value)
        }

        binding.seekCooldown.addOnChangeListener { _, value, _ ->
            hysteresisValue = value
            updateHysteresisDisplay()
        }

        // Слушатели для кнопок сортировки
        binding.sortSegmented?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newSortType = when (checkedId) {
                    R.id.btnSortAlpha -> TrackManager.SortType.ALPHA
                    R.id.btnSortFreq -> TrackManager.SortType.FREQ
                    R.id.btnSortDuration -> TrackManager.SortType.DURATION
                    else -> TrackManager.SortType.ALPHA
                }
                trackManager.setSortType(newSortType)
                updateTracksList()
            }
        }
    }

    private fun loadSavedState() {
        // Загружаем настройки сортировки
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedSortType = prefs.getString(PREF_SORT, "ALPHA")
        trackManager.setSortType(TrackManager.SortType.valueOf(savedSortType ?: "ALPHA"))
        
        // Загружаем статистику
        trackManager.loadStatistics()
        
        // Обновляем UI
        updateTracksList()
        updateSpeedThresholdDisplay()
        updateHysteresisDisplay()
        updateSortButtons()
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startSpeedTracking() {
        speedTracker.start()
        showToast("Отслеживание скорости запущено")
    }

    // Обработчики событий скорости
    private fun onSpeedBurst(speed: Float) {
        Log.d(TAG, "Speed burst detected: $speed km/h")
        currentTrack?.let { track ->
            val isUserTrack = trackManager.isUserTrack(track)
            soundPlayer.crossfadeTo(track, true, isUserTrack)
        }
    }

    private fun onSpeedChange(speed: Float) {
        currentTrack?.let { track ->
            val isUserTrack = trackManager.isUserTrack(track)
            
            // Логика гистерезиса: включаем superspeed при speed >= threshold, 
            // выключаем при speed < (threshold - hysteresis)
            val shouldBeSuperSpeed = speed >= speedThreshold
            val shouldBeNormalSpeed = speed < (speedThreshold - hysteresisValue)
            
            when {
                !isSuperSpeedMode && shouldBeSuperSpeed -> {
                    isSuperSpeedMode = true
                    soundPlayer.crossfadeTo(track, true, isUserTrack)
                }
                isSuperSpeedMode && shouldBeNormalSpeed -> {
                    isSuperSpeedMode = false
                    soundPlayer.crossfadeTo(track, false, isUserTrack)
                }
            }
        }
    }

    // Обработчик выбора трека
    private fun onTrackSelected(trackName: String) {
        if (currentTrack != trackName) {
            currentTrack = trackName
            isSuperSpeedMode = false // Всегда начинаем с обычной версии
            
            val isUserTrack = trackManager.isUserTrack(trackName)
            soundPlayer.playBoth(trackName, isUserTrack, false)
            trackManager.incrementPlayCount(trackName)
            
            updateTracksList()
        }
    }

    // Обновление UI
    private fun updateTracksList() {
        val trackInfoList = trackManager.getTrackInfoList()
        tracksAdapter.updateData(trackInfoList, currentTrack)
        
        // Инициализация первого трека при первом запуске
        if (currentTrack == null && trackInfoList.isNotEmpty()) {
            onTrackSelected(trackInfoList[0].name)
        }
    }

    private fun updateSpeedThresholdDisplay() {
        binding.textSpeed.text = "Порог Faith Plate: ${speedThreshold.toInt()} км/ч"
    }

    private fun updateHysteresisDisplay() {
        binding.textHysteresis.text = "Гистерезис: ${hysteresisValue.toInt()} км/ч"
    }

    private fun updateSortButtons() {
        val sortType = trackManager.getSortType()
        binding.btnSortAlpha?.isChecked = sortType == TrackManager.SortType.ALPHA
        binding.btnSortFreq?.isChecked = sortType == TrackManager.SortType.FREQ
        binding.btnSortDuration?.isChecked = sortType == TrackManager.SortType.DURATION
    }

    // Меню
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

    // Обработка результатов импорта
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                IMPORT_ZIP_REQUEST_CODE -> {
                    data?.data?.let { uri -> showTrackNameInputDialog(uri) }
                }
                IMPORT_PACK_REQUEST_CODE -> {
                    data?.data?.let { uri -> importTracksFromFolder(uri) }
                }
            }
        }
    }

    private fun showTrackNameInputDialog(zipUri: Uri) {
        val input = android.widget.EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Введите название саундтрека")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    importTrackFromZip(zipUri, name)
                } else {
                    showToast("Название не может быть пустым")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun importTrackFromZip(zipUri: Uri, trackName: String) {
        showProgressDialog("Импортируем саундтрек...") { dialog ->
            Thread {
                try {
                    val success = trackManager.importTrackFromZip(zipUri, trackName)
                    runOnUiThread {
                        dialog.dismiss()
                        if (success) {
                            showToast("Саундтрек импортирован!")
                            updateTracksList()
                        } else {
                            showToast("Ошибка: в ZIP должны быть normal.wav и superspeed.wav")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        dialog.dismiss()
                        showToast("Ошибка импорта: ${e.message}")
                    }
                }
            }.start()
        }
    }

    private fun importTracksFromFolder(folderUri: Uri) {
        showProgressDialog("Импортируем пак саундтреков...") { dialog ->
            Thread {
                try {
                    val result = trackManager.importTracksFromFolder(folderUri)
                    runOnUiThread {
                        dialog.dismiss()
                        showToast("Импортировано: ${result.imported}, ошибок: ${result.failed}")
                        updateTracksList()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        dialog.dismiss()
                        showToast("Ошибка импорта пака: ${e.message}")
                    }
                }
            }.start()
        }
    }

    // Вспомогательные методы
    private fun showProgressDialog(message: String, onShow: (ProgressDialog) -> Unit) {
        val dialog = ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(false)
        }
        dialog.show()
        onShow(dialog)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // Сохранение состояния
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        soundPlayer.releaseAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.releaseAll()
        trackManager.saveStatistics()
    }
}
