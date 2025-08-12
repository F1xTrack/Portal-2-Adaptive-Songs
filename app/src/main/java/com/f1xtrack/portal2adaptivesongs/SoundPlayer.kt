package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.random.Random

class SoundPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaPlayerAlt: MediaPlayer? = null
    private var crossfadeJob: Handler? = null
    private var loopHandlerNormal: Handler? = null
    private var loopHandlerSuper: Handler? = null
    private var loopRunnableNormal: Runnable? = null
    private var loopRunnableSuper: Runnable? = null

    private var currentTrackName: String? = null
    private var lastNormalVariant: String? = null
    private var lastSuperVariant: String? = null

    // Генерация длинного файла (20x) в кэше для конкретной вариации (например, normal2)
    private fun getOrCreateLongFile(trackName: String, fileName: String, isUserTrack: Boolean, force: Boolean = false): File {
        val cacheFile = File(context.cacheDir, "${trackName}_${fileName}_long.wav")
        if (cacheFile.exists() && !force) return cacheFile
        try {
            val data = if (isUserTrack) {
                val userFile = File(context.filesDir, "soundtracks/$trackName/$fileName.wav")
                userFile.readBytes()
            } else {
                val input = context.assets.open("$trackName/$fileName.wav")
                val bytes = input.readBytes()
                input.close()
                bytes
            }
            val out = FileOutputStream(cacheFile)
            repeat(20) { out.write(data) }
            out.close()
        } catch (e: IOException) {
            Log.e("SoundPlayer", "Error creating long file", e)
        }
        return cacheFile
    }

    private fun getShortFile(trackName: String, fileName: String, isUserTrack: Boolean): File {
        return if (isUserTrack) {
            File(context.filesDir, "soundtracks/$trackName/$fileName.wav")
        } else {
            // Кладём конкретную вариацию в кэш с уникальным именем
            File(context.cacheDir, "tmp_${trackName}_${fileName}.wav").apply {
                if (!exists()) {
                    val input = context.assets.open("$trackName/$fileName.wav")
                    outputStream().use { it.write(input.readBytes()) }
                    input.close()
                }
            }
        }
    }

    private fun listVariantNames(trackName: String, base: String, isUserTrack: Boolean): List<String> {
        // Возвращаем имена без расширения: normal, normal1, normal2, ...
        val pattern = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return try {
            if (isUserTrack) {
                val dir = File(context.filesDir, "soundtracks/$trackName")
                if (!dir.exists()) return emptyList()
                dir.listFiles()?.mapNotNull { file ->
                    val name = file.name
                    if (pattern.matches(name)) name.removeSuffix(".wav") else null
                }?.sortedWith(compareBy({ it.length }, { it })) ?: emptyList()
            } else {
                val files = context.assets.list(trackName) ?: return emptyList()
                files.mapNotNull { name ->
                    if (pattern.matches(name)) name.removeSuffix(".wav") else null
                }.sortedWith(compareBy({ it.length }, { it }))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun chooseRandomVariant(trackName: String, base: String, isUserTrack: Boolean, exclude: String?): String {
        val variants = listVariantNames(trackName, base, isUserTrack)
        if (variants.isEmpty()) return base
        if (variants.size == 1) return variants.first()
        val pool = variants.filter { it != exclude }
        return if (pool.isNotEmpty()) pool.random() else variants.random()
    }

    private fun getSourceFileFor(trackName: String, variantName: String, isUserTrack: Boolean, useLong: Boolean): File {
        return if (useLong) getOrCreateLongFile(trackName, variantName, isUserTrack) else getShortFile(trackName, variantName, isUserTrack)
    }

    // Удалить длинные файлы и флаги для конкретного трека (все вариации)
    fun removeLongFilesForTrack(trackName: String, isUserTrack: Boolean) {
        context.cacheDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("${trackName}_") && (name.endsWith("_long.wav") || name.endsWith("_long.flag"))) {
                file.delete()
            }
        }
    }

    // Запуск обоих режимов одновременно с выбором случайной вариации
    fun playBoth(trackName: String, isUserTrack: Boolean) {
        mediaPlayer?.release()
        mediaPlayerAlt?.release()
        loopHandlerNormal?.removeCallbacksAndMessages(null)
        loopHandlerSuper?.removeCallbacksAndMessages(null)
        mediaPlayer = MediaPlayer()
        mediaPlayerAlt = MediaPlayer()
        currentTrackName = trackName
        lastNormalVariant = null
        lastSuperVariant = null
        try {
            val prefs = context.getSharedPreferences("track_settings", Context.MODE_PRIVATE)
            val useLong = prefs.getBoolean("long_$trackName", false)

            val normalVariant = chooseRandomVariant(trackName, "normal", isUserTrack, exclude = null).also { lastNormalVariant = it }
            val superVariant = chooseRandomVariant(trackName, "superspeed", isUserTrack, exclude = null).also { lastSuperVariant = it }

            val fileNormal = getSourceFileFor(trackName, normalVariant, isUserTrack, useLong)
            val fileSuper = getSourceFileFor(trackName, superVariant, isUserTrack, useLong)

            mediaPlayer?.setDataSource(fileNormal.absolutePath)
            mediaPlayerAlt?.setDataSource(fileSuper.absolutePath)
            mediaPlayer?.prepare()
            mediaPlayerAlt?.prepare()
            mediaPlayer?.setVolume(1f, 1f)
            mediaPlayerAlt?.setVolume(0f, 0f)
            mediaPlayer?.seekTo(0)
            mediaPlayerAlt?.seekTo(0)
            mediaPlayer?.start()
            mediaPlayerAlt?.start()
            val desyncFix = prefs.getBoolean("desync_${trackName}", true)
            startManualLoop(mediaPlayer, false, desyncFix)
            startManualLoop(mediaPlayerAlt, true, desyncFix)
        } catch (e: IOException) {
            Log.e("SoundPlayer", "Error playing both", e)
        }
    }

    // Кроссфейд между двумя MediaPlayer с переинициализацией целевого плеера на случайную вариацию
    fun crossfadeTo(trackName: String, toSuperSpeed: Boolean, isUserTrack: Boolean) {
        val prefs = context.getSharedPreferences("track_settings", Context.MODE_PRIVATE)
        val useLong = prefs.getBoolean("long_$trackName", false)

        // Если сменился трек, сбрасываем последние вариации
        if (currentTrackName != trackName) {
            currentTrackName = trackName
            lastNormalVariant = null
            lastSuperVariant = null
        }

        try {
            if (toSuperSpeed) {
                // Переинициализируем mediaPlayerAlt на случайную вариацию superspeed
                val newVariant = chooseRandomVariant(trackName, "superspeed", isUserTrack, exclude = lastSuperVariant)
                lastSuperVariant = newVariant
                val target = mediaPlayerAlt ?: MediaPlayer().also { mediaPlayerAlt = it }
                target.reset()
                val file = getSourceFileFor(trackName, newVariant, isUserTrack, useLong)
                target.setDataSource(file.absolutePath)
                target.prepare()
                target.seekTo(0)
                target.setVolume(0f, 0f)
                target.start()
            } else {
                // Переинициализируем mediaPlayer на случайную вариацию normal
                val newVariant = chooseRandomVariant(trackName, "normal", isUserTrack, exclude = lastNormalVariant)
                lastNormalVariant = newVariant
                val target = mediaPlayer ?: MediaPlayer().also { mediaPlayer = it }
                target.reset()
                val file = getSourceFileFor(trackName, newVariant, isUserTrack, useLong)
                target.setDataSource(file.absolutePath)
                target.prepare()
                target.seekTo(0)
                target.setVolume(if (mediaPlayerAlt == null) 1f else 0.1f, if (mediaPlayerAlt == null) 1f else 0.1f)
                target.start()
            }
        } catch (e: Exception) {
            Log.e("SoundPlayer", "Error preparing variant for crossfade", e)
        }

        val fromPlayer = if (toSuperSpeed) mediaPlayer else mediaPlayerAlt
        val toPlayer = if (toSuperSpeed) mediaPlayerAlt else mediaPlayer
        val duration = 1000L
        val steps = 20
        val delay = duration / steps
        crossfadeJob?.removeCallbacksAndMessages(null)
        crossfadeJob = Handler(context.mainLooper)
        for (i in 0..steps) {
            crossfadeJob?.postDelayed({
                val vol = i / steps.toFloat()
                try {
                    if (toSuperSpeed) {
                        toPlayer?.setVolume(vol, vol)
                        fromPlayer?.setVolume(1 - 0.9f * vol, 1 - 0.9f * vol)
                    } else {
                        toPlayer?.setVolume(0.1f + 0.9f * vol, 0.1f + 0.9f * vol)
                        fromPlayer?.setVolume(1 - vol, 1 - vol)
                    }
                } catch (e: Exception) {
                    Log.e("SoundPlayer", "setVolume error", e)
                }
                if (i == steps) {
                    try {
                        if (toSuperSpeed) {
                            fromPlayer?.setVolume(0.1f, 0.1f)
                            toPlayer?.setVolume(1f, 1f)
                        } else {
                            toPlayer?.setVolume(1f, 1f)
                            fromPlayer?.setVolume(0f, 0f)
                        }
                    } catch (_: Exception) {}
                }
            }, i * delay)
        }
    }

    // Проверка, используется ли длинная версия для трека (по флагу конкретной вариации)
    fun isLongVersionEnabled(trackName: String, fileName: String): Boolean {
        val flagFile = File(context.cacheDir, "${trackName}_${fileName}_long.flag")
        return flagFile.exists()
    }

    // Установить/снять флаг длинной версии для конкретной вариации
    fun setLongVersionEnabled(trackName: String, fileName: String, enabled: Boolean) {
        val flagFile = File(context.cacheDir, "${trackName}_${fileName}_long.flag")
        if (enabled) flagFile.writeText("1") else flagFile.delete()
    }

    // Асинхронное создание длинных файлов для ВСЕХ вариаций normal/superspeed
    fun createAllLongFilesForTrackAsync(trackName: String, isUserTrack: Boolean, onDone: () -> Unit) {
        Thread {
            try {
                val normals = listVariantNames(trackName, "normal", isUserTrack).ifEmpty { listOf("normal") }
                val supers = listVariantNames(trackName, "superspeed", isUserTrack).ifEmpty { listOf("superspeed") }
                (normals + supers).forEach { variant ->
                    getOrCreateLongFile(trackName, variant, isUserTrack, force = true)
                    setLongVersionEnabled(trackName, variant, true)
                }
            } catch (_: Exception) {
            } finally {
                onDone()
            }
        }.start()
    }

    // Очистка кэша длинных файлов и флагов (всех треков)
    fun clearCache() {
        context.cacheDir.listFiles()?.forEach {
            if (it.name.endsWith("_long.wav") || it.name.endsWith("_long.flag")) it.delete()
        }
    }

    // Синхронный рестарт обоих плееров
    private fun restartBothPlayers() {
        try {
            mediaPlayer?.seekTo(0)
            mediaPlayerAlt?.seekTo(0)
            mediaPlayer?.start()
            mediaPlayerAlt?.start()
        } catch (_: Exception) {}
    }

    // Ручной loop: за 1 сек до конца длинного файла делаем seekTo(0) и start() для ОБОИХ плееров, если включён desyncFix
    private fun startManualLoop(player: MediaPlayer?, isSuper: Boolean, desyncFix: Boolean) {
        if (isSuper) {
            loopHandlerSuper?.removeCallbacksAndMessages(null)
            loopHandlerSuper = Handler(context.mainLooper)
        } else {
            loopHandlerNormal?.removeCallbacksAndMessages(null)
            loopHandlerNormal = Handler(context.mainLooper)
        }
        val handler = if (isSuper) loopHandlerSuper else loopHandlerNormal
        val runnable = object : Runnable {
            override fun run() {
                try {
                    val duration = player?.duration ?: 0
                    val pos = player?.currentPosition ?: 0
                    if (duration > 0 && duration - pos < 1000 && desyncFix) {
                        restartBothPlayers()
                    }
                } catch (_: Exception) {}
                handler?.postDelayed(this, 100)
            }
        }
        if (isSuper) {
            loopRunnableSuper = runnable
        } else {
            loopRunnableNormal = runnable
        }
        handler?.post(runnable)
    }

    fun releaseAll() {
        mediaPlayer?.release()
        mediaPlayerAlt?.release()
        loopHandlerNormal?.removeCallbacksAndMessages(null)
        loopHandlerSuper?.removeCallbacksAndMessages(null)
        crossfadeJob?.removeCallbacksAndMessages(null)
    }
}

