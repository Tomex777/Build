package app.nami.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
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
        val container = FrameLayout(this).apply {
            id = containerId
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(container)
        ViewCompat.requestApplyInsets(container)

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

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val listener = object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(child: View) {
                    tintExtensionSwitches(child)
                }

                override fun onChildViewDetachedFromWindow(child: View) = Unit
            }
            listView.addOnChildAttachStateChangeListener(listener)
            for (index in 0 until listView.childCount) {
                tintExtensionSwitches(listView.getChildAt(index))
            }
        }

        private fun tintExtensionSwitches(view: View) {
            if (view is SwitchCompat) {
                view.thumbTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(),
                    ),
                    intArrayOf(
                        Color.rgb(138, 180, 248),
                        Color.rgb(138, 147, 158),
                    ),
                )
                view.trackTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(),
                    ),
                    intArrayOf(
                        Color.rgb(63, 99, 143),
                        Color.rgb(55, 63, 73),
                    ),
                )
                return
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    tintExtensionSwitches(view.getChildAt(index))
                }
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_ID = "source_id"
    }
}
