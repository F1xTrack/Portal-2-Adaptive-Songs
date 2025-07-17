package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.media.audiofx.Visualizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/**
 * Класс для управления воспроизведением адаптивной музыки
 * 
 * Основные функции:
 * - Воспроизведение двух версий трека одновременно (normal и superspeed)
 * - Плавный переход между версиями через кроссфейд
 * - Генерация и воспроизведение спектрограмм для визуализации
 * - Поддержка длинных версий треков (20x повторений)
 * - Ручное управление зацикливанием для синхронизации
 * - Визуализация спектра в реальном времени через Visualizer API
 */
class SoundPlayer(private val context: Context) {
    
    // Основные MediaPlayer для воспроизведения
    private var mediaPlayer: MediaPlayer? = null      // Для normal версии
    private var mediaPlayerAlt: MediaPlayer? = null  // Для superspeed версии
    
    // Обработчики для управления зацикливанием и кроссфейдом
    private var crossfadeJob: Handler? = null        // Управление кроссфейдом
    private var loopHandlerNormal: Handler? = null   // Зацикливание normal версии
    private var loopHandlerSuper: Handler? = null    // Зацикливание superspeed версии
    private var loopRunnableNormal: Runnable? = null // Задача зацикливания normal
    private var loopRunnableSuper: Runnable? = null  // Задача зацикливания superspeed
    
    // Визуализация спектра
    private var visualizer: Visualizer? = null       // API для анализа аудио
    private var spectrumCallback: ((FloatArray) -> Unit)? = null // Колбэк для передачи спектра

    /**
     * Установка слушателя для получения данных спектра
     * @param listener Функция, которая будет вызываться с массивом амплитуд
     */
    fun setSpectrumListener(listener: ((FloatArray) -> Unit)?) {
        spectrumCallback = listener
    }

    /**
     * Создание или получение длинной версии файла (20x повторений)
     * Используется для создания бесконечных треков
     * 
     * @param trackName Название трека
     * @param fileName Имя файла (normal или superspeed)
     * @param isUserTrack Флаг пользовательского трека
     * @param force Принудительное пересоздание файла
     * @return Файл с длинной версией
     */
    private fun getOrCreateLongFile(trackName: String, fileName: String, isUserTrack: Boolean, force: Boolean = false): File {
        val cacheFile = File(context.cacheDir, "${trackName}_${fileName}_long.wav")
        if (cacheFile.exists() && !force) return cacheFile
        
        try {
            // Читаем исходный файл
            val data = if (isUserTrack) {
                val userFile = File(context.filesDir, "soundtracks/$trackName/$fileName.wav")
                userFile.readBytes()
            } else {
                val input = context.assets.open("$trackName/$fileName.wav")
                val bytes = input.readBytes()
                input.close()
                bytes
            }
            
            // Создаем длинную версию (20 повторений)
            val out = FileOutputStream(cacheFile)
            repeat(20) { out.write(data) }
            out.close()
        } catch (e: IOException) {
            Log.e("SoundPlayer", "Error creating long file", e)
        }
        return cacheFile
    }

    /**
     * Получение короткой версии файла
     * Для пользовательских треков - прямой путь, для asset - копия в кэш
     */
    private fun getShortFile(trackName: String, fileName: String, isUserTrack: Boolean): File {
        return if (isUserTrack) {
            File(context.filesDir, "soundtracks/$trackName/$fileName.wav")
        } else {
            File(context.cacheDir, "tmp_${fileName}.wav").apply {
                val input = context.assets.open("$trackName/$fileName.wav")
                outputStream().use { it.write(input.readBytes()) }
                input.close()
            }
        }
    }

