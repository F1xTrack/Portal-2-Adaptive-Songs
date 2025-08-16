package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class OnboardingActivity : AppCompatActivity() {

    private lateinit var btnLanguage: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var status: TextView

    private val SOUNDTRACKS_URL = "https://github.com/F1xTrack/Portal-2-Adaptive-Songs/releases/download/v1.3/soundtracks.zip"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Применяем сохранённый язык до инфлейта вью, чтобы сразу подтянулись правильные строки
        run {
            val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            val lang = prefs.getString("app_lang", "system")
            val locales = if (lang == null || lang == "system" || lang.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(lang)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
        setContentView(R.layout.activity_onboarding)

        btnLanguage = findViewById(R.id.btnChooseLanguage)
        btnDownload = findViewById(R.id.btnDownloadSoundtracks)
        btnSkip = findViewById(R.id.btnContinue)
        progress = findViewById(R.id.progressDownload)
        status = findViewById(R.id.textStatus)

        // Apply expressive multi-color palette for progress indicator (contiguous animation requires >=3 colors)
        try {
            val ta = resources.obtainTypedArray(R.array.progress_indicator_colors)
            val colors = IntArray(ta.length()) { i ->
                val resId = ta.getResourceId(i, 0)
                if (resId != 0) ContextCompat.getColor(this, resId) else ta.getColor(i, 0)
            }
            ta.recycle()
            if (colors.isNotEmpty()) {
                progress.setIndicatorColor(*colors)
                progress.setIndeterminateAnimationType(
                    com.google.android.material.progressindicator.LinearProgressIndicator.INDETERMINATE_ANIMATION_TYPE_DISJOINT
                )
            }
        } catch (_: Exception) { }

        btnLanguage.setOnClickListener { showLanguageDialog() }
        btnDownload.setOnClickListener { confirmAndDownload() }
        btnSkip.setOnClickListener { finishOnboardingAndOpenMain() }
    }

    private fun showLanguageDialog() {
        val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val languages = listOf(
            "system" to getString(R.string.language_system),
            "ar" to "العربية",
            "de" to "Deutsch",
            "en" to "English",
            "es" to "Español",
            "hi" to "हिन्दी",
            "it" to "Italiano",
            "ja" to "日本語",
            "ko" to "한국어",
            "pl" to "Polski",
            "pt" to "Português",
            "ru" to "Русский",
            "tr" to "Türkçe",
            "zh" to "中文"
        )
        val options = languages.map { it.second }.toTypedArray()
        val currentLangCode = prefs.getString("app_lang", "system")
        val current = languages.indexOfFirst { it.first == currentLangCode }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.onboarding_language_title)
            .setSingleChoiceItems(options, current) { dialog, which ->
                val code = languages[which].first
                val prev = prefs.getString("app_lang", "system")
                prefs.edit().putString("app_lang", code).apply()
                val locales = if (code == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code)
                AppCompatDelegate.setApplicationLocales(locales)
                dialog.dismiss()
                if (prev != code) {
                    // Пересоздаём экран онбординга, чтобы строки и layout сразу применили новый язык
                    recreate()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmAndDownload() {
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.onboarding_download_title)
            .setMessage(R.string.onboarding_download_message)
            .setPositiveButton(R.string.onboarding_download_confirm) { _, _ -> startDownload() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setLoading(loading: Boolean, text: String? = null) {
        if (loading) progress.show() else progress.hide()
        btnLanguage.isEnabled = !loading
        btnDownload.isEnabled = !loading
        btnSkip.isEnabled = !loading
        if (text != null) status.text = text
    }

    private fun startDownload() {
        setLoading(true, getString(R.string.onboarding_downloading))
        runOnUiThread {
            progress.isIndeterminate = true
            progress.progress = 0
        }
        Thread {
            var ok = false
            var error: String? = null
            try {
                // 1) Скачать zip во временный файл
                val tmp = File(cacheDir, "soundtracks.zip")
                val url = URL(SOUNDTRACKS_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                var downloaded = 0L
                var lastPercent = -1
                runOnUiThread {
                    if (total > 0) {
                        progress.isIndeterminate = false
                        progress.max = 100
                        progress.setProgressCompat(0, true)
                        status.text = getString(R.string.onboarding_progress_percent, 0)
                    } else {
                        progress.isIndeterminate = true
                    }
                }
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            if (total > 0) {
                                downloaded += n
                                val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    runOnUiThread {
                                        progress.setProgressCompat(percent, true)
                                        status.text = getString(R.string.onboarding_progress_percent, percent)
                                    }
                                }
                            }
                        }
                    }
                }
                // Если размер неизвестен, после завершения выставим 100%
                if (total <= 0) {
                    runOnUiThread {
                        progress.isIndeterminate = false
                        progress.max = 100
                        progress.setProgressCompat(100, true)
                        status.text = getString(R.string.onboarding_progress_percent, 100)
                    }
                }
                // 2) Распаковать в filesDir/soundtracks (с сохранением структуры)
                val destRoot = File(filesDir, "soundtracks")
                runOnUiThread {
                    status.text = getString(R.string.onboarding_unpacking)
                    progress.isIndeterminate = true
                }
                if (!destRoot.exists()) destRoot.mkdirs()
                ZipInputStream(tmp.inputStream()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val safeName = entry.name.replace("\\", "/")
                        if (safeName.contains("..")) {
                            throw IllegalArgumentException("Unsafe zip entry: $safeName")
                        }
                        // Некоторые архивы имеют верхний уровень 'soundtracks/'. Уберём его, чтобы
                        // результат оказался прямо в filesDir/soundtracks/<track>/...
                        var rel = safeName.trimStart('/')
                        if (rel.startsWith("soundtracks/", ignoreCase = true)) {
                            rel = rel.substring(rel.indexOf('/') + 1)
                        }
                        if (entry.isDirectory) {
                            // Создаём папку, если это каталог верхнего архива
                            File(destRoot, rel).mkdirs()
                        } else if (rel.endsWith(".zip", ignoreCase = true)) {
                            // Формат: <track>.zip с wav-файлами внутри — распакуем во вложенную папку трека
                            val fileName = rel.substringAfterLast('/')
                            val trackName = fileName.substring(0, fileName.length - 4) // без .zip
                            val destDir = File(destRoot, trackName)
                            destDir.mkdirs()

                            // Сначала сохраним текущий entry во временный файл
                            val innerZip = File(cacheDir, "inner_${trackName}.zip")
                            FileOutputStream(innerZip).use { out ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val n = zis.read(buf)
                                    if (n == -1) break
                                    out.write(buf, 0, n)
                                }
                            }

                            // Теперь распакуем его, раскладывая normal*.wav и superspeed*.wav в корень destDir
                            val innerRegex = Regex("^(normal(\\d*)|superspeed(\\d*))\\.wav$", RegexOption.IGNORE_CASE)
                            ZipInputStream(FileInputStream(innerZip)).use { inner ->
                                var e = inner.nextEntry
                                while (e != null) {
                                    if (!e.isDirectory) {
                                        val innerSafe = e.name.replace("\\", "/").trimStart('/')
                                        val base = innerSafe.substringAfterLast('/')
                                        if (innerRegex.matches(base)) {
                                            val outFile = File(destDir, base)
                                            outFile.parentFile?.mkdirs()
                                            FileOutputStream(outFile).use { out ->
                                                val buf = ByteArray(16 * 1024)
                                                while (true) {
                                                    val n = inner.read(buf)
                                                    if (n == -1) break
                                                    out.write(buf, 0, n)
                                                }
                                            }
                                        }
                                    }
                                    inner.closeEntry()
                                    e = inner.nextEntry
                                }
                            }
                            innerZip.delete()
                        } else {
                            // Прямые файлы из верхнего архива (редкий случай)
                            val outFile = File(destRoot, rel)
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                val buf = ByteArray(8 * 1024)
                                while (true) {
                                    val n = zis.read(buf)
                                    if (n == -1) break
                                    out.write(buf, 0, n)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                ok = true
            } catch (e: Exception) {
                error = e.message
            }
            runOnUiThread {
                setLoading(false)
                if (ok) {
                    Toast.makeText(this, getString(R.string.onboarding_done), Toast.LENGTH_LONG).show()
                    finishOnboardingAndOpenMain()
                } else {
                    MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                        .setTitle(R.string.onboarding_error_title)
                        .setMessage(getString(R.string.onboarding_error_message, error ?: ""))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }.start()
    }

    private fun finishOnboardingAndOpenMain() {
        val prefs = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
