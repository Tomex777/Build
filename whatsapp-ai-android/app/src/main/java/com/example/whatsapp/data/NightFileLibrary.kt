package com.example.whatsapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class NightLibraryFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val createdAt: Long,
)

object NightFileLibrary {
    private const val PREFS = "night_file_library"
    private const val KEY_ITEMS = "items"

    private fun root(context: Context): File =
        File(context.filesDir, "night_library").apply { mkdirs() }

    fun list(context: Context): List<NightLibraryFile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]")
            .orEmpty()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val file = NightLibraryFile(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        mimeType = item.optString("mimeType", "application/octet-stream"),
                        sizeBytes = item.optLong("sizeBytes", 0L),
                        localPath = item.getString("localPath"),
                        createdAt = item.optLong("createdAt", 0L),
                    )
                    if (File(file.localPath).exists()) add(file)
                }
            }.sortedByDescending { it.createdAt }
        }.getOrElse { emptyList() }
    }

    fun find(context: Context, id: String): NightLibraryFile? =
        list(context).firstOrNull { it.id == id }

    fun importUri(context: Context, source: Uri): NightLibraryFile? {
        val resolver = context.contentResolver
        var displayName = "attachment"
        var declaredSize = 0L

        resolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
            }
        }

        val id = UUID.randomUUID().toString()
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(root(context), "${id}_${safeName}")
        val mime = resolver.getType(source) ?: "application/octet-stream"

        return runCatching {
            resolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return null

            val item = NightLibraryFile(
                id = id,
                name = displayName,
                mimeType = mime,
                sizeBytes = if (declaredSize > 0) declaredSize else target.length(),
                localPath = target.absolutePath,
                createdAt = System.currentTimeMillis(),
            )
            save(context, list(context) + item)
            item
        }.getOrNull()
    }

    fun registerLocalFile(
        context: Context,
        source: File,
        name: String = source.name,
        mimeType: String = "application/octet-stream",
    ): NightLibraryFile? {
        if (!source.exists()) return null
        val id = UUID.randomUUID().toString()
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(root(context), "${id}_${safeName}")

        return runCatching {
            source.copyTo(target, overwrite = true)
            val item = NightLibraryFile(
                id = id,
                name = name,
                mimeType = mimeType,
                sizeBytes = target.length(),
                localPath = target.absolutePath,
                createdAt = System.currentTimeMillis(),
            )
            save(context, list(context) + item)
            item
        }.getOrNull()
    }

    fun remove(context: Context, id: String) {
        val current = list(context)
        current.firstOrNull { it.id == id }?.let { File(it.localPath).delete() }
        save(context, current.filterNot { it.id == id })
    }

    private fun save(context: Context, items: List<NightLibraryFile>) {
        val array = JSONArray()
        items.distinctBy { it.id }.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("mimeType", item.mimeType)
                    .put("sizeBytes", item.sizeBytes)
                    .put("localPath", item.localPath)
                    .put("createdAt", item.createdAt)
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}
