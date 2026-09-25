package app.nami.android

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import app.nami.compat.aniyomi.AniyomiConfigurableSourceHandle
import kotlinx.coroutines.launch

/**
 * Hosts the native PreferenceScreen exposed by ConfigurableAnimeSource implementations.
 *
 * This stays separate from Compose so extension-owned AndroidX Preference objects can run
 * unchanged, just as they do in Aniyomi.
 */
class AniyomiSourcePreferencesActivity : FragmentActivity() {

    internal lateinit var sourceHandle: AniyomiConfigurableSourceHandle

    override fun onCreate(savedInstanceState: Bundle?) {
        // Do not restore a fragment before the extension source has been re-resolved.
        super.onCreate(null)

        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
        if (sourceId.isNullOrBlank()) {
            finish()
            return
        }

        val containerId = View.generateViewId()
        setContentView(
            FrameLayout(this).apply {
                id = containerId
            },
        )

        lifecycleScope.launch {
            val source = (application as NamiApplication)
                .sourceRegistry
                .installedSources()
                .firstOrNull { it.metadata.id == sourceId }

            val handle = source as? AniyomiConfigurableSourceHandle
            if (handle == null) {
                finish()
                return@launch
            }

            title = source.metadata.name
            sourceHandle = handle

            supportFragmentManager
                .beginTransaction()
                .replace(containerId, ExtensionPreferenceFragment())
                .commit()
        }
    }

    class ExtensionPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(
            savedInstanceState: Bundle?,
            rootKey: String?,
        ) {
            val screen = preferenceManager.createPreferenceScreen(requireContext())
            preferenceScreen = screen

            val host = requireActivity() as AniyomiSourcePreferencesActivity
            host.sourceHandle.setupPreferenceScreen(screen)
        }
    }

    companion object {
        const val EXTRA_SOURCE_ID = "source_id"
    }
}
