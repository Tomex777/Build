package com.night.keyboard.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.night.keyboard.data.theme.ThemeRepository
import com.night.keyboard.model.KeyStyleOverride
import com.night.keyboard.model.ThemeSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditorViewModel @Inject constructor(private val themes: ThemeRepository) : ViewModel() {
    val theme: StateFlow<ThemeSnapshot> = themes.activeTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSnapshot())
    fun updateSelected(selected: Set<String>, transform: (KeyStyleOverride) -> KeyStyleOverride) {
        if (selected.isEmpty()) return
        val current = theme.value; val next = current.overrides.toMutableMap()
        selected.forEach { id -> next[id] = transform(next[id] ?: KeyStyleOverride()) }
        save(current.copy(overrides = next))
    }
    fun updateBase(transform: (ThemeSnapshot) -> ThemeSnapshot) = save(transform(theme.value))
    private fun save(snapshot: ThemeSnapshot) { viewModelScope.launch { themes.saveAsActive(snapshot) } }
}
