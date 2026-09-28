package studio.artistscene.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import studio.artistscene.core.SceneProjectStore

internal const val RUNTIME_LOG_TAG = "MiseRuntime"

class MainActivity : ComponentActivity() {
    private lateinit var store: SceneProjectStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SceneProjectStore(this)
        val existing = runCatching { store.load(PrototypeScene.PROJECT_ID) }
            .onFailure { Log.w(RUNTIME_LOG_TAG, "scene-restore-failed project=\${PrototypeScene.PROJECT_ID}", it) }
            .getOrNull()

        if (existing == null) {
            Log.i(RUNTIME_LOG_TAG, "scene-new project=\${PrototypeScene.PROJECT_ID}")
        } else {
            Log.i(
                RUNTIME_LOG_TAG,
                "scene-restored project=\${existing.id} x=\${"%.2f".format(java.util.Locale.US, existing.propX())}",
            )
        }

        setContent {
            MaterialTheme {
                StudioScreen(
                    initialProject = existing ?: PrototypeScene.create(),
                    initiallyRestored = existing != null,
                    onSave = store::save,
                    onRestore = {
                        runCatching { store.load(PrototypeScene.PROJECT_ID) }
                            .onFailure { Log.w(RUNTIME_LOG_TAG, "scene-restore-manual-failed", it) }
                            .getOrNull()
                    },
                )
            }
        }
    }
}

internal fun studio.artistscene.core.SceneProject.propX(): Float =
    actors.firstOrNull { it.id == PrototypeScene.PROP_ID }?.transform?.position?.x ?: 0f
