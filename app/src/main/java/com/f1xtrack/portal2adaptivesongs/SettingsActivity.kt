package com.f1xtrack.portal2adaptivesongs

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.f1xtrack.portal2adaptivesongs.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarSettings.setNavigationOnClickListener { finish() }

        val uiPrefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)

        binding.switchAmoled.isChecked = uiPrefs.getBoolean("amoled_mode", false)
        binding.switchKeepScreenOn.isChecked = uiPrefs.getBoolean("keep_screen_on", false)

        binding.switchAmoled.setOnCheckedChangeListener { _, isChecked ->
            uiPrefs.edit().putBoolean("amoled_mode", isChecked).apply()
            recreate()
        }

        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            uiPrefs.edit().putBoolean("keep_screen_on", isChecked).apply()
        }

        binding.buttonTheme.setOnClickListener { showThemeDialog() }
        binding.buttonLanguage.setOnClickListener { showLanguageDialog() }
        binding.buttonStorage.setOnClickListener { startActivity(Intent(this, StorageActivity::class.java)) }
        binding.buttonRoutes.setOnClickListener { startActivity(Intent(this, RouteSettingsActivity::class.java)) }
        binding.buttonTimeAttack.setOnClickListener { startActivity(Intent(this, TimeAttackSettingsActivity::class.java)) }
        binding.buttonHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.buttonAchievements.setOnClickListener { startActivity(Intent(this, AchievementsActivity::class.java)) }
    }

    private fun showThemeDialog() {
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val options = arrayOf(
            getString(R.string.theme_portal2_default),
            getString(R.string.theme_asi),
            getString(R.string.theme_portal2_overgrowth),
            getString(R.string.theme_portal1)
        )
        val current = when (prefs.getString("app_theme", "portal2")) {
            "asi" -> 1
            "portal2_overgrowth" -> 2
            "portal1" -> 3
            else -> 0
        }
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.menu_theme)
            .setSingleChoiceItems(options, current) { dialog, which ->
                val key = when (which) {
                    1 -> "asi"
                    2 -> "portal2_overgrowth"
                    3 -> "portal1"
                    else -> "portal2"
                }
                prefs.edit().putString("app_theme", key).apply()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val languages = buildLanguageOptions(this)
        val options = languages.map { it.label }.toTypedArray()
        val currentLangCode = prefs.getString("app_lang", "system")
        val current = languages.indexOfFirst { it.code == currentLangCode }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.dialog_language_title)
            .setSingleChoiceItems(options, current) { dialog, which ->
                val code = languages[which].code
                prefs.edit().putString("app_lang", code).apply()
                val locales = if (code == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(code)
                }
                AppCompatDelegate.setApplicationLocales(locales)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

private fun SettingsActivity.applyThemeFromPrefs() {
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
