package com.f1xtrack.portal2adaptivesongs.ui.screens

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import com.f1xtrack.portal2adaptivesongs.R
import com.f1xtrack.portal2adaptivesongs.TrackZipImportResult
import com.f1xtrack.portal2adaptivesongs.TrackZipImporter
import com.f1xtrack.portal2adaptivesongs.TrackZipPackagePreview
import com.f1xtrack.portal2adaptivesongs.TrackZipManifestSupport
import com.f1xtrack.portal2adaptivesongs.TracksAdapter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val STORAGE_PREFS = "storage_prefs"
private const val TRACK_STATS_PREFS = "track_stats"
private const val SOUNDTRACKS_DIR = "soundtracks"
private const val HIDDEN_ASSETS_KEY = "hidden_assets"
private const val PLAY_COUNTS_KEY = "play_counts"
private const val DURATIONS_KEY = "durations"

@Immutable
data class LibraryUiState(
    val totalVisibleTracks: Int = 0,
    val builtInVisibleCount: Int = 0,
    val importedVisibleCount: Int = 0,
    val hiddenBuiltInCount: Int = 0,
    val problemPackageCount: Int = 0,
    val builtInTracks: ImmutableList<LibraryTrackItem> = persistentListOf(),
    val importedTracks: ImmutableList<LibraryTrackItem> = persistentListOf(),
    val problemPackages: ImmutableList<LibraryProblemPackage> = persistentListOf(),
    val pendingImport: PendingLibraryImport? = null,
    val importFeedback: LibraryImportFeedback? = null,
    val isBusy: Boolean = false,
) {
    val hasVisibleTracks: Boolean
        get() = totalVisibleTracks > 0
}

@Immutable
data class LibraryTrackItem(
    val name: String,
    val durationMs: Int,
    val plays: Int,
    val isUserTrack: Boolean,
    val isHidden: Boolean,
)

@Immutable
data class LibraryProblemPackage(
    val name: String,
    val problemKind: LibraryProblemKind,
    val fileCount: Int,
)

enum class LibraryProblemKind {
    MissingNormal,
    MissingSuperSpeed,
    MissingBoth,
}

@Immutable
data class PendingLibraryImport(
    val sourceLabel: String,
    val suggestedName: String,
    val preview: TrackZipPackagePreview,
)

@Immutable
data class LibraryImportFeedback(
    val title: String,
    val body: String,
    val severity: LibraryFeedbackSeverity,
)

enum class LibraryFeedbackSeverity {
    Info,
    Success,
    Warning,
    Error,
}

