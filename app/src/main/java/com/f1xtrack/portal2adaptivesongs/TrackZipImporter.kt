package com.f1xtrack.portal2adaptivesongs

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class TrackZipImportResult(
    val foundNormal: Boolean,
    val foundSuperSpeed: Boolean,
    val importedFiles: Int
) {
    val isValid: Boolean
        get() = foundNormal && foundSuperSpeed
}

object TrackZipImporter {
    private val allowedFilePattern =
        Regex("^(normal(\\d*)|superspeed(\\d*))\\.wav$", RegexOption.IGNORE_CASE)

    fun importFromStream(inputStream: InputStream, outputDir: File): TrackZipImportResult {
        outputDir.mkdirs()

        var foundNormal = false
        var foundSuperSpeed = false
        var importedFiles = 0

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val fileName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    if (allowedFilePattern.matches(fileName)) {
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
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return TrackZipImportResult(
            foundNormal = foundNormal,
            foundSuperSpeed = foundSuperSpeed,
            importedFiles = importedFiles
        )
    }
}
