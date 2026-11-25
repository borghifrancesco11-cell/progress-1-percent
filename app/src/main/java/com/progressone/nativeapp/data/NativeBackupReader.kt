package com.progressone.nativeapp.data

import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.Reader

/**
 * Bounded reader for the native `data.json` member of a backup archive.
 *
 * The file remains ordinary, human-readable JSON.  It is deliberately *not*
 * loaded as one large [JSONObject]: the reader walks the document and keeps
 * only one entity/entry at a time.  This prevents a large backup from
 * requiring a second, monolithic copy of the complete JSON document in
 * memory.
 */
internal object NativeBackupReader {
    /** Maximum size of one entity, entry, setting value or unknown JSON value. */
    const val MAX_VALUE_CHARS: Int = 4 * 1024 * 1024

    /** Defensive record-count limits in addition to the archive byte limit. */
    const val MAX_TOTAL_ENTITY_RECORDS: Int = 1_000_000
    const val MAX_ENTRY_RECORDS: Int = 2_000_000
    const val MAX_SETTING_RECORDS: Int = 100_000

    data class Entry(
        val ownerId: String,
        val date: String,
        val value: Double,
        val status: String,
        val notes: String,
    )

    data class Payload(
        val format: String,
        val version: Int,
        val entities: Map<String, List<JSONObject>>,
        val entries: List<Entry>,
        val settings: Map<String, String>,
    )

