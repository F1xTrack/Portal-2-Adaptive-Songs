package com.f1xtrack.portal2adaptivesongs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.f1xtrack.portal2adaptivesongs.databinding.ActivitySettingsBinding

/**
 * Активность настроек приложения
 * 
 * Основные функции:
 * - Импорт саундтреков из ZIP файлов и папок
 * - Управление настройками длинных версий треков
 * - Управление настройками исправления рассинхронизации
 * - Динамическое создание UI для каждого трека
 */
class SettingsActivity : AppCompatActivity() {
    
    // View Binding для доступа к элементам интерфейса
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализация View Binding
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Настройка панели инструментов
        supportActionBar?.title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Создание секций настроек
        setupImportSection()
        setupTracksSettings()
    }

    /**
     * Настройка секции импорта саундтреков
     * Создает заголовок и кнопки для импорта
     */
    private fun setupImportSection() {
        // Заголовок секции импорта
        val importTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_import)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        
        // Кнопка импорта ZIP файла
        val btnImportZip = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.settings_import_zip)
            setOnClickListener {
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE)
                intent.type = "application/zip"
                startActivityForResult(intent, 101)
            }
        }
        
        // Кнопка импорта папки с саундтреками
        val btnImportFolder = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.settings_import_folder)
            setOnClickListener {
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, 102)
            }
        }

        // Заголовки для других секций
        val longTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_long)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        
        val desyncTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_desync)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }

        // Добавление элементов в основной контейнер
        val layout = binding.layoutSettingsContent
        layout.removeView(binding.textSettingsTitle) // Убираем дублирующий заголовок
        layout.addView(importTitle)
        layout.addView(btnImportZip)
        layout.addView(btnImportFolder)
        layout.addView(longTitle)
        layout.addView(desyncTitle)
    }

    /**
     * Настройка секций настроек для каждого трека
     * Динамически создает переключатели для каждого трека
     */
    private fun setupTracksSettings() {
        // Получаем настройки треков
        val prefs = getSharedPreferences("track_settings", MODE_PRIVATE)
        
        // Получаем список всех треков (пользовательские + стандартные)
        val userDir = java.io.File(filesDir, "soundtracks")
        val userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && java.io.File(dir, "normal.wav").exists() && java.io.File(dir, "superspeed.wav").exists()
        }?.map { it.name } ?: emptyList()
        
        val assetTracks = assets.list("")?.filter { name ->
            assets.list(name)?.contains("superspeed.wav") == true
        } ?: emptyList()
        
        val allTracks = (assetTracks + userTracks).sortedBy { it.lowercase() }
        
        // Создаем настройки для каждого трека
        allTracks.forEach { track ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            
            // Название трека
            val label = android.widget.TextView(this).apply {
                text = track
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(0, 0, 0, 4)
            }
            
            // Переключатель длинной версии
            val switchLong = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
                text = getString(R.string.settings_long_enable)
                isChecked = prefs.getBoolean("long_$track", false)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("long_$track", checked).apply()
                }
                // Оранжевый цвет акцента
                this.thumbTintList = getColorStateList(R.color.portal_orange)
                this.trackTintList = getColorStateList(R.color.portal_orange)
            }
            
            // Переключатель исправления рассинхронизации
            val switchDesync = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
                text = getString(R.string.settings_desync_fix)
                isChecked = prefs.getBoolean("desync_$track", true)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("desync_$track", checked).apply()
                }
                // Синий цвет акцента
                this.thumbTintList = getColorStateList(R.color.portal_blue)
                this.trackTintList = getColorStateList(R.color.portal_blue)
            }
            
            // Добавляем элементы в строку
            row.addView(label)
            row.addView(switchLong)
            row.addView(switchDesync)
            
            // Добавляем строку в основной контейнер
            binding.layoutSettingsContent.addView(row)
        }
    }

    /**
     * Обработка навигации назад
     * Закрывает активность при нажатии на стрелку назад
     */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Обработка результатов активности (импорт файлов)
     * 
     * @param requestCode Код запроса (101 - ZIP, 102 - папка)
     * @param resultCode Результат операции
     * @param data Данные с URI файла/папки
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 101 && resultCode == RESULT_OK) {
            // Импорт ZIP файла
            val uri = data?.data ?: return
            showNameInputDialog(uri)
        } else if (requestCode == 102 && resultCode == RESULT_OK) {
            // Импорт папки
            val treeUri = data?.data ?: return
            importPackFromFolder(treeUri)
        }
    }

    /**
     * Диалог для ввода названия импортируемого саундтрека
     * 
     * @param zipUri URI ZIP файла для импорта
     */
    private fun showNameInputDialog(zipUri: android.net.Uri) {
        val input = android.widget.EditText(this)
        android.app.AlertDialog.Builder(this)
            .setTitle("Введите название саундтрека")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    importZipToSoundtracks(zipUri, name)
                } else {
                    android.widget.Toast.makeText(this, "Название не может быть пустым", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Импорт ZIP-архива с саундтреком
     * Извлекает файлы normal.wav и superspeed.wav из архива
     * 
     * @param zipUri URI ZIP файла
     * @param name Название саундтрека
     */
    private fun importZipToSoundtracks(zipUri: android.net.Uri, name: String) {
        val dialog = android.app.ProgressDialog(this)
        dialog.setMessage("Импортируем саундтрек...")
        dialog.setCancelable(false)
        dialog.show()
        
        Thread {
            try {
                // Создаем директорию для саундтрека
                val dir = java.io.File(filesDir, "soundtracks/$name")
                dir.mkdirs()
                
                // Открываем ZIP файл
                val inputStream = contentResolver.openInputStream(zipUri) ?: throw Exception("Не удалось открыть ZIP")
                val zis = java.util.zip.ZipInputStream(inputStream)
                var entry = zis.nextEntry
                var foundNormal = false
                var foundSuper = false
                
                // Извлекаем файлы из архива
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = when (entry.name) {
                            "normal.wav" -> java.io.File(dir, "normal.wav").also { foundNormal = true }
                            "superspeed.wav" -> java.io.File(dir, "superspeed.wav").also { foundSuper = true }
                            else -> null
                        }
                        if (outFile != null) {
                            java.io.FileOutputStream(outFile).use { out ->
                                zis.copyTo(out)
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
                zis.close()
                inputStream.close()
                
                // Показываем результат
                runOnUiThread {
                    dialog.dismiss()
                    if (foundNormal && foundSuper) {
                        android.widget.Toast.makeText(this, "Саундтрек импортирован!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        dir.deleteRecursively()
                        android.widget.Toast.makeText(this, "В ZIP должны быть normal.wav и superspeed.wav", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    android.widget.Toast.makeText(this, "Ошибка импорта: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /**
     * Импорт пака саундтреков из папки
     * Обрабатывает все ZIP файлы в выбранной папке
     * 
     * @param treeUri URI папки с саундтреками
     */
    private fun importPackFromFolder(treeUri: android.net.Uri) {
        val dialog = android.app.ProgressDialog(this)
        dialog.setMessage("Импортируем пак саундтреков...")
        dialog.setCancelable(false)
        dialog.show()
        
        Thread {
            var imported = 0
            var failed = 0
            
            try {
                // Получаем список файлов в папке
                val children = contentResolver.query(
                    android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri)),
                    arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null
                )
                
                if (children != null) {
                    while (children.moveToNext()) {
                        val name = children.getString(0)
                        val docId = children.getString(1)
                        val mime = children.getString(2)
                        
                        // Обрабатываем только ZIP файлы
                        if (mime == "application/zip" || name.endsWith(".zip", true)) {
                            val zipUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
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
                    android.widget.Toast.makeText(this, "Ошибка импорта пака: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            
            runOnUiThread {
                dialog.dismiss()
                android.widget.Toast.makeText(this, "Импортировано: $imported, ошибок: $failed", android.widget.Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /**
     * Синхронный импорт ZIP файла без диалогов
     * Используется для пакетного импорта
     * 
     * @param zipUri URI ZIP файла
     * @param name Название саундтрека
     * @return true при успешном импорте, false при ошибке
     */
    private fun importZipToSoundtracksSync(zipUri: android.net.Uri, name: String): Boolean {
        return try {
            val dir = java.io.File(filesDir, "soundtracks/$name")
            dir.mkdirs()
            
            val inputStream = contentResolver.openInputStream(zipUri) ?: throw Exception("Не удалось открыть ZIP")
            val zis = java.util.zip.ZipInputStream(inputStream)
            var entry = zis.nextEntry
            var foundNormal = false
            var foundSuper = false
            
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = when (entry.name) {
                        "normal.wav" -> java.io.File(dir, "normal.wav").also { foundNormal = true }
                        "superspeed.wav" -> java.io.File(dir, "superspeed.wav").also { foundSuper = true }
                        else -> null
                    }
                    if (outFile != null) {
                        java.io.FileOutputStream(outFile).use { out ->
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
} 