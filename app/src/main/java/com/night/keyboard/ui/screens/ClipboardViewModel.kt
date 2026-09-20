package com.night.keyboard.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.night.keyboard.data.clipboard.ClipboardRepository
import com.night.keyboard.data.prefs.KeyboardPreferenceState
import com.night.keyboard.data.prefs.KeyboardPreferences
import com.night.keyboard.model.ClipboardItem
import com.night.keyboard.model.RetentionPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val preferences: KeyboardPreferences,
) : ViewModel() {
    val items: StateFlow<List<ClipboardItem>> = repository.items.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val prefs: StateFlow<KeyboardPreferenceState> = preferences.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        KeyboardPreferenceState(),
    )

    private var deleted: ClipboardItem? = null
    private var cleared: List<ClipboardItem> = emptyList()

    init {
        viewModelScope.launch { repository.purgeExpired() }
    }

    fun pin(item: ClipboardItem, pinned: Boolean) = viewModelScope.launch {
        repository.setPinned(item, pinned)
    }

    fun retention(item: ClipboardItem, preset: RetentionPreset) = viewModelScope.launch {
        repository.setRetention(item, preset)
    }

    fun customRetention(item: ClipboardItem, durationMinutes: Long) = viewModelScope.launch {
        repository.setCustomRetention(item, durationMinutes)
    }

    fun move(item: ClipboardItem, direction: Int) = viewModelScope.launch {
        repository.move(item, direction)
    }

    fun delete(item: ClipboardItem) {
        deleted = item
        viewModelScope.launch { repository.delete(item) }
    }

    fun undoDelete() {
        val item = deleted ?: return
        deleted = null
        viewModelScope.launch { repository.restore(item) }
    }

    suspend fun clearUnpinnedForUndo(): Int {
        cleared = repository.clearUnpinnedWithBackup()
        return cleared.size
    }

    fun undoClear() {
        val items = cleared
        if (items.isEmpty()) return
        cleared = emptyList()
        viewModelScope.launch { repository.restoreAll(items) }
    }

    fun clearUnpinned() = viewModelScope.launch { repository.clearUnpinned() }

    fun setDefaultRetention(value: RetentionPreset) = viewModelScope.launch {
        preferences.setDefaultRetention(value)
    }

    fun setMaxHistory(value: Int) = viewModelScope.launch {
        preferences.setMaxHistory(value)
    }

    fun setPinnedAtTop(value: Boolean) = viewModelScope.launch {
        preferences.setKeepPinnedAtTop(value)
    }
}
