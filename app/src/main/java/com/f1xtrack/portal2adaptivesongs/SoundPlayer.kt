package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SoundPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaPlayerAlt: MediaPlayer? = null
    private var crossfadeJob: Handler? = null
    private var loopHandlerNormal: Handler? = null
    private var loopHandlerSuper: Handler? = null
    private var loopRunnableNormal: Runnable? = null
    private var loopRunnableSuper: Runnable? = null

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

    // Запуск обоих треков одновременно (оба MediaPlayer), длинные версии только если стоит флаг
    fun playBoth(trackName: String, isUserTrack: Boolean) {
        mediaPlayer?.release()
        mediaPlayerAlt?.release()
        loopHandlerNormal?.removeCallbacksAndMessages(null)
        loopHandlerSuper?.removeCallbacksAndMessages(null)
        mediaPlayer = MediaPlayer()
        mediaPlayerAlt = MediaPlayer()
        try {
            val prefs = context.getSharedPreferences("track_settings", Context.MODE_PRIVATE)
            val useLong = prefs.getBoolean("long_$trackName", false)
            val fileNormal = if (useLong) getOrCreateLongFile(trackName, "normal", isUserTrack) else getShortFile(trackName, "normal", isUserTrack)
            val fileSuper = if (useLong) getOrCreateLongFile(trackName, "superspeed", isUserTrack) else getShortFile(trackName, "superspeed", isUserTrack)
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
            val desyncFix = prefs.getBoolean("desync_$trackName", true)
            startManualLoop(mediaPlayer, false, desyncFix)
            startManualLoop(mediaPlayerAlt, true, desyncFix)
        } catch (e: IOException) {
            Log.e("SoundPlayer", "Error playing both", e)
        }
    }

    // Кроссфейд между двумя MediaPlayer (оба всегда играют, меняется только громкость)
    fun crossfadeTo(trackName: String, toSuperSpeed: Boolean, isUserTrack: Boolean) {
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

