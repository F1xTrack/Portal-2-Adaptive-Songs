package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.f1xtrack.portal2adaptivesongs.ExoSoundPlayer
import com.f1xtrack.portal2adaptivesongs.SpeedTracker
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

private const val PREFS_STATS = "track_stats"
private const val DEFAULT_THRESHOLD_KMH = 10
private const val DEFAULT_HYSTERESIS_KMH = 3

@Immutable
data class NowPlaybackUiState(
    val availableTracks: ImmutableList<TrackItem> = persistentListOf(),
    val selectedTrackName: String? = null,
    val currentTrackDurationMs: Int = 0,
    val currentTrackPlays: Int = 0,
    val isPlaying: Boolean = false,
    val isSuperSpeed: Boolean = false,
    val currentSpeedKmh: Float = 0f,
    val thresholdKmh: Int = DEFAULT_THRESHOLD_KMH,
    val hysteresisKmh: Int = DEFAULT_HYSTERESIS_KMH,
    val volumePercent: Int = 100,
    val hasLocationPermission: Boolean = false,
    val isTrackingActive: Boolean = false,
) {
    @Immutable
    data class TrackItem(
        val name: String,
        val durationMs: Int,
        val plays: Int,
        val isSelected: Boolean,
        val isUserTrack: Boolean,
    )
}

internal enum class PlaybackMode {
    Normal,
    SuperSpeed,
}

internal fun resolvePlaybackMode(
    speedKmh: Float,
    thresholdKmh: Int,
    hysteresisKmh: Int,
    wasSuperSpeed: Boolean,
): PlaybackMode {
    return if (wasSuperSpeed) {
        if (speedKmh < thresholdKmh - hysteresisKmh) PlaybackMode.Normal else PlaybackMode.SuperSpeed
    } else {
        if (speedKmh >= thresholdKmh) PlaybackMode.SuperSpeed else PlaybackMode.Normal
    }
}

