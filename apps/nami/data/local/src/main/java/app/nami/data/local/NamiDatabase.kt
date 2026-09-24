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
                state TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(source_id, source_episode_id)
            )""".trimIndent(),
        )
    }

    companion object {
        private const val DATABASE_NAME = "nami.db"
        private const val VERSION = 2
    }
}
