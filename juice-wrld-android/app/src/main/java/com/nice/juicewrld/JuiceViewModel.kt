package com.nice.juicewrld

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JuiceState(
    val items: List<ReleaseGroup> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val offset: Int = 0,
    val error: String? = null,
    val fromCache: Boolean = false,
    val selected: ReleaseGroup? = null,
    val tracks: List<Track> = emptyList(),
    val loadingTracks: Boolean = false,
    val trackError: String? = null,
    val savedTab: Boolean = false,
)

class JuiceViewModel(application: Application) : AndroidViewModel(application) {
    private val service = MusicBrainzService(application)
    private val preferences = application.getSharedPreferences("favorites", 0)
    private val _state = MutableStateFlow(
        JuiceState(favorites = preferences.getStringSet("ids", emptySet()).orEmpty().toSet())
    )
    val state: StateFlow<JuiceState> = _state
    private var listing = false
    private var detailRequest = 0

    init {
        viewModelScope.launch {
            val cached = service.readCache()
            if (cached.isNotEmpty()) {
                _state.update { it.copy(items = cached, fromCache = true, loading = false) }
            }
            fetchPage(refresh = true)
        }
    }

    fun fetchPage(refresh: Boolean = false) {
        if (listing) return
        listing = true
        val current = _state.value
        if (refresh) _state.update {
            it.copy(loading = it.items.isEmpty(), loadingMore = false, error = null)
        } else {
            _state.update { it.copy(loadingMore = true, error = null) }
        }
        viewModelScope.launch {
            try {
                val page = service.releasesPage(if (refresh) 0 else current.offset)
                val updated = CatalogParser.merge(
                    if (refresh) current.items.filter { it.id in current.favorites } else current.items,
                    page.entries
                )
                _state.update { it.copy(
                    items = updated, offset = page.nextOffset, hasMore = page.hasMore,
                    loading = false, loadingMore = false, error = null, fromCache = false
                ) }
                service.writeCache(updated)
            } catch (_: Exception) {
                _state.update { it.copy(
                    loading = false, loadingMore = false, error =
                        if (it.items.isEmpty()) "Unable to load releases. Check your connection."
                        else "Couldn't refresh. Showing previously saved releases.",
                    fromCache = it.items.isNotEmpty()
                ) }
            } finally {
                listing = false
            }
        }
    }

    fun showRelease(release: ReleaseGroup) {
        val request = ++detailRequest
        _state.update { it.copy(
            selected = release, tracks = emptyList(), trackError = null, loadingTracks = true
        ) }
        viewModelScope.launch {
            try {
                val result = service.tracks(release.id)
                if (request == detailRequest) _state.update { it.copy(
                    tracks = result, loadingTracks = false,
                    trackError = if (result.isEmpty()) "Tracklist unavailable for this edition." else null
                ) }
            } catch (_: Exception) {
                if (request == detailRequest) _state.update {
                    it.copy(tracks = emptyList(), loadingTracks = false,
                        trackError = "Couldn't load the tracklist. Check your connection.")
                }
            }
        }
    }

    fun closeRelease() {
        detailRequest++
        _state.update { it.copy(selected = null, loadingTracks = false, trackError = null) }
    }

    fun toggleFavorite(id: String) {
        val next = _state.value.favorites.toMutableSet()
        if (!next.add(id)) next.remove(id)
        preferences.edit().putStringSet("ids", next).apply()
        _state.update { it.copy(favorites = next.toSet()) }
    }

    fun setSavedTab(showSaved: Boolean) = _state.update { it.copy(savedTab = showSaved) }
}
