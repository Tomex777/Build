package studio.artistscene.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.UUID
import studio.artistscene.core.ProjectMetadata
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SceneProjectStore

internal const val RUNTIME_LOG_TAG = "MiseRuntime"

class MainActivity : ComponentActivity() {
    private lateinit var store: SceneProjectStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SceneProjectStore(this)
        val preferences = getSharedPreferences("mise", MODE_PRIVATE)
        if (!preferences.getBoolean(STARTER_SEEDED_KEY, false)) {
            val alreadyHasProjects = runCatching { store.list().isNotEmpty() }.getOrDefault(true)
            if (!alreadyHasProjects) {
                runCatching { store.save(PrototypeScene.create()) }
                    .onFailure { Log.w(RUNTIME_LOG_TAG, "starter-scene-create-failed", it) }
            }
            preferences.edit().putBoolean(STARTER_SEEDED_KEY, true).apply()
        }

        setContent {
            MaterialTheme {
                var projects by remember { mutableStateOf(runCatching { store.list() }.getOrDefault(emptyList())) }
                var activeProject by remember { mutableStateOf<SceneProject?>(null) }
                var openedFromDisk by remember { mutableStateOf(false) }
                var browserMessage by remember { mutableStateOf<String?>(null) }

                val refreshProjects = { projects = runCatching { store.list() }.getOrDefault(emptyList()) }
                val openProject: (String) -> Unit = { id ->
                    runCatching { store.load(id) }
                        .onSuccess {
                            activeProject = it
                            openedFromDisk = true
                            browserMessage = null
                            Log.i(
                                RUNTIME_LOG_TAG,
                                "scene-opened project=${it.id} x=${"%.2f".format(java.util.Locale.US, it.propX())}",
                            )
                        }
                        .onFailure {
                            browserMessage = "Could not open this scene · ${it.message ?: "project file unavailable"}"
                            Log.w(RUNTIME_LOG_TAG, "project-open-failed id=$id", it)
                            refreshProjects()
                        }
                }

                val project = activeProject
                if (project == null) {
                    ProjectBrowser(
                        projects = projects,
                        message = browserMessage,
                        onCreate = { name ->
                            val now = System.currentTimeMillis()
                            val created = SceneProject(
                                id = "scene-" + UUID.randomUUID().toString().replace("-", "").take(20),
                                name = name.trim().take(80),
                                metadata = ProjectMetadata(createdAtEpochMs = now, modifiedAtEpochMs = now),
                            )
                            runCatching { store.save(created) }
                                .onSuccess {
                                    activeProject = created
                                    openedFromDisk = false
                                    browserMessage = null
                                    refreshProjects()
                                }
                                .onFailure { browserMessage = "Could not create scene · ${it.message ?: "storage error"}" }
                        },
                        onOpen = openProject,
                        onRename = { id, name ->
                            runCatching { store.rename(id, name) }
                                .onSuccess { browserMessage = null; refreshProjects() }
                                .onFailure { browserMessage = "Could not rename scene · ${it.message ?: "storage error"}" }
                        },
                        onDelete = { id ->
                            runCatching { store.delete(id) }
                                .onSuccess { browserMessage = null; refreshProjects() }
                                .onFailure { browserMessage = "Could not delete scene · ${it.message ?: "storage error"}" }
                        },
                    )
                } else {
                    StudioScreen(
                        initialProject = project,
                        initiallyRestored = openedFromDisk,
                        onSave = store::save,
                        onRestore = { id -> runCatching { store.load(id) }.getOrNull() },
                        onExitToBrowser = { activeProject = null; refreshProjects() },
                    )
                }
            }
        }
    }
}

private const val STARTER_SEEDED_KEY = "starter-scene-seeded-v1"

internal fun studio.artistscene.core.SceneProject.propX(): Float =
    actors.firstOrNull { it.id == PrototypeScene.PROP_ID }?.transform?.position?.x ?: 0f
