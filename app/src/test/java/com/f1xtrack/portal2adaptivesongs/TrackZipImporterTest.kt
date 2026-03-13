package com.f1xtrack.portal2adaptivesongs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TrackZipImporterTest {

    @Test
    fun `imports base files from zip`() {
        val outputDir = Files.createTempDirectory("track-import-base").toFile()

        val result = TrackZipImporter.importFromStream(
            inputStream = buildZip(
                "normal.wav" to "normal-data",
                "superspeed.wav" to "super-data"
            ),
            outputDir = outputDir
        )

        assertTrue(result.isValid)
        assertEquals(2, result.importedFiles)
        assertTrue(File(outputDir, "normal.wav").exists())
        assertTrue(File(outputDir, "superspeed.wav").exists())

        outputDir.deleteRecursively()
    }

    @Test
    fun `imports numbered variants from nested zip paths`() {
        val outputDir = Files.createTempDirectory("track-import-variants").toFile()

        val result = TrackZipImporter.importFromStream(
            inputStream = buildZip(
                "folder/normal2.wav" to "normal-two",
                "folder/superspeed3.wav" to "super-three",
                "folder/readme.txt" to "ignore-me"
            ),
            outputDir = outputDir
        )

        assertTrue(result.isValid)
        assertEquals(2, result.importedFiles)
        assertTrue(File(outputDir, "normal2.wav").exists())
        assertTrue(File(outputDir, "superspeed3.wav").exists())
        assertFalse(File(outputDir, "readme.txt").exists())

        outputDir.deleteRecursively()
    }

    @Test
    fun `fails validation when one required track family is missing`() {
        val outputDir = Files.createTempDirectory("track-import-invalid").toFile()

        val result = TrackZipImporter.importFromStream(
            inputStream = buildZip("normal.wav" to "normal-only"),
            outputDir = outputDir
        )

        assertFalse(result.isValid)
        assertTrue(result.foundNormal)
        assertFalse(result.foundSuperSpeed)

        outputDir.deleteRecursively()
    }

    @Test
    fun `normalizes imported filenames to lowercase`() {
        val outputDir = Files.createTempDirectory("track-import-uppercase").toFile()

        val result = TrackZipImporter.importFromStream(
            inputStream = buildZip(
                "NORMAL.WAV" to "normal-upper",
                "folder/SUPERSPEED9.WAV" to "super-upper"
            ),
            outputDir = outputDir
        )

        assertTrue(result.isValid)
        assertTrue(File(outputDir, "normal.wav").exists())
        assertTrue(File(outputDir, "superspeed9.wav").exists())

        outputDir.deleteRecursively()
    }

    @Test
    fun `drops unsafe path parts and keeps only the file name`() {
        val outputDir = Files.createTempDirectory("track-import-safe-name").toFile()

        val result = TrackZipImporter.importFromStream(
            inputStream = buildZip(
                "../../normal.wav" to "normal-traversal",
                "..\\..\\superspeed.wav" to "super-traversal"
            ),
            outputDir = outputDir
        )

        assertTrue(result.isValid)
        assertTrue(File(outputDir, "normal.wav").exists())
        assertTrue(File(outputDir, "superspeed.wav").exists())
        assertFalse(File(outputDir.parentFile, "normal.wav").exists())

        outputDir.deleteRecursively()
    }

    private fun buildZip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
