package com.progressone.nativeapp.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class BackupArchiveTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `large photo archive streams binary members and restores every checksum`() {
        val data = temporary.newFile("data.json")
        data.writeText("""{"format":"progress-one-native-v1","version":1,"entities":{}}""")

        val photoCount = 48
        val photos = (0 until photoCount).map { index ->
            val file = temporary.newFile("photo_$index.jpg")
            // Incompressible-ish deterministic bytes keep this test representative of a
            // multi-photo archive without checking a very large file into the repository.
            var seed = index + 1
            val bytes = ByteArray(256 * 1024) {
                seed = seed * 1_664_525 + 1_013_904_223
                (seed ushr 24).toByte()
            }
            file.writeBytes(bytes)
            BackupArchive.PhotoSource("photo_$index.jpg", file)
        }

        val archiveBytes = ByteArrayOutputStream().also { output ->
            BackupArchive.write(output, data, photos)
        }.toByteArray()
        assertTrue(archiveBytes.size > 10L * 1024L * 1024L)

        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            var entry = zip.getNextEntry()
            while (entry != null) {
                entryNames += entry.name
                zip.closeEntry()
                entry = zip.getNextEntry()
            }
        }
        assertEquals(photoCount + 2, entryNames.size)
        assertTrue(BackupArchive.MANIFEST_ENTRY in entryNames)
        assertTrue(BackupArchive.DATA_ENTRY in entryNames)
        assertFalse(entryNames.any { it.endsWith(".base64") })

        val staging = temporary.newFolder("staging")
        BackupArchive.extract(ByteArrayInputStream(archiveBytes), staging).use { extracted ->
            assertEquals(photoCount, extracted.photos.size)
            assertEquals(data.readText(), extracted.dataFile.readText())
            photos.forEach { source ->
                val restored = extracted.photos.getValue(source.name)
                assertEquals(source.file.length(), restored.length())
                assertEquals(BackupArchive.sha256(source.file), BackupArchive.sha256(restored))
            }
            val manifest = extracted.manifest
            assertEquals(BackupArchive.FORMAT, manifest.getString("format"))
            assertEquals(photoCount, manifest.getJSONArray("photos").length())
        }
    }

    @Test
    fun `archive rejects unsafe member names and checksum mismatches`() {
        val data = temporary.newFile("data.json")
        data.writeText("""{"format":"progress-one-native-v1","version":1,"entities":{}}""")
        val output = ByteArrayOutputStream()
        assertTrue(runCatching {
            BackupArchive.write(
                output,
                data,
                listOf(BackupArchive.PhotoSource("../escape.jpg", temporary.newFile("escape.jpg"))),
            )
        }.isFailure)

        val manifest = JSONObject()
            .put("format", BackupArchive.FORMAT)
            .put("version", BackupArchive.VERSION)
            .put("dataFormat", BackupArchive.DATA_FORMAT)
            .put("data", BackupArchive.DATA_ENTRY)
            .put("dataSha256", BackupArchive.sha256(data))
            .put("photos", org.json.JSONArray().put(
                JSONObject()
                    .put("name", "photo.jpg")
                    .put("path", "photos/photo.jpg")
                    .put("size", 3)
                    .put("sha256", "0".repeat(64)),
            ))
        val archive = temporary.newFile("bad.zip")
        java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchive.MANIFEST_ENTRY))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupArchive.DATA_ENTRY))
            zip.write(data.readBytes())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("photos/photo.jpg"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        assertTrue(runCatching {
            BackupArchive.extract(archive.inputStream(), temporary.newFolder("bad-staging"))
        }.isFailure)
    }
}