internal fun formatDurationLabel(durationMs: Int): String {
    if (durationMs <= 0) return "—"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

class NowPlaybackController(
    private val appContext: Context,
) {
    private val player = ExoSoundPlayer(appContext)
    private val trackPlayCount = mutableMapOf<String, Int>()
    private val trackDuration = mutableMapOf<String, Int>()
    private var userTracks: Set<String> = emptySet()
    private var selectedTrack: String? = null
    private var isSuperSpeed = false
    private var lastTrack: String? = null

    private val tracker = SpeedTracker(
        context = appContext,
        onSpeedBurst = {},
        onSpeedChange = ::onSpeedChanged,
    )

    private val _uiState = MutableStateFlow(NowPlaybackUiState())
    val uiState: StateFlow<NowPlaybackUiState> = _uiState.asStateFlow()

    init {
        loadStats()
        tracker.setThreshold(DEFAULT_THRESHOLD_KMH.toFloat())
        refreshTracks()
    }

    fun refreshTracks() {
        val assetTracks = appContext.assets.list("")
            ?.filter { name ->
                assetFolderHasAnyVariant(name, "normal") && assetFolderHasAnyVariant(name, "superspeed")
            }
            ?.sortedBy { it.lowercase() }
            ?: emptyList()

        val userDir = File(appContext.filesDir, "soundtracks")
        val importedTracks = userDir.listFiles()
            ?.filter { dir ->
                dir.isDirectory && userFolderHasAnyVariant(dir, "normal") && userFolderHasAnyVariant(dir, "superspeed")
            }
            ?.map { it.name }
            ?.sortedBy { it.lowercase() }
            ?: emptyList()

        userTracks = importedTracks.toSet()
        val allTracks = (assetTracks + importedTracks).distinct().sortedBy { it.lowercase() }
        allTracks.forEach { name ->
            if (!trackDuration.containsKey(name)) {
                trackDuration[name] = resolveTrackDuration(name, isUserTrack = userTracks.contains(name))
            }
        }

        val selected = selectedTrack
        val trackItems = allTracks.map { name ->
            NowPlaybackUiState.TrackItem(
                name = name,
                durationMs = trackDuration[name] ?: 0,
                plays = trackPlayCount[name] ?: 0,
                isSelected = name == selected,
                isUserTrack = userTracks.contains(name),
            )
        }.toImmutableList()

        _uiState.update { state ->
            state.copy(
                availableTracks = trackItems,
                selectedTrackName = selected,
                currentTrackDurationMs = selected?.let { trackDuration[it] } ?: 0,
                currentTrackPlays = selected?.let { trackPlayCount[it] } ?: 0,
            )
        }
    }

    fun onTrackSelected(trackName: String) {
        if (selectedTrack == trackName) {
            togglePlayback()
            return
        }

        player.releaseAll()
        selectedTrack = trackName
        isSuperSpeed = false
        lastTrack = trackName

        val isUserTrack = userTracks.contains(trackName)
        player.playBoth(trackName, isUserTrack)
        player.setMasterVolume(_uiState.value.volumePercent / 100f)

        trackPlayCount[trackName] = (trackPlayCount[trackName] ?: 0) + 1
        saveStats()
        refreshTracks()
        _uiState.update { state ->
            state.copy(
                selectedTrackName = trackName,
                currentTrackDurationMs = trackDuration[trackName] ?: 0,
                currentTrackPlays = trackPlayCount[trackName] ?: 0,
                isPlaying = true,
                isSuperSpeed = false,
            )
        }
    }

    fun togglePlayback() {
        if (selectedTrack == null) return
        player.togglePause()
        _uiState.update { it.copy(isPlaying = player.isPlaying()) }
    }

    fun setThreshold(value: Int) {
        val normalized = value.coerceIn(5, 60)
        tracker.setThreshold(normalized.toFloat())
        _uiState.update { it.copy(thresholdKmh = normalized) }
    }

    fun setHysteresis(value: Int) {
        _uiState.update { it.copy(hysteresisKmh = value.coerceIn(1, 20)) }
    }

    fun setVolume(percent: Int) {
        val normalized = percent.coerceIn(0, 100)
        player.setMasterVolume(normalized / 100f)
        _uiState.update { it.copy(volumePercent = normalized) }
    }

    fun onLocationPermissionChanged(granted: Boolean) {
        if (granted) {
            tracker.start()
        } else {
            tracker.stop()
        }
        _uiState.update {
            it.copy(
                hasLocationPermission = granted,
                isTrackingActive = granted,
                currentSpeedKmh = if (granted) it.currentSpeedKmh else 0f,
            )
        }
    }

    fun release() {
        tracker.stop()
        player.releaseAll()
    }

    private fun onSpeedChanged(speedKmh: Float) {
        val currentTrack = selectedTrack
        if (currentTrack.isNullOrBlank()) {
            _uiState.update { it.copy(currentSpeedKmh = speedKmh) }
            return
        }

        val currentState = _uiState.value
        val nextMode = resolvePlaybackMode(
            speedKmh = speedKmh,
            thresholdKmh = currentState.thresholdKmh,
            hysteresisKmh = currentState.hysteresisKmh,
            wasSuperSpeed = isSuperSpeed,
        )
        val shouldBeSuperSpeed = nextMode == PlaybackMode.SuperSpeed
        if (lastTrack != currentTrack) {
            lastTrack = currentTrack
            isSuperSpeed = false
        }
        if (shouldBeSuperSpeed != isSuperSpeed) {
            isSuperSpeed = shouldBeSuperSpeed
            player.crossfadeTo(currentTrack, shouldBeSuperSpeed, userTracks.contains(currentTrack))
        }
        _uiState.update {
            it.copy(
                currentSpeedKmh = speedKmh,
                isSuperSpeed = isSuperSpeed,
            )
        }
    }

    private fun loadStats() {
        val statsPrefs = appContext.getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)
        statsPrefs.getStringSet("play_counts", null)?.forEach {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) {
                trackPlayCount[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
        statsPrefs.getStringSet("durations", null)?.forEach {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) {
                trackDuration[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
    }

    private fun saveStats() {
        val statsPrefs = appContext.getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)
        val playCounts = trackPlayCount.map { "${it.key}|${it.value}" }.toSet()
        val durations = trackDuration.map { "${it.key}|${it.value}" }.toSet()
        statsPrefs.edit().putStringSet("play_counts", playCounts).putStringSet("durations", durations).apply()
    }

    private fun resolveTrackDuration(trackName: String, isUserTrack: Boolean): Int {
        val path = findFirstVariantPath(trackName, "normal", isUserTrack)?.absolutePath ?: return 0
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        } finally {
            retriever.release()
        }
    }

    private fun assetFolderHasAnyVariant(folder: String, base: String): Boolean {
        return try {
            val files = appContext.assets.list(folder) ?: return false
            val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
            files.any { regex.matches(it) }
        } catch (_: Exception) {
            false
        }
    }

    private fun userFolderHasAnyVariant(dir: File, base: String): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return dir.listFiles()?.any { file -> regex.matches(file.name) } == true
    }

    private fun findFirstVariantPath(track: String, base: String, isUserTrack: Boolean): File? {
        val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        return try {
            if (isUserTrack) {
                val dir = File(appContext.filesDir, "soundtracks/$track")
                dir.listFiles()?.firstOrNull { file -> regex.matches(file.name) }
            } else {
                val files = appContext.assets.list(track) ?: return null
                val candidate = files.firstOrNull { name -> regex.matches(name) } ?: return null
                val tempFile = File(appContext.cacheDir, "tmp_${track}_${candidate}")
                if (!tempFile.exists()) {
                    appContext.assets.open("$track/$candidate").use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                tempFile
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun rememberNowPlaybackController(
    context: Context,
): NowPlaybackController = remember(context) {
    NowPlaybackController(context)
}
