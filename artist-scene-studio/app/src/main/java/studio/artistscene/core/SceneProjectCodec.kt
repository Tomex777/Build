package studio.artistscene.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Version-aware scene serialization kept independent of Android storage so migrations can be unit tested.
 */
object SceneProjectCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(project: SceneProject): String {
        require(project.schemaVersion == SceneProject.CURRENT_SCHEMA_VERSION) {
            "Scene must be migrated before saving"
        }
        return json.encodeToString(project)
    }

    fun decode(serialized: String): SceneProject {
        val decoded = json.decodeFromString<SceneProject>(serialized)
        require(decoded.schemaVersion <= SceneProject.CURRENT_SCHEMA_VERSION) {
            "Scene schema ${decoded.schemaVersion} is newer than supported ${SceneProject.CURRENT_SCHEMA_VERSION}"
        }
        return migrate(decoded)
    }

    private fun migrate(project: SceneProject): SceneProject {
        var current = project
        while (current.schemaVersion < SceneProject.CURRENT_SCHEMA_VERSION) {
            current = when (current.schemaVersion) {
                1 -> current.copy(schemaVersion = 2)
                2 -> current.copy(schemaVersion = 3)
                3 -> current.copy(schemaVersion = 4)
                4 -> current.copy(schemaVersion = 5)
                5 -> current.copy(schemaVersion = 6)
                else -> error("No migration path for scene schema ${current.schemaVersion}")
            }
        }
        return current
    }
}
