package app.mira.android

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MiraSourceEnablementStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableDisabledIds = MutableStateFlow(readDisabledIds())
    val disabledIds: StateFlow<Set<String>> = mutableDisabledIds.asStateFlow()

    fun isEnabled(sourceId: String): Boolean =
        sourceId !in mutableDisabledIds.value

    fun setEnabled(sourceId: String, enabled: Boolean) {
        val next = mutableDisabledIds.value.toMutableSet()
        if (enabled) {
            next.remove(sourceId)
        } else {
            next.add(sourceId)
        }
        val snapshot = next.toSet()
        mutableDisabledIds.value = snapshot
        preferences.edit()
            .putStringSet(KEY_DISABLED_SOURCE_IDS, snapshot)
            .apply()
    }

    private fun readDisabledIds(): Set<String> =
        preferences.getStringSet(KEY_DISABLED_SOURCE_IDS, emptySet())
            .orEmpty()
            .toSet()

    companion object {
        private const val PREFERENCES_NAME = "mira_source_enablement"
        private const val KEY_DISABLED_SOURCE_IDS = "disabled_source_ids"
    }
}
