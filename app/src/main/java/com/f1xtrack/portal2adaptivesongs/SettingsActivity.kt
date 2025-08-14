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

    private lateinit var trackSettingsTitle: android.widget.TextView
    private lateinit var importTitle: android.widget.TextView
    private lateinit var routeRecordingTitle: android.widget.TextView
    private lateinit var timeAttackTitle: android.widget.TextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)



        // --- Секция настроек треков (пока пустая) ---
        trackSettingsTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_tracks)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 16, 0, 8)
        }
        val trackSettingsPlaceholder = android.widget.TextView(this).apply {
            text = getString(R.string.settings_tracks_placeholder)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(16, 8, 16, 8)
            alpha = 0.6f
        }
        importTitle = android.widget.TextView(this).apply {
            text = getString(R.string.importTitle)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 16, 0, 8)
        }
        routeRecordingTitle = android.widget.TextView(this).apply {
            text = getString(R.string.routeRecordingTitle)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 16, 0, 8)
        }
        timeAttackTitle = android.widget.TextView(this).apply {
            text = getString(R.string.timeAttackTitle)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 16, 0, 8)
        }



        // Получаем LinearLayout для размещения секций настроек
        val layout = binding.layoutSettingsContent
        // Удаляем заголовок, чтобы не было дублирования
        layout.removeView(binding.textSettingsTitle)
        

        
        // --- Секция настроек треков (пустая) ---
        layout.addView(trackSettingsTitle)
        layout.addView(trackSettingsPlaceholder)
        layout.addView(importTitle)
        layout.addView(routeRecordingTitle)
        layout.addView(timeAttackTitle)

        // Секция рассинхрона и переключатели десинхронизации убраны согласно требованиям

        // Прокрутка к секции по extra open_section
        binding.settingsScroll.post {
            when (intent.getStringExtra("open_section")) {
                "import" -> binding.settingsScroll.smoothScrollTo(0, importTitle.top)
                "tracks" -> binding.settingsScroll.smoothScrollTo(0, trackSettingsTitle.top)
                "route_recording" -> binding.settingsScroll.smoothScrollTo(0, routeRecordingTitle.top)
                "time_attack" -> binding.settingsScroll.smoothScrollTo(0, timeAttackTitle.top)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}