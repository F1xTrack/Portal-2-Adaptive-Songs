package com.f1xtrack.portal2adaptivesongs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.f1xtrack.portal2adaptivesongs.databinding.ActivityTimeAttackSettingsBinding

class TimeAttackSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeAttackSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeAttackSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Material 3: no top app bar here; rely on system back navigation

        val layout = binding.settingsContainer

        // --- Секция тайм-атаки ---
        val timeAttackTitle = android.widget.TextView(this).apply {
            text = getString(R.string.settings_section_time_attack)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 16, 0, 8)
        }
        val diffGroup = com.google.android.material.button.MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val btnEasy = com.google.android.material.button.MaterialButton(this).apply {
            id = android.view.View.generateViewId()
            text = getString(R.string.difficulty_easy)
            isCheckable = true
        }
        val btnNormal = com.google.android.material.button.MaterialButton(this).apply {
            id = android.view.View.generateViewId()
            text = getString(R.string.difficulty_normal)
            isCheckable = true
        }
        val btnHard = com.google.android.material.button.MaterialButton(this).apply {
            id = android.view.View.generateViewId()
            text = getString(R.string.difficulty_hard)
            isCheckable = true
        }
        diffGroup.addView(btnEasy)
        diffGroup.addView(btnNormal)
        diffGroup.addView(btnHard)
        val taPrefs = getSharedPreferences("time_attack_prefs", MODE_PRIVATE)
        when (taPrefs.getString("difficulty", "normal")) {
            "easy" -> diffGroup.check(btnEasy.id)
            "hard" -> diffGroup.check(btnHard.id)
            else -> diffGroup.check(btnNormal.id)
        }
        diffGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val value = when (checkedId) {
                    btnEasy.id -> "easy"
                    btnHard.id -> "hard"
                    else -> "normal"
                }
                taPrefs.edit().putString("difficulty", value).apply()
            }
        }
        val btnStartNow = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.time_attack_trigger_now)
            setOnClickListener {
                taPrefs.edit().putBoolean("start_now", true).apply()
                android.widget.Toast.makeText(this@TimeAttackSettingsActivity, getString(R.string.menu_time_attack), android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        layout.addView(timeAttackTitle)
        layout.addView(diffGroup)
        layout.addView(btnStartNow)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
