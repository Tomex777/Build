package app.mira.android

import android.content.Context

class MiraSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("mira_settings", Context.MODE_PRIVATE)
    var incognito: Boolean
        get() = preferences.getBoolean("incognito", false)
        set(value) { preferences.edit().putBoolean("incognito", value).apply() }
}
