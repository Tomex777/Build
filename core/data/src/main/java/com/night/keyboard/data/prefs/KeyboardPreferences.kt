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
private const val EmojiSeparator = "\u001F"

enum class OneHandedMode { OFF, LEFT, RIGHT }

data class KeyboardPreferenceState(
    val defaultRetention: String = RetentionPreset.TWO_HOURS.name,
    val maxHistory: Int = 50,
    val keepPinnedAtTop: Boolean = true,
    val numberRow: Boolean = false,
    val autocorrect: Boolean = true,
    val autocorrectAggression: Int = 2,
    val suggestions: Boolean = true,
    val swipeTyping: Boolean = true,
    val swipeTrail: Boolean = true,
    val haptics: Boolean = true,
    val secondaryCharacters: Boolean = true,
    val incognito: Boolean = false,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val emojiRecents: List<String> = emptyList(),
    val emojiFavorites: Set<String> = emptySet(),
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
        val autocorrectAggression = intPreferencesKey("autocorrect_aggression")
        val suggestions = booleanPreferencesKey("suggestions")
        val swipeTyping = booleanPreferencesKey("swipe_typing")
        val swipeTrail = booleanPreferencesKey("swipe_trail")
        val haptics = booleanPreferencesKey("haptics")
        val secondaryCharacters = booleanPreferencesKey("secondary_characters")
        val incognito = booleanPreferencesKey("incognito")
        val oneHandedMode = stringPreferencesKey("one_handed_mode")
        val emojiRecents = stringPreferencesKey("emoji_recents")
        val emojiFavorites = stringPreferencesKey("emoji_favorites")
        val serverUrl = stringPreferencesKey("server_url")
    }

    val state: Flow<KeyboardPreferenceState> = context.keyboardDataStore.data.map { p ->
        KeyboardPreferenceState(
            defaultRetention = p[Keys.defaultRetention] ?: RetentionPreset.TWO_HOURS.name,
            maxHistory = p[Keys.maxHistory] ?: 50,
            keepPinnedAtTop = p[Keys.keepPinnedAtTop] ?: true,
            numberRow = p[Keys.numberRow] ?: false,
            autocorrect = p[Keys.autocorrect] ?: true,
            autocorrectAggression = (p[Keys.autocorrectAggression] ?: 2).coerceIn(1, 3),
            suggestions = p[Keys.suggestions] ?: true,
            swipeTyping = p[Keys.swipeTyping] ?: true,
            swipeTrail = p[Keys.swipeTrail] ?: true,
            haptics = p[Keys.haptics] ?: true,
            secondaryCharacters = p[Keys.secondaryCharacters] ?: true,
            incognito = p[Keys.incognito] ?: false,
            oneHandedMode = runCatching {
                OneHandedMode.valueOf(p[Keys.oneHandedMode] ?: OneHandedMode.OFF.name)
            }.getOrDefault(OneHandedMode.OFF),
            emojiRecents = decodeEmojiList(p[Keys.emojiRecents]),
            emojiFavorites = decodeEmojiList(p[Keys.emojiFavorites]).toSet(),
            serverUrl = p[Keys.serverUrl] ?: "",
        )
    }

    suspend fun setDefaultRetention(value: RetentionPreset) {
        context.keyboardDataStore.edit { it[Keys.defaultRetention] = value.name }
    }

    suspend fun setMaxHistory(value: Int) {
        context.keyboardDataStore.edit { it[Keys.maxHistory] = value.coerceIn(10, 500) }
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

    suspend fun setAutocorrectAggression(value: Int) {
        context.keyboardDataStore.edit { it[Keys.autocorrectAggression] = value.coerceIn(1, 3) }
    }

    suspend fun setSuggestions(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.suggestions] = value }
    }

    suspend fun setSwipeTyping(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.swipeTyping] = value }
    }

    suspend fun setSwipeTrail(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.swipeTrail] = value }
    }

    suspend fun setHaptics(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.haptics] = value }
    }

    suspend fun setSecondaryCharacters(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.secondaryCharacters] = value }
    }

    suspend fun setIncognito(value: Boolean) {
        context.keyboardDataStore.edit { it[Keys.incognito] = value }
    }

    suspend fun setOneHandedMode(value: OneHandedMode) {
        context.keyboardDataStore.edit { it[Keys.oneHandedMode] = value.name }
    }

    suspend fun recordEmoji(output: String) {
        if (output.isBlank()) return
        context.keyboardDataStore.edit { p ->
            val next = (listOf(output) + decodeEmojiList(p[Keys.emojiRecents]).filterNot { it == output })
                .take(24)
            p[Keys.emojiRecents] = encodeEmojiList(next)
        }
    }

    suspend fun toggleEmojiFavorite(output: String) {
        if (output.isBlank()) return
        context.keyboardDataStore.edit { p ->
            val current = decodeEmojiList(p[Keys.emojiFavorites]).toMutableList()
            if (!current.remove(output)) current.add(0, output)
            p[Keys.emojiFavorites] = encodeEmojiList(current.distinct().take(48))
        }
    }

    suspend fun setServerUrl(value: String) {
        context.keyboardDataStore.edit { it[Keys.serverUrl] = value.trim() }
    }
}

private fun decodeEmojiList(raw: String?): List<String> =
    raw.orEmpty().split(EmojiSeparator).filter(String::isNotBlank)

private fun encodeEmojiList(items: Collection<String>): String =
    items.filter(String::isNotBlank).joinToString(EmojiSeparator)
