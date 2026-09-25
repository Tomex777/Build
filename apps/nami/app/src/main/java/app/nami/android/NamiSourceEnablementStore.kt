package app.nami.android

import android.content.Context
import app.nami.runtime.SourceEnablementStore

class NamiSourceEnablementStore(context: Context) : SourceEnablementStore {
    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    override fun isEnabled(sourceId: String): Boolean =
        sourceId !in preferences
            .getStringSet(KEY_DISABLED_SOURCE_IDS, emptySet())
            .orEmpty()

    override fun setEnabled(sourceId: String, enabled: Boolean) {
        val disabled = preferences
            .getStringSet(KEY_DISABLED_SOURCE_IDS, emptySet())
            .orEmpty()
            .toMutableSet()

        if (enabled) {
            disabled.remove(sourceId)
        } else {
            disabled.add(sourceId)
        }

        preferences.edit()
            .putStringSet(KEY_DISABLED_SOURCE_IDS, disabled)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "nami_source_enablement"
        private const val KEY_DISABLED_SOURCE_IDS = "disabled_source_ids"
    }
}
