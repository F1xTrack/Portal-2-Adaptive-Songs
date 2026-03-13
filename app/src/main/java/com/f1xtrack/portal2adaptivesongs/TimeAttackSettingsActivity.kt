package com.f1xtrack.portal2adaptivesongs

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.f1xtrack.portal2adaptivesongs.databinding.ActivityTimeAttackSettingsBinding

class TimeAttackSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeAttackSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        binding = ActivityTimeAttackSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarTimeAttack.setNavigationOnClickListener { finish() }

        val taPrefs = getSharedPreferences("time_attack_prefs", MODE_PRIVATE)

        when (taPrefs.getString("difficulty", "normal")) {
            "easy" -> binding.groupDifficulty.check(binding.buttonDifficultyEasy.id)
            "hard" -> binding.groupDifficulty.check(binding.buttonDifficultyHard.id)
            "extreme" -> binding.groupDifficulty.check(binding.buttonDifficultyExtreme.id)
            else -> binding.groupDifficulty.check(binding.buttonDifficultyNormal.id)
        }

        binding.groupDifficulty.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = when (checkedId) {
                binding.buttonDifficultyEasy.id -> "easy"
                binding.buttonDifficultyHard.id -> "hard"
                binding.buttonDifficultyExtreme.id -> "extreme"
                else -> "normal"
            }
            taPrefs.edit().putString("difficulty", value).apply()
        }

        binding.buttonStartTimeAttackNow.setOnClickListener {
            taPrefs.edit().putBoolean("start_now", true).apply()
            Toast.makeText(this, getString(R.string.menu_time_attack), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

private fun TimeAttackSettingsActivity.applyThemeFromPrefs() {
    val prefs = getSharedPreferences("ui_prefs", AppCompatActivity.MODE_PRIVATE)
    val amoled = prefs.getBoolean("amoled_mode", false)
    when {
        amoled -> setTheme(R.style.Theme_Portal2AdaptiveSongs_Amoled)
        prefs.getString("app_theme", "portal2") == "asi" -> setTheme(R.style.Theme_Portal_ASI)
        prefs.getString("app_theme", "portal2") == "portal2_overgrowth" -> setTheme(R.style.Theme_Portal2_Overgrowth)
        prefs.getString("app_theme", "portal2") == "portal1" -> setTheme(R.style.Theme_Portal1)
        else -> setTheme(R.style.Theme_Portal2AdaptiveSongs)
    }
}
