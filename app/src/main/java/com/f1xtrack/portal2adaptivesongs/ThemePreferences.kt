package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

internal fun ComponentActivity.applyAppThemeFromPrefs() {
    val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    val amoled = prefs.getBoolean("amoled_mode", false)
    when {
        amoled -> setTheme(R.style.Theme_Portal2AdaptiveSongs_Amoled)
        prefs.getString("app_theme", "portal2") == "asi" -> setTheme(R.style.Theme_Portal_ASI)
        prefs.getString("app_theme", "portal2") == "portal2_overgrowth" -> setTheme(R.style.Theme_Portal2_Overgrowth)
        prefs.getString("app_theme", "portal2") == "portal1" -> setTheme(R.style.Theme_Portal1)
        else -> setTheme(R.style.Theme_Portal2AdaptiveSongs)
    }

    val lang = prefs.getString("app_lang", "system")
    val locales = if (lang == null || lang == "system" || lang.isBlank()) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(lang)
    }
    AppCompatDelegate.setApplicationLocales(locales)
}
