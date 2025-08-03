package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SoundPlayer(private val context: Context) {
    companion object {
        private const val TAG = "SoundPlayer"
        private const val CROSSFADE_DURATION = 1000L
        private const val CROSSFADE_STEPS = 20
        private const val LONG_FILE_REPEAT_COUNT = 20
    }

    private var normalPlayer: MediaPlayer? = null
    private var superSpeedPlayer: MediaPlayer? = null
    private var crossfadeHandler: Handler? = null
    private var normalLoopHandler: Handler? = null
    private var superSpeedLoopHandler: Handler? = null

    // Генерация длинного файла (20x) в кэше
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
            File(context.cacheDir, "tmp_${fileName}.wav").apply {
                val input = context.assets.open("$trackName/$fileName.wav"); outputStream().use { it.write(input.readBytes()) }; input.close()
            }
        }
    }

    // Удалить длинные файлы и флаги для конкретного трека
    fun removeLongFilesForTrack(trackName: String, isUserTrack: Boolean) {
        val files = listOf(
            File(context.cacheDir, "${trackName}_normal_long.wav"),
            File(context.cacheDir, "${trackName}_superspeed_long.wav"),
            File(context.cacheDir, "${trackName}_normal_long.flag"),
            File(context.cacheDir, "${trackName}_superspeed_long.flag")
        )
        files.forEach { if (it.exists()) it.delete() }
    }

    /**
     * Запускает оба трека одновременно (обычный и ускоренный)
     * @param trackName Название трека
     * @param isUserTrack Является ли трек пользовательским
     * @param startWithSuperSpeed Начинать ли с ускоренной версии
     */
    fun playBoth(trackName: String, isUserTrack: Boolean, startWithSuperSpeed: Boolean = false) {
        releasePlayers()
        clearLoopHandlers()
        
        try {
            initializePlayers(trackName, isUserTrack)
            setInitialVolumes(startWithSuperSpeed)
            startPlayers()
            setupLoopHandlers(trackName, isUserTrack)
        } catch (e: IOException) {
            Log.e(TAG, "Error playing both tracks", e)
        }
    }

    /**
     * Плавно переключает между обычной и ускоренной версией трека
     * @param trackName Название трека
     * @param toSuperSpeed Переключаться ли на ускоренную версию
     * @param isUserTrack Является ли трек пользовательским
     */
    fun crossfadeTo(trackName: String, toSuperSpeed: Boolean, isUserTrack: Boolean) {
        val fromPlayer = if (toSuperSpeed) normalPlayer else superSpeedPlayer
        val toPlayer = if (toSuperSpeed) superSpeedPlayer else normalPlayer
        
        crossfadeHandler?.removeCallbacksAndMessages(null)
        crossfadeHandler = Handler(Looper.getMainLooper())
        
        val stepDelay = CROSSFADE_DURATION / CROSSFADE_STEPS
        
        for (i in 0..CROSSFADE_STEPS) {
            crossfadeHandler?.postDelayed({
                performCrossfadeStep(toSuperSpeed, i, fromPlayer, toPlayer)
            }, i * stepDelay)
        }
    }

    // Проверка, используется ли длинная версия для трека
    fun isLongVersionEnabled(trackName: String, fileName: String): Boolean {
        val flagFile = File(context.cacheDir, "${trackName}_${fileName}_long.flag")
        return flagFile.exists()
    }

    // Установить/снять флаг длинной версии для трека
    fun setLongVersionEnabled(trackName: String, fileName: String, enabled: Boolean) {
        val flagFile = File(context.cacheDir, "${trackName}_${fileName}_long.flag")
        if (enabled) flagFile.writeText("1") else flagFile.delete()
    }

    // Асинхронное создание длинного файла с callback (например, для показа диалога загрузки)
    fun createLongFileAsync(trackName: String, fileName: String, isUserTrack: Boolean, onDone: () -> Unit) {
        Thread {
            getOrCreateLongFile(trackName, fileName, isUserTrack, force = true)
            setLongVersionEnabled(trackName, fileName, true)
            onDone()
        }.start()
    }

    // Очистка кэша длинных файлов и флагов
    fun clearCache() {
        context.cacheDir.listFiles()?.forEach {
            if (it.name.endsWith("_long.wav") || it.name.endsWith("_long.flag")) it.delete()
        }
    }

    // Вспомогательные методы для инициализации плееров
    private fun releasePlayers() {
        normalPlayer?.release()
        superSpeedPlayer?.release()
        normalPlayer = null
        superSpeedPlayer = null
    }

    private fun clearLoopHandlers() {
        normalLoopHandler?.removeCallbacksAndMessages(null)
        superSpeedLoopHandler?.removeCallbacksAndMessages(null)
    }

    private fun initializePlayers(trackName: String, isUserTrack: Boolean) {
        val prefs = context.getSharedPreferences("track_settings", Context.MODE_PRIVATE)
        val useLong = prefs.getBoolean("long_$trackName", false)
        
        val normalFile = if (useLong) {
            getOrCreateLongFile(trackName, "normal", isUserTrack)
        } else {
            getShortFile(trackName, "normal", isUserTrack)
        }
        
        val superSpeedFile = if (useLong) {
            getOrCreateLongFile(trackName, "superspeed", isUserTrack)
        } else {
            getShortFile(trackName, "superspeed", isUserTrack)
        }
        
        normalPlayer = MediaPlayer().apply {
            setDataSource(normalFile.absolutePath)
            prepare()
        }
        
        superSpeedPlayer = MediaPlayer().apply {
            setDataSource(superSpeedFile.absolutePath)
            prepare()
        }
    }

    private fun setInitialVolumes(startWithSuperSpeed: Boolean) {
        if (startWithSuperSpeed) {
            normalPlayer?.setVolume(0f, 0f)
            superSpeedPlayer?.setVolume(1f, 1f)
        } else {
            normalPlayer?.setVolume(1f, 1f)
            superSpeedPlayer?.setVolume(0f, 0f)
        }
    }

    private fun startPlayers() {
        normalPlayer?.apply {
            seekTo(0)
            start()
        }
        superSpeedPlayer?.apply {
            seekTo(0)
            start()
        }
    }

    private fun setupLoopHandlers(trackName: String, isUserTrack: Boolean) {
        val prefs = context.getSharedPreferences("track_settings", Context.MODE_PRIVATE)
        val desyncFix = prefs.getBoolean("desync_$trackName", true)
        
        startManualLoop(normalPlayer, false, desyncFix)
        startManualLoop(superSpeedPlayer, true, desyncFix)
    }

    private fun performCrossfadeStep(toSuperSpeed: Boolean, step: Int, fromPlayer: MediaPlayer?, toPlayer: MediaPlayer?) {
        val volume = step / CROSSFADE_STEPS.toFloat()
        
        try {
            if (toSuperSpeed) {
                toPlayer?.setVolume(volume, volume)
                fromPlayer?.setVolume(1f - 0.9f * volume, 1f - 0.9f * volume)
            } else {
                toPlayer?.setVolume(0.1f + 0.9f * volume, 0.1f + 0.9f * volume)
                fromPlayer?.setVolume(1f - volume, 1f - volume)
            }
            
            // Финальный шаг - устанавливаем точные значения
            if (step == CROSSFADE_STEPS) {
                if (toSuperSpeed) {
                    fromPlayer?.setVolume(0.1f, 0.1f)
                    toPlayer?.setVolume(1f, 1f)
                } else {
                    toPlayer?.setVolume(1f, 1f)
                    fromPlayer?.setVolume(0f, 0f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during crossfade step", e)
        }
    }

    // Синхронный рестарт обоих плееров
    private fun restartBothPlayers() {
        try {
            normalPlayer?.seekTo(0)
            superSpeedPlayer?.seekTo(0)
            normalPlayer?.start()
            superSpeedPlayer?.start()
        } catch (_: Exception) {}
    }

    // Ручной loop: за 1 сек до конца длинного файла делаем seekTo(0) и start() для ОБОИХ плееров, если включён desyncFix
    private fun startManualLoop(player: MediaPlayer?, isSuper: Boolean, desyncFix: Boolean) {
        val handler = if (isSuper) {
            superSpeedLoopHandler?.removeCallbacksAndMessages(null)
            superSpeedLoopHandler = Handler(Looper.getMainLooper())
            superSpeedLoopHandler
        } else {
            normalLoopHandler?.removeCallbacksAndMessages(null)
            normalLoopHandler = Handler(Looper.getMainLooper())
            normalLoopHandler
        }
        
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
        
        handler?.post(runnable)
    }

    // Очистка ресурсов
    fun releaseAll() {
        releasePlayers()
        clearLoopHandlers()
        crossfadeHandler?.removeCallbacksAndMessages(null)
    }
}

