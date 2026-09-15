package com.night.keyboard.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.night.keyboard.model.RetentionPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.keyboardDataStore by preferencesDataStore(name = "keyboard_preferences")

data class KeyboardPreferenceState(
    val defaultRetention: String = RetentionPreset.TWO_HOURS.name,
    val maxHistory: Int = 50,
    val keepPinnedAtTop: Boolean = true,
    val numberRow: Boolean = false,
    val autocorrect: Boolean = true,
    val suggestions: Boolean = true,
    val haptics: Boolean = true,
    val secondaryCharacters: Boolean = true,
    val serverUrl: String = "",
)

@Singleton
class KeyboardPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val defaultRetention = stringPreferencesKey("default_retention")
        val maxHistory = intPreferencesKey("max_history")
        val keepPinnedAtTop = booleanPreferencesKey("keep_pinned_at_top")
        val numberRow = booleanPreferencesKey("number_row")
        val autocorrect = booleanPreferencesKey("autocorrect")
        val suggestions = booleanPreferencesKey("suggestions")
        val haptics = booleanPreferencesKey("haptics")
        val secondaryCharacters = booleanPreferencesKey("secondary_characters")
        val serverUrl = stringPreferencesKey("server_url")
    }

    val state: Flow<KeyboardPreferenceState> = context.keyboardDataStore.data.map { p ->
        KeyboardPreferenceState(
            defaultRetention = p[Keys.defaultRetention] ?: RetentionPreset.TWO_HOURS.name,
            maxHistory = p[Keys.maxHistory] ?: 50,
            keepPinnedAtTop = p[Keys.keepPinnedAtTop] ?: true,
            numberRow = p[Keys.numberRow] ?: false,
            autocorrect = p[Keys.autocorrect] ?: true,
            suggestions = p[Keys.suggestions] ?: true,
            haptics = p[Keys.haptics] ?: true,
            secondaryCharacters = p[Keys.secondaryCharacters] ?: true,
            serverUrl = p[Keys.serverUrl] ?: "",
        )
    }

    suspend fun setDefaultRetention(value: RetentionPreset) {
        context.keyboardDataStore.edit { it[Keys.defaultRetention] = value.name }
    }

    suspend fun setMaxHistory(value: Int) {
        context.keyboardDataStore.edit { it[Keys.maxHistory] = value }
    }

    suspend fun setKeepPinnedAtTop(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.keepPinnedAtTop] = value }
    }

    suspend fun setNumberRow(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.numberRow] = value }
    }

    suspend fun setAutocorrect(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.autocorrect] = value }
    }

    suspend fun setSuggestions(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.suggestions] = value }
    }

    suspend fun setHaptics(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.haptics] = value }
    }

    suspend fun setSecondaryCharacters(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.secondaryCharacters] = value }
    }

    suspend fun setServerUrl(value: String) {
        context.keyboardDataStore.edit { it[Keys.serverUrl] = value.trim() }
    }
}
