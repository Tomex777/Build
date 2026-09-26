package app.nami.android

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.DialogPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.forEach
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
                .installedSourceRegistry
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

        /**
         * Preference dialogs are AppCompat dialogs. Mirror Aniyomi's preference-context
         * wrapping so ListPreference and MultiSelectListPreference can open safely even
         * though Nami's main Compose activity uses a platform Material theme.
         */
        override fun getContext(): Context? {
            val base = super.getContext() ?: return null
            val value = TypedValue()
            val resolved = base.theme.resolveAttribute(
                androidx.preference.R.attr.preferenceTheme,
                value,
                true,
            )
            return if (resolved && value.resourceId != 0) {
                ContextThemeWrapper(base, value.resourceId)
            } else {
                base
            }
        }

        override fun onCreatePreferences(
            savedInstanceState: Bundle?,
            rootKey: String?,
        ) {
            val host = requireActivity() as AniyomiSourcePreferencesActivity

            // Aniyomi extensions read "source_<source id>" SharedPreferences directly.
            // Point PreferenceManager at that exact file; otherwise the UI can appear to
            // change while the extension keeps reading a different value.
            preferenceManager.sharedPreferencesName = host.sourceHandle.preferenceName()

            val screen = preferenceManager.createPreferenceScreen(requireContext())
            host.sourceHandle.setupPreferenceScreen(screen)

            // Match Aniyomi's native preference host behavior and make dialog titles sane.
            screen.forEach { preference ->
                preference.isIconSpaceReserved = false
                preference.isSingleLineTitle = false
                if (preference is DialogPreference && preference.dialogTitle.isNullOrEmpty()) {
                    preference.dialogTitle = preference.title
                }
            }

            preferenceScreen = screen
        }
    }

    companion object {
        const val EXTRA_SOURCE_ID = "source_id"
    }
}
