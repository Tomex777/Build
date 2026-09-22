package com.example.whatsapp.data.night

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NightLibraryFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
)

class NightLibraryFolderStore private constructor(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun folders(): List<NightLibraryFolder> {
        val raw = prefs.getString(KEY_FOLDERS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        NightLibraryFolder(
                            id = id,
                            name = name,
                            createdAt = item.optLong("createdAt", 0L),
                        )
                    )
                }
            }.sortedWith(
                compareBy<NightLibraryFolder> { it.name.lowercase() }
                    .thenBy { it.createdAt }
            )
        }.getOrElse { emptyList() }
    }

    @Synchronized
    fun createFolder(name: String): NightLibraryFolder {
        val cleanName = name
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(48)
        require(cleanName.isNotBlank()) { "Folder name is required." }

        folders().firstOrNull {
            it.name.equals(cleanName, ignoreCase = true)
        }?.let { return it }

        val folder = NightLibraryFolder(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            createdAt = System.currentTimeMillis(),
        )
        writeFolders(folders() + folder)
        return folder
    }

    @Synchronized
    fun deleteFolder(folderId: String) {
        writeFolders(folders().filterNot { it.id == folderId })

        val assignments = assignments().toMutableMap()
        assignments.entries.removeAll { it.value == folderId }
        writeAssignments(assignments)
    }

    @Synchronized
    fun folderForItem(itemId: String): String? =
        assignments()[itemId]

    @Synchronized
    fun assign(
        itemId: String,
        folderId: String?,
    ) {
        require(itemId.isNotBlank()) { "itemId is required." }
        if (folderId != null) {
            require(folders().any { it.id == folderId }) {
                "Unknown Library folder."
            }
        }

        val assignments = assignments().toMutableMap()
        if (folderId == null) {
            assignments.remove(itemId)
        } else {
            assignments[itemId] = folderId
        }
        writeAssignments(assignments)
    }

    @Synchronized
    fun prune(validItemIds: Set<String>) {
        val cleaned = assignments().filterKeys { it in validItemIds }
        writeAssignments(cleaned)
    }

    private fun assignments(): Map<String, String> {
        val raw = prefs.getString(KEY_ASSIGNMENTS, "{}").orEmpty()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val itemId = keys.next()
                    val folderId = json.optString(itemId).trim()
                    if (folderId.isNotBlank()) put(itemId, folderId)
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun writeFolders(folders: List<NightLibraryFolder>) {
        val array = JSONArray()
        folders.distinctBy { it.id }.forEach { folder ->
            array.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("createdAt", folder.createdAt)
            )
        }
        prefs.edit().putString(KEY_FOLDERS, array.toString()).apply()
    }

    private fun writeAssignments(assignments: Map<String, String>) {
        val json = JSONObject()
        assignments.forEach { (itemId, folderId) ->
            json.put(itemId, folderId)
        }
        prefs.edit().putString(KEY_ASSIGNMENTS, json.toString()).apply()
    }

    companion object {
        private const val PREFS = "night_library_folders"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_ASSIGNMENTS = "assignments"

        @Volatile private var instance: NightLibraryFolderStore? = null

        fun get(context: Context): NightLibraryFolderStore =
            instance ?: synchronized(this) {
                instance ?: NightLibraryFolderStore(
                    context.applicationContext
                ).also { instance = it }
            }
    }
}
