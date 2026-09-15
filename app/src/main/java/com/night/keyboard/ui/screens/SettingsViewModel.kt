package com.night.keyboard.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.night.keyboard.data.prefs.KeyboardPreferenceState
import com.night.keyboard.data.prefs.KeyboardPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val preferences: KeyboardPreferences) : ViewModel() {
    val state: StateFlow<KeyboardPreferenceState> = preferences.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeyboardPreferenceState())
    fun numberRow(v: Boolean) = viewModelScope.launch { preferences.setNumberRow(v) }
    fun autocorrect(v: Boolean) = viewModelScope.launch { preferences.setAutocorrect(v) }
    fun suggestions(v: Boolean) = viewModelScope.launch { preferences.setSuggestions(v) }
    fun haptics(v: Boolean) = viewModelScope.launch { preferences.setHaptics(v) }
    fun secondary(v: Boolean) = viewModelScope.launch { preferences.setSecondaryCharacters(v) }
    fun serverUrl(v: String) = viewModelScope.launch { preferences.setServerUrl(v) }
}
