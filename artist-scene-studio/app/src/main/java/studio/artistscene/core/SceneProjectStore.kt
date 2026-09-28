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

/** Versioned JSON persistence using same-directory POSIX atomic rename. */
class SceneProjectStore(context: Context) {
    private val directory = File(context.filesDir, "projects").apply { mkdirs() }

    fun save(project: SceneProject) {
        require(project.schemaVersion == SceneProject.CURRENT_SCHEMA_VERSION)
        val target = projectFile(project.id)
        val pending = File(directory, project.id + ".pending")
        pending.writeText(SceneProjectCodec.encode(project))
        try {
            Os.rename(pending.absolutePath, target.absolutePath)
        } catch (failure: Exception) {
            pending.delete()
            throw failure
        }
    }

    fun load(projectId: String): SceneProject =
        SceneProjectCodec.decode(projectFile(projectId).readText())

    fun exists(projectId: String): Boolean = projectFile(projectId).isFile

    fun list(): List<SceneProjectSummary> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".scene.json") }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val project = SceneProjectCodec.decode(file.readText())
                    SceneProjectSummary(
                        id = project.id,
                        name = project.name,
                        actorCount = project.actors.size,
                        modifiedAtEpochMs = maxOf(project.metadata.modifiedAtEpochMs, file.lastModified()),
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.modifiedAtEpochMs }

    fun delete(projectId: String): Boolean {
        val target = projectFile(projectId)
        File(directory, projectId + ".pending").delete()
        return !target.exists() || target.delete()
    }

    fun rename(projectId: String, name: String, modifiedAtEpochMs: Long = System.currentTimeMillis()): SceneProject {
        val normalizedName = name.trim().take(80)
        require(normalizedName.isNotEmpty()) { "Project name cannot be empty." }
        val current = load(projectId)
        val renamed = current.copy(
            name = normalizedName,
            metadata = current.metadata.copy(modifiedAtEpochMs = modifiedAtEpochMs),
        )
        save(renamed)
        return renamed
    }

    fun duplicate(sourceId: String, newId: String, newName: String): SceneProject {
        val source = load(sourceId)
        val copy = source.copy(
            id = newId,
            name = newName,
            metadata = source.metadata.copy(modifiedAtEpochMs = 0L),
        )
        save(copy)
        return copy
    }

    private fun projectFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid project ID" }
        return File(directory, id + ".scene.json")
    }
}