    /**
     * Удаление длинных файлов и флагов для конкретного трека
     * Используется при изменении настроек трека
     */
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
     * Запуск воспроизведения обоих треков одновременно
     * Оба MediaPlayer играют одновременно, но с разной громкостью
     * 
     * @param trackName Название трека
     * @param isUserTrack Флаг пользовательского трека
     */
    fun playBoth(trackName: String, isUserTrack: Boolean) {
        // Освобождаем предыдущие ресурсы
        mediaPlayer?.release()
        mediaPlayerAlt?.release()
        loopHandlerNormal?.removeCallbacksAndMessages(null)
        loopHandlerSuper?.removeCallbacksAndMessages(null)
        
        // Создаем новые плееры
        mediaPlayer = MediaPlayer()
        mediaPlayerAlt = MediaPlayer()
        
        try {
            // Читаем настройки трека
            val prefs = context.getSharedPreferences("track_settings", Context.MODE_PRIVATE)
            val useLong = prefs.getBoolean("long_$trackName", false)
            val desyncFix = prefs.getBoolean("desync_$trackName", true)
            
            // Выбираем файлы в зависимости от настроек
            val fileNormal = if (useLong) getOrCreateLongFile(trackName, "normal", isUserTrack) else getShortFile(trackName, "normal", isUserTrack)
            val fileSuper = if (useLong) getOrCreateLongFile(trackName, "superspeed", isUserTrack) else getShortFile(trackName, "superspeed", isUserTrack)
            
            // Настраиваем плееры
            mediaPlayer?.setDataSource(fileNormal.absolutePath)
            mediaPlayerAlt?.setDataSource(fileSuper.absolutePath)
            mediaPlayer?.prepare()
            mediaPlayerAlt?.prepare()
            
            // Устанавливаем начальную громкость (normal громко, superspeed тихо)
            mediaPlayer?.setVolume(1f, 1f)
            mediaPlayerAlt?.setVolume(0f, 0f)
            
            // Запускаем воспроизведение
            mediaPlayer?.seekTo(0)
            mediaPlayerAlt?.seekTo(0)
            mediaPlayer?.start()
            mediaPlayerAlt?.start()
            
            // Запускаем ручное зацикливание
            startManualLoop(mediaPlayer, false, desyncFix)
            startManualLoop(mediaPlayerAlt, true, desyncFix)
            
            // Настраиваем визуализатор с задержкой для стабильности
            val sessionId = mediaPlayer?.audioSessionId ?: 0
            try {
                setupVisualizerForSession(sessionId)
            } catch (e: Exception) {
                android.os.Handler(context.mainLooper).postDelayed({
                    try {
                        setupVisualizerForSession(sessionId)
                    } catch (e2: Exception) {
                        android.util.Log.e("SoundPlayer", "Visualizer retry failed", e2)
                    }
                }, 300)
            }
        } catch (e: IOException) {
            Log.e("SoundPlayer", "Error playing both", e)
        }
    }

