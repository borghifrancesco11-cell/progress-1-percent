package com.progressone.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The v1 native backup container.
 *
 * A backup is a normal ZIP file with three kinds of entries:
 *
 *  - `manifest.json` describes the format and the files in `photos/`;
 *  - `data.json` contains the database snapshot as ordinary JSON;
 *  - `photos/<name>` contains binary image data.
 *
 * The implementation deliberately copies entries as streams.  It never creates
 * a Base64 copy of a photo and never needs the complete ZIP in memory.  The
 * same limits are used while writing and while reading an archive.
 */
object BackupArchive {
    const val FORMAT: String = "progress-one-native-archive"
    const val VERSION: Int = 1
    const val DATA_FORMAT: String = "progress-one-native-v1"
    const val MANIFEST_ENTRY: String = "manifest.json"
    const val DATA_ENTRY: String = "data.json"
    const val PHOTO_PREFIX: String = "photos/"

    /** Maximum compressed archive size accepted at either side of the boundary. */
    const val MAX_ARCHIVE_BYTES: Long = 512L * 1024L * 1024L

    /** Maximum size of one binary photo. */
    const val MAX_PHOTO_BYTES: Long = 9L * 1024L * 1024L

    /** Maximum length of a local photo member name. */
    const val MAX_PHOTO_NAME_CHARS: Int = 180

    /** A bounded uncompressed payload also protects against ZIP bombs. */
    const val MAX_UNCOMPRESSED_BYTES: Long = 1024L * 1024L * 1024L

    /**
     * The data member is intentionally not limited by the old 60 MB JSON
     * character cap.  It is still bounded independently of the archive.
     */
    const val MAX_DATA_BYTES: Long = 256L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES: Long = 2L * 1024L * 1024L

    /** A source photo that is eligible to be put in the archive. */
    data class PhotoSource(val name: String, val file: File)

    /** Metadata stored in manifest.json for one photo. */
    data class PhotoInfo(val name: String, val size: Long, val sha256: String)

    /**
     * The result of extraction.  All files live below [stagingDirectory] until
     * the caller has committed the database and promoted the photos.
     */
    class ExtractedArchive(
        val manifest: JSONObject,
        val dataFile: File,
        val photos: Map<String, File>,
        val stagingDirectory: File
    ) : Closeable {
        override fun close() {
            stagingDirectory.deleteRecursively()
        }
    }