class LibraryController(
    private val appContext: Context,
) {
    private val _uiState = MutableStateFlow(loadLibraryUiState(appContext))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var pendingZipUri: Uri? = null

    fun refresh() {
        publishState(
            feedback = _uiState.value.importFeedback,
            pendingImport = _uiState.value.pendingImport,
            isBusy = false,
        )
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(importFeedback = null) }
    }

    fun dismissPendingImport() {
        pendingZipUri = null
        _uiState.update { it.copy(pendingImport = null, isBusy = false) }
    }

    fun toggleBuiltInVisibility(trackName: String) {
        val hiddenAssets = getHiddenAssets(appContext)
        if (trackName in hiddenAssets) {
            hiddenAssets.remove(trackName)
        } else {
            hiddenAssets.add(trackName)
        }
        setHiddenAssets(appContext, hiddenAssets)
        publishState(
            feedback = LibraryImportFeedback(
                title = appContext.getString(R.string.library_feedback_visibility_title),
                body = appContext.getString(
                    if (trackName in hiddenAssets) {
                        R.string.library_feedback_track_hidden
                    } else {
                        R.string.library_feedback_track_visible
                    },
                    trackName,
                ),
                severity = LibraryFeedbackSeverity.Info,
            ),
        )
    }

    fun unhideAllBuiltInTracks() {
        setHiddenAssets(appContext, emptySet())
        publishState(
            feedback = LibraryImportFeedback(
                title = appContext.getString(R.string.library_feedback_visibility_title),
                body = appContext.getString(R.string.library_feedback_unhide_all),
                severity = LibraryFeedbackSeverity.Success,
            ),
        )
    }

    fun clearTrackStats() {
        appContext.getSharedPreferences(TRACK_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(PLAY_COUNTS_KEY)
            .remove(DURATIONS_KEY)
            .apply()
        publishState(
            feedback = LibraryImportFeedback(
                title = appContext.getString(R.string.library_feedback_stats_title),
                body = appContext.getString(R.string.library_feedback_stats_cleared),
                severity = LibraryFeedbackSeverity.Success,
            ),
        )
    }

    fun deleteImportedTrack(trackName: String) {
        val trackDir = File(appContext.filesDir, "$SOUNDTRACKS_DIR/$trackName")
        if (trackDir.exists()) {
            trackDir.deleteRecursively()
        }

        val statsPrefs = appContext.getSharedPreferences(TRACK_STATS_PREFS, Context.MODE_PRIVATE)
        val playCounts = statsPrefs.getStringSet(PLAY_COUNTS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val durations = statsPrefs.getStringSet(DURATIONS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        playCounts.removeAll { it.substringBefore('|') == trackName }
        durations.removeAll { it.substringBefore('|') == trackName }
        statsPrefs.edit()
            .putStringSet(PLAY_COUNTS_KEY, playCounts)
            .putStringSet(DURATIONS_KEY, durations)
            .apply()

        publishState(
            feedback = LibraryImportFeedback(
                title = appContext.getString(R.string.library_feedback_delete_title),
                body = appContext.getString(R.string.library_feedback_delete_body, trackName),
                severity = LibraryFeedbackSeverity.Success,
            ),
        )
    }

    suspend fun prepareZipImport(uri: Uri, displayName: String) {
        _uiState.update { it.copy(isBusy = true, importFeedback = null) }
        val fallbackName = displayName
            .removeSuffix(".zip")
            .removeSuffix(".ZIP")
            .ifBlank { appContext.getString(R.string.library_import_default_track_name) }

        try {
            val preview = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use(TrackZipImporter::inspectPackage)
                    ?: throw IllegalStateException(appContext.getString(R.string.import_open_zip_failed))
            }
            pendingZipUri = uri
            publishState(
                pendingImport = PendingLibraryImport(
                    sourceLabel = displayName.ifBlank { fallbackName },
                    suggestedName = sanitizeTrackName(fallbackName),
                    preview = preview,
                ),
                isBusy = false,
            )
        } catch (error: Exception) {
            pendingZipUri = null
            publishState(
                feedback = LibraryImportFeedback(
                    title = appContext.getString(R.string.library_feedback_import_title),
                    body = appContext.getString(
                        R.string.library_feedback_import_error,
                        error.message ?: appContext.getString(R.string.library_feedback_unknown_error),
                    ),
                    severity = LibraryFeedbackSeverity.Error,
                ),
                pendingImport = null,
                isBusy = false,
            )
        }
    }

    suspend fun importPendingZip(trackNameInput: String) {
        val pendingImport = _uiState.value.pendingImport ?: return
        val zipUri = pendingZipUri ?: return
        val trackName = sanitizeTrackName(trackNameInput)
        if (trackName.isBlank()) {
            _uiState.update {
                it.copy(
                    importFeedback = LibraryImportFeedback(
                        title = appContext.getString(R.string.library_feedback_import_title),
                        body = appContext.getString(R.string.import_name_required),
                        severity = LibraryFeedbackSeverity.Warning,
                    ),
                )
            }
            return
        }

        _uiState.update { it.copy(isBusy = true, importFeedback = null) }

        val importResult = withContext(Dispatchers.IO) {
            importZipToSoundtracks(zipUri, trackName)
        }

        pendingZipUri = null

        publishState(
            feedback = importResult.toFeedback(appContext, trackName, pendingImport.preview),
            pendingImport = null,
            isBusy = false,
        )
    }

    suspend fun importZipPack(treeUri: Uri) {
        _uiState.update { it.copy(isBusy = true, importFeedback = null) }
        val result = withContext(Dispatchers.IO) {
            importZipPackFromFolder(treeUri)
        }

        publishState(
            feedback = result.toFeedback(appContext),
            isBusy = false,
        )
    }

    private fun publishState(
        feedback: LibraryImportFeedback? = _uiState.value.importFeedback,
        pendingImport: PendingLibraryImport? = _uiState.value.pendingImport,
        isBusy: Boolean = false,
    ) {
        val baseState = loadLibraryUiState(appContext)
        _uiState.value = baseState.copy(
            pendingImport = pendingImport,
            importFeedback = feedback,
            isBusy = isBusy,
        )
    }

    private fun importZipToSoundtracks(zipUri: Uri, trackName: String): SingleZipImportResult {
        return try {
            val outputDir = File(appContext.filesDir, "$SOUNDTRACKS_DIR/$trackName")
            val result = appContext.contentResolver.openInputStream(zipUri)?.use { input ->
                TrackZipImporter.importFromStream(input, outputDir)
            } ?: throw IllegalStateException(appContext.getString(R.string.import_open_zip_failed))

            if (!result.isValid) {
                outputDir.deleteRecursively()
            }

            SingleZipImportResult(trackName = trackName, result = result)
        } catch (error: Exception) {
            SingleZipImportResult(trackName = trackName, error = error)
        }
    }

    private fun importZipPackFromFolder(treeUri: Uri): FolderImportResult {
        var imported = 0
        var failed = 0
        var manifestPackages = 0

        return try {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
            appContext.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(0) ?: continue
                    val documentId = cursor.getString(1) ?: continue
                    val mimeType = cursor.getString(2)
                    if (mimeType == "application/zip" || displayName.endsWith(".zip", ignoreCase = true)) {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        val preview = appContext.contentResolver.openInputStream(documentUri)?.use(TrackZipImporter::inspectPackage)
                        if (preview?.manifestSupport == TrackZipManifestSupport.PresentButIgnored) {
                            manifestPackages += 1
                        }
                        val trackName = sanitizeTrackName(displayName.removeSuffix(".zip").removeSuffix(".ZIP"))
                        val importResult = importZipToSoundtracks(documentUri, trackName)
                        if (importResult.result?.isValid == true) {
                            imported += 1
                        } else {
                            failed += 1
                        }
                    }
                }
            }
            FolderImportResult(imported = imported, failed = failed, manifestPackages = manifestPackages)
        } catch (error: Exception) {
            FolderImportResult(imported = imported, failed = failed, manifestPackages = manifestPackages, error = error)
        }
    }
}

