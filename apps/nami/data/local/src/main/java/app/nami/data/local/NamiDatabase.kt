package app.nami.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Nami-owned local store. Schema starts empty and contains no imported or demo entries. */
class NamiDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
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
        db.execSQL("CREATE TABLE watch_progress (source_id TEXT NOT NULL, source_episode_id TEXT NOT NULL, position_ms INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(source_id, source_episode_id))")
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, source_id TEXT NOT NULL, source_episode_id TEXT NOT NULL, played_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema changes will be added as explicit, forward-only migrations.
    }

    companion object {
        private const val DATABASE_NAME = "nami.db"
        private const val VERSION = 1
    }
}
