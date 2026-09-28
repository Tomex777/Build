package app.yomi.reader.local

import android.content.Context
import android.net.Uri
import app.yomi.reader.core.ReaderBookId
import java.util.UUID

class LocalBookIdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("yomi_book_identity", Context.MODE_PRIVATE)

    fun getOrCreate(uri: Uri): ReaderBookId {
        val uriKey = uri.toString()
        prefs.getString("uri." + uriKey, null)?.let { return ReaderBookId(it) }

        val id = UUID.randomUUID().toString()
        check(
            prefs.edit()
                .putString("uri." + uriKey, id)
                .putString("book." + id + ".uri", uriKey)
                .commit(),
        ) { "Unable to persist local book identity" }
        return ReaderBookId(id)
    }

    fun relink(bookId: ReaderBookId, newUri: Uri) {
        val oldUri = prefs.getString("book." + bookId.value + ".uri", null)
        val edit = prefs.edit()
        if (oldUri != null) edit.remove("uri." + oldUri)
        check(
            edit
                .putString("uri." + newUri.toString(), bookId.value)
                .putString("book." + bookId.value + ".uri", newUri.toString())
                .commit(),
        ) { "Unable to relink local book identity" }
    }

    fun uriFor(bookId: ReaderBookId): Uri? {
        return prefs.getString("book." + bookId.value + ".uri", null)?.let(Uri::parse)
    }
}