internal fun loadLibraryUiState(context: Context): LibraryUiState {
    val hiddenAssets = getHiddenAssets(context)
    val playCounts = parseStatsMap(
        context.getSharedPreferences(TRACK_STATS_PREFS, Context.MODE_PRIVATE)
            .getStringSet(PLAY_COUNTS_KEY, emptySet())
            .orEmpty(),
    )
    val durations = parseStatsMap(
        context.getSharedPreferences(TRACK_STATS_PREFS, Context.MODE_PRIVATE)
            .getStringSet(DURATIONS_KEY, emptySet())
            .orEmpty(),
    )

    val builtInTracks = loadBuiltInTracks(context, hiddenAssets, playCounts, durations)
    val importedScan = loadImportedTracks(context, playCounts, durations)

    return LibraryUiState(
        totalVisibleTracks = builtInTracks.size + importedScan.visibleTracks.size,
        builtInVisibleCount = builtInTracks.size,
        importedVisibleCount = importedScan.visibleTracks.size,
        hiddenBuiltInCount = hiddenAssets.size,
        problemPackageCount = importedScan.problemPackages.size,
        builtInTracks = builtInTracks.toImmutableList(),
        importedTracks = importedScan.visibleTracks.toImmutableList(),
        problemPackages = importedScan.problemPackages.toImmutableList(),
    )
}

private data class ImportedTrackScan(
    val visibleTracks: List<LibraryTrackItem>,
    val problemPackages: List<LibraryProblemPackage>,
)

private data class SingleZipImportResult(
    val trackName: String,
    val result: TrackZipImportResult? = null,
    val error: Exception? = null,
)

private data class FolderImportResult(
    val imported: Int,
    val failed: Int,
    val manifestPackages: Int,
    val error: Exception? = null,
)

