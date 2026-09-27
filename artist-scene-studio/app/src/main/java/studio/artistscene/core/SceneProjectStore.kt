package studio.artistscene.core

import android.content.Context
import android.system.Os
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned JSON persistence using a same-directory POSIX atomic rename. */
class SceneProjectStore(context: Context) {
    private val directory = File(context.filesDir, "projects").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun save(project: SceneProject) {
        require(project.schemaVersion == SceneProject.CURRENT_SCHEMA_VERSION)
        val target = projectFile(project.id)
        val pending = File(directory, project.id + ".pending")
        pending.writeText(json.encodeToString(project))
        try {
            Os.rename(pending.absolutePath, target.absolutePath)
        } catch (failure: Exception) {
            pending.delete()
            throw failure
        }
    }

    fun load(projectId: String): SceneProject {
        val project = json.decodeFromString<SceneProject>(projectFile(projectId).readText())
        require(project.schemaVersion == SceneProject.CURRENT_SCHEMA_VERSION)
        return project
    }

    private fun projectFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid project ID" }
        return File(directory, id + ".scene.json")
    }
}
