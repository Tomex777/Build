package com.night.keyboard.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.night.keyboard.data.theme.ThemeRepository
import com.night.keyboard.model.KeyStyleOverride
import com.night.keyboard.model.ThemeSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class EditorViewModel @Inject constructor(private val themes: ThemeRepository) : ViewModel() {
    private val persisted = themes.activeTheme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ThemeSnapshot(),
    )
    private val edits = MutableStateFlow<ThemeSnapshot?>(null)
    private val saveRequests = MutableSharedFlow<ThemeSnapshot>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val theme: StateFlow<ThemeSnapshot> = combine(persisted, edits) { saved, working ->
        working ?: saved
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSnapshot())

    init {
        viewModelScope.launch {
            saveRequests.debounce(120).collect { snapshot ->
                val persistedId = themes.saveAsActive(snapshot)
                if (snapshot.id == 0L && persistedId != 0L) {
                    edits.update { current ->
                        current?.let { if (it.id == 0L) it.copy(id = persistedId) else it }
                    }
                }
            }
        }
    }

    fun updateSelected(selected: Set<String>, transform: (KeyStyleOverride) -> KeyStyleOverride) {
        if (selected.isEmpty()) return
        val current = theme.value
        val nextOverrides = current.overrides.toMutableMap()
        selected.forEach { id ->
            nextOverrides[id] = transform(nextOverrides[id] ?: KeyStyleOverride())
        }
        setWorking(current.copy(overrides = nextOverrides))
    }

    fun updateBase(transform: (ThemeSnapshot) -> ThemeSnapshot) {
        setWorking(transform(theme.value))
    }

    private fun setWorking(snapshot: ThemeSnapshot) {
        edits.value = snapshot
        saveRequests.tryEmit(snapshot)
    }
}
