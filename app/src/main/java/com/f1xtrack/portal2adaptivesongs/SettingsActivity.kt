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
import android.widget.Toast

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val IMPORT_ZIP_REQUEST_CODE = 101
        private const val IMPORT_FOLDER_REQUEST_CODE = 102
        private const val TRACK_SETTINGS_PREFS = "track_settings"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var trackManager: TrackManager
    private lateinit var soundPlayer: SoundPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeUI()
        initializeComponents()
        setupImportSection()
        setupTracksSection()
    }

    private fun initializeUI() {
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initializeComponents() {
        trackManager = TrackManager(this)
        soundPlayer = SoundPlayer(this)
    }

    private fun setupImportSection() {
        val importTitle = createSectionTitle(getString(R.string.settings_section_import))
        val btnImportZip = createImportButton(getString(R.string.settings_import_zip)) {
            openZipFilePicker()
        }
        val btnImportFolder = createImportButton("Импортировать из папки") {
            openFolderPicker()
        }

        val layout = binding.layoutSettingsContent
        layout.removeView(binding.textSettingsTitle) // Убираем дублирующий заголовок
        layout.addView(importTitle)
        layout.addView(btnImportZip)
        layout.addView(btnImportFolder)
    }

    private fun setupTracksSection() {
        val longTitle = createSectionTitle(getString(R.string.settings_section_long))
        val desyncTitle = createSectionTitle(getString(R.string.settings_section_desync))
        
        val layout = binding.layoutSettingsContent
        layout.addView(longTitle)
        layout.addView(desyncTitle)

        // Добавляем настройки для каждого трека
        val allTracks = trackManager.getTrackInfoList().map { it.name }
        allTracks.forEach { trackName ->
            addTrackSettingsRow(trackName)
        }
    }

    private fun createSectionTitle(text: String): android.widget.TextView {
        return android.widget.TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
    }

    private fun createImportButton(text: String, onClick: () -> Unit): com.google.android.material.button.MaterialButton {
        return com.google.android.material.button.MaterialButton(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
    }

    private fun addTrackSettingsRow(trackName: String) {
        val prefs = getSharedPreferences(TRACK_SETTINGS_PREFS, MODE_PRIVATE)
        val isUserTrack = trackManager.isUserTrack(trackName)

        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val label = android.widget.TextView(this).apply {
            text = trackName
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setPadding(0, 0, 0, 4)
        }

        val switchLong = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
            text = getString(R.string.settings_long_enable)
            isChecked = prefs.getBoolean("long_$trackName", false)
            setOnCheckedChangeListener { _, checked ->
                onLongVersionToggle(trackName, checked, isUserTrack)
            }
            thumbTintList = getColorStateList(R.color.portal_orange)
            trackTintList = getColorStateList(R.color.portal_orange)
        }

        val switchDesync = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
            text = getString(R.string.settings_desync_fix)
            isChecked = prefs.getBoolean("desync_$trackName", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("desync_$trackName", checked).apply()
            }
            thumbTintList = getColorStateList(R.color.portal_blue)
            trackTintList = getColorStateList(R.color.portal_blue)
        }

        row.addView(label)
        row.addView(switchLong)
        row.addView(switchDesync)
        binding.layoutSettingsContent.addView(row)
    }

    private fun onLongVersionToggle(trackName: String, enabled: Boolean, isUserTrack: Boolean) {
        val prefs = getSharedPreferences(TRACK_SETTINGS_PREFS, MODE_PRIVATE)
        prefs.edit().putBoolean("long_$trackName", enabled).apply()

        if (enabled) {
            showProgressDialog("Создание длинной версии трека...") { dialog ->
                soundPlayer.createLongFileAsync(trackName, "normal", isUserTrack) {
                    soundPlayer.createLongFileAsync(trackName, "superspeed", isUserTrack) {
                        runOnUiThread {
                            dialog.dismiss()
                            showToast("Длинные версии созданы")
                        }
                    }
                }
            }
        } else {
            soundPlayer.removeLongFilesForTrack(trackName, isUserTrack)
        }
    }

    private fun openZipFilePicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, IMPORT_ZIP_REQUEST_CODE)
    }

    private fun openFolderPicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, IMPORT_FOLDER_REQUEST_CODE)
    }

    private fun showProgressDialog(message: String, onShow: (ProgressLongVersionDialog) -> Unit) {
        val dialog = ProgressLongVersionDialog()
        dialog.show(supportFragmentManager, "progress_long_version")
        onShow(dialog)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                IMPORT_ZIP_REQUEST_CODE -> {
                    data?.data?.let { uri -> showTrackNameInputDialog(uri) }
                }
                IMPORT_FOLDER_REQUEST_CODE -> {
                    data?.data?.let { uri -> importTracksFromFolder(uri) }
                }
            }
        }
    }

    private fun showTrackNameInputDialog(zipUri: android.net.Uri) {
        val input = android.widget.EditText(this)
        android.app.AlertDialog.Builder(this)
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

    private fun importTrackFromZip(zipUri: android.net.Uri, trackName: String) {
        showProgressDialog("Импортируем саундтрек...") { dialog ->
            Thread {
                try {
                    val success = trackManager.importTrackFromZip(zipUri, trackName)
                    runOnUiThread {
                        dialog.dismiss()
                        if (success) {
                            showToast("Саундтрек импортирован!")
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

    private fun importTracksFromFolder(folderUri: android.net.Uri) {
        showProgressDialog("Импортируем пак саундтреков...") { dialog ->
            Thread {
                try {
                    val result = trackManager.importTracksFromFolder(folderUri)
                    runOnUiThread {
                        dialog.dismiss()
                        showToast("Импортировано: ${result.imported}, ошибок: ${result.failed}")
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

    private fun showProgressDialog(message: String, onShow: (ProgressLongVersionDialog) -> Unit) {
        val dialog = ProgressLongVersionDialog()
        dialog.show(supportFragmentManager, "progress_long_version")
        onShow(dialog)
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