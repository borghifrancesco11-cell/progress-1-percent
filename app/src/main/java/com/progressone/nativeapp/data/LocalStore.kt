package com.progressone.nativeapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Native, versioned SQLite data store. No WebView, no JavaScript bridge, no localStorage.
 * Media is stored in internal app files rather than in the database or in a base64 UI state.
 * Core entities are independent rows; history is separately indexed by (item, calendar date).
 */
class LocalStore(private val appContext: Context) : SQLiteOpenHelper(appContext, "progress_one.db", null, 2) {
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
    }

    fun all(type: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        readableDatabase.query("entities", arrayOf("payload"), "type=?", arrayOf(type), null, null,
            "updated_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                try { result += JSONObject(cursor.getString(0)) } catch (_: Exception) { /* corrupt row isolated */ }
            }
        }
        return result
    }

    fun find(type: String, id: String): JSONObject? = all(type).firstOrNull { it.optString("id") == id }

    fun put(type: String, record: JSONObject) {
        require(type in TYPES) { "Unsupported record type" }
        val id = record.optString("id").ifBlank { freshId() }
        record.put("id", id)
        writeEntity(writableDatabase, type, record)
        revision++
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

    fun saveEntry(id: String, date: String, value: Double, status: String = "done", note: String = "") {
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val row = ContentValues().apply {
            put("owner_id", id); put("date", date); put("value", value)
            put("status", status); put("notes", note)
        }
        writableDatabase.insertWithOnConflict("entries", null, row, SQLiteDatabase.CONFLICT_REPLACE)
        revision++
    }

    fun deleteEntry(id: String, date: String) {
        writableDatabase.delete("entries", "owner_id=? AND date=?", arrayOf(id, date))
        revision++
    }

    data class Entry(val ownerId: String, val date: String, val value: Double, val status: String, val notes: String)

    fun entries(id: String? = null): List<Entry> {
        val result = mutableListOf<Entry>()
        readableDatabase.query("entries", arrayOf("owner_id", "date", "value", "status", "notes"),
            if (id == null) null else "owner_id=?", if (id == null) null else arrayOf(id),
            null, null, "date DESC").use { cursor ->
            while (cursor.moveToNext()) result += Entry(cursor.getString(0), cursor.getString(1),
                cursor.getDouble(2), cursor.getString(3), cursor.getString(4))
        }
        return result
    }

    fun setting(key: String, default: String = ""): String {
        readableDatabase.query("settings", arrayOf("value"), "key=?", arrayOf(key),
            null, null, null).use { if (it.moveToFirst()) return it.getString(0) }
        return default
    }

    fun setSetting(key: String, value: String) {
        val c = ContentValues().apply { put("key", key); put("value", value) }
        writableDatabase.insertWithOnConflict("settings", null, c, SQLiteDatabase.CONFLICT_REPLACE)
        revision++
    }

    fun photoFile(filename: String): File? {
        if (filename.isBlank() || filename != File(filename).name ||
            !(filename.endsWith(".png") || filename.endsWith(".jpg"))) return null
        return File(photoDirectory, filename)
    }

    /** Remove a media file only if no garment or food record still uses it. */
    fun cleanupUnreferencedPhoto(filename: String?) {
        if (filename.isNullOrBlank()) return
        if ((all("garment") + all("food")).none {
                it.optString("photo") == filename || it.optString("sourcePhoto") == filename
            }) {
            photoFile(filename)?.delete()
        }
    }

    /** Full round-trip backup, including images; exported only through Android's Storage Access Framework. */
    fun exportJson(): String {
        val entities = JSONObject()
        for (type in TYPES) {
            val array = JSONArray()
            all(type).forEach { array.put(it) }
            entities.put(type, array)
        }
        val logs = JSONArray()
        entries().forEach { e -> logs.put(JSONObject().apply {
            put("ownerId", e.ownerId); put("date", e.date); put("value", e.value)
            put("status", e.status); put("notes", e.notes)
        }) }
        val settings = JSONObject()
        readableDatabase.query("settings", arrayOf("key", "value"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) settings.put(c.getString(0), c.getString(1))
        }
        val media = JSONObject()
        for (f in (photoDirectory.listFiles() ?: emptyArray())) {
            if (f.isFile && (f.extension == "png" || f.extension == "jpg")) {
                media.put(f.name, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
        }
        return JSONObject().apply {
            put("format", "progress-one-native-v1")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("entities", entities)
            put("entries", logs)
            put("settings", settings)
            put("photos", media)
        }.toString(2)
    }

    /**
     * Supports full native backup, Progress 1% WebView {progressiOne,progressiLocal}
     * and the standalone Progress Local {items,tasks} schema.
     * All imported database writes are atomic; malformed JSON is rejected before the wipe.
     */
    fun importJson(source: String): String {
        require(source.length <= 60_000_000) { "Backup is too large (max 60 MB)." }
        val root = JSONObject(source)
        val native = root.optString("format") == "progress-one-native-v1"
        val legacyMain = root.optJSONObject("progressiOne")
        val legacyTracker = root.optJSONObject("progressiLocal") ?: if (root.has("items") && root.has("tasks")) root else null
        if (!native && legacyMain == null && legacyTracker == null) {
            throw IllegalArgumentException("Unsupported backup format")
        }
        if (native) require(root.optJSONObject("entities") != null) { "Missing records" }
        if (legacyMain != null) require(legacyMain.optJSONArray("wardrobe") != null) { "Invalid Progress 1% backup" }
        if (legacyTracker != null) require(legacyTracker.optJSONArray("items") != null) { "Invalid tracker backup" }

        val pendingImages = mutableMapOf<String, ByteArray>()
        if (native) {
            val photos = root.optJSONObject("photos") ?: JSONObject()
            for (key in photos.keys()) {
                val file = photoFile(key) ?: continue
                val bytes = Base64.decode(photos.optString(key), Base64.DEFAULT)
                if (bytes.size > 9_000_000) throw IllegalArgumentException("Photo too large: ${file.name}")
                pendingImages[key] = bytes
            }
        }
        if (legacyMain != null) {
            val wardrobe = legacyMain.optJSONArray("wardrobe") ?: JSONArray()
            for (i in 0 until wardrobe.length()) {
                val item = wardrobe.optJSONObject(i) ?: continue
                val photo = item.optString("photo")
                if (photo.startsWith("data:image/") && photo.contains("base64,")) {
                    val bytes = Base64.decode(photo.substringAfter("base64,"), Base64.DEFAULT)
                    if (bytes.size <= 9_000_000) {
                        val name = "import_${freshId()}.png"
                        pendingImages[name] = bytes
                        item.put("photo", name)
                    } else item.remove("photo")
                }
            }
        }
        // Preserve prior media until after the successful SQLite transaction.
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entities", null, null)
            db.delete("entries", null, null)
            db.delete("settings", null, null)
            if (native) {
                val groups = root.getJSONObject("entities")
                for (type in TYPES) {
                    val rows = groups.optJSONArray(type) ?: JSONArray()
                    for (i in 0 until rows.length()) {
                        val obj = rows.optJSONObject(i) ?: continue
                        if (obj.optString("id").isBlank()) obj.put("id", freshId())
                        writeEntity(db, type, obj)
                    }
                }
                val logs = root.optJSONArray("entries") ?: JSONArray()
                for (i in 0 until logs.length()) {
                    val e = logs.optJSONObject(i) ?: continue
                    writeEntry(db, e.optString("ownerId"), e.optString("date"),
                        e.optDouble("value"), e.optString("status"), e.optString("notes"))
                }
                val conf = root.optJSONObject("settings") ?: JSONObject()
                for (key in conf.keys()) writeSetting(db, key, conf.optString(key))
            } else {
                if (legacyMain != null) {
                    val maps = mapOf("wardrobe" to "garment", "outfits" to "outfit", "wears" to "wear",
                        "packing" to "packing", "wishlist" to "wishlist", "subjects" to "subject",
                        "grades" to "grade", "exams" to "exam", "transactions" to "transaction")
                    for ((old, kind) in maps) {
                        val rows = legacyMain.optJSONArray(old) ?: continue
                        for (i in 0 until rows.length()) {
                            val item = rows.optJSONObject(i) ?: continue
                            if (item.optString("id").isBlank()) item.put("id", freshId())
                            writeEntity(db, kind, item)
                        }
                    }
                    val settings = legacyMain.optJSONObject("settings") ?: JSONObject()
                    for (key in settings.keys()) {
                        val v = settings.opt(key)
                        if (v != null && v != JSONObject.NULL) writeSetting(db, key, v.toString())
                    }
                    val budgets = legacyMain.optJSONObject("budgets") ?: JSONObject()
                    for (month in budgets.keys()) writeSetting(db, "budget_$month", budgets.optString(month))
                }
                if (legacyTracker != null) {
                    val items = legacyTracker.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val t = items.optJSONObject(i) ?: continue
                        if (t.optString("id").isBlank()) t.put("id", freshId())
                        val history = t.optJSONObject("entries") ?: JSONObject()
                        for (date in history.keys()) {
                            val e = history.optJSONObject(date) ?: continue
                            writeEntry(db, t.getString("id"), date, e.optDouble("value"),
                                e.optString("status"), e.optString("notes"))
                        }
                        t.remove("entries")
                        writeEntity(db, "tracker", t)
                    }
                    val tasks = legacyTracker.optJSONArray("tasks") ?: JSONArray()
                    for (i in 0 until tasks.length()) {
                        val task = tasks.optJSONObject(i) ?: continue
                        if (task.optString("id").isBlank()) task.put("id", freshId())
                        writeEntity(db, "task", task)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        for ((filename, bytes) in pendingImages) photoFile(filename)?.writeBytes(bytes)
        revision++
        return if (native) "Native backup restored" else "Previous app backup imported"
    }

    private fun writeSetting(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun writeEntry(db: SQLiteDatabase, id: String, date: String,
                           value: Double, status: String, notes: String) {
        if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || id.isBlank()) return
        db.insertWithOnConflict("entries", null, ContentValues().apply {
            put("owner_id", id); put("date", date); put("value", value)
            put("status", status); put("notes", notes)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        val TYPES = listOf("tracker", "task", "challenge", "garment", "outfit", "wear",
            "subject", "grade", "exam", "transaction", "packing", "wishlist",
            "journal", "plan", "focus_session", "food",
            "sleep", "gym_template", "gym_workout")
        fun freshId(): String = UUID.randomUUID().toString()
        fun obj(vararg parts: Pair<String, Any?>): JSONObject = JSONObject().apply {
            for ((k, v) in parts) put(k, v)
            if (optString("id").isBlank()) put("id", freshId())
        }
    }
}
