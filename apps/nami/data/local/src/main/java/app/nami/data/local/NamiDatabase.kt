package app.nami.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeRef

data class StoredLibraryEntry(
    val id: Long,
    val ref: AnimeRef,
    val title: String,
    val coverUrl: String?,
    val addedAtEpochMillis: Long,
)

data class StoredDownload(
    val id: Long,
    val sourceId: String,
    val extensionName: String,
    val sourceAnimeId: String,
    val sourceEpisodeId: String,
    val relativePath: String,
    val displayName: String?,
    val contentUri: String?,
    val mimeType: String?,
    val state: String,
    val progress: Int,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** Nami-owned local store. */
class NamiDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createBaseTables(db)
        createDownloadTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createDownloadTable(db)
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db, "downloads", "display_name", "TEXT")
            addColumnIfMissing(db, "downloads", "content_uri", "TEXT")
            addColumnIfMissing(db, "downloads", "mime_type", "TEXT")
            addColumnIfMissing(db, "downloads", "progress", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "downloads", "error_message", "TEXT")
            addColumnIfMissing(db, "downloads", "updated_at", "INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun isInLibrary(ref: AnimeRef): Boolean {
        readableDatabase.query(
            "library_entries",
            arrayOf("id"),
            "source_id = ? AND source_anime_id = ?",
            arrayOf(ref.sourceId, ref.sourceAnimeId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun addToLibrary(details: AnimeDetails) {
        val values = ContentValues().apply {
            put("source_id", details.ref.sourceId)
            put("source_anime_id", details.ref.sourceAnimeId)
            put("title", details.title)
            put("cover_url", details.coverUrl)
            put("added_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "library_entries",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun removeFromLibrary(ref: AnimeRef) {
        writableDatabase.delete(
            "library_entries",
            "source_id = ? AND source_anime_id = ?",
            arrayOf(ref.sourceId, ref.sourceAnimeId),
        )
    }

    fun getLibraryEntries(): List<StoredLibraryEntry> {
        readableDatabase.query(
            "library_entries",
            arrayOf("id", "source_id", "source_anime_id", "title", "cover_url", "added_at"),
            null,
            null,
            null,
            null,
            "title COLLATE NOCASE ASC",
        ).use { cursor ->
            val items = ArrayList<StoredLibraryEntry>(cursor.count)
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
            val animeIdIndex = cursor.getColumnIndexOrThrow("source_anime_id")
            val titleIndex = cursor.getColumnIndexOrThrow("title")
            val coverIndex = cursor.getColumnIndexOrThrow("cover_url")
            val addedIndex = cursor.getColumnIndexOrThrow("added_at")
            while (cursor.moveToNext()) {
                items += StoredLibraryEntry(
                    id = cursor.getLong(idIndex),
                    ref = AnimeRef(
                        sourceId = cursor.getString(sourceIdIndex),
                        sourceAnimeId = cursor.getString(animeIdIndex),
                    ),
                    title = cursor.getString(titleIndex),
                    coverUrl = cursor.getString(coverIndex),
                    addedAtEpochMillis = cursor.getLong(addedIndex),
                )
            }
            return items
        }
    }

    fun upsertDownload(
        sourceId: String,
        extensionName: String,
        sourceAnimeId: String,
        sourceEpisodeId: String,
        relativePath: String,
        state: String,
        progress: Int,
        displayName: String? = null,
        contentUri: String? = null,
        mimeType: String? = null,
        errorMessage: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val existing = getDownload(sourceId, sourceEpisodeId)
        val values = ContentValues().apply {
            put("source_id", sourceId)
            put("extension_name", extensionName)
            put("source_anime_id", sourceAnimeId)
            put("source_episode_id", sourceEpisodeId)
            put("relative_path", relativePath)
            put("state", state)
            put("progress", progress.coerceIn(0, 100))
            put("display_name", displayName)
            put("content_uri", contentUri)
            put("mime_type", mimeType)
            put("error_message", errorMessage)
            put("updated_at", now)
            put("created_at", existing?.createdAtEpochMillis ?: now)
        }
        writableDatabase.insertWithOnConflict(
            "downloads",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getDownload(sourceId: String, sourceEpisodeId: String): StoredDownload? {
        readableDatabase.query(
            "downloads",
            DOWNLOAD_COLUMNS,
            "source_id = ? AND source_episode_id = ?",
            arrayOf(sourceId, sourceEpisodeId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) readDownload(cursor) else null
        }
    }

    fun getDownloads(): List<StoredDownload> {
        readableDatabase.query(
            "downloads",
            DOWNLOAD_COLUMNS,
            null,
            null,
            null,
            null,
            "updated_at DESC",
        ).use { cursor ->
            val items = ArrayList<StoredDownload>(cursor.count)
            while (cursor.moveToNext()) {
                items += readDownload(cursor)
            }
            return items
        }
    }

    fun deleteDownloadRecord(sourceId: String, sourceEpisodeId: String) {
        writableDatabase.delete(
            "downloads",
            "source_id = ? AND source_episode_id = ?",
            arrayOf(sourceId, sourceEpisodeId),
        )
    }

    private fun readDownload(cursor: android.database.Cursor): StoredDownload {
        return StoredDownload(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            sourceId = cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
            extensionName = cursor.getString(cursor.getColumnIndexOrThrow("extension_name")),
            sourceAnimeId = cursor.getString(cursor.getColumnIndexOrThrow("source_anime_id")),
            sourceEpisodeId = cursor.getString(cursor.getColumnIndexOrThrow("source_episode_id")),
            relativePath = cursor.getString(cursor.getColumnIndexOrThrow("relative_path")),
            displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
            contentUri = cursor.getString(cursor.getColumnIndexOrThrow("content_uri")),
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
            state = cursor.getString(cursor.getColumnIndexOrThrow("state")),
            progress = cursor.getInt(cursor.getColumnIndexOrThrow("progress")),
            errorMessage = cursor.getString(cursor.getColumnIndexOrThrow("error_message")),
            createdAtEpochMillis = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAtEpochMillis = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
        )
    }

    private fun createBaseTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE library_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id TEXT NOT NULL,
                source_anime_id TEXT NOT NULL,
                title TEXT NOT NULL,
                cover_url TEXT,
                added_at INTEGER NOT NULL,
                UNIQUE(source_id, source_anime_id)
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                sort_order INTEGER NOT NULL DEFAULT 0
            )""".trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE library_category_membership (
                library_entry_id INTEGER NOT NULL REFERENCES library_entries(id) ON DELETE CASCADE,
                category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
                PRIMARY KEY(library_entry_id, category_id)
            )""".trimIndent(),
        )
        db.execSQL(
            "CREATE TABLE watch_progress (" +
                "source_id TEXT NOT NULL, source_episode_id TEXT NOT NULL, " +
                "position_ms INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(source_id, source_episode_id))",
        )
        db.execSQL(
            "CREATE TABLE history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, source_id TEXT NOT NULL, " +
                "source_episode_id TEXT NOT NULL, played_at INTEGER NOT NULL)",
        )
    }

    private fun createDownloadTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id TEXT NOT NULL,
                extension_name TEXT NOT NULL,
                source_anime_id TEXT NOT NULL,
                source_episode_id TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                display_name TEXT,
                content_uri TEXT,
                mime_type TEXT,
                state TEXT NOT NULL,
                progress INTEGER NOT NULL DEFAULT 0,
                error_message TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL DEFAULT 0,
                UNIQUE(source_id, source_episode_id)
            )""".trimIndent(),
        )
    }

    private fun addColumnIfMissing(
        db: SQLiteDatabase,
        table: String,
        column: String,
        declaration: String,
    ) {
        val hasColumn = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                    found = true
                    break
                }
            }
            found
        }
        if (!hasColumn) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $declaration")
        }
    }

    companion object {
        private const val DATABASE_NAME = "nami.db"
        private const val VERSION = 3

        private val DOWNLOAD_COLUMNS = arrayOf(
            "id",
            "source_id",
            "extension_name",
            "source_anime_id",
            "source_episode_id",
            "relative_path",
            "display_name",
            "content_uri",
            "mime_type",
            "state",
            "progress",
            "error_message",
            "created_at",
            "updated_at",
        )
    }
}