    /**
     * Плавный переход между версиями трека через кроссфейд
     * Оба плеера играют одновременно, меняется только громкость
     * 
     * @param trackName Название трека
     * @param toSuperSpeed Переключение на быструю версию
     * @param isUserTrack Флаг пользовательского трека
     */
    fun crossfadeTo(trackName: String, toSuperSpeed: Boolean, isUserTrack: Boolean) {
        val fromPlayer = if (toSuperSpeed) mediaPlayer else mediaPlayerAlt
        val toPlayer = if (toSuperSpeed) mediaPlayerAlt else mediaPlayer
        
        // Настройки кроссфейда
        val duration = 1000L  // Длительность перехода в миллисекундах
        val steps = 20        // Количество шагов
        val delay = duration / steps
        
        // Останавливаем предыдущий кроссфейд
        crossfadeJob?.removeCallbacksAndMessages(null)
        crossfadeJob = Handler(context.mainLooper)
        
        // Выполняем кроссфейд пошагово
        for (i in 0..steps) {
            crossfadeJob?.postDelayed({
                val vol = i / steps.toFloat()
                try {
                    if (toSuperSpeed) {
                        // Переход к superspeed: увеличиваем громкость superspeed, уменьшаем normal
                        toPlayer?.setVolume(vol, vol)
                        fromPlayer?.setVolume(1 - 0.9f * vol, 1 - 0.9f * vol)
                    } else {
                        // Переход к normal: увеличиваем громкость normal, уменьшаем superspeed
                        toPlayer?.setVolume(0.1f + 0.9f * vol, 0.1f + 0.9f * vol)
                        fromPlayer?.setVolume(1 - vol, 1 - vol)
                    }
                } catch (e: Exception) {
                    Log.e("SoundPlayer", "setVolume error", e)
                }
                
                // Финальная установка громкости
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

    /**
     * Проверка, используется ли длинная версия для трека
     */
    fun isLongVersionEnabled(trackName: String, fileName: String): Boolean {
        val flagFile = File(context.cacheDir, "${trackName}_${fileName}_long.flag")
        return flagFile.exists()
    }

    /**
     * Установка/снятие флага длинной версии для трека
     */
    fun setLongVersionEnabled(trackName: String, fileName: String, enabled: Boolean) {
        val flagFile = File(context.cacheDir, "${trackName}_${fileName}_long.flag")
        if (enabled) flagFile.writeText("1") else flagFile.delete()
    }

    /**
     * Асинхронное создание длинного файла с callback
     * Используется для показа диалога загрузки
     */
    fun createLongFileAsync(trackName: String, fileName: String, isUserTrack: Boolean, onDone: () -> Unit) {
        Thread {
            getOrCreateLongFile(trackName, fileName, isUserTrack, force = true)
            setLongVersionEnabled(trackName, fileName, true)
            onDone()
        }.start()
    }

    /**
     * Очистка кэша длинных файлов и флагов
     */
    fun clearCache() {
        context.cacheDir.listFiles()?.forEach {
            if (it.name.endsWith("_long.wav") || it.name.endsWith("_long.flag")) it.delete()
        }
    }

    /**
     * Синхронный рестарт обоих плееров
     * Используется для синхронизации при зацикливании
     */
    private fun restartBothPlayers() {
        try {
            mediaPlayer?.seekTo(0)
            mediaPlayerAlt?.seekTo(0)
            mediaPlayer?.start()
            mediaPlayerAlt?.start()
        } catch (_: Exception) {}
    }

    /**
     * Ручное управление зацикливанием
     * За 1 секунду до конца файла делает seekTo(0) для обоих плееров
     * Это предотвращает рассинхронизацию между версиями
     * 
     * @param player MediaPlayer для управления
     * @param isSuper Флаг superspeed версии
     * @param desyncFix Включить ли исправление рассинхронизации
     */
    private fun startManualLoop(player: MediaPlayer?, isSuper: Boolean, desyncFix: Boolean) {
        // Останавливаем предыдущий цикл
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
                    
                    // Если до конца меньше секунды и включен desyncFix - рестартуем оба плеера
                    if (duration > 0 && duration - pos < 1000 && desyncFix) {
                        restartBothPlayers()
                    }
                } catch (_: Exception) {}
                
                // Проверяем каждые 100мс
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

    /**
     * Настройка визуализатора для анализа аудио
     * Использует Visualizer API для получения FFT данных
     * 
     * @param sessionId ID аудиосессии MediaPlayer
     */
    private fun setupVisualizerForSession(sessionId: Int) {
        try {
            Log.d("SoundPlayer", "Init Visualizer for sessionId=$sessionId")
            visualizer?.release()
            
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        // Не используем waveform данные
                    }
                    
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        try {
                            Log.d("SoundPlayer", "onFftDataCapture: fft=${fft?.joinToString(",", limit=8)}")
                            
                            if (fft != null && spectrumCallback != null) {
                                val n = fft.size / 2
                                if (n > 0) {
                                    // Преобразуем FFT данные в амплитуды
                                    val amplitudes = FloatArray(n) { i ->
                                        val re = fft[2 * i].toInt()
                                        val im = fft[2 * i + 1].toInt()
                                        val mag = Math.hypot(re.toDouble(), im.toDouble()).toFloat()
                                        (mag / 128f).coerceIn(0f, 1f) // Нормализация
                                    }
                                    Log.d("SoundPlayer", "amplitudes[0..7]=${amplitudes.take(8)}")
                                    spectrumCallback?.invoke(amplitudes)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SoundPlayer", "Visualizer FFT error", e)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                
                enabled = true
                Log.d("SoundPlayer", "Visualizer enabled: $enabled, captureSize=$captureSize")
            }
        } catch (e: Exception) {
            Log.e("SoundPlayer", "Visualizer init error", e)
        }
    }

    /**
     * Генерация предрендеренной спектрограммы из WAV файла
     * Создает бинарный файл с FFT данными для каждого кадра
     * 
     * @param wavFile Исходный WAV файл
     * @param spectrumFile Файл для сохранения спектрограммы
     * @param bands Количество частотных полос
     * @param fps Частота кадров спектрограммы
     */
    fun generateSpectrumForWav(wavFile: File, spectrumFile: File, bands: Int = 64, fps: Int = 20) {
        try {
            val wavData = wavFile.readBytes()
            
            // Находим начало PCM данных в WAV файле
            val dataOffset = wavData.indexOfSequence(byteArrayOf('d'.toByte(), 'a'.toByte(), 't'.toByte(), 'a'.toByte()))
            if (dataOffset < 0) throw Exception("WAV: data chunk not found")
            val pcmOffset = dataOffset + 8 // "data" + size (4+4)
            val pcm = wavData.copyOfRange(pcmOffset, wavData.size)
            
            // Параметры WAV (упрощенно, в реальности нужно читать из заголовка)
            val sampleRate = 44100
            val bytesPerSample = 2
            val channels = 1 // mono
            
            // Преобразуем байты в сэмплы
            val samples = ShortArray(pcm.size / bytesPerSample) { i ->
                ((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort()
            }
            
            // Параметры анализа
            val windowSize = 1024
            val hop = sampleRate / fps
            val nFrames = (samples.size - windowSize) / hop
            val spectrum = ArrayList<FloatArray>(nFrames)
            
            // Анализируем каждый кадр
            for (frame in 0 until nFrames) {
                val start = frame * hop
                val window = samples.sliceArray(start until (start + windowSize))
                val fft = fftMag(window, bands)
                spectrum.add(fft)
            }
            
            // Сохраняем как бинарный файл: [frame0][frame1]...[frameN]
            // Каждый frame содержит bands float32 значений
            spectrumFile.outputStream().use { out ->
                for (frame in spectrum) {
                    val buf = ByteBuffer.allocate(4 * bands).order(ByteOrder.LITTLE_ENDIAN)
                    frame.forEach { buf.putFloat(it) }
                    out.write(buf.array())
                }
            }
            
            Log.d("SoundPlayer", "Spectrum generated: ${spectrumFile.absolutePath}, size=${spectrumFile.length()} bytes, frames=$nFrames")
        } catch (e: Exception) {
            Log.e("SoundPlayer", "generateSpectrumForWav error", e)
        }
    }

    /**
     * Быстрое преобразование Фурье и группировка в частотные полосы
     * 
     * @param samples Аудиосэмплы
     * @param bands Количество частотных полос
     * @return Массив амплитуд для каждой полосы
     */
    private fun fftMag(samples: ShortArray, bands: Int): FloatArray {
        val n = samples.size
        val re = DoubleArray(n) { samples[it].toDouble() }
        val im = DoubleArray(n) { 0.0 }
        
        // Выполняем FFT
        fft(re, im)
        
        // Группируем в полосы
        val mags = FloatArray(bands)
        val binPerBand = n / 2 / bands
        
        for (b in 0 until bands) {
            var sum = 0.0
            for (i in b * binPerBand until (b + 1) * binPerBand) {
                val mag = sqrt(re[i].pow(2) + im[i].pow(2))
                sum += mag
            }
            mags[b] = (sum / binPerBand).toFloat() / 32768f // Нормализация
        }
        return mags
    }

    /**
     * Простая реализация FFT алгоритма Cooley-Tukey (in-place)
     * 
     * @param re Массив действительных частей
     * @param im Массив мнимых частей
     */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        if (n == 1) return
        
        // Разделяем на четные и нечетные индексы
        val evenRe = DoubleArray(n / 2)
        val evenIm = DoubleArray(n / 2)
        val oddRe = DoubleArray(n / 2)
        val oddIm = DoubleArray(n / 2)
        
        for (i in 0 until n / 2) {
            evenRe[i] = re[2 * i]
            evenIm[i] = im[2 * i]
            oddRe[i] = re[2 * i + 1]
            oddIm[i] = im[2 * i + 1]
        }
        
        // Рекурсивно вычисляем FFT для четных и нечетных частей
        fft(evenRe, evenIm)
        fft(oddRe, oddIm)
        
        // Объединяем результаты
        for (k in 0 until n / 2) {
            val t = -2 * Math.PI * k / n
            val cosT = cos(t)
            val sinT = sin(t)
            val tre = cosT * oddRe[k] - sinT * oddIm[k]
            val tim = sinT * oddRe[k] + cosT * oddIm[k]
            
            re[k] = evenRe[k] + tre
            im[k] = evenIm[k] + tim
            re[k + n / 2] = evenRe[k] - tre
            im[k + n / 2] = evenIm[k] - tim
        }
    }

    /**
     * Генерация спектрограмм для трека при импорте
     * Создает спектрограммы для обеих версий (normal и superspeed)
     */
    fun generateSpectraForTrack(trackName: String, isUserTrack: Boolean) {
        try {
            val dir = if (isUserTrack) File(context.filesDir, "soundtracks/$trackName") else context.cacheDir
            val normalWav = if (isUserTrack) File(dir, "normal.wav") else File(dir, "tmp_${trackName}_normal.wav")
            val superWav = if (isUserTrack) File(dir, "superspeed.wav") else File(dir, "tmp_${trackName}_superspeed.wav")
            val normalSpec = File(dir, "normal.spectrum")
            val superSpec = File(dir, "superspeed.spectrum")
            
            // Генерируем спектрограммы только если их нет
            if (normalWav.exists() && !normalSpec.exists()) generateSpectrumForWav(normalWav, normalSpec)
            if (superWav.exists() && !superSpec.exists()) generateSpectrumForWav(superWav, superSpec)
        } catch (e: Exception) {
            Log.e("SoundPlayer", "generateSpectraForTrack error", e)
        }
    }

    /**
     * Чтение спектрограммы из файла
     * 
     * @param spectrumFile Файл спектрограммы
     * @param bands Количество частотных полос
     * @return Список кадров спектрограммы
     */
    fun readSpectrum(spectrumFile: File, bands: Int = 64): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        try {
            val bytes = spectrumFile.readBytes()
            val frameSize = 4 * bands
            val nFrames = bytes.size / frameSize
            
            // Читаем каждый кадр
            for (i in 0 until nFrames) {
                val buf = ByteBuffer.wrap(bytes, i * frameSize, frameSize).order(ByteOrder.LITTLE_ENDIAN)
                val arr = FloatArray(bands) { buf.getFloat() }
                frames.add(arr)
            }
            
            Log.d("SoundPlayer", "Read spectrum: ${spectrumFile.name}, frames=$nFrames, size=${bytes.size}")
        } catch (e: Exception) {
            Log.e("SoundPlayer", "readSpectrum error", e)
        }
        return frames
    }

    /**
     * Анимация спектрограммы во время воспроизведения
     * Воспроизводит предрендеренную спектрограмму в реальном времени
     * 
     * @param trackName Название трека
     * @param isUserTrack Флаг пользовательского трека
     * @param update Функция обновления UI с данными спектра
     */
    fun playSpectrumForTrack(trackName: String, isUserTrack: Boolean, update: (FloatArray) -> Unit) {
        val dir = if (isUserTrack) File(context.filesDir, "soundtracks/$trackName") else context.cacheDir
        val normalSpec = File(dir, "normal.spectrum")
        
        if (!normalSpec.exists()) {
            Log.d("SoundPlayer", "No spectrum file for $trackName")
            return
        }
        
        val frames = readSpectrum(normalSpec)
        if (frames.isEmpty()) {
            Log.d("SoundPlayer", "Spectrum frames empty for $trackName")
            return
        }
        
        Log.d("SoundPlayer", "Start spectrum animation for $trackName, frames=${frames.size}")
        
        // Настраиваем анимацию
        val handler = Handler(context.mainLooper)
        val fps = 20
        val frameDuration = 1000L / fps
        var frameIdx = 0
        
        val runnable = object : Runnable {
            override fun run() {
                if (frameIdx < frames.size) {
                    update(frames[frameIdx])
                    frameIdx++
                    handler.postDelayed(this, frameDuration)
                }
            }
        }
        
        handler.post(runnable)
    }

    /**
     * Освобождение всех ресурсов
     * Вызывается при уничтожении активности
     */
    fun releaseAll() {
        mediaPlayer?.release()
        mediaPlayerAlt?.release()
        loopHandlerNormal?.removeCallbacksAndMessages(null)
        loopHandlerSuper?.removeCallbacksAndMessages(null)
        crossfadeJob?.removeCallbacksAndMessages(null)
        visualizer?.release()
        visualizer = null
    }

    /**
     * Функция-расширение для поиска подпоследовательности в ByteArray
     * Используется для поиска "data" чанка в WAV файле
     */
    private fun ByteArray.indexOfSequence(seq: ByteArray): Int {
        outer@ for (i in 0..this.size - seq.size) {
            for (j in seq.indices) {
                if (this[i + j] != seq[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

