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

    /** Android may reclaim the source-byte cache; native Filament resources follow the scene. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            MiseModelBytes.trim()
        }
    }

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

        // Include an actual posed, anime-inspired 3D study for artists. Never overwrite
        // an existing project, even after app upgrades or repeated launches.
        if (!preferences.getBoolean(ANIME_STUDY_SEEDED_KEY, false)) {
            if (!store.exists(PrototypeScene.ANIME_POSE_PROJECT_ID)) {
                runCatching { store.save(PrototypeScene.createAnimePoseStudy()) }
                    .onFailure { Log.w(RUNTIME_LOG_TAG, "anime-study-seed-failed", it) }
            }
            preferences.edit().putBoolean(ANIME_STUDY_SEEDED_KEY, true).apply()
        }

        // Preserve any artist edits or deliberate deletion after the first seed.
        // A failed save is retried on the next launch, never silently marked complete.
        if (!preferences.getBoolean(UNDER_TREE_SEEDED_KEY, false)) {
            if (store.exists(UnderTreeScene.PROJECT_ID)) {
                preferences.edit().putBoolean(UNDER_TREE_SEEDED_KEY, true).apply()
            } else {
                runCatching { store.save(UnderTreeScene.create()) }
                    .onSuccess {
                        preferences.edit().putBoolean(UNDER_TREE_SEEDED_KEY, true).apply()
                    }
                    .onFailure { Log.w(RUNTIME_LOG_TAG, "under-tree-scene-seed-failed", it) }
            }
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
                    primary = Color(0xFF87DBC9),
                    onPrimary = Color(0xFF102822),
                    secondary = Color(0xFFB3B6FF),
                    background = Color(0xFF10171C),
                    surface = Color(0xFF1B2931),
                    onSurface = Color(0xFFF3FAFA),
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
                            browserMessage = "Could not open this scene."
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
                                actors = PrototypeScene.create().actors.filter {
                                    it.kind == studio.artistscene.core.ActorKind.LIGHT
                                },
                                metadata = ProjectMetadata(createdAtEpochMs = now, modifiedAtEpochMs = now),
                            )
                            runCatching { store.save(created) }
                                .onSuccess {
                                    activeProject = created
                                    openedFromDisk = false
                                    browserMessage = null
                                    refreshProjects()
                                }
                                .onFailure { browserMessage = "Could not create this scene." }
                        },
                        onOpen = openProject,
                        onRename = { id, name ->
                            runCatching { store.rename(id, name) }
                                .onSuccess { browserMessage = null; refreshProjects() }
                                .onFailure { browserMessage = "Could not rename this scene." }
                        },
                        onDuplicate = { id ->
                            val original = projects.firstOrNull { it.id == id }
                            if (original != null) {
                                val copyId = "scene-" + UUID.randomUUID().toString().replace("-", "").take(20)
                                runCatching { store.duplicate(id, copyId, "${original.name} copy".take(80)) }
                                    .onSuccess { browserMessage = null; refreshProjects() }
                                    .onFailure { browserMessage = "Could not duplicate this scene." }
                            }
                        },
                        onDelete = { id ->
                            runCatching { store.delete(id) }
                                .onSuccess { browserMessage = null; refreshProjects() }
                                .onFailure { browserMessage = "Could not delete this scene." }
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
private const val ANIME_STUDY_SEEDED_KEY = "anime-pose-study-seeded-v1"
private const val UNDER_TREE_SEEDED_KEY = "under-tree-scene-seeded-v1"

internal fun studio.artistscene.core.SceneProject.propX(): Float =
    actors.firstOrNull { it.id == PrototypeScene.PROP_ID }?.transform?.position?.x ?: 0f
