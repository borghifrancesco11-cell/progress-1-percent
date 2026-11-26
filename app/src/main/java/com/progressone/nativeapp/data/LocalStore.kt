package com.progressone.nativeapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.util.Base64
import java.util.UUID

/**
 * Native, versioned SQLite data store. No WebView, no JavaScript bridge, no localStorage.
 * Media is stored in internal app files rather than in the database or in a base64 UI state.
 * Core entities are independent rows; history is separately indexed by (item, calendar date).
 *
 * Every write goes through the same small validation policy as an import.  A
 * malformed row is ignored rather than being allowed to poison calculations
 * with NaN, infinity, or an impossible calendar date.
 */
class LocalStore(private val appContext: Context) : SQLiteOpenHelper(appContext, "progress_one.db", null, 3) {
    var revision by mutableIntStateOf(0)
        private set
    val photoDirectory: File = File(appContext.filesDir, "garments").apply { mkdirs() }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE entities (
            type TEXT NOT NULL,
            id TEXT NOT NULL,
            payload TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY(type,id)
        )""")
        db.execSQL("CREATE INDEX ix_entities_type_updated ON entities(type,updated_at)")
        db.execSQL("""CREATE TABLE entries (
            owner_id TEXT NOT NULL,
            date TEXT NOT NULL,
            value REAL NOT NULL DEFAULT 0,
            status TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '',
            PRIMARY KEY(owner_id,date)
        )""")
        db.execSQL("CREATE INDEX ix_entries_date ON entries(date)")
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never DROP user data on version changes.
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS ix_entries_date ON entries(date)")
        }
        if (oldVersion < 3) {
            cleanInvalidHistoricalData(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // Older databases may contain values written before finite-number and
        // strict-date validation existed.  Clean those rows on open while
        // preserving every valid user record.
        cleanInvalidHistoricalData(db)
    }

    fun all(type: String): List<JSONObject> {
        if (type !in TYPES) return emptyList()
        val result = mutableListOf<JSONObject>()
        readableDatabase.query("entities", arrayOf("payload"), "type=?", arrayOf(type), null, null,
            "updated_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val source = JSONObject(cursor.getString(0))
                    sanitizeEntity(type, source)?.let(result::add)
                } catch (_: Exception) { /* corrupt row isolated */ }
            }
        }
        return result
    }

    fun find(type: String, id: String): JSONObject? = all(type).firstOrNull { it.optString("id") == id }

    /**
     * Stores a valid record.  Invalid optional fields are removed; records
     * whose central field is malformed (for example a transaction amount or a
     * sleep date) are skipped.  This keeps UI callers from persisting a
     * partially corrupt object while still allowing ordinary edits.
     */
    fun put(type: String, record: JSONObject): Boolean {
        require(type in TYPES) { "Unsupported record type" }
        synchronized(MEDIA_LOCK) {
            try {
                val id = record.optString("id").trim().ifBlank { freshId() }
                record.put("id", id)
                val clean = sanitizeEntity(type, record) ?: return false
                writeEntity(writableDatabase, type, clean)
                revision++
                return true
            } catch (_: Exception) {
                // A malformed caller object must not make the rest of the app
                // crash or write NaN into SQLite.
                return false
            }
        }
    }

    private fun writeEntity(db: SQLiteDatabase, type: String, record: JSONObject) {
        val now = System.currentTimeMillis()
        val row = ContentValues().apply {
            put("type", type)
            put("id", record.getString("id"))
            put("payload", record.toString())
            put("created_at", now)
            put("updated_at", now)
        }
        db.insertWithOnConflict("entities", null, row, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun remove(type: String, id: String) {
        synchronized(MEDIA_LOCK) {
            // Defer media deletion until the record is gone, and keep shared imported files.
            val oldRecord = if (type == "garment" || type == "food") find(type, id) else null
            val pendingPhoto = oldRecord?.optString("photo")
            val pendingOriginal = oldRecord?.optString("sourcePhoto")
            if (type == "garment") {
                all("outfit").forEach { outfit ->
                    val updated = JSONArray()
                    val items = outfit.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) if (items.optString(i) != id) updated.put(items.optString(i))
                    outfit.put("items", updated)
                    writeEntity(writableDatabase, "outfit", outfit)
                }
            }
            writableDatabase.delete("entities", "type=? AND id=?", arrayOf(type, id))
            writableDatabase.delete("entries", "owner_id=?", arrayOf(id))
            cleanupUnreferencedPhoto(pendingPhoto)
            cleanupUnreferencedPhoto(pendingOriginal)
            revision++
        }
    }

    fun saveEntry(id: String, date: String, value: Double, status: String = "done", note: String = "") {
        if (!isValidEntry(id, date, value)) return
        synchronized(MEDIA_LOCK) {
            val row = ContentValues().apply {
                put("owner_id", id); put("date", date); put("value", value)
                put("status", status); put("notes", note)
            }
            writableDatabase.insertWithOnConflict("entries", null, row, SQLiteDatabase.CONFLICT_REPLACE)
            revision++
        }
    }

    fun deleteEntry(id: String, date: String) {
        if (id.isBlank() || !InputValidation.isValidDate(date)) return
        synchronized(MEDIA_LOCK) {
            writableDatabase.delete("entries", "owner_id=? AND date=?", arrayOf(id, date))
            revision++
        }
    }

    data class Entry(val ownerId: String, val date: String, val value: Double, val status: String, val notes: String)

    fun entries(id: String? = null): List<Entry> {
        val result = mutableListOf<Entry>()
        readableDatabase.query("entries", arrayOf("owner_id", "date", "value", "status", "notes"),
            if (id == null) null else "owner_id=?", if (id == null) null else arrayOf(id),
            null, null, "date DESC").use { cursor ->
            while (cursor.moveToNext()) {
                val owner = cursor.getString(0)
                val date = cursor.getString(1)
                val value = cursor.getDouble(2)
                if (isValidEntry(owner, date, value)) {
                    result += Entry(owner, date, value, cursor.getString(3), cursor.getString(4))
                }
            }
        }
        return result
    }

    fun setting(key: String, default: String = ""): String {
        readableDatabase.query("settings", arrayOf("value"), "key=?", arrayOf(key),
            null, null, null).use { if (it.moveToFirst()) {
            val value = sanitizeSetting(key, it.getString(0))
            if (value != null) return value
        } }
        return default
    }

    fun setSetting(key: String, value: String) {
        val clean = sanitizeSetting(key, value) ?: return
        synchronized(MEDIA_LOCK) {
            val c = ContentValues().apply { put("key", key); put("value", clean) }
            writableDatabase.insertWithOnConflict("settings", null, c, SQLiteDatabase.CONFLICT_REPLACE)
            revision++
        }
    }

    /**
     * Runs a media-file mutation under the same process-wide lock used by
     * backup snapshots.  Callers must only use this for short, non-blocking
     * filesystem operations; record updates use [put] directly.
     */
    fun <T> withMediaLock(action: () -> T): T = synchronized(MEDIA_LOCK) { action() }

    fun photoFile(filename: String): File? {
        if (filename.isBlank() || filename != File(filename).name ||
            filename.contains('\u0000') ||
            !(filename.endsWith(".png") || filename.endsWith(".jpg"))) return null
        return File(photoDirectory, filename)
    }

    /** Remove a media file only if no garment or food record still uses it. */
    fun cleanupUnreferencedPhoto(filename: String?) {
        if (filename.isNullOrBlank()) return
        synchronized(MEDIA_LOCK) {
            if ((all("garment") + all("food")).none {
                    it.optString("photo") == filename || it.optString("sourcePhoto") == filename
                }) {
                photoFile(filename)?.delete()
            }
        }
    }

    /** Remove all media which is not referenced by a persisted garment/food record. */
    private fun cleanupOrphanedPhotos() {
        synchronized(MEDIA_LOCK) {
            val referenced = referencedPhotoNames()
            photoDirectory.listFiles()?.forEach { file ->
                if (file.isFile && file.name !in referenced) file.delete()
            }
        }
    }

    /**
     * Exports the current data as a streaming ZIP archive.  Only photos which
     * are actually referenced by persisted garment/food records are included;
     * orphaned camera files and temporary files are intentionally ignored.
     */
    fun exportArchive(output: OutputStream) {
        val snapshot = createExportSnapshot()
        try {
            // The ZIP is written from immutable staging files after the data and
            // photo snapshot has been completed.  A slow SAF provider therefore
            // cannot block normal record mutations.
            BackupArchive.write(output, snapshot.dataFile, snapshot.photos)
        } finally {
            snapshot.directory.deleteRecursively()
        }
    }

    private data class ExportSnapshot(
        val directory: File,
        val dataFile: File,
        val photos: List<BackupArchive.PhotoSource>
    )

    /**
     * Captures records and every referenced photo while holding the global
     * media lock.  The staged files are never mutated afterwards, so their
     * manifest checksums and ZIP members always describe the same bytes.
     */
    private fun createExportSnapshot(): ExportSnapshot = synchronized(MEDIA_LOCK) {
        val directory = File(appContext.cacheDir, "progress1-export-${UUID.randomUUID()}")
        check(directory.mkdirs()) { "Cannot create backup snapshot directory" }
        try {
            val dataFile = File(directory, "data.json")
            writeDataSnapshot(dataFile)

            val photosDirectory = File(directory, "photos").apply {
                check(mkdirs()) { "Cannot create backup photo snapshot directory" }
            }
            val stagedPhotos = collectReferencedPhotos().map { source ->
                val target = File(photosDirectory, source.name)
                copyPhotoSnapshot(source.file, target, source.name)
                BackupArchive.PhotoSource(source.name, target)
            }
            ExportSnapshot(directory, dataFile, stagedPhotos)
        } catch (t: Throwable) {
            directory.deleteRecursively()
            throw t
        }
    }

    private fun copyPhotoSnapshot(source: File, target: File, name: String) {
        val expectedSize = source.length()
        require(expectedSize <= BackupArchive.MAX_PHOTO_BYTES) { "Photo too large: $name" }
        var copied = 0L
        source.inputStream().buffered().use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= BackupArchive.MAX_PHOTO_BYTES) { "Photo too large: $name" }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(copied == expectedSize) { "Photo changed during backup: $name" }
    }

    /** Alias with a name convenient for callers that treat it as a backup. */
    fun exportBackup(output: OutputStream) = exportArchive(output)

    /**
     * Imports a ZIP archive.  The archive is fully validated and its photos
     * are staged before the SQLite transaction starts.  The transaction and
     * media promotion are rolled back on failure, leaving the old user data
     * and old photos intact.
     */
    fun importArchive(input: InputStream): String {
        val staging = File(appContext.cacheDir, "progress1-import-${UUID.randomUUID()}")
        try {
            val archive = BackupArchive.extract(input, staging)
            val payload = try {
                NativeBackupReader.read(archive.dataFile)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid backup data.json", e)
            }
            val prepared = prepareNativePayload(payload)
            validateArchiveMedia(prepared, archive.photos.keys)
            applyPreparedData(prepared, archive.photos)
            return "Native archive restored"
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Alias with a name convenient for callers that treat it as a backup. */
    fun importBackup(input: InputStream): String = importArchive(input)

    /**
     * Detects a ZIP archive or a legacy JSON document without buffering the
     * complete input.  Legacy JSON remains supported for migration purposes.
     */
    fun importBackupOrLegacy(input: InputStream): String {
        val pushback = PushbackInputStream(input, 8)
        val magic = ByteArray(4)
        var read = 0
        while (read < magic.size) {
            val count = pushback.read(magic, read, magic.size - read)
            if (count < 0) break
            read += count
        }
        if (read > 0) pushback.unread(magic, 0, read)
        val isZip = read >= 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        return if (isZip) importArchive(pushback) else {
            val text = pushback.bufferedReader(Charsets.UTF_8).use { it.readText() }
            importJson(text)
        }
    }

    /**
     * Legacy JSON import.  The 60 MB character limit applies only to this
     * legacy text format; the v1 ZIP archive has its own byte limits.
     */
    fun importJson(source: String): String {
        require(source.length <= 60_000_000) { "Backup is too large (max 60 MB)." }
        val root = try { JSONObject(source) } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup JSON", e)
        }
        val native = root.optString("format") == BackupArchive.DATA_FORMAT
        val legacyMain = root.optJSONObject("progressiOne")
        val legacyTracker = root.optJSONObject("progressiLocal") ?: if (root.has("items") && root.has("tasks")) root else null
        if (!native && legacyMain == null && legacyTracker == null) {
            throw IllegalArgumentException("Unsupported backup format")
        }
        if (native) require(root.optJSONObject("entities") != null) { "Missing records" }
        if (legacyMain != null) require(legacyMain.optJSONArray("wardrobe") != null) { "Invalid Progress 1% backup" }
        if (legacyTracker != null) require(legacyTracker.optJSONArray("items") != null) { "Invalid tracker backup" }

        val bundle = if (native) prepareNativeBundle(root) else prepareLegacyBundle(root, legacyMain, legacyTracker)
        val mediaDirectory = File(appContext.cacheDir, "progress1-legacy-media-${UUID.randomUUID()}")
        try {
            mediaDirectory.mkdirs()
            val mediaFiles = bundle.mediaBytes.mapValues { (name, bytes) ->
                File(mediaDirectory, name).also { it.writeBytes(bytes) }
            }
            val prepared = bundle.prepared
            removeMissingPhotoReferences(prepared, mediaFiles.keys)
            applyPreparedData(prepared, mediaFiles)
            return if (native) "Native backup restored" else "Previous app backup imported"
        } finally {
            mediaDirectory.deleteRecursively()
        }
    }

    /**
     * Kept for source compatibility with older integrations.  New exports use
     * [exportArchive], which is binary, readable and does not Base64-encode
     * photos.  This legacy view intentionally contains records and settings,
     * but not an unbounded photo payload.
     */
    @Deprecated("Use exportArchive(OutputStream) for the v1 ZIP format")
    fun exportJson(): String {
        val root = JSONObject()
        root.put("format", BackupArchive.DATA_FORMAT)
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val groups = JSONObject()
        for (type in TYPES) {
            val array = JSONArray()
            all(type).forEach { array.put(it) }
            groups.put(type, array)
        }
        root.put("entities", groups)
        val logs = JSONArray()
        entries().forEach { e -> logs.put(JSONObject().apply {
            put("ownerId", e.ownerId); put("date", e.date); put("value", e.value)
            put("status", e.status); put("notes", e.notes)
        }) }
        root.put("entries", logs)
        val settings = JSONObject()
        readableDatabase.query("settings", arrayOf("key", "value"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) settings.put(c.getString(0), c.getString(1))
        }
        root.put("settings", settings)
        root.put("photos", JSONObject())
        return root.toString(2)
    }

    private data class PreparedData(
        val entities: Map<String, List<JSONObject>>,
        val entries: List<Entry>,
        val settings: Map<String, String>
    )

    private data class PreparedBundle(
        val prepared: PreparedData,
        val mediaBytes: Map<String, ByteArray>
    )

    private fun preparedEntity(source: JSONObject): JSONObject {
        val copy = JSONObject(source.toString())
        if (copy.optString("id").trim().isBlank()) copy.put("id", freshId())
        return copy
    }

    private fun prepareNativeBundle(root: JSONObject): PreparedBundle {
        val prepared = prepareNativeData(root)
        val media = linkedMapOf<String, ByteArray>()
        val photos = root.optJSONObject("photos") ?: JSONObject()
        val keys = photos.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            if (!BackupArchive.isSafePhotoName(name)) continue
            val encoded = photos.optString(name)
            val bytes = decodeBase64(encoded) ?: continue
            if (bytes.size > BackupArchive.MAX_PHOTO_BYTES) continue
            media[name] = bytes
        }
        return PreparedBundle(prepared, media)
    }

    private fun prepareLegacyBundle(
        root: JSONObject,
        legacyMain: JSONObject?,
        legacyTracker: JSONObject?
    ): PreparedBundle {
        val entities = linkedMapOf<String, MutableList<JSONObject>>()
        val entries = mutableListOf<Entry>()
        val settings = linkedMapOf<String, String>()
        val media = linkedMapOf<String, ByteArray>()

        if (legacyMain != null) {
            val maps = mapOf("wardrobe" to "garment", "outfits" to "outfit", "wears" to "wear",
                "packing" to "packing", "wishlist" to "wishlist", "subjects" to "subject",
                "grades" to "grade", "exams" to "exam", "transactions" to "transaction")
            for ((old, kind) in maps) {
                val rows = legacyMain.optJSONArray(old) ?: continue
                for (i in 0 until rows.length()) {
                    val item = rows.optJSONObject(i) ?: continue
                    if (kind == "garment") extractLegacyPhoto(item, media)
                    val clean = sanitizeEntity(kind, preparedEntity(item)) ?: continue
                    entities.getOrPut(kind) { mutableListOf() }.add(clean)
                }
            }
            val legacySettings = legacyMain.optJSONObject("settings") ?: JSONObject()
            val settingKeys = legacySettings.keys()
            while (settingKeys.hasNext()) {
                val key = settingKeys.next()
                val value = legacySettings.opt(key)
                if (value == null || value == JSONObject.NULL) continue
                val clean = sanitizeSetting(key, value.toString()) ?: continue
                settings[key] = clean
            }
            val budgets = legacyMain.optJSONObject("budgets") ?: JSONObject()
            val budgetKeys = budgets.keys()
            while (budgetKeys.hasNext()) {
                val month = budgetKeys.next()
                val value = budgets.opt(month)
                if (value == null || value == JSONObject.NULL) continue
                val clean = sanitizeSetting("budget_$month", value.toString()) ?: continue
                settings["budget_$month"] = clean
            }
        }

        if (legacyTracker != null) {
            val items = legacyTracker.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val tracker = preparedEntity(items.optJSONObject(i) ?: continue)
                val history = tracker.optJSONObject("entries") ?: JSONObject()
                val historyKeys = history.keys()
                while (historyKeys.hasNext()) {
                    val date = historyKeys.next()
                    val entry = history.optJSONObject(date) ?: continue
                    val value = readFiniteNumber(entry, "value") ?: continue
                    if (!InputValidation.isValidDate(date) || tracker.optString("id").isBlank()) continue
                    entries += Entry(tracker.optString("id"), date, value,
                        entry.optString("status"), entry.optString("notes"))
                }
                tracker.remove("entries")
                val clean = sanitizeEntity("tracker", tracker) ?: continue
                entities.getOrPut("tracker") { mutableListOf() }.add(clean)
            }
            val tasks = legacyTracker.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until tasks.length()) {
                val task = preparedEntity(tasks.optJSONObject(i) ?: continue)
                val clean = sanitizeEntity("task", task) ?: continue
                entities.getOrPut("task") { mutableListOf() }.add(clean)
            }
        }
        return PreparedBundle(PreparedData(entities.mapValues { it.value.toList() }, entries, settings), media)
    }

    private fun prepareNativeData(root: JSONObject): PreparedData {
        require(root.optString("format") == BackupArchive.DATA_FORMAT) { "Unsupported backup data format" }
        require(root.optInt("version", 1) == 1) { "Unsupported backup data version" }
        val groups = root.optJSONObject("entities") ?: throw IllegalArgumentException("Missing records")
        val entities = linkedMapOf<String, List<JSONObject>>()
        for (type in TYPES) {
            val rows = groups.optJSONArray(type) ?: JSONArray()
            val cleanRows = mutableListOf<JSONObject>()
            for (i in 0 until rows.length()) {
                val source = rows.optJSONObject(i) ?: continue
                sanitizeEntity(type, preparedEntity(source))?.let(cleanRows::add)
            }
            entities[type] = cleanRows
        }
        val entries = mutableListOf<Entry>()
        val logs = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until logs.length()) {
            val source = logs.optJSONObject(i) ?: continue
            val owner = source.optString("ownerId").trim()
            val date = source.optString("date").trim()
            val value = readFiniteNumber(source, "value") ?: continue
            if (isValidEntry(owner, date, value)) {
                entries += Entry(owner, date, value, source.optString("status"), source.optString("notes"))
            }
        }
        val settings = linkedMapOf<String, String>()
        val sourceSettings = root.optJSONObject("settings") ?: JSONObject()
        val keys = sourceSettings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = sourceSettings.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            sanitizeSetting(key, value.toString())?.let { settings[key] = it }
        }
        return PreparedData(entities, entries, settings)
    }

    /** Sanitize a payload read one bounded record at a time from data.json. */
    private fun prepareNativePayload(payload: NativeBackupReader.Payload): PreparedData {
        require(payload.format == BackupArchive.DATA_FORMAT) { "Unsupported backup data format" }
        require(payload.version == 1) { "Unsupported backup data version" }

        val entities = linkedMapOf<String, List<JSONObject>>()
        for (type in TYPES) {
            val cleanRows = mutableListOf<JSONObject>()
            for (source in payload.entities[type].orEmpty()) {
                sanitizeEntity(type, preparedEntity(source))?.let(cleanRows::add)
            }
            entities[type] = cleanRows
        }

        val entries = mutableListOf<Entry>()
        for (source in payload.entries) {
            if (isValidEntry(source.ownerId, source.date, source.value)) {
                entries += Entry(source.ownerId, source.date, source.value, source.status, source.notes)
            }
        }

        val settings = linkedMapOf<String, String>()
        for ((key, value) in payload.settings) {
            sanitizeSetting(key, value)?.let { settings[key] = it }
        }
        return PreparedData(entities, entries, settings)
    }

    private fun validateArchiveMedia(prepared: PreparedData, archivePhotos: Set<String>) {
        val references = preparedPhotoReferences(prepared)
        for (name in references) {
            require(name in archivePhotos) { "Backup is missing referenced photo: $name" }
        }
        require(archivePhotos.containsAll(references)) { "Backup photo list is incomplete" }
        // A v1 archive is self-contained.  Rejecting unreferenced members also
        // prevents an accidental temporary camera file from being restored.
        require(archivePhotos.none { it !in references }) {
            "Backup contains an unreferenced photo"
        }
    }

    private fun removeMissingPhotoReferences(prepared: PreparedData, available: Set<String>) {
        for (records in prepared.entities.values) {
            for (record in records) {
                for (field in PHOTO_FIELDS) {
                    val name = record.optString(field)
                    if (name.isNotBlank() && name !in available) record.remove(field)
                }
            }
        }
    }

    private fun applyPreparedData(prepared: PreparedData, mediaFiles: Map<String, File>) {
        synchronized(MEDIA_LOCK) {
            val promoted = promoteMedia(mediaFiles)
            var committed = false
            try {
                rewritePhotoReferences(prepared, promoted)
                val db = writableDatabase
                db.beginTransaction()
                try {
                    db.delete("entities", null, null)
                    db.delete("entries", null, null)
                    db.delete("settings", null, null)
                    for (type in TYPES) {
                        prepared.entities[type].orEmpty().forEach { writeEntity(db, type, it) }
                    }
                    prepared.entries.forEach {
                        writeEntry(db, it.ownerId, it.date, it.value, it.status, it.notes)
                    }
                    prepared.settings.forEach { (key, value) -> writeSetting(db, key, value) }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                committed = true
                cleanupOrphanedPhotos()
                revision++
            } catch (t: Throwable) {
                // Never delete promoted media after the database transaction has
                // committed: the committed records would point to missing files.
                if (!committed) promoted.values.forEach { name -> File(photoDirectory, name).delete() }
                throw t
            }
        }
    }

    /**
     * Copies staged photos to the live media directory before the DB commit.
     * Existing files are never overwritten; a collision gets a fresh name and
     * the entity references are rewritten accordingly.  If the transaction
     * fails, the newly copied files are removed and the old media remains.
     */
    private fun promoteMedia(mediaFiles: Map<String, File>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val created = mutableListOf<File>()
        try {
            for ((name, source) in mediaFiles) {
                if (!BackupArchive.isSafePhotoName(name)) continue
                if (!source.isFile || source.length() > BackupArchive.MAX_PHOTO_BYTES) {
                    throw IllegalArgumentException("Invalid photo in backup: $name")
                }
                var targetName = name
                var target = File(photoDirectory, targetName)
                if (target.exists()) {
                    // Keep the collision name within the archive's 180-character
                    // limit; otherwise the next export would reject the restored
                    // photo as unsafe and silently drop its reference.
                    val extension = name.substringAfterLast('.', "jpg")
                    val stem = name.substringBeforeLast('.', "photo")
                    val prefix = "import_${UUID.randomUUID()}_"
                    val maxStem = (BackupArchive.MAX_PHOTO_NAME_CHARS - prefix.length - extension.length - 1)
                        .coerceAtLeast(1)
                    do {
                        targetName = "$prefix${stem.take(maxStem)}.$extension"
                        target = File(photoDirectory, targetName)
                    } while (target.exists())
                }
                val temporary = File(photoDirectory, ".$targetName.${UUID.randomUUID()}.tmp")
                try {
                    source.inputStream().use { input ->
                        temporary.outputStream().use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    // The temporary and final files are in the same directory, so the
                    // rename is atomic. A failed rename must abort the import rather
                    // than expose a partially copied photo.
                    if (!temporary.renameTo(target)) {
                        throw java.io.IOException("Could not atomically install imported photo: $name")
                    }
                } finally {
                    temporary.delete()
                }
                created += target
                result[name] = targetName
            }
            return result
        } catch (t: Throwable) {
            created.forEach { it.delete() }
            throw t
        }
    }

    private fun rewritePhotoReferences(prepared: PreparedData, mapping: Map<String, String>) {
        for (records in prepared.entities.values) {
            for (record in records) {
                for (field in PHOTO_FIELDS) {
                    val old = record.optString(field)
                    mapping[old]?.let { record.put(field, it) }
                }
            }
        }
    }

    private fun collectReferencedPhotos(): List<BackupArchive.PhotoSource> {
        val result = linkedMapOf<String, BackupArchive.PhotoSource>()
        for (type in MEDIA_TYPES) {
            queryEntities(type) { source ->
                val clean = sanitizeEntity(type, source) ?: return@queryEntities
                removeMissingPhotoReferencesInRecord(clean)
                for (field in PHOTO_FIELDS) {
                    val name = clean.optString(field).trim()
                    if (name.isBlank()) continue
                    val file = photoFile(name) ?: continue
                    if (!file.isFile) continue
                    if (file.length() > BackupArchive.MAX_PHOTO_BYTES) {
                        throw IllegalArgumentException("Photo too large: $name")
                    }
                    if (!result.containsKey(name)) result[name] = BackupArchive.PhotoSource(name, file)
                }
            }
        }
        return result.values.toList()
    }

    private fun referencedPhotoNames(): Set<String> {
        val result = linkedSetOf<String>()
        for (type in MEDIA_TYPES) {
            queryEntities(type) { source ->
                val clean = sanitizeEntity(type, source) ?: return@queryEntities
                for (field in PHOTO_FIELDS) {
                    val name = clean.optString(field).trim()
                    if (name.isNotBlank()) result += name
                }
            }
        }
        return result
    }

    private fun preparedPhotoReferences(prepared: PreparedData): Set<String> {
        val result = linkedSetOf<String>()
        for ((type, records) in prepared.entities) {
            if (type !in MEDIA_TYPES) continue
            for (record in records) {
                for (field in PHOTO_FIELDS) {
                    val name = record.optString(field).trim()
                    if (name.isNotBlank()) result += name
                }
            }
        }
        return result
    }

    private fun removeMissingPhotoReferencesInRecord(record: JSONObject) {
        for (field in PHOTO_FIELDS) {
            val name = record.optString(field).trim()
            if (name.isNotBlank() && (photoFile(name)?.isFile != true)) record.remove(field)
        }
    }

    private fun queryEntities(type: String, block: (JSONObject) -> Unit) {
        readableDatabase.query("entities", arrayOf("payload"), "type=?", arrayOf(type), null, null,
            "updated_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                // Only malformed persisted JSON is isolated.  Exceptions raised
                // by the callback (for example an oversized referenced photo)
                // must abort the export instead of producing an incomplete ZIP.
                val source = try {
                    JSONObject(cursor.getString(0))
                } catch (_: Exception) {
                    continue
                }
                block(source)
            }
        }
    }

    /** Writes data.json incrementally; photos are never represented in JSON. */
    private fun writeDataSnapshot(target: File) {
        target.outputStream().bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("{\n")
            out.write("  \"format\": ${JSONObject.quote(BackupArchive.DATA_FORMAT)},\n")
            out.write("  \"version\": 1,\n")
            out.write("  \"exportedAt\": ${System.currentTimeMillis()},\n")
            out.write("  \"entities\": {\n")
            for ((typeIndex, type) in TYPES.withIndex()) {
                out.write("    ${JSONObject.quote(type)}: [\n")
                var first = true
                queryEntities(type) { source ->
                    val clean = sanitizeEntity(type, source) ?: return@queryEntities
                    if (type == "garment" || type == "food") removeMissingPhotoReferencesInRecord(clean)
                    try {
                        if (!first) out.write(",\n")
                        out.write("      " + prettyJson(clean, "      "))
                        first = false
                    } catch (_: Exception) { }
                }
                out.write("\n    ]")
                if (typeIndex < TYPES.lastIndex) out.write(",")
                out.write("\n")
            }
            out.write("  },\n")
            out.write("  \"entries\": [\n")
            var firstEntry = true
            readableDatabase.query("entries", arrayOf("owner_id", "date", "value", "status", "notes"),
                null, null, null, null, "date DESC").use { cursor ->
                while (cursor.moveToNext()) {
                    val owner = cursor.getString(0)
                    val date = cursor.getString(1)
                    val value = cursor.getDouble(2)
                    if (!isValidEntry(owner, date, value)) continue
                    if (!firstEntry) out.write(",\n")
                    out.write("      " + prettyJson(JSONObject().apply {
                        put("ownerId", owner); put("date", date); put("value", value)
                        put("status", cursor.getString(3)); put("notes", cursor.getString(4))
                    }, "      "))
                    firstEntry = false
                }
            }
            out.write("\n  ],\n")
            out.write("  \"settings\": {\n")
            var firstSetting = true
            readableDatabase.query("settings", arrayOf("key", "value"), null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    val value = sanitizeSetting(key, cursor.getString(1)) ?: continue
                    if (!firstSetting) out.write(",\n")
                    out.write("    ${JSONObject.quote(key)}: ${JSONObject.quote(value)}")
                    firstSetting = false
                }
            }
            out.write("\n  }\n}\n")
        }
    }

    private fun prettyJson(value: JSONObject, indent: String): String =
        value.toString(2).replace("\n", "\n$indent")

    /** Removes malformed historical rows and non-finite values in-place. */
    private fun cleanInvalidHistoricalData(db: SQLiteDatabase) {
        val badEntities = mutableListOf<Pair<String, String>>()
        val changedEntities = mutableListOf<Triple<String, String, String>>()
        db.query("entities", arrayOf("type", "id", "payload"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getString(0)
                val id = cursor.getString(1)
                val payload = cursor.getString(2)
                if (type !in TYPES || id.isBlank()) {
                    badEntities += type to id
                    continue
                }
                val clean = try { sanitizeEntity(type, JSONObject(payload)) } catch (_: Exception) { null }
                if (clean == null) badEntities += type to id
                else if (clean.toString() != payload) changedEntities += Triple(type, id, clean.toString())
            }
        }
        badEntities.forEach { (type, id) -> db.delete("entities", "type=? AND id=?", arrayOf(type, id)) }
        changedEntities.forEach { (type, id, payload) ->
            db.update("entities", ContentValues().apply { put("payload", payload) },
                "type=? AND id=?", arrayOf(type, id))
        }

        val badEntries = mutableListOf<Pair<String, String>>()
        db.query("entries", arrayOf("owner_id", "date", "value"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val owner = cursor.getString(0)
                val date = cursor.getString(1)
                val value = cursor.getDouble(2)
                if (!isValidEntry(owner, date, value)) badEntries += owner to date
            }
        }
        badEntries.forEach { (owner, date) ->
            db.delete("entries", "owner_id=? AND date=?", arrayOf(owner, date))
        }

        val badSettings = mutableListOf<String>()
        db.query("settings", arrayOf("key", "value"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                val value = cursor.getString(1)
                if (sanitizeSetting(key, value) == null) badSettings += key
            }
        }
        badSettings.forEach { db.delete("settings", "key=?", arrayOf(it)) }
    }

    private fun sanitizeSetting(key: String, value: String): String? {
        return when {
            key == "user_weight_kg" -> if (value.isBlank()) "" else
                InputValidation.parseWeightKg(value)?.toString()
            key == "user_height_cm" -> if (value.isBlank()) "" else
                InputValidation.parseHeightCm(value)?.toString()
            key == "user_age" -> if (value.isBlank()) "" else
                InputValidation.parseAgeYears(value)?.toString()
            key == "user_name" -> value.trim().takeIf { it.length <= 80 }
            key == "city" -> value.trim().takeIf { it.length <= 120 }
            key == "latitude" -> if (value.isBlank()) "" else value.trim().toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in -90.0..90.0 }?.toString()
            key == "longitude" -> if (value.isBlank()) "" else value.trim().toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in -180.0..180.0 }?.toString()
            key == "sleep_target_hours" -> if (value.isBlank()) null else
                InputValidation.parseNonNegativeNumber(value)?.coerceIn(4.0, 12.0)?.toString()
            key == "food_target_kcal" -> if (value.isBlank()) null else
                InputValidation.parseNonNegativeNumber(value)
                    ?.takeIf { it in 1.0..100_000.0 }?.toString()
            key == "food_target_protein" || key == "food_target_carbs" || key == "food_target_fat" ->
                if (value.isBlank()) null else InputValidation.parseNonNegativeNumber(value)
                    ?.takeIf { it in 1.0..10_000.0 }?.toString()
            key.startsWith("budget_") -> if (value.isBlank()) null else
                InputValidation.parseAmount(value)?.toString()
            else -> value
        }
    }

    /**
     * Returns a sanitized copy of an entity, or null when its central fields
     * are unusable.  Optional malformed fields are removed; central fields
     * cause the record to be skipped.
     */
    private fun sanitizeEntity(type: String, source: JSONObject): JSONObject? {
        val obj = try { JSONObject(source.toString()) } catch (_: Exception) { return null }
        val id = obj.optString("id").trim()
        if (id.isBlank() || id.length > 240) return null

        // Core records are not useful when their identifying text is missing. Reject
        // incomplete imports instead of persisting blank tasks, meals or transactions.
        for (field in REQUIRED_TEXT_FIELDS[type].orEmpty()) {
            val text = obj.optString(field).trim()
            if (text.isBlank() || text.length > 500) return null
            obj.put(field, text)
        }

        // Photo references are local filenames, never paths or URLs, and are
        // meaningful only for the two media-backed record types.
        if (type in MEDIA_TYPES) {
            for (field in PHOTO_FIELDS) {
                val name = obj.optString(field).trim()
                if (name.isNotBlank() && !BackupArchive.isSafePhotoName(name)) obj.remove(field)
            }
        } else {
            PHOTO_FIELDS.forEach(obj::remove)
        }

        when (type) {
            "transaction" -> {
                val date = obj.optString("date").trim()
                if (!InputValidation.isValidDate(date)) return null
                val amount = readFiniteNumber(obj, "amount") ?: return null
                if (amount !in InputValidation.MIN_MONEY_AMOUNT..InputValidation.MAX_MONEY_AMOUNT) return null
                obj.put("amount", amount)
                val transactionType = obj.optString("type").trim()
                if (transactionType !in setOf("income", "expense")) return null
            }
            "sleep" -> {
                val date = obj.optString("date").trim()
                if (!InputValidation.isValidDate(date)) return null
                if (!InputValidation.isValidTime(obj.optString("bed")) ||
                    !InputValidation.isValidTime(obj.optString("wake"))) return null
                val minutes = readFiniteNumber(obj, "minutes") ?: return null
                if (minutes !in 0.0..1440.0) return null
                obj.put("minutes", minutes)
                val quality = readFiniteNumber(obj, "quality") ?: return null
                if (quality !in 0.0..5.0) return null
                obj.put("quality", quality)
            }
            "food" -> {
                val date = obj.optString("date").trim()
                if (!InputValidation.isValidDate(date)) return null
                for (field in FOOD_NUMERIC_FIELDS) {
                    if (!obj.has(field) || obj.isNull(field)) continue
                    val number = readFiniteNumber(obj, field) ?: return null
                    if (number < 0.0 || number > 100_000.0) return null
                    obj.put(field, number)
                }
                val score = readFiniteNumber(obj, "foodScore")
                if (score != null) {
                    if (score !in 0.0..100.0) return null
                    obj.put("foodScore", score)
                }
                if (obj.optBoolean("nutritionKnown")) {
                    for (field in listOf("kcal", "protein", "carbs", "fat")) {
                        val total = readFiniteNumber(obj, field) ?: return null
                        if (total !in 0.0..100_000.0) return null
                        obj.put(field, total)
                    }
                }
            }
            "task" -> {
                val due = obj.optString("due").trim()
                if (due.isNotBlank() && !InputValidation.isValidDate(due)) return null
                if (due.isBlank()) obj.remove("due")
                val reminder = obj.optString("reminder").trim()
                if (reminder.isNotBlank()) {
                    if (!InputValidation.isValidTime(reminder)) return null
                }
                val history = obj.optJSONArray("completedHistory")
                if (history != null) {
                    val cleanHistory = JSONArray()
                    for (i in 0 until history.length()) {
                        val date = history.optString(i).trim()
                        if (InputValidation.isValidDate(date)) cleanHistory.put(date)
                    }
                    obj.put("completedHistory", cleanHistory)
                }
                val completedAt = obj.optString("completedAt").trim()
                if (completedAt.isNotBlank() && !InputValidation.isValidDate(completedAt)) obj.remove("completedAt")
            }
            "grade" -> {
                val value = readFiniteNumber(obj, "value") ?: return null
                if (value !in 0.0..10.0) return null
                obj.put("value", value)
                val weight = readFiniteNumber(obj, "weight")
                if (weight != null) {
                    if (weight < 0.0 || weight > 1_000.0) return null
                    obj.put("weight", weight)
                }
            }
            "gym_workout" -> {
                if (!validateWorkoutSets(obj)) return null
            }
        }

        // Dates in all other record kinds are optional, but if present they
        // must be real ISO dates in the supported year window.
        for (field in DATE_FIELDS) {
            if (!obj.has(field) || obj.isNull(field)) continue
            val value = obj.optString(field).trim()
            if (value.isBlank()) {
                obj.remove(field)
            } else if (!InputValidation.isValidDate(value)) {
                return null
            } else {
                obj.put(field, value)
            }
        }
        for (field in REQUIRED_DATE_TYPES[type].orEmpty()) {
            if (!InputValidation.isValidDate(obj.optString(field))) return null
        }

        // Generic numeric fields must never retain NaN/infinity.  A malformed
        // value invalidates the record rather than being silently changed to
        // zero, which would look like real user data.
        for (field in NUMERIC_FIELDS) {
            if (!obj.has(field) || obj.isNull(field)) continue
            val number = readFiniteNumber(obj, field) ?: return null
            if (number < 0.0 && field !in NEGATIVE_ALLOWED_FIELDS) return null
            obj.put(field, number)
        }
        return obj
    }

    private fun validateWorkoutSets(record: JSONObject): Boolean {
        if (!validateSetArray(record.optJSONArray("sets"))) return false
        val exercises = record.optJSONArray("exercises") ?: return true
        if (exercises.length() > 10_000) return false
        for (index in 0 until exercises.length()) {
            val exercise = exercises.optJSONObject(index) ?: return false
            if (!validateSetArray(exercise.optJSONArray("sets"))) return false
        }
        return true
    }

    private fun validateSetArray(sets: JSONArray?): Boolean {
        if (sets == null) return true
        if (sets.length() > 100_000) return false
        for (index in 0 until sets.length()) {
            val set = sets.optJSONObject(index) ?: return false
            val weight = readFiniteNumber(set, "weight") ?: return false
            val reps = readFiniteNumber(set, "reps") ?: return false
            if (weight !in 0.0..100_000.0 || reps !in 0.0..100_000.0) return false
        }
        return true
    }

    private fun readFiniteNumber(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val raw = obj.opt(key) ?: return null
        val value = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }
        return value?.takeIf { it.isFinite() }
    }

    private fun isValidEntry(id: String, date: String, value: Double): Boolean =
        id.isNotBlank() && InputValidation.isValidDate(date) && value.isFinite()

    private fun writeSetting(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun writeEntry(db: SQLiteDatabase, id: String, date: String,
                           value: Double, status: String, notes: String) {
        if (!isValidEntry(id, date, value)) return
        db.insertWithOnConflict("entries", null, ContentValues().apply {
            put("owner_id", id); put("date", date); put("value", value)
            put("status", status); put("notes", notes)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun extractLegacyPhoto(item: JSONObject, media: MutableMap<String, ByteArray>) {
        val photo = item.optString("photo")
        if (!photo.startsWith("data:image/") || !photo.contains("base64,")) return
        val bytes = decodeBase64(photo.substringAfter("base64,")) ?: return
        if (bytes.size > BackupArchive.MAX_PHOTO_BYTES) {
            item.remove("photo")
            return
        }
        val name = "import_${freshId()}.png"
        media[name] = bytes
        item.put("photo", name)
    }

    private fun decodeBase64(value: String): ByteArray? = try {
        Base64.getMimeDecoder().decode(value)
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Process-wide lock: multiple LocalStore instances share the same database and media directory. */
        private val MEDIA_LOCK = Any()

        val TYPES = listOf("tracker", "task", "challenge", "garment", "outfit", "wear",
            "subject", "grade", "exam", "transaction", "packing", "wishlist",
            "journal", "plan", "focus_session", "food",
            "sleep", "gym_template", "gym_workout")
        fun freshId(): String = UUID.randomUUID().toString()
        fun obj(vararg parts: Pair<String, Any?>): JSONObject = JSONObject().apply {
            for ((k, v) in parts) put(k, v)
            if (optString("id").isBlank()) put("id", freshId())
        }

        private val PHOTO_FIELDS = setOf("photo", "sourcePhoto")
        private val MEDIA_TYPES = setOf("garment", "food")
        private val DATE_FIELDS = setOf("date", "due", "created", "deadline", "startDate",
            "endDate", "plannedDate", "added", "completedAt")
        private val NUMERIC_FIELDS = setOf("value", "target", "step", "amount", "price",
            "weight", "height", "minutes", "quality", "goal", "duration", "reps", "rest",
            "kcal", "protein", "carbs", "fat", "foodScore", "portionGrams", "per100kcal",
            "per100protein", "per100carbs", "per100fat", "limit")
        private val FOOD_NUMERIC_FIELDS = setOf("kcal", "protein", "carbs", "fat", "portionGrams",
            "per100kcal", "per100protein", "per100carbs", "per100fat")
        private val NEGATIVE_ALLOWED_FIELDS = setOf<String>()
        private val REQUIRED_TEXT_FIELDS = mapOf(
            "tracker" to listOf("name"),
            "task" to listOf("name"),
            "garment" to listOf("name"),
            "subject" to listOf("name"),
            "exam" to listOf("title"),
            "transaction" to listOf("title", "type"),
            "food" to listOf("name"),
            "gym_workout" to listOf("name"),
        )
        private val REQUIRED_DATE_TYPES = mapOf(
            "transaction" to listOf("date"),
            "sleep" to listOf("date"),
            "food" to listOf("date"),
            "wear" to listOf("date"),
            "journal" to listOf("date"),
            "exam" to listOf("date"),
            "gym_workout" to listOf("date")
        )
    }
}