    fun read(file: File, supportedTypes: Set<String> = LocalStore.TYPES.toSet()): Payload {
        file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
            return read(reader, supportedTypes)
        }
    }

    fun read(reader: Reader, supportedTypes: Set<String> = LocalStore.TYPES.toSet()): Payload {
        val cursor = JsonCursor(reader)
        cursor.expect('{')

        var format = ""
        var version = -1
        var sawEntities = false
        var sawEntries = false
        var sawSettings = false
        val entities = linkedMapOf<String, MutableList<JSONObject>>()
        val entries = mutableListOf<Entry>()
        val settings = linkedMapOf<String, String>()
        var entityCount = 0

        while (true) {
            val key = cursor.readString()
            cursor.expect(':')
            when (key) {
                "format" -> format = cursor.readString()
                "version" -> version = cursor.readInt()
                "entities" -> {
                    require(!sawEntities) { "Duplicate entities object in backup data" }
                    sawEntities = true
                    entityCount = readEntities(cursor, entities, supportedTypes, entityCount)
                }
                "entries" -> {
                    require(!sawEntries) { "Duplicate entries array in backup data" }
                    sawEntries = true
                    readEntries(cursor, entries)
                }
                "settings" -> {
                    require(!sawSettings) { "Duplicate settings object in backup data" }
                    sawSettings = true
                    readSettings(cursor, settings)
                }
                else -> cursor.skipValue()
            }

            when (cursor.readStructural()) {
                '}'.code -> break
                ','.code -> Unit
                -1 -> throw IllegalArgumentException("Unexpected end of backup data.json")
                else -> throw IllegalArgumentException("Invalid separator in backup data.json")
            }
        }

        cursor.requireEnd()
        require(format == BackupArchive.DATA_FORMAT) { "Unsupported backup data format" }
        require(version == 1) { "Unsupported backup data version" }
        require(sawEntities) { "Missing records" }
        return Payload(
            format = format,
            version = version,
            entities = entities.mapValues { it.value.toList() },
            entries = entries,
            settings = settings,
        )
    }

    private fun readEntities(
        cursor: JsonCursor,
        destination: MutableMap<String, MutableList<JSONObject>>,
        supportedTypes: Set<String>,
        initialCount: Int,
    ): Int {
        cursor.expect('{')
        var count = initialCount
        val seenTypes = mutableSetOf<String>()
        if (cursor.peek() == '}'.code) {
            cursor.read()
            return count
        }
        while (true) {
            val type = cursor.readString()
            cursor.expect(':')
            require(seenTypes.add(type)) { "Duplicate entity group in backup data: $type" }
            val records = if (type in supportedTypes) mutableListOf<JSONObject>() else null
            cursor.expect('[')
            if (cursor.peek() == ']'.code) {
                cursor.read()
                if (records != null) destination[type] = records
                when (cursor.readStructural()) {
                    '}'.code -> return count
                    ','.code -> continue
                    -1 -> throw IllegalArgumentException("Unexpected end of entities object")
                    else -> throw IllegalArgumentException("Invalid entities object separator")
                }
            }
            while (true) {
                val raw = cursor.readRawValue(MAX_VALUE_CHARS)
                count++
                require(count <= MAX_TOTAL_ENTITY_RECORDS) { "Too many records in backup data" }
                if (records != null) {
                    val record = parseObject(raw)
                    records += record
                }
                when (cursor.readStructural()) {
                    ']'.code -> break
                    ','.code -> Unit
                    -1 -> throw IllegalArgumentException("Unexpected end of entity group")
                    else -> throw IllegalArgumentException("Invalid entity array separator")
                }
            }
            if (records != null) destination[type] = records
            when (cursor.readStructural()) {
                '}'.code -> return count
                ','.code -> Unit
                -1 -> throw IllegalArgumentException("Unexpected end of entities object")
                else -> throw IllegalArgumentException("Invalid entities object separator")
            }
        }
    }

    private fun readEntries(cursor: JsonCursor, destination: MutableList<Entry>) {
        cursor.expect('[')
        if (cursor.peek() == ']'.code) {
            cursor.read()
            return
        }
        while (true) {
            val raw = cursor.readRawValue(MAX_VALUE_CHARS)
            val record = parseObject(raw)
            val ownerId = record.optString("ownerId").trim()
            val date = record.optString("date").trim()
            val value = finiteNumber(record.opt("value"))
            destination += Entry(
                ownerId = ownerId,
                date = date,
                value = value ?: Double.NaN,
                status = record.optString("status"),
                notes = record.optString("notes"),
            )
            require(destination.size <= MAX_ENTRY_RECORDS) { "Too many entry records in backup data" }
            when (cursor.readStructural()) {
                ']'.code -> return
                ','.code -> Unit
                -1 -> throw IllegalArgumentException("Unexpected end of entries array")
                else -> throw IllegalArgumentException("Invalid entries array separator")
            }
        }
    }

    private fun readSettings(cursor: JsonCursor, destination: MutableMap<String, String>) {
        cursor.expect('{')
        if (cursor.peek() == '}'.code) {
            cursor.read()
            return
        }
        while (true) {
            val key = cursor.readString()
            cursor.expect(':')
            require(key !in destination) { "Duplicate setting in backup data: $key" }
            val raw = cursor.readRawValue(MAX_VALUE_CHARS)
            val value = parseScalar(raw)
            if (value != null && value !== JSONObject.NULL) destination[key] = value.toString()
            require(destination.size <= MAX_SETTING_RECORDS) { "Too many settings in backup data" }
            when (cursor.readStructural()) {
                '}'.code -> return
                ','.code -> Unit
                -1 -> throw IllegalArgumentException("Unexpected end of settings object")
                else -> throw IllegalArgumentException("Invalid settings object separator")
            }
        }
    }

    private fun parseObject(raw: String): JSONObject = try {
        JSONObject(raw)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid JSON record in backup data", e)
    }

    private fun parseScalar(raw: String): Any? {
        val tokener = JSONTokener(raw)
        val value = tokener.nextValue()
        if (tokener.nextClean() != 0.toChar()) {
            throw IllegalArgumentException("Invalid scalar value in backup data")
        }
        return value
    }

    private fun finiteNumber(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    /** A minimal character cursor which never buffers the complete JSON value. */
    private class JsonCursor(private val reader: Reader) {
        private var pushedBack: Int? = null

        fun read(): Int {
            pushedBack?.let {
                pushedBack = null
                return it
            }
            return reader.read()
        }

        /** Returns the next non-whitespace character without consuming it. */
        fun peek(): Int {
            skipWhitespace()
            return pushedBack!!
        }

        fun expect(expected: Char) {
            skipWhitespace()
            val actual = read()
            require(actual == expected.code) {
                val found = if (actual < 0) "EOF" else actual.toChar().toString()
                "Expected '$expected' in backup data.json but found $found"
            }
        }

        /** Read a structural separator, allowing insignificant whitespace before it. */
        fun readStructural(): Int {
            skipWhitespace()
            return read()
        }

        fun readString(): String {
            skipWhitespace()
            require(read() == '"'.code) { "Expected a string in backup data.json" }
            val result = StringBuilder()
            while (true) {
                val value = read()
                require(value >= 0) { "Unexpected end of string in backup data.json" }
                val char = value.toChar()
                if (char == '"') return result.toString()
                if (char == '\\') {
                    val escaped = read()
                    require(escaped >= 0) { "Unexpected end of escape in backup data.json" }
                    when (escaped.toChar()) {
                        '"' -> result.append('"')
                        '\\' -> result.append('\\')
                        '/' -> result.append('/')
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            var code = 0
                            repeat(4) {
                                val digit = read()
                                require(digit >= 0) { "Invalid unicode escape in backup data.json" }
                                val hex = digit.toChar().digitToIntOrNull(16)
                                    ?: throw IllegalArgumentException("Invalid unicode escape in backup data.json")
                                code = code * 16 + hex
                            }
                            result.append(code.toChar())
                        }
                        else -> throw IllegalArgumentException("Invalid escape in backup data.json")
                    }
                } else {
                    require(char.code >= 0x20) { "Invalid control character in backup data.json" }
                    result.append(char)
                }
                require(result.length <= MAX_VALUE_CHARS) { "JSON string is too large in backup data" }
            }
        }

        fun readInt(): Int {
            val raw = readRawValue(MAX_VALUE_CHARS)
            val value = parseScalar(raw)
            require(value is Number && value.toDouble().isFinite()) { "Invalid version in backup data" }
            return value.toInt()
        }

        fun readRawValue(limit: Int): String {
            skipWhitespace()
            val result = StringBuilder()
            val first = read()
            require(first >= 0) { "Unexpected end of backup data.json" }
            result.append(first.toChar())
            when (first.toChar()) {
                '{', '[' -> readComposite(result, limit)
                '"' -> readStringBody(result, limit)
                else -> readPrimitive(result, limit)
            }
            return result.toString()
        }

        fun skipValue() {
            readRawValue(MAX_VALUE_CHARS)
        }

        fun requireEnd() {
            skipWhitespace()
            require(read() < 0) { "Unexpected data after backup data.json" }
        }

        private fun readComposite(result: StringBuilder, limit: Int) {
            val stack = ArrayDeque<Char>()
            stack.addLast(if (result[0] == '{') '{' else '[')
            var inString = false
            var escaped = false
            while (stack.isNotEmpty()) {
                val value = read()
                require(value >= 0) { "Unexpected end of composite value in backup data.json" }
                val char = value.toChar()
                result.append(char)
                require(result.length <= limit) { "JSON value is too large in backup data" }
                if (inString) {
                    if (escaped) {
                        escaped = false
                    } else if (char == '\\') {
                        escaped = true
                    } else if (char == '"') {
                        inString = false
                    }
                    continue
                }
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> stack.addLast(char)
                    '}' -> {
                        require(stack.removeLastOrNull() == '{') { "Mismatched JSON brackets" }
                    }
                    ']' -> {
                        require(stack.removeLastOrNull() == '[') { "Mismatched JSON brackets" }
                    }
                }
            }
        }

        private fun readStringBody(result: StringBuilder, limit: Int) {
            var escaped = false
            while (true) {
                val value = read()
                require(value >= 0) { "Unexpected end of string in backup data.json" }
                val char = value.toChar()
                result.append(char)
                require(result.length <= limit) { "JSON string is too large in backup data" }
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    return
                }
            }
        }

        private fun readPrimitive(result: StringBuilder, limit: Int) {
            while (true) {
                val value = read()
                if (value < 0) return
                val char = value.toChar()
                if (char.isWhitespace() || char == ',' || char == ']' || char == '}') {
                    pushedBack = value
                    return
                }
                result.append(char)
                require(result.length <= limit) { "JSON value is too large in backup data" }
            }
        }

        private fun skipWhitespace() {
            while (true) {
                val value = read()
                if (value < 0 || !value.toChar().isWhitespace()) {
                    pushedBack = value
                    return
                }
            }
        }
    }
}
