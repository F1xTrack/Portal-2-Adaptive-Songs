package com.f1xtrack.portal2adaptivesongs

import androidx.appcompat.app.AppCompatActivity
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.f1xtrack.portal2adaptivesongs.databinding.ActivitySettingsBinding
import com.google.android.material.progressindicator.CircularProgressIndicator

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private fun assetFolderHasAnyVariant(folder: String, base: String): Boolean {
        return try {
            val files = assets.list(folder) ?: return false
            val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
            files.any { regex.matches(it) }
        } catch (_: Exception) { false }
    }

    private fun userFolderHasAnyVariant(dir: java.io.File, base: String): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return dir.listFiles()?.any { file -> regex.matches(file.name) } == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // --- Секция импорта ---
        val importTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_import)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        val btnImportZip = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.settings_import_zip)
        }
        val btnImportFolder = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.settings_import_folder)
        }
        // Кнопка одиночного файла убрана

        // --- Секция длинных версий ---
        val longTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_long)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        // Здесь позже появится список треков с переключателями длинных версий

        // --- Секция рассинхрона ---
        val desyncTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_desync)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        // Здесь позже появится список треков с чекбоксами рассинхрона

        btnImportZip.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE)
            intent.type = "application/zip"
            startActivityForResult(intent, 101)
        }
        btnImportFolder.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, 102)
        }

        // Получаем LinearLayout для размещения секций настроек
        val layout = binding.layoutSettingsContent
        // Удаляем заголовок, чтобы не было дублирования
        layout.removeView(binding.textSettingsTitle)
        layout.addView(importTitle)
        layout.addView(btnImportZip)
        layout.addView(btnImportFolder)
        // layout.addView(btnImportFile) // убрано
        layout.addView(longTitle)
        layout.addView(desyncTitle)

        // --- Секция длинных версий и рассинхрона для каждого трека ---
        val prefs = getSharedPreferences("track_settings", MODE_PRIVATE)
        val userDir = java.io.File(filesDir, "soundtracks")
        val userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && userFolderHasAnyVariant(dir, "normal") && userFolderHasAnyVariant(dir, "superspeed")
        }?.map { it.name } ?: emptyList()
        val assetTracks = assets.list("")?.filter { name ->
            assetFolderHasAnyVariant(name, "superspeed") && assetFolderHasAnyVariant(name, "normal")
        } ?: emptyList()
        val allTracks = (assetTracks + userTracks).sortedBy { it.lowercase() }
        allTracks.forEach { track ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val label = android.widget.TextView(this).apply {
                text = track
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(0, 0, 0, 4)
            }
            val switchLong = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
                text = getString(R.string.settings_long_enable)
                isChecked = prefs.getBoolean("long_$track", false)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("long_$track", checked).apply()
                    val player = SoundPlayer(this@SettingsActivity)
                    val isUser = userTracks.contains(track)
                    if (checked) {
                        val progressDialog = ProgressLongVersionDialog()
                        progressDialog.show(supportFragmentManager, "progress_long_version")
                        player.createAllLongFilesForTrackAsync(track, isUser) {
                            runOnUiThread {
                                progressDialog.dismiss()
                                android.widget.Toast.makeText(this@SettingsActivity, "Длинные версии созданы", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        player.removeLongFilesForTrack(track, isUser)
                    }
                }
                // Цвет акцента
                this.thumbTintList = getColorStateList(R.color.portal_orange)
                this.trackTintList = getColorStateList(R.color.portal_orange)
            }
            val switchDesync = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
                text = getString(R.string.settings_desync_fix)
                isChecked = prefs.getBoolean("desync_$track", true)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("desync_$track", checked).apply()
                }
                // Цвет акцента
                this.thumbTintList = getColorStateList(R.color.portal_blue)
                this.trackTintList = getColorStateList(R.color.portal_blue)
            }
            row.addView(label)
            row.addView(switchLong)
            row.addView(switchDesync)
            layout.addView(row)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            showNameInputDialog(uri)
        } else if (requestCode == 102 && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            importPackFromFolder(treeUri)
        }
    }

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

    private fun importZipToSoundtracks(zipUri: android.net.Uri, name: String) {
        val dialog = android.app.ProgressDialog(this)
        dialog.setMessage("Импортируем саундтрек...")
        dialog.setCancelable(false)
        dialog.show()
        Thread {
            try {
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

    private fun importPackFromFolder(treeUri: android.net.Uri) {
        val dialog = android.app.ProgressDialog(this)
        dialog.setMessage("Импортируем пак саундтреков...")
        dialog.setCancelable(false)
        dialog.show()
        Thread {
            var imported = 0
            var failed = 0
            try {
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

class ProgressLongVersionDialog : DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.progress_long_version_dialog, container, false)
    }
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        isCancelable = false
    }
} 