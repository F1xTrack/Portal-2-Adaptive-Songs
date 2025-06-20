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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var tracker: SpeedTracker
    private lateinit var player: SoundPlayer
    private var isSuperSpeed = false
    private var lastTrack: String? = null
    private val IMPORT_ZIP_REQUEST_CODE = 101
    private var userTracks: List<String> = emptyList()

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

        player = SoundPlayer(this)

        tracker = SpeedTracker(this, { speed ->
            runOnUiThread {
                Log.d("MainActivity", "Triggered speed=$speed")
                val track = binding.spinnerTracks.text.toString()
                val isUser = userTracks.contains(track)
                player.crossfadeTo(track, true, isUser)
            }
        }, { speed ->
            runOnUiThread {
                val threshold = tracker.getThreshold()
                val track = binding.spinnerTracks.text.toString()
                val isUser = userTracks.contains(track)
                if (lastTrack != track) {
                    // При смене трека всегда сбрасываем состояние
                    isSuperSpeed = false
                    lastTrack = track
                    player.crossfadeTo(track, false, isUser)
                }
                if (!isSuperSpeed && speed >= threshold) {
                    isSuperSpeed = true
                    player.crossfadeTo(track, true, isUser)
                } else if (isSuperSpeed && speed < threshold) {
                    isSuperSpeed = false
                    player.crossfadeTo(track, false, isUser)
                }
            }
        })
        // Устанавливаем threshold из seekBurst (Slider)
        tracker.setThreshold(binding.seekBurst.value)
        binding.textSpeed.text = "Порог Faith Plate: ${binding.seekBurst.value.toInt()} км/ч"
        // Слушатель для seekBurst (Slider)
        binding.seekBurst.addOnChangeListener { slider, value, fromUser ->
            tracker.setThreshold(value)
            binding.textSpeed.text = "Порог Faith Plate: ${value.toInt()} км/ч"
        }

        // Заполняем AutoCompleteTextView названиями папок в assets
        val tracks = assets.list("")!!.filter { name ->
            assets.list(name)?.contains("superspeed.wav") == true
        }
        val tracksAdapter = ArrayAdapter(
            this,
            R.layout.item_track_dropdown,
            tracks
        )
        binding.spinnerTracks.setAdapter(tracksAdapter)

        binding.spinnerTracks.setOnClickListener {
            binding.spinnerTracks.showDropDown()
        }
        binding.spinnerTracks.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.spinnerTracks.showDropDown()
            }
        }

        // Запрос разрешений
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        // --- Работа с CheckBox длинной версии ---
        val updateLongCheckbox = {
            val track = binding.spinnerTracks.text.toString()
            val checked = player.isLongVersionEnabled(track, "normal")
            binding.checkboxLongVersion.isChecked = checked
        }

        binding.spinnerTracks.setOnItemClickListener { parent, view, position, id ->
            updateLongCheckbox()
            val track = binding.spinnerTracks.text.toString()
            val isUser = userTracks.contains(track)
            player.playBoth(track, isUser)
        }

        binding.checkboxLongVersion.setOnCheckedChangeListener { _, isChecked ->
            val track = binding.spinnerTracks.text.toString()
            val isUser = userTracks.contains(track)
            if (isChecked) {
                // Показываем диалог загрузки
                val dialog = ProgressDialog(this)
                dialog.setMessage("Создание длинной версии трека...")
                dialog.setCancelable(false)
                dialog.show()
                // Останавливаем плееры
                player.releaseAll()
                player.createLongFileAsync(track, "normal", isUser) {
                    player.createLongFileAsync(track, "superspeed", isUser) {
                        runOnUiThread {
                            dialog.dismiss()
                            player.playBoth(track, isUser)
                        }
                    }
                }
            } else {
                player.removeLongFilesForTrack(track, isUser)
                player.releaseAll()
                player.playBoth(track, isUser)
            }
        }

        binding.buttonImportZip.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/zip"
            startActivityForResult(intent, IMPORT_ZIP_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_ZIP_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            showNameInputDialog(uri)
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
                        updateTracksList()
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

    private fun updateTracksList() {
        // Стандартные треки из assets
        val assetTracks = assets.list("")!!.filter { name ->
            assets.list(name)?.contains("superspeed.wav") == true
        }
        // Пользовательские треки из filesDir/soundtracks
        val userDir = File(filesDir, "soundtracks")
        userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && File(dir, "normal.wav").exists() && File(dir, "superspeed.wav").exists()
        }?.map { it.name } ?: emptyList()
        val allTracks = assetTracks + userTracks
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, allTracks
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerTracks.setAdapter(adapter)
    }

    private fun startTracking() {
        tracker.start()
        Toast.makeText(this, "Трекинг запущен", Toast.LENGTH_SHORT).show()
    }
}
