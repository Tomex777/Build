package com.night.keyboard.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.night.keyboard.data.prefs.KeyboardPreferenceState
import com.night.keyboard.data.prefs.KeyboardPreferences
import com.night.keyboard.data.prefs.OneHandedMode
import com.night.keyboard.data.prediction.PredictionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: KeyboardPreferences,
    private val predictions: PredictionRepository,
) : ViewModel() {
    val state: StateFlow<KeyboardPreferenceState> = preferences.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        KeyboardPreferenceState(),
    )

    fun numberRow(v: Boolean) = viewModelScope.launch { preferences.setNumberRow(v) }
    fun autocorrect(v: Boolean) = viewModelScope.launch { preferences.setAutocorrect(v) }
    fun autocorrectAggression(v: Int) = viewModelScope.launch { preferences.setAutocorrectAggression(v) }
    fun suggestions(v: Boolean) = viewModelScope.launch { preferences.setSuggestions(v) }
    fun swipeTyping(v: Boolean) = viewModelScope.launch { preferences.setSwipeTyping(v) }
    fun swipeTrail(v: Boolean) = viewModelScope.launch { preferences.setSwipeTrail(v) }
    fun haptics(v: Boolean) = viewModelScope.launch { preferences.setHaptics(v) }
    fun secondary(v: Boolean) = viewModelScope.launch { preferences.setSecondaryCharacters(v) }
    fun incognito(v: Boolean) = viewModelScope.launch { preferences.setIncognito(v) }
    fun oneHanded(v: OneHandedMode) = viewModelScope.launch { preferences.setOneHandedMode(v) }
    fun floatingKeyboard(v: Boolean) = viewModelScope.launch { preferences.setFloatingKeyboard(v) }
    fun floatingWidth(v: Int) = viewModelScope.launch { preferences.setFloatingWidthPercent(v) }
    fun floatingLift(v: Int) = viewModelScope.launch { preferences.setFloatingLiftDp(v) }
    fun clearLearnedWords() = viewModelScope.launch { predictions.clear() }
    fun serverUrl(v: String) = viewModelScope.launch { preferences.setServerUrl(v) }
}
