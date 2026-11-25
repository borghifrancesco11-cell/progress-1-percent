package com.progressone.nativeapp.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NativeBackupReaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `reads ordinary json one bounded record at a time`() {
        val file = temporary.newFile("data.json")
        file.writeText(
            """
            {
              "format": "progress-one-native-v1",
              "version": 1,
              "exportedAt": 1760000000000,
              "entities": {
                "tracker": [
                  {"id":"tracker-1","name":"Read","target":10},
                  {"id":"tracker-2","name":"Walk","target":3}
                ],
                "task": [
                  {"id":"task-1","name":"Submit","done":false}
                ],
                "future_type": [
                  {"id":"ignored","value":1}
                ]
              },
              "entries": [
                {"ownerId":"tracker-1","date":"2026-11-24","value":1.5,"status":"done","notes":"ok"}
              ],
              "settings": {
                "user_name": "Local user",
                "notifications_enabled": "true"
              }
            }
            """.trimIndent()
        )

        val payload = NativeBackupReader.read(file)
        assertEquals(BackupArchive.DATA_FORMAT, payload.format)
        assertEquals(1, payload.version)
        assertEquals(2, payload.entities.getValue("tracker").size)
        assertEquals("Read", payload.entities.getValue("tracker").first().getString("name"))
        assertEquals(1, payload.entities.getValue("task").size)
        assertTrue("future_type" !in payload.entities)
        assertEquals(1, payload.entries.size)
        assertEquals(1.5, payload.entries.single().value, 0.0)
        assertEquals("Local user", payload.settings.getValue("user_name"))
        assertEquals("true", payload.settings.getValue("notifications_enabled"))
    }

    @Test
    fun `reads empty groups arrays and settings`() {
        val file = temporary.newFile("empty.json")
        file.writeText(
            """
            {
              "format": "progress-one-native-v1",
              "version": 1,
              "entities": {
                "tracker": [],
                "task": []
              },
              "entries": [],
              "settings": {}
            }
            """.trimIndent()
        )

        val payload = NativeBackupReader.read(file)
        assertTrue(payload.entities.getValue("tracker").isEmpty())
        assertTrue(payload.entities.getValue("task").isEmpty())
        assertTrue(payload.entries.isEmpty())
        assertTrue(payload.settings.isEmpty())
    }

    @Test
    fun `rejects trailing data and duplicate entity groups`() {
        val trailing = temporary.newFile("trailing.json")
        trailing.writeText(
            """{"format":"progress-one-native-v1","version":1,"entities":{}} trailing"""
        )
        assertTrue(runCatching { NativeBackupReader.read(trailing) }.isFailure)

        val duplicate = temporary.newFile("duplicate.json")
        duplicate.writeText(
            """
            {
              "format":"progress-one-native-v1",
              "version":1,
              "entities":{
                "tracker":[],
                "tracker":[]
              }
            }
            """.trimIndent()
        )
        assertTrue(runCatching { NativeBackupReader.read(duplicate) }.isFailure)
    }

    @Test
    fun `keeps each entity record independent and bounded`() {
        val file = temporary.newFile("many.json")
        file.bufferedWriter().use { writer ->
            writer.write("{\"format\":\"progress-one-native-v1\",\"version\":1,\"entities\":{\"tracker\":[")
            repeat(500) { index ->
                if (index > 0) writer.write(",")
                writer.write(JSONObject().put("id", "tracker-$index").put("name", "Task $index").toString())
            }
            writer.write("]}}")
        }
        val payload = NativeBackupReader.read(file)
        assertEquals(500, payload.entities.getValue("tracker").size)
        assertEquals("tracker-499", payload.entities.getValue("tracker").last().getString("id"))
    }
}
