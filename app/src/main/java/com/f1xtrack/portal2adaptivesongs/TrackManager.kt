package com.f1xtrack.portal2adaptivesongs

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.util.zip.ZipInputStream

class TrackManager(private val context: Context) {
    companion object {
        private const val PREFS_STATS = "track_stats"
        private const val PREFS_SORT = "track_sort_prefs"
        private const val PREF_SORT = "sort_type"
    }

    enum class SortType { ALPHA, FREQ, DURATION }

    private var sortType: SortType = SortType.ALPHA
    private val trackPlayCount = mutableMapOf<String, Int>()
    private val trackDuration = mutableMapOf<String, Int>()
    private var userTracks: List<String> = emptyList()

    data class TrackInfo(
        val name: String,
        val duration: Int, // ms
        val plays: Int
    )

    data class ImportResult(
        val imported: Int,
        val failed: Int
    )

    fun setSortType(type: SortType) {
        sortType = type
        saveSortPreferences()
    }

    fun getSortType(): SortType = sortType

    fun isUserTrack(trackName: String): Boolean {
        return userTracks.contains(trackName)
    }

    fun incrementPlayCount(trackName: String) {
        trackPlayCount[trackName] = (trackPlayCount[trackName] ?: 0) + 1
        saveStatistics()
    }

    fun getTrackInfoList(): List<TrackInfo> {
        refreshTracks()
        val allTracks = getSortedTracks()
        return allTracks.map { name ->
            TrackInfo(
                name = name,
                duration = trackDuration[name] ?: 0,
                plays = trackPlayCount[name] ?: 0
            )
        }
    }

    fun loadStatistics() {
        val statsPrefs = context.getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)
        
        // Загружаем счетчики прослушиваний
        statsPrefs.getStringSet("play_counts", null)?.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                trackPlayCount[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
        
        // Загружаем длительности
        statsPrefs.getStringSet("durations", null)?.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                trackDuration[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
    }

    fun saveStatistics() {
        val statsPrefs = context.getSharedPreferences(PREFS_STATS, Context.MODE_PRIVATE)
        
        val playCounts = trackPlayCount.map { "${it.key}|${it.value}" }.toSet()
        val durations = trackDuration.map { "${it.key}|${it.value}" }.toSet()
        
        statsPrefs.edit()
            .putStringSet("play_counts", playCounts)
            .putStringSet("durations", durations)
            .apply()
    }

    fun importTrackFromZip(zipUri: Uri, trackName: String): Boolean {
        return try {
            val trackDir = File(context.filesDir, "soundtracks/$trackName")
            trackDir.mkdirs()
            
            val inputStream = context.contentResolver.openInputStream(zipUri) 
                ?: throw Exception("Не удалось открыть ZIP")
            
            val zis = ZipInputStream(inputStream)
            var entry = zis.nextEntry
            var foundNormal = false
            var foundSuper = false
            
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = when (entry.name) {
                        "normal.wav" -> File(trackDir, "normal.wav").also { foundNormal = true }
                        "superspeed.wav" -> File(trackDir, "superspeed.wav").also { foundSuper = true }
                        else -> null
                    }
                    
                    if (outFile != null) {
                        outFile.outputStream().use { out ->
                            zis.copyTo(out)
                        }
                    }
                }
                entry = zis.nextEntry
            }
            
            zis.close()
            inputStream.close()
            
            if (foundNormal && foundSuper) {
                refreshTracks()
                true
            } else {
                trackDir.deleteRecursively()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun importTracksFromFolder(folderUri: Uri): ImportResult {
        var imported = 0
        var failed = 0
        
        try {
            val children = context.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri, 
                    DocumentsContract.getTreeDocumentId(folderUri)
                ),
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )
            
            children?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val docId = cursor.getString(1)
                    val mime = cursor.getString(2)
                    
                    if (mime == "application/zip" || name.endsWith(".zip", true)) {
                        val zipUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        val trackName = name.removeSuffix(".zip").removeSuffix(".ZIP")
                        
                        if (importTrackFromZip(zipUri, trackName)) {
                            imported++
                        } else {
                            failed++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ошибка обрабатывается в вызывающем коде
        }
        
        return ImportResult(imported, failed)
    }

    private fun refreshTracks() {
        // Получаем стандартные треки из assets
        val assetTracks = context.assets.list("")?.filter { name ->
            context.assets.list(name)?.contains("superspeed.wav") == true
        } ?: emptyList()
        
        // Получаем пользовательские треки
        val userDir = File(context.filesDir, "soundtracks")
        userTracks = userDir.listFiles()?.filter { dir ->
            dir.isDirectory && 
            File(dir, "normal.wav").exists() && 
            File(dir, "superspeed.wav").exists()
        }?.map { it.name } ?: emptyList()
        
        // Обновляем длительности для новых треков
        (assetTracks + userTracks).forEach { track ->
            if (!trackDuration.containsKey(track)) {
                trackDuration[track] = getTrackDuration(track)
            }
        }
    }

    private fun getSortedTracks(): List<String> {
        val allTracks = getAssetTracks() + userTracks
        return when (sortType) {
            SortType.ALPHA -> allTracks.sortedBy { it.lowercase() }
            SortType.FREQ -> allTracks.sortedByDescending { trackPlayCount[it] ?: 0 }
            SortType.DURATION -> allTracks.sortedByDescending { trackDuration[it] ?: 0 }
        }
    }

    private fun getAssetTracks(): List<String> {
        return context.assets.list("")?.filter { name ->
            context.assets.list(name)?.contains("superspeed.wav") == true
        } ?: emptyList()
    }

    private fun getTrackDuration(trackName: String): Int {
        val file = if (userTracks.contains(trackName)) {
            File(context.filesDir, "soundtracks/$trackName/normal.wav")
        } else {
            val tmpFile = File(context.cacheDir, "tmp_${trackName}.wav")
            if (!tmpFile.exists()) {
                context.assets.open("$trackName/normal.wav").use { input ->
                    tmpFile.outputStream().use { output -> 
                        input.copyTo(output) 
                    }
                }
            }
            tmpFile
        }
        
        return getTrackDurationFromFile(file.absolutePath)
    }

    private fun getTrackDurationFromFile(filePath: String): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        } finally {
            retriever.release()
        }
    }

    private fun saveSortPreferences() {
        val prefs = context.getSharedPreferences(PREFS_SORT, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_SORT, sortType.name).apply()
    }
}