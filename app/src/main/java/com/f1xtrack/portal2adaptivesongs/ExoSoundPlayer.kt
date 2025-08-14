package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExoSoundPlayer(private val context: Context) {
    private var normalActive: ExoPlayer? = null
    private var superActive: ExoPlayer? = null
    private var normalStandby: ExoPlayer? = null
    private var superStandby: ExoPlayer? = null
    private var crossfadeJob: Handler? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentTrackName: String? = null
    private var lastNormalVariant: String? = null
    private var lastSuperVariant: String? = null
    private var userPaused: Boolean = false

    private fun log(tag: String, msg: String) = Log.d("ExoSoundPlayer-$tag", msg)

    /**
     * Try to build and prepare an ExoPlayer for the given media item safely.
     * Returns null if preparation fails (e.g., unsupported WAV encoding).
     */
    private fun createPreparedPlayerSafe(item: MediaItem, playWhenReady: Boolean, initialVolume: Float = 0f): ExoPlayer? {
        return try {
            val player = ExoPlayer.Builder(context).build()
            player.setMediaItem(item)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.prepare()
            player.volume = initialVolume
            player.playWhenReady = playWhenReady
            player
        } catch (e: Exception) {
            Log.e("ExoSoundPlayer", "Failed to prepare player for item: $item", e)
            null
        }
    }

    /**
     * Choose a playable variant name by trying candidates until one prepares successfully.
     * Excludes the provided variant (used to avoid immediate repeats).
     */
    private fun choosePlayableVariant(
        trackName: String,
        base: String,
        isUserTrack: Boolean,
        exclude: String?
    ): Pair<String?, ExoPlayer?> {
        val candidates = buildList {
            val variants = listVariantNames(trackName, base, isUserTrack)
            // Prefer anything except the excluded one
            addAll(variants.filter { it != exclude })
            // If everything got filtered out, try the rest
            addAll(variants.filter { it == exclude })
            // Also try the base name itself as a fallback (e.g., normal.wav / superspeed.wav)
            if (base !in this) add(base)
        }.distinct()
        Log.d("ExoSoundPlayer-choose", "Candidates for $trackName/$base (exclude=$exclude): ${candidates.joinToString()}")

        for (variant in candidates) {
            val item = buildMediaItem(trackName, variant, isUserTrack)
            Log.d("ExoSoundPlayer-choose", "Trying variant: $variant -> ${item.localConfiguration?.uri}")
            val p = createPreparedPlayerSafe(item, playWhenReady = true, initialVolume = 0f)
            if (p != null) {
                Log.d("ExoSoundPlayer-choose", "Prepared OK: $variant")
                return variant to p
            } else {
                Log.w("ExoSoundPlayer-choose", "Failed to prepare: $variant")
            }
        }
        return null to null
    }

    private fun listVariantNames(trackName: String, base: String, isUserTrack: Boolean): List<String> {
        val pattern = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return try {
            if (isUserTrack) {
                val dir = File(context.filesDir, "soundtracks/$trackName")
                if (!dir.exists()) return emptyList()
                val all = dir.listFiles()?.map { it.name } ?: emptyList()
                Log.d("ExoSoundPlayer-list", "User track files under ${dir.absolutePath}: ${all.joinToString()}")
                dir.listFiles()?.mapNotNull { file ->
                    val name = file.name
                    if (pattern.matches(name)) name.removeSuffix(".wav") else null
                }?.sortedWith(compareBy({ it.length }, { it })) ?: emptyList()
            } else {
                val files = context.assets.list(trackName) ?: return emptyList()
                Log.d("ExoSoundPlayer-list", "Asset files under assets/$trackName: ${files.joinToString()}")
                files.mapNotNull { name -> if (pattern.matches(name)) name.removeSuffix(".wav") else null }
                    .sortedWith(compareBy({ it.length }, { it }))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun buildMediaItem(trackName: String, variant: String, isUserTrack: Boolean): MediaItem {
        val uri: Uri = if (isUserTrack) {
            Uri.fromFile(File(context.filesDir, "soundtracks/$trackName/$variant.wav"))
        } else {
            Uri.parse("asset:///$trackName/$variant.wav")
        }
        Log.d("ExoSoundPlayer-media", "Built media URI: $uri (user=$isUserTrack)")
        return MediaItem.fromUri(uri)
    }

    private fun createPreparedPlayer(item: MediaItem, playWhenReady: Boolean, initialVolume: Float = 0f): ExoPlayer {
        // Keep non-null version for existing call sites that expect non-null; fall back to safe creation
        return createPreparedPlayerSafe(item, playWhenReady, initialVolume)
            ?: throw IllegalStateException("Unable to prepare player for item: $item")
    }

    fun prepareStandbyFor(modeSuper: Boolean, trackName: String, isUserTrack: Boolean) {
        scope.launch {
            if (modeSuper) {
                if (superStandby != null) return@launch
                val (variant, player) = choosePlayableVariant(
                    trackName = trackName,
                    base = "superspeed",
                    isUserTrack = isUserTrack,
                    exclude = lastSuperVariant
                )
                if (player != null) {
                    superStandby = player
                    lastSuperVariant = variant
                    log("prepareStandbyFor", "Prepared SUPER standby: $variant")
                    // Если пользователь поставил паузу – не проигрываем даже standby
                    if (userPaused) {
                        try { superStandby?.pause() } catch (_: Exception) {}
                    }
                } else {
                    log("prepareStandbyFor", "Failed to prepare SUPER standby")
                }
            } else {
                if (normalStandby != null) return@launch
                val (variant, player) = choosePlayableVariant(
                    trackName = trackName,
                    base = "normal",
                    isUserTrack = isUserTrack,
                    exclude = lastNormalVariant
                )
                if (player != null) {
                    normalStandby = player
                    lastNormalVariant = variant
                    log("prepareStandbyFor", "Prepared NORMAL standby: $variant")
                    // Если пользователь поставил паузу – не проигрываем даже standby
                    if (userPaused) {
                        try { normalStandby?.pause() } catch (_: Exception) {}
                    }
                } else {
                    log("prepareStandbyFor", "Failed to prepare NORMAL standby")
                }
            }
        }
    }

    fun playBoth(trackName: String, isUserTrack: Boolean) {
        // Release existing players but keep internal scope alive
        try { normalActive?.release() } catch (_: Exception) {}
        try { superActive?.release() } catch (_: Exception) {}
        try { normalStandby?.release() } catch (_: Exception) {}
        try { superStandby?.release() } catch (_: Exception) {}
        normalActive = null
        superActive = null
        normalStandby = null
        superStandby = null
        currentTrackName = trackName
        lastNormalVariant = null
        lastSuperVariant = null

        val (normalVar, normalPlayer) = choosePlayableVariant(trackName, "normal", isUserTrack, exclude = null)
        val (superVar, superPlayer) = choosePlayableVariant(trackName, "superspeed", isUserTrack, exclude = null)

        if (normalVar == null || normalPlayer == null) {
            Log.e("ExoSoundPlayer", "Cannot start: no playable NORMAL variant for $trackName")
            return
        }
        if (superVar == null || superPlayer == null) {
            Log.e("ExoSoundPlayer", "Cannot start: no playable SUPERSPEED variant for $trackName")
            return
        }

        lastNormalVariant = normalVar
        lastSuperVariant = superVar

        // активные играют только если не стоит пользовательская пауза
        normalPlayer.playWhenReady = !userPaused
        superPlayer.playWhenReady = !userPaused
        // Стартовые уровни громкости: нормальный слышен, супер — на нуле
        try {
            normalPlayer.volume = 1f
            superPlayer.volume = 0f
        } catch (_: Exception) {}
        normalActive = normalPlayer
        superActive = superPlayer

        // standby тоже запускаем сразу на нуле
        prepareStandbyFor(modeSuper = true, trackName = trackName, isUserTrack = isUserTrack)
        prepareStandbyFor(modeSuper = false, trackName = trackName, isUserTrack = isUserTrack)

        // Если пользователь уже поставил паузу – ставим на паузу все плееры
        if (userPaused) {
            try {
                normalActive?.pause(); superActive?.pause(); normalStandby?.pause(); superStandby?.pause()
            } catch (_: Exception) {}
        }
    }

    fun crossfadeTo(trackName: String, toSuperSpeed: Boolean, isUserTrack: Boolean) {
        if (currentTrackName != trackName) {
            playBoth(trackName, isUserTrack)
            return
        }
        crossfadeJob?.removeCallbacksAndMessages(null)
        crossfadeJob = Handler(context.mainLooper)

        if (toSuperSpeed) {
            val standby = superStandby ?: run { prepareStandbyFor(true, trackName, isUserTrack); superStandby }
            // Fallback: используем уже активный super, если standby ещё не готов
            val target = standby ?: superActive
            if (target == null) {
                Log.e("ExoSoundPlayer", "Skip crossfade to SUPER: no playable player for $trackName")
                return
            }
            // Стартовые уровни
            try {
                target.volume = 0f
                normalActive?.volume = 1f
                // Глушим все прочие
                normalStandby?.volume = 0f
                superActive?.volume = 0f
            } catch (_: Exception) {}
            if (!userPaused) target.playWhenReady = true
            val fromRef = normalActive
            performCrossfade(from = fromRef, to = target) {
                superActive = target
                if (target === superStandby) superStandby = null
                prepareStandbyFor(modeSuper = false, trackName = trackName, isUserTrack = isUserTrack)
                prepareStandbyFor(modeSuper = true, trackName = trackName, isUserTrack = isUserTrack)
                try { fromRef?.volume = 0f } catch (_: Exception) {}
                // Сохраняем состояние паузы
                if (userPaused) {
                    try { superActive?.pause(); normalActive?.pause() } catch (_: Exception) {}
                }
            }
        } else {
            val standby = normalStandby ?: run { prepareStandbyFor(false, trackName, isUserTrack); normalStandby }
            // Fallback: используем уже активный normal, если standby ещё не готов
            val target = standby ?: normalActive
            if (target == null) {
                Log.e("ExoSoundPlayer", "Skip crossfade to NORMAL: no playable player for $trackName")
                return
            }
            try {
                target.volume = 0f
                superActive?.volume = 1f
                superStandby?.volume = 0f
                normalActive?.volume = 0f
            } catch (_: Exception) {}
            if (!userPaused) target.playWhenReady = true
            val fromRef = superActive
            performCrossfade(from = fromRef, to = target) {
                normalActive = target
                if (target === normalStandby) normalStandby = null
                prepareStandbyFor(modeSuper = true, trackName = trackName, isUserTrack = isUserTrack)
                prepareStandbyFor(modeSuper = false, trackName = trackName, isUserTrack = isUserTrack)
                try { fromRef?.volume = 0f } catch (_: Exception) {}
                // Сохраняем состояние паузы
                if (userPaused) {
                    try { superActive?.pause(); normalActive?.pause() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun performCrossfade(from: ExoPlayer?, to: ExoPlayer?, onComplete: () -> Unit) {
        val duration = 1000L
        val steps = 20
        val delay = duration / steps
        for (i in 0..steps) {
            crossfadeJob?.postDelayed({
                val vol = i / steps.toFloat()
                try {
                    to?.volume = vol
                    from?.volume = 1 - vol
                } catch (_: Exception) {}
                if (i == steps) {
                    try { to?.volume = 1f; from?.volume = 0f } catch (_: Exception) {}
                    onComplete()
                }
            }, i * delay)
        }
    }

    fun pause() {
        userPaused = true
        normalActive?.pause()
        superActive?.pause()
    }

    fun play() {
        userPaused = false
        normalActive?.play()
        superActive?.play()
    }

    fun togglePause() {
        val currentlyPlaying = normalActive?.isPlaying ?: false || superActive?.isPlaying ?: false
        if (currentlyPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun releaseAll() {
        try { normalActive?.release() } catch (_: Exception) {}
        try { superActive?.release() } catch (_: Exception) {}
        try { normalStandby?.release() } catch (_: Exception) {}
        try { superStandby?.release() } catch (_: Exception) {}
        normalActive = null
        superActive = null
        normalStandby = null
        superStandby = null
        crossfadeJob?.removeCallbacksAndMessages(null)
    }
}