package studio.artistscene.app

import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import java.util.UUID
import studio.artistscene.core.ProjectMetadata
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SceneProjectStore

internal const val RUNTIME_LOG_TAG = "MiseRuntime"

class MainActivity : ComponentActivity() {
    private lateinit var store: SceneProjectStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
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

        // Clean up presentation-only fixture labels from earlier development builds without
        // changing IDs, transforms, poses, assets, or any names the artist has already edited.
        if (store.exists(PrototypeScene.PROJECT_ID)) {
            runCatching {
                val legacy = store.load(PrototypeScene.PROJECT_ID)
                val cleaned = legacy.copy(
                    name = if (legacy.name == "Scene Studio Test Stage") "Starter Scene" else legacy.name,
                    actors = legacy.actors.map { actor ->
                        when {
                            actor.id == PrototypeScene.CHARACTER_ID && actor.name == "Cesium Man · Rig Fixture" ->
                                actor.copy(name = "Cesium Man")
                            actor.id == PrototypeScene.SECOND_CHARACTER_ID && actor.name == "Cesium Man · Rig Fixture B" ->
                                actor.copy(name = "Cesium Man B")
                            else -> actor
                        }
                    },
                )
                if (cleaned != legacy) store.save(cleaned)
            }.onFailure { Log.w(RUNTIME_LOG_TAG, "starter-presentation-migration-failed", it) }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF7C9BFF),
                    onPrimary = Color(0xFF101624),
                    secondary = Color(0xFF8AB8E8),
                    background = Color(0xFF15191F),
                    surface = Color(0xFF222832),
                    onSurface = Color(0xFFF2F5F8),
                ),
            ) {
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
