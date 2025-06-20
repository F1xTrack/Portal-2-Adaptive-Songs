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

        // Функция для получения текущего выбранного трека
        fun getCurrentTrack(): String {
            return if (binding.root.findViewById<android.widget.ListView?>(R.id.listTracks) != null) {
                selectedTrack ?: ""
            } else {
                binding.spinnerTracks?.text?.toString() ?: ""
            }
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
        // Подпись к ползунку гистерезиса
        binding.seekCooldown.setLabelFormatter { value -> "Гистерезис: ${value.toInt()} км/ч" }
        // Слушатель для seekCooldown (Slider)
        binding.seekCooldown.addOnChangeListener { slider, value, fromUser ->
            hysteresis = value
            binding.textHysteresis.text = "Гистерезис: ${value.toInt()} км/ч"
        }
        // Инициализация подписи при запуске
        binding.textHysteresis.text = "Гистерезис: ${binding.seekCooldown.value.toInt()} км/ч"

        // Заполняем список треков для обоих режимов
        val tracks = assets.list("")!!.filter { name ->
            assets.list(name)?.contains("superspeed.wav") == true
        }
        val allTracks = tracks + userTracks
        val tracksAdapter = ArrayAdapter(
            this,
            R.layout.item_track_dropdown,
            allTracks
        )
        // Если есть listTracks (landscape)
        val listTracks = binding.root.findViewById<android.widget.ListView?>(R.id.listTracks)
        if (listTracks != null) {
            listTracks.adapter = tracksAdapter
            // По умолчанию выбираем первый трек
            if (allTracks.isNotEmpty()) {
                selectedTrack = allTracks[0]
                listTracks.setItemChecked(0, true)
            }
            listTracks.setOnItemClickListener { parent, view, position, id ->
                selectedTrack = allTracks[position]
                val isUser = userTracks.contains(selectedTrack)
                player.playBoth(selectedTrack!!, isUser)
                // Обновить чекбокс длинной версии
                val checked = player.isLongVersionEnabled(selectedTrack!!, "normal")
                binding.checkboxLongVersion.isChecked = checked
            }
        } else {
            // Портретный режим — AutoCompleteTextView
            binding.spinnerTracks?.setAdapter(tracksAdapter)
            binding.spinnerTracks?.setOnClickListener {
                binding.spinnerTracks?.showDropDown()
            }
            binding.spinnerTracks?.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    binding.spinnerTracks?.showDropDown()
                }
            }
            binding.spinnerTracks?.setOnItemClickListener { parent, view, position, id ->
                val track = binding.spinnerTracks?.text?.toString() ?: ""
                val isUser = userTracks.contains(track)
                // Обновить чекбокс длинной версии
                val checked = player.isLongVersionEnabled(track, "normal")
                binding.checkboxLongVersion.isChecked = checked
                player.playBoth(track, isUser)
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
            val track = getCurrentTrack()
            val checked = player.isLongVersionEnabled(track, "normal")
            binding.checkboxLongVersion.isChecked = checked
        }

        binding.spinnerTracks?.setOnItemClickListener { parent, view, position, id ->
            updateLongCheckbox()
            val track = getCurrentTrack()
            val isUser = userTracks.contains(track)
            player.playBoth(track, isUser)
        }

        binding.checkboxLongVersion.setOnCheckedChangeListener { _, isChecked ->
            val track = getCurrentTrack()
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

        binding.buttonImportPack.setOnClickListener {
            // Открываем диалог выбора папки
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, IMPORT_PACK_REQUEST_CODE)
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

    private fun updateTracksList(selectedTrack: String? = null) {
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
            this, R.layout.item_track_dropdown, allTracks
        )
        val listTracks = binding.root.findViewById<android.widget.ListView?>(R.id.listTracks)
        if (listTracks != null) {
            listTracks.adapter = adapter
            if (allTracks.isNotEmpty()) {
                this.selectedTrack = allTracks[0]
                listTracks.setItemChecked(0, true)
            }
        } else {
            binding.spinnerTracks?.setAdapter(adapter)
            if (selectedTrack != null) {
                binding.spinnerTracks?.setText(selectedTrack, false)
            }
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player.releaseAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.releaseAll()
    }
}
