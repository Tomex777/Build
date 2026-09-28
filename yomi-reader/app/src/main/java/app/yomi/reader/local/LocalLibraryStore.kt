package app.yomi.reader.local

import android.content.Context
import android.net.Uri
import app.yomi.reader.core.ReaderBookId

class LocalLibraryStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val identities = LocalBookIdentityStore(appContext)

    fun upsert(
        uri: Uri,
        title: String,
        locationType: LibraryLocationType,
        pageCount: Int? = null,
        coverUri: String? = null,
    ): LibraryBook {
        val id = identities.getOrCreate(uri)
        val prefix = prefix(id)
        val now = System.currentTimeMillis()
        val dateAdded = if (prefs.contains(prefix + KEY_ADDED)) {
            prefs.getLong(prefix + KEY_ADDED, now)
        } else {
            now
        }
        val ids = (prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()).toMutableSet()
        ids += id.value

        val edit = prefs.edit()
            .putStringSet(KEY_IDS, ids)
            .putString(prefix + KEY_TITLE, title)
            .putString(prefix + KEY_URI, uri.toString())
            .putString(prefix + KEY_TYPE, locationType.name)
            .putLong(prefix + KEY_ADDED, dateAdded)
            .putString(prefix + KEY_AVAILABILITY, LibraryAvailability.AVAILABLE.name)

        if (pageCount != null) edit.putInt(prefix + KEY_PAGE_COUNT, pageCount)
        if (coverUri != null) edit.putString(prefix + KEY_COVER, coverUri)

        check(edit.commit()) { "Unable to persist Yomi library" }
        return get(id) ?: error("Unable to read persisted Yomi book")
    }

    fun list(): List<LibraryBook> {
        val ids = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        return ids.mapNotNull { get(ReaderBookId(it)) }
            .sortedWith(
                compareByDescending<LibraryBook> { it.lastOpenedEpochMillis ?: Long.MIN_VALUE }
                    .thenByDescending { it.dateAddedEpochMillis },
            )
    }

    fun get(id: ReaderBookId): LibraryBook? {
        val prefix = prefix(id)
        val uri = prefs.getString(prefix + KEY_URI, null) ?: return null
        val title = prefs.getString(prefix + KEY_TITLE, null) ?: return null
        val type = prefs.getString(prefix + KEY_TYPE, null)
            ?.let { runCatching { LibraryLocationType.valueOf(it) }.getOrNull() }
            ?: return null
        val availability = prefs.getString(prefix + KEY_AVAILABILITY, LibraryAvailability.AVAILABLE.name)
            ?.let { runCatching { LibraryAvailability.valueOf(it) }.getOrNull() }
            ?: LibraryAvailability.AVAILABLE
        val progressBits = prefs.getLong(prefix + KEY_PROGRESS, 0.0.toBits())
        val progress = Double.fromBits(progressBits).coerceIn(0.0, 1.0)

        return LibraryBook(
            id = id,
            title = title,
            locationUri = uri,
            locationType = type,
            fingerprint = null,
            dateAddedEpochMillis = prefs.getLong(prefix + KEY_ADDED, 0L),
            lastOpenedEpochMillis = prefs.getLong(prefix + KEY_LAST_OPENED, -1L).takeIf { it >= 0L },
            availability = availability,
            pageCount = prefs.getInt(prefix + KEY_PAGE_COUNT, -1).takeIf { it >= 0 },
            progress = progress,
            coverUri = prefs.getString(prefix + KEY_COVER, null),
        )
    }

    fun markOpened(id: ReaderBookId, atEpochMillis: Long = System.currentTimeMillis()) {
        if (get(id) == null) return
        check(
            prefs.edit()
                .putLong(prefix(id) + KEY_LAST_OPENED, atEpochMillis)
                .putString(prefix(id) + KEY_AVAILABILITY, LibraryAvailability.AVAILABLE.name)
                .commit(),
        ) { "Unable to persist Yomi recent activity" }
    }

    fun markProgress(id: ReaderBookId, progress: Double) {
        if (get(id) == null) return
        check(
            prefs.edit()
                .putLong(prefix(id) + KEY_PROGRESS, progress.coerceIn(0.0, 1.0).toBits())
                .putLong(prefix(id) + KEY_LAST_OPENED, System.currentTimeMillis())
                .commit(),
        ) { "Unable to persist Yomi reading progress" }
    }

    fun setAvailability(id: ReaderBookId, availability: LibraryAvailability) {
        if (get(id) == null) return
        check(
            prefs.edit()
                .putString(prefix(id) + KEY_AVAILABILITY, availability.name)
                .commit(),
        ) { "Unable to persist Yomi availability" }
    }

    fun remove(id: ReaderBookId) {
        val ids = (prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()).toMutableSet()
        ids.remove(id.value)
        val prefix = prefix(id)
        val edit = prefs.edit().putStringSet(KEY_IDS, ids)
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(edit::remove)
        check(edit.commit()) { "Unable to remove Yomi book" }
    }

    private fun prefix(id: ReaderBookId): String = "book." + id.value + "."

    private companion object {
        const val PREFS = "yomi_local_library_v2"
        const val KEY_IDS = "book_ids"
        const val KEY_TITLE = "title"
        const val KEY_URI = "uri"
        const val KEY_TYPE = "type"
        const val KEY_ADDED = "added"
        const val KEY_LAST_OPENED = "last_opened"
        const val KEY_AVAILABILITY = "availability"
        const val KEY_PAGE_COUNT = "page_count"
        const val KEY_PROGRESS = "progress"
        const val KEY_COVER = "cover"
    }
}