    /**
     * Writes a complete archive to [output].  The caller's stream is flushed
     * but not closed, which makes the method safe to use with SAF streams.
     */
    fun write(
        output: OutputStream,
        dataFile: File,
        photos: List<PhotoSource>,
        manifestExtras: JSONObject = JSONObject()
    ) {
        require(dataFile.isFile) { "Backup data file is missing" }
        require(dataFile.length() <= MAX_DATA_BYTES) {
            "Backup data is too large (max ${MAX_DATA_BYTES} bytes)."
        }

        val uniquePhotos = LinkedHashMap<String, PhotoSource>()
        for (photo in photos) {
            require(isSafePhotoName(photo.name)) { "Unsafe photo name: ${photo.name}" }
            require(photo.file.isFile) { "Photo is missing: ${photo.name}" }
            require(photo.file.length() <= MAX_PHOTO_BYTES) {
                "Photo too large: ${photo.name}"
            }
            require(!uniquePhotos.containsKey(photo.name)) {
                "Duplicate photo: ${photo.name}"
            }
            uniquePhotos[photo.name] = photo
        }

        val photoInfo = uniquePhotos.values.map { source ->
            PhotoInfo(source.name, source.file.length(), sha256(source.file))
        }
        val manifest = buildManifest(dataFile, photoInfo, manifestExtras)
        val manifestBytes = manifest.toString(2).toByteArray(Charsets.UTF_8)
        require(manifestBytes.size <= MAX_MANIFEST_BYTES) { "Backup manifest is too large." }

        val counter = CountingOutputStream(output, MAX_ARCHIVE_BYTES)
        val shield = object : FilterOutputStream(counter) {
            override fun close() {
                flush()
            }
        }
        var uncompressed = 0L
        ZipOutputStream(BufferedOutputStream(shield)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifestBytes)
            uncompressed += manifestBytes.size
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            dataFile.inputStream().use { input ->
                copyLimited(input, zip, MAX_DATA_BYTES, "data.json") { count ->
                    uncompressed += count
                    require(uncompressed <= MAX_UNCOMPRESSED_BYTES) {
                        "Backup expands beyond the allowed size."
                    }
                }
            }
            zip.closeEntry()

            for (source in uniquePhotos.values) {
                zip.putNextEntry(ZipEntry(PHOTO_PREFIX + source.name))
                source.file.inputStream().use { input ->
                    copyLimited(input, zip, MAX_PHOTO_BYTES, source.name) { count ->
                        uncompressed += count
                        require(uncompressed <= MAX_UNCOMPRESSED_BYTES) {
                            "Backup expands beyond the allowed size."
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        // The ZIP central directory has now been written.  The shield prevented
        // use() from closing the SAF-owned stream.
        output.flush()
    }

    /**
     * Extracts and validates an archive into [stagingDirectory].  No database
     * or live media is touched by this method.
     */
    fun extract(input: InputStream, stagingDirectory: File): ExtractedArchive {
        require(stagingDirectory.mkdirs() || stagingDirectory.isDirectory) {
            "Cannot create backup staging directory"
        }
        val photosDirectory = File(stagingDirectory, "photos").apply { mkdirs() }
        val dataFile = File(stagingDirectory, DATA_ENTRY)
        val stagedPhotos = linkedMapOf<String, File>()
        var manifestBytes: ByteArray? = null
        var uncompressed = 0L
        var sawData = false

        val counted = CountingInputStream(BufferedInputStream(input), MAX_ARCHIVE_BYTES)
        ZipInputStream(counted).use { zip ->
            var entry: ZipEntry? = zip.getNextEntry()
            while (entry != null) {
                val current = entry
                val name = current.name
                require(!current.isDirectory || name == PHOTO_PREFIX) {
                    "Unexpected directory in backup: $name"
                }
                when {
                    name == MANIFEST_ENTRY -> {
                        require(manifestBytes == null) { "Duplicate manifest.json" }
                        manifestBytes = readEntry(zip, MAX_MANIFEST_BYTES, "manifest.json")
                        uncompressed += manifestBytes.size
                    }
                    name == DATA_ENTRY -> {
                        require(!sawData) { "Duplicate data.json" }
                        sawData = true
                        dataFile.outputStream().use { target ->
                            val copied = copyLimited(zip, target, MAX_DATA_BYTES, "data.json") { count ->
                                uncompressed += count
                                require(uncompressed <= MAX_UNCOMPRESSED_BYTES) {
                                    "Backup expands beyond the allowed size."
                                }
                            }
                            @Suppress("UNUSED_VARIABLE")
                            val ignored = copied
                        }
                    }
                    name.startsWith(PHOTO_PREFIX) -> {
                        val fileName = name.removePrefix(PHOTO_PREFIX)
                        require(isSafePhotoName(fileName)) { "Unsafe photo path: $name" }
                        require(!stagedPhotos.containsKey(fileName)) { "Duplicate photo: $fileName" }
                        val target = File(photosDirectory, fileName)
                        target.outputStream().use { destination ->
                            val copied = copyLimited(zip, destination, MAX_PHOTO_BYTES, fileName) { count ->
                                uncompressed += count
                                require(uncompressed <= MAX_UNCOMPRESSED_BYTES) {
                                    "Backup expands beyond the allowed size."
                                }
                            }
                            @Suppress("UNUSED_VARIABLE")
                            val ignored = copied
                        }
                        stagedPhotos[fileName] = target
                    }
                    else -> throw IllegalArgumentException("Unexpected backup entry: $name")
                }
                zip.closeEntry()
                entry = zip.getNextEntry()
            }
        }

        require(manifestBytes != null) { "Backup is missing manifest.json" }
        require(sawData) { "Backup is missing data.json" }
        require(uncompressed <= MAX_UNCOMPRESSED_BYTES) { "Backup expands beyond the allowed size." }

        val manifest = try {
            JSONObject(String(manifestBytes!!, Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup manifest.json", e)
        }
        validateManifest(manifest, stagedPhotos, dataFile)
        return ExtractedArchive(manifest, dataFile, stagedPhotos, stagingDirectory)
    }

    /** Returns true when a filename is a safe photo member name. */
    fun isSafePhotoName(name: String): Boolean {
        if (name.isBlank() || name.length > MAX_PHOTO_NAME_CHARS || name != File(name).name) return false
        if (name == "." || name == ".." || name.startsWith(".")) return false
        if (!name.matches(Regex("[A-Za-z0-9_.-]+\\.(png|jpg)"))) return false
        return true
    }

    /** Computes the SHA-256 digest of a file without loading it in memory. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun buildManifest(
        dataFile: File,
        photos: List<PhotoInfo>,
        extras: JSONObject
    ): JSONObject {
        val manifest = JSONObject()
        manifest.put("format", FORMAT)
        manifest.put("version", VERSION)
        manifest.put("createdAt", System.currentTimeMillis())
        manifest.put("dataFormat", DATA_FORMAT)
        manifest.put("data", DATA_ENTRY)
        manifest.put("dataSha256", sha256(dataFile))
        val photoArray = JSONArray()
        photos.forEach { photo ->
            photoArray.put(JSONObject().apply {
                put("name", photo.name)
                put("path", PHOTO_PREFIX + photo.name)
                put("size", photo.size)
                put("sha256", photo.sha256)
            })
        }
        manifest.put("photos", photoArray)
        manifest.put("limits", JSONObject().apply {
            put("maxArchiveBytes", MAX_ARCHIVE_BYTES)
            put("maxUncompressedBytes", MAX_UNCOMPRESSED_BYTES)
            put("maxDataBytes", MAX_DATA_BYTES)
            put("maxPhotoBytes", MAX_PHOTO_BYTES)
            put("maxManifestBytes", MAX_MANIFEST_BYTES)
        })
        // Extras are useful for counts, but cannot replace the format fields.
        val keys = extras.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in RESERVED_MANIFEST_KEYS && !extras.isNull(key)) manifest.put(key, extras.get(key))
        }
        return manifest
    }

    private fun validateManifest(
        manifest: JSONObject,
        stagedPhotos: Map<String, File>,
        dataFile: File
    ) {
        require(manifest.optString("format") == FORMAT) { "Unsupported backup archive format" }
        require(manifest.optInt("version", -1) == VERSION) { "Unsupported backup archive version" }
        require(manifest.optString("dataFormat") == DATA_FORMAT) { "Unsupported backup data format" }
        require(manifest.optString("data") == DATA_ENTRY) { "Invalid backup data path" }
        require(dataFile.isFile && dataFile.length() <= MAX_DATA_BYTES) { "Invalid backup data.json" }
        require(manifest.optString("dataSha256") == sha256(dataFile)) {
            "Backup data.json checksum does not match"
        }

        val listed = linkedMapOf<String, PhotoInfo>()
        val array = manifest.optJSONArray("photos") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: throw IllegalArgumentException("Invalid photo manifest")
            val name = item.optString("name")
            require(isSafePhotoName(name)) { "Unsafe photo in manifest: $name" }
            require(item.optString("path") == PHOTO_PREFIX + name) { "Invalid photo path in manifest" }
            val size = item.optLong("size", -1L)
            require(size in 0..MAX_PHOTO_BYTES) { "Invalid photo size in manifest" }
            val digest = item.optString("sha256")
            require(digest.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid photo checksum in manifest" }
            require(!listed.containsKey(name)) { "Duplicate photo in manifest: $name" }
            listed[name] = PhotoInfo(name, size, digest.lowercase())
        }
        require(listed.keys == stagedPhotos.keys) { "Photo manifest does not match archive photos" }
        for ((name, info) in listed) {
            val file = stagedPhotos.getValue(name)
            require(file.length() == info.size) { "Photo size does not match manifest: $name" }
            require(sha256(file) == info.sha256) { "Photo checksum does not match manifest: $name" }
        }
    }

    private fun readEntry(input: InputStream, limit: Long, label: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val copied = copyLimited(input, output, limit, label)
        @Suppress("UNUSED_VARIABLE")
        val ignored = copied
        return output.toByteArray()
    }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        limit: Long,
        label: String,
        onBytes: (Long) -> Unit = {}
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= limit) { "$label is too large (max $limit bytes)." }
            output.write(buffer, 0, count)
            onBytes(count.toLong())
        }
        return total
    }

    private class CountingOutputStream(
        private val delegate: OutputStream,
        private val limit: Long
    ) : OutputStream() {
        var count: Long = 0L
            private set

        override fun write(value: Int) {
            write(byteArrayOf(value.toByte()), 0, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            require(count + length <= limit) {
                "Backup archive is too large (max $limit bytes)."
            }
            delegate.write(buffer, offset, length)
            count += length
        }

        override fun flush() = delegate.flush()
    }

    private class CountingInputStream(
        private val delegate: InputStream,
        private val limit: Long
    ) : InputStream() {
        var count: Long = 0L
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                count++
                require(count <= limit) { "Backup archive is too large (max $limit bytes)." }
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = delegate.read(buffer, offset, length)
            if (count > 0) {
                this.count += count
                require(this.count <= limit) { "Backup archive is too large (max $limit bytes)." }
            }
            return count
        }

        override fun available(): Int = delegate.available()
        override fun close() = delegate.close()
    }

    private val RESERVED_MANIFEST_KEYS = setOf(
        "format", "version", "createdAt", "dataFormat", "data", "dataSha256", "photos", "limits"
    )
}
