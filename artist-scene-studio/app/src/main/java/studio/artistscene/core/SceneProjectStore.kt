package studio.artistscene.core

import android.content.Context
import android.system.Os
import java.io.File

data class SceneProjectSummary(
    val id: String,
    val name: String,
    val actorCount: Int,
    val modifiedAtEpochMs: Long,
)

/**
 * Versioned JSON persistence. All store instances in this process share one lock so that a
 * duplicate/delete cannot interleave with an autosave of the same file.
 *
 * Every write uses its own same-directory temporary file and a POSIX atomic rename. A failed
 * encode/write never truncates the last committed scene. This is not a substitute for full device
 * backups or for running large scene saves off the UI thread.
 */
class SceneProjectStore(context: Context) {
    private val directory = File(context.filesDir, "projects").apply { mkdirs() }

    fun save(project: SceneProject) = synchronized(IO_LOCK) {
        // Validate and encode before ever touching the current persisted scene.
        val serialized = SceneProjectCodec.encode(project)
        val target = projectFile(project.id)
        // The old fixed "<id>.pending" name let overlapping writers truncate each other.
        val pending = File.createTempFile("${project.id}.", ".pending", directory)
        try {
            pending.writeText(serialized)
            Os.rename(pending.absolutePath, target.absolutePath)
        } finally {
            pending.delete()
        }
    }

    fun load(projectId: String): SceneProject = synchronized(IO_LOCK) {
        val project = SceneProjectCodec.decode(projectFile(projectId).readText())
        // Never quietly open or save a different scene because its JSON was copied/renamed.
        require(project.id == projectId) {
            "Project ID mismatch: requested $projectId but file contains ${project.id}"
        }
        project
    }

    fun exists(projectId: String): Boolean = synchronized(IO_LOCK) {
        projectFile(projectId).isFile
    }

    fun list(): List<SceneProjectSummary> = synchronized(IO_LOCK) {
        directory.listFiles { file -> file.isFile && file.name.endsWith(".scene.json") }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val project = SceneProjectCodec.decode(file.readText())
                    // Leave misplaced or corrupt files untouched for recovery instead of
                    // presenting a broken browser entry whose ID cannot be reopened.
                    if (file.name != "${project.id}.scene.json") return@runCatching null
                    SceneProjectSummary(
                        id = project.id,
                        name = project.name,
                        actorCount = project.actors.size,
                        modifiedAtEpochMs = maxOf(project.metadata.modifiedAtEpochMs, file.lastModified()),
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.modifiedAtEpochMs }
    }

    fun delete(projectId: String): Boolean = synchronized(IO_LOCK) {
        val target = projectFile(projectId)
        // Clean up legacy fixed-name pending files created by older versions.
        File(directory, projectId + ".pending").delete()
        !target.exists() || target.delete()
    }

    fun rename(projectId: String, name: String, modifiedAtEpochMs: Long = System.currentTimeMillis()): SceneProject =
        synchronized(IO_LOCK) {
            val normalizedName = name.trim().take(80)
            require(normalizedName.isNotEmpty()) { "Project name cannot be empty." }
            val current = load(projectId)
            val renamed = current.copy(
                name = normalizedName,
                metadata = current.metadata.copy(modifiedAtEpochMs = modifiedAtEpochMs),
            )
            save(renamed)
            renamed
        }

    fun duplicate(sourceId: String, newId: String, newName: String): SceneProject =
        synchronized(IO_LOCK) {
            require(sourceId != newId) { "A scene cannot be duplicated over itself." }
            // Validate the destination path before reading or writing anything.
            val destination = projectFile(newId)
            require(!destination.exists()) {
                "A scene with ID $newId already exists; duplication must not overwrite it."
            }
            val normalizedName = newName.trim().take(80)
            require(normalizedName.isNotEmpty()) { "Project name cannot be empty." }
            val source = load(sourceId)
            val now = System.currentTimeMillis()
            val copy = source.copy(
                id = newId,
                name = normalizedName,
                metadata = source.metadata.copy(
                    createdAtEpochMs = now,
                    modifiedAtEpochMs = now,
                ),
            )
            save(copy)
            copy
        }

    private fun projectFile(id: String): File {
        require(id.matches(PROJECT_ID_PATTERN)) { "Invalid project ID" }
        return File(directory, id + ".scene.json")
    }

    private companion object {
        // Shared among SceneProjectStore instances in one app process, including the renderer.
        val IO_LOCK = Any()
        val PROJECT_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,100}")
    }
}
