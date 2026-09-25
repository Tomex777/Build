package app.nami.compat.aniyomi

import androidx.preference.PreferenceScreen

/**
 * Android-only bridge for Aniyomi sources that expose a PreferenceScreen.
 * Nami's generic source API deliberately stays free of Android preference types.
 */
interface AniyomiConfigurableSourceHandle {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}
