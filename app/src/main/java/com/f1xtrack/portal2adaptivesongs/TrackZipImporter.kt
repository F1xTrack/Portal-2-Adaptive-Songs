package com.f1xtrack.portal2adaptivesongs

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class TrackZipImportResult(
    val foundNormal: Boolean,
    val foundSuperSpeed: Boolean,
    val importedFiles: Int,
    val manifestSupport: TrackZipManifestSupport = TrackZipManifestSupport.NotPresent,
    val ignoredEntries: Int = 0,
) {
    val isValid: Boolean
        get() = foundNormal && foundSuperSpeed
}

enum class TrackZipManifestSupport {
    NotPresent,
    PresentButIgnored,
}

data class TrackZipPackagePreview(
    val foundNormal: Boolean,
    val foundSuperSpeed: Boolean,
    val importedAudioCandidates: Int,
    val manifestSupport: TrackZipManifestSupport,
    val ignoredEntries: Int,
) {
    val isImportable: Boolean
        get() = foundNormal && foundSuperSpeed
}

object TrackZipImporter {
    private val allowedFilePattern =
        Regex("^(normal(\\d*)|superspeed(\\d*))\\.wav$", RegexOption.IGNORE_CASE)
    private val manifestPattern =
        Regex("^(track_)?manifest\\.json$", RegexOption.IGNORE_CASE)

    fun inspectPackage(inputStream: InputStream): TrackZipPackagePreview {
        var foundNormal = false
        var foundSuperSpeed = false
        var audioCandidates = 0
        var ignoredEntries = 0
        var manifestSupport = TrackZipManifestSupport.NotPresent

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    when {
                        allowedFilePattern.matches(fileName) -> {
                            val normalizedName = fileName.lowercase()
                            if (normalizedName.startsWith("normal")) {
                                foundNormal = true
                            }
                            if (normalizedName.startsWith("superspeed")) {
                                foundSuperSpeed = true
                            }
                            audioCandidates += 1
                        }

                        manifestPattern.matches(fileName) -> {
                            manifestSupport = TrackZipManifestSupport.PresentButIgnored
                            ignoredEntries += 1
                        }

                        else -> {
                            ignoredEntries += 1
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return TrackZipPackagePreview(
            foundNormal = foundNormal,
            foundSuperSpeed = foundSuperSpeed,
            importedAudioCandidates = audioCandidates,
            manifestSupport = manifestSupport,
            ignoredEntries = ignoredEntries,
        )
    }

    fun importFromStream(inputStream: InputStream, outputDir: File): TrackZipImportResult {
        outputDir.mkdirs()

        var foundNormal = false
        var foundSuperSpeed = false
        var importedFiles = 0
        var ignoredEntries = 0
        var manifestSupport = TrackZipManifestSupport.NotPresent

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    when {
                        allowedFilePattern.matches(fileName) -> {
                            val normalizedName = fileName.lowercase()
                            if (normalizedName.startsWith("normal")) {
                                foundNormal = true
                            }
                            if (normalizedName.startsWith("superspeed")) {
                                foundSuperSpeed = true
                            }

                            val outputFile = File(outputDir, normalizedName)
                            outputFile.parentFile?.mkdirs()
                            outputFile.outputStream().use { out -> zip.copyTo(out) }
                            importedFiles += 1
                        }

                        manifestPattern.matches(fileName) -> {
                            manifestSupport = TrackZipManifestSupport.PresentButIgnored
                            ignoredEntries += 1
                        }

                        else -> {
                            ignoredEntries += 1
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return TrackZipImportResult(
            foundNormal = foundNormal,
            foundSuperSpeed = foundSuperSpeed,
            importedFiles = importedFiles,
            manifestSupport = manifestSupport,
            ignoredEntries = ignoredEntries,
        )
    }
}
