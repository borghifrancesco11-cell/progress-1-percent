package com.progressone.nativeapp.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Device-level coverage for the SQLite/media side of the streaming backup format. */
@RunWith(AndroidJUnit4::class)
class LocalStoreBackupInstrumentedTest {
    @Test
    fun incompleteAndImplausibleRecordsAreRejected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LocalStore(context)
        try {
            val taskId = "invalid-task-${UUID.randomUUID()}"
            val sleepId = "invalid-sleep-${UUID.randomUUID()}"
            val transactionId = "invalid-transaction-${UUID.randomUUID()}"
            store.put("task", LocalStore.obj("id" to taskId, "done" to false))
            store.put("sleep", LocalStore.obj(
                "id" to sleepId,
                "date" to "2026-02-30",
                "bed" to "23:00",
                "wake" to "07:00",
                "minutes" to 480.0,
                "quality" to 4.0,
            ))
            store.put("transaction", LocalStore.obj(
                "id" to transactionId,
                "type" to "expense",
                "title" to "Overflow",
                "amount" to "1e309",
                "date" to "2026-11-24",
            ))

            assertEquals(null, store.find("task", taskId))
            assertEquals(null, store.find("sleep", sleepId))
            assertEquals(null, store.find("transaction", transactionId))
        } finally {
            store.close()
        }
    }

    @Test
    fun oversizedReferencedPhotoFailsExportBeforeWritingArchive() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LocalStore(context)
        val id = "oversized-photo-${UUID.randomUUID()}"
        val photoName = "oversized_${UUID.randomUUID()}.jpg"

        try {
            val photo = store.photoFile(photoName)
            assertNotNull(photo)
            photo!!.writeBytes(ByteArray(BackupArchive.MAX_PHOTO_BYTES.toInt() + 1))
            store.put(
                "garment",
                LocalStore.obj(
                    "id" to id,
                    "name" to "Oversized photo",
                    "category" to "test",
                    "photo" to photoName,
                ),
            )

            val output = ByteArrayOutputStream()
            val failure = runCatching { store.exportArchive(output) }.exceptionOrNull()
            assertNotNull("An oversized referenced photo must abort the export", failure)
            assertTrue("The failed export must not emit a partial archive", output.size() == 0)
        } finally {
            store.remove("garment", id)
            store.photoFile(photoName)?.delete()
            store.close()
        }
    }

    @Test
    fun largeMultiPhotoArchiveRoundTripsAndFailedImportKeepsCurrentData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LocalStore(context)
        val createdIds = mutableListOf<String>()
        val createdPhotos = mutableListOf<String>()
        val sentinelId = "backup-sentinel-${UUID.randomUUID()}"

        try {
            val photoCount = 48
            repeat(photoCount) { index ->
                val id = "backup-test-${UUID.randomUUID()}"
                val photoName = "backup_test_${UUID.randomUUID()}.jpg"
                val photo = store.photoFile(photoName)
                assertNotNull(photo)
                var seed = index + 1
                val bytes = ByteArray(256 * 1024) {
                    seed = seed * 1_664_525 + 1_013_904_223
                    (seed ushr 24).toByte()
                }
                photo!!.writeBytes(bytes)
                createdIds += id
                createdPhotos += photoName
                store.put(
                    "garment",
                    LocalStore.obj(
                        "id" to id,
                        "name" to "Backup garment $index",
                        "category" to "test",
                        "photo" to photoName,
                    ),
                )
            }

            val archiveBytes = ByteArrayOutputStream().also { output ->
                store.exportArchive(output)
            }.toByteArray()
            assertTrue("The representative archive must exceed 10 MiB", archiveBytes.size > 10L * 1024L * 1024L)

            val staging = File(context.cacheDir, "backup-inspection-${UUID.randomUUID()}")
            BackupArchive.extract(ByteArrayInputStream(archiveBytes), staging).use { extracted ->
                val data = JSONObject(extracted.dataFile.readText())
                val entities = data.getJSONObject("entities")
                val referencedPhotos = linkedSetOf<String>()
                for (type in listOf("garment", "food")) {
                    val records = entities.getJSONArray(type)
                    for (index in 0 until records.length()) {
                        val record = records.getJSONObject(index)
                        for (field in listOf("photo", "sourcePhoto")) {
                            val photo = record.optString(field)
                            if (photo.isNotBlank()) referencedPhotos += photo
                        }
                    }
                }
                val manifestPhotos = extracted.manifest.getJSONArray("photos").let { photos ->
                    (0 until photos.length()).mapTo(linkedSetOf()) { index ->
                        photos.getJSONObject(index).getString("name")
                    }
                }
                assertEquals("JSON references and manifest photos differ", referencedPhotos, manifestPhotos)
            }

            store.importArchive(ByteArrayInputStream(archiveBytes))
            createdIds.forEach { id ->
                assertNotNull("Imported garment $id is missing", store.find("garment", id))
            }

            // A malformed import must not replace the data restored by the valid archive.
            store.put("task", LocalStore.obj("id" to sentinelId, "name" to "Keep me", "done" to false))
            runCatching {
                store.importArchive(ByteArrayInputStream("not-a-zip".toByteArray()))
            }.onFailure { /* expected */ }
            assertNotNull("A failed import changed the current database", store.find("task", sentinelId))
            assertEquals(photoCount, createdIds.count { store.find("garment", it) != null })
        } finally {
            createdIds.forEach { store.remove("garment", it) }
            store.remove("task", sentinelId)
            createdPhotos.forEach { store.photoFile(it)?.delete() }
            store.close()
        }
    }
}
