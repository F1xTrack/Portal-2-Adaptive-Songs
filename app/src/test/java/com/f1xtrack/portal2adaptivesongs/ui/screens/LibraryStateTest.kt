package com.f1xtrack.portal2adaptivesongs.ui.screens

import com.f1xtrack.portal2adaptivesongs.TrackZipImporter
import com.f1xtrack.portal2adaptivesongs.TrackZipManifestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LibraryStateTest {

    @Test
    fun classifyPackageProblem_detectsMissingSuperspeedFamily() {
        val kind = classifyPackageProblem(
            hasNormal = true,
            hasSuperSpeed = false,
        )

        assertEquals(LibraryProblemKind.MissingSuperSpeed, kind)
    }

    @Test
    fun parseStatsMap_ignoresBrokenEntries() {
        val stats = parseStatsMap(
            setOf(
                "Portal|12",
                "BrokenWithoutValue",
                "AlsoBroken|abc",
            ),
        )

        assertEquals(mapOf("Portal" to 12), stats)
    }

    @Test
    fun inspectPackage_reportsManifestAwarePreview() {
        val preview = TrackZipImporter.inspectPackage(
            buildZip(
                "package/normal.wav" to "n",
                "package/superspeed.wav" to "s",
                "package/track_manifest.json" to "{}",
                "package/readme.txt" to "ignore",
            ),
        )

        assertTrue(preview.isImportable)
        assertEquals(2, preview.importedAudioCandidates)
        assertEquals(2, preview.ignoredEntries)
        assertEquals(TrackZipManifestSupport.PresentButIgnored, preview.manifestSupport)
    }

    @Test
    fun inspectPackage_blocksManifestlessArchiveWithoutSuperspeed() {
        val preview = TrackZipImporter.inspectPackage(
            buildZip("normal.wav" to "only-normal"),
        )

        assertFalse(preview.isImportable)
        assertEquals(TrackZipManifestSupport.NotPresent, preview.manifestSupport)
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