private fun SingleZipImportResult.toFeedback(
    context: Context,
    trackName: String,
    preview: TrackZipPackagePreview,
): LibraryImportFeedback {
    val error = error
    if (error != null) {
        return LibraryImportFeedback(
            title = context.getString(R.string.library_feedback_import_title),
            body = context.getString(
                R.string.library_feedback_import_error,
                error.message ?: context.getString(R.string.library_feedback_unknown_error),
            ),
            severity = LibraryFeedbackSeverity.Error,
        )
    }

    val result = requireNotNull(result)
    if (!result.isValid) {
        return LibraryImportFeedback(
            title = context.getString(R.string.library_feedback_import_title),
            body = context.getString(R.string.library_feedback_import_invalid_package),
            severity = LibraryFeedbackSeverity.Warning,
        )
    }

    val manifestNote = if (preview.manifestSupport == TrackZipManifestSupport.PresentButIgnored) {
        context.getString(R.string.library_feedback_manifest_present)
    } else {
        context.getString(R.string.library_feedback_manifest_missing)
    }
    val ignoredNote = if (result.ignoredEntries > 0) {
        context.getString(R.string.library_feedback_import_ignored_entries, result.ignoredEntries)
    } else {
        context.getString(R.string.library_feedback_import_no_ignored_entries)
    }
    return LibraryImportFeedback(
        title = context.getString(R.string.library_feedback_import_success_title),
        body = context.getString(
            R.string.library_feedback_import_success_body,
            trackName,
            result.importedFiles,
            manifestNote,
            ignoredNote,
        ),
        severity = LibraryFeedbackSeverity.Success,
    )
}

private fun FolderImportResult.toFeedback(context: Context): LibraryImportFeedback {
    val error = error
    if (error != null) {
        return LibraryImportFeedback(
            title = context.getString(R.string.library_feedback_import_title),
            body = context.getString(
                R.string.library_feedback_import_error,
                error.message ?: context.getString(R.string.library_feedback_unknown_error),
            ),
            severity = LibraryFeedbackSeverity.Error,
        )
    }

    val manifestLine = if (manifestPackages > 0) {
        context.getString(R.string.library_feedback_pack_manifest_count, manifestPackages)
    } else {
        context.getString(R.string.library_feedback_pack_manifest_none)
    }
    val severity = if (failed > 0) LibraryFeedbackSeverity.Warning else LibraryFeedbackSeverity.Success
    return LibraryImportFeedback(
        title = context.getString(R.string.library_feedback_pack_title),
        body = context.getString(
            R.string.library_feedback_pack_body,
            imported,
            failed,
            manifestLine,
        ),
        severity = severity,
    )
}

private fun loadBuiltInTracks(
    context: Context,
    hiddenAssets: Set<String>,
    playCounts: Map<String, Int>,
    durations: Map<String, Int>,
): List<LibraryTrackItem> {
    val assetTracks = context.assets.list("")?.filter { trackName ->
        trackName !in hiddenAssets &&
            assetFolderHasAnyVariant(context, trackName, "normal") &&
            assetFolderHasAnyVariant(context, trackName, "superspeed")
    }.orEmpty()

    return assetTracks
        .sortedBy { it.lowercase(Locale.getDefault()) }
        .map { trackName ->
            LibraryTrackItem(
                name = trackName,
                durationMs = durations[trackName] ?: readTrackDuration(context, trackName, isUserTrack = false),
                plays = playCounts[trackName] ?: 0,
                isUserTrack = false,
                isHidden = false,
            )
        }
}

private fun loadImportedTracks(
    context: Context,
    playCounts: Map<String, Int>,
    durations: Map<String, Int>,
): ImportedTrackScan {
    val soundtracksDir = File(context.filesDir, SOUNDTRACKS_DIR)
    if (!soundtracksDir.exists()) {
        return ImportedTrackScan(emptyList(), emptyList())
    }

    val visibleTracks = mutableListOf<LibraryTrackItem>()
    val problemPackages = mutableListOf<LibraryProblemPackage>()

    soundtracksDir.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
        ?.forEach { directory ->
            val hasNormal = userFolderHasAnyVariant(directory, "normal")
            val hasSuperSpeed = userFolderHasAnyVariant(directory, "superspeed")
            if (hasNormal && hasSuperSpeed) {
                visibleTracks += LibraryTrackItem(
                    name = directory.name,
                    durationMs = durations[directory.name] ?: readTrackDuration(context, directory.name, isUserTrack = true),
                    plays = playCounts[directory.name] ?: 0,
                    isUserTrack = true,
                    isHidden = false,
                )
            } else {
                val problemKind = classifyPackageProblem(hasNormal, hasSuperSpeed) ?: return@forEach
                problemPackages += LibraryProblemPackage(
                    name = directory.name,
                    problemKind = problemKind,
                    fileCount = directory.listFiles()?.size ?: 0,
                )
            }
        }

    return ImportedTrackScan(visibleTracks = visibleTracks, problemPackages = problemPackages)
}

internal fun classifyPackageProblem(
    hasNormal: Boolean,
    hasSuperSpeed: Boolean,
): LibraryProblemKind? {
    return when {
        hasNormal && hasSuperSpeed -> null
        !hasNormal && !hasSuperSpeed -> LibraryProblemKind.MissingBoth
        !hasNormal -> LibraryProblemKind.MissingNormal
        else -> LibraryProblemKind.MissingSuperSpeed
    }
}

internal fun parseStatsMap(entries: Set<String>): Map<String, Int> {
    return entries.mapNotNull { entry ->
        val key = entry.substringBefore('|', missingDelimiterValue = "")
        val value = entry.substringAfter('|', missingDelimiterValue = "")
        val parsed = value.toIntOrNull() ?: return@mapNotNull null
        if (key.isBlank()) return@mapNotNull null
        key to parsed
    }.toMap()
}

private fun readTrackDuration(
    context: Context,
    trackName: String,
    isUserTrack: Boolean,
): Int {
    val mediaFile = findFirstVariantFile(context, trackName, "normal", isUserTrack) ?: return 0
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(mediaFile.absolutePath)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        duration?.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    }
}

private fun findFirstVariantFile(
    context: Context,
    trackName: String,
    baseName: String,
    isUserTrack: Boolean,
): File? {
    val regex = Regex("^${baseName}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
    return if (isUserTrack) {
        File(context.filesDir, "$SOUNDTRACKS_DIR/$trackName")
            .listFiles()
            ?.filter { regex.matches(it.name) }
            ?.sortedBy { it.name.length }
            ?.firstOrNull()
    } else {
        val matchingName = context.assets.list(trackName)
            ?.filter { regex.matches(it) }
            ?.sortedBy { it.length }
            ?.firstOrNull()
            ?: return null
        File(context.cacheDir, "library_${trackName}_${matchingName.lowercase(Locale.getDefault())}").apply {
            if (!exists()) {
                context.assets.open("$trackName/$matchingName").use { input ->
                    outputStream().use(input::copyTo)
                }
            }
        }
    }
}

private fun sanitizeTrackName(raw: String): String {
    val cleaned = raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), " ")
    return cleaned.replace(Regex("\\s+"), " ").trim()
}

private fun getHiddenAssets(context: Context): MutableSet<String> {
    return context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        .getStringSet(HIDDEN_ASSETS_KEY, emptySet())
        ?.toMutableSet()
        ?: mutableSetOf()
}

private fun setHiddenAssets(context: Context, hiddenAssets: Set<String>) {
    context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(HIDDEN_ASSETS_KEY, hiddenAssets)
        .apply()
}

private fun assetFolderHasAnyVariant(context: Context, folder: String, base: String): Boolean {
    return try {
        val files = context.assets.list(folder) ?: return false
        val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
        files.any(regex::matches)
    } catch (_: Exception) {
        false
    }
}

private fun userFolderHasAnyVariant(directory: File, base: String): Boolean {
    if (!directory.exists() || !directory.isDirectory) return false
    val regex = Regex("^${base}(\\d*)\\.wav$", RegexOption.IGNORE_CASE)
    return directory.listFiles()?.any { file -> regex.matches(file.name) } == true
}
