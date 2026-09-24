package com.tomex.aether

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID


data class AetherUiState(
    val categories: List<FeedCategory> = defaultCategories(),
    val selectedCategoryId: String = defaultCategories().first().id,
    val settings: AppSettings = AppSettings(),
    val posts: List<MemePost> = emptyList(),
    val savedPosts: List<MemePost> = emptyList(),
    val seenIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val activePostId: String? = null,
    val commentsPost: MemePost? = null,
    val comments: List<RedditComment> = emptyList(),
    val visibleCommentCount: Int = 25,
    val commentsLoading: Boolean = false,
    val discoveryCandidates: List<SubredditCandidate> = emptyList(),
    val discovering: Boolean = false,
    val aiResult: AiResult? = null,
    val aiLoading: Boolean = false,
    val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore(application)
    private val reddit = RedditClient()
    private val repository = FeedRepository(reddit, AiGatewayClient())

    private val _state = MutableStateFlow(AetherUiState())
    val state: StateFlow<AetherUiState> = _state.asStateFlow()

    private var feedJob: Job? = null
    private var lastConfigKey: String? = null

    init {
        viewModelScope.launch {
            combine(store.categories, store.selectedCategoryId, store.appSettings) { cats, selected, settings ->
                Triple(cats, selected, settings)
            }.collect { (cats, selected, settings) ->
                val safeSelected = selected.takeIf { id -> cats.any { it.id == id } } ?: cats.firstOrNull()?.id.orEmpty()
                _state.update { it.copy(categories = cats, selectedCategoryId = safeSelected, settings = settings) }
                val key = buildString {
                    append(cats.hashCode()); append('|'); append(safeSelected); append('|')
                    append(settings.includeVideos); append('|'); append(settings.sortMode)
                }
                if (key != lastConfigKey) {
                    lastConfigKey = key
                    reload()
                }
            }
        }
        viewModelScope.launch { store.seenIds.collect { ids -> _state.update { it.copy(seenIds = ids) } } }
        viewModelScope.launch { store.savedPosts.collect { posts -> _state.update { it.copy(savedPosts = posts) } } }
    }

    fun selectCategory(id: String) {
        viewModelScope.launch { store.setSelectedCategory(id) }
    }

    fun reload() {
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            repository.reset()
            _state.update { it.copy(posts = emptyList(), loading = true, loadingMore = false, error = null, activePostId = null) }
            loadMoreInternal(initial = true)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore) return
        viewModelScope.launch { loadMoreInternal(initial = false) }
    }

    private suspend fun loadMoreInternal(initial: Boolean) {
        val s = _state.value
        val category = s.categories.firstOrNull { it.id == s.selectedCategoryId } ?: return
        _state.update { if (initial) it.copy(loading = true) else it.copy(loadingMore = true) }
        runCatching {
            repository.nextBatch(
                category = category,
                settings = s.settings,
                seenIds = _state.value.seenIds,
                currentIds = _state.value.posts.mapTo(mutableSetOf()) { it.id },
            )
        }.onSuccess { batch ->
            _state.update { current ->
                current.copy(
                    posts = (current.posts + batch).distinctBy { it.id },
                    loading = false,
                    loadingMore = false,
                    error = if (batch.isEmpty() && current.posts.isEmpty()) "No fresh posts found yet." else null,
                )
            }
        }.onFailure { error ->
            _state.update { it.copy(loading = false, loadingMore = false, error = error.message ?: "Could not load Reddit") }
        }
    }

    fun togglePostActions(postId: String) {
        _state.update { it.copy(activePostId = if (it.activePostId == postId) null else postId) }
    }

    fun markSeen(postId: String) {
        if (postId in _state.value.seenIds) return
        viewModelScope.launch { store.markSeen(postId) }
    }

    fun dismiss(post: MemePost) {
        viewModelScope.launch {
            store.markSeen(post.id)
            _state.update { it.copy(posts = it.posts.filterNot { p -> p.id == post.id }, activePostId = null) }
            if (_state.value.posts.size < 8) loadMore()
        }
    }

    fun toggleSave(post: MemePost) {
        viewModelScope.launch {
            val current = _state.value.savedPosts
            val exists = current.any { it.id == post.id }
            val next = if (exists) current.filterNot { it.id == post.id } else listOf(post) + current
            store.setSavedPosts(next)
            postMessage(if (exists) "Removed from saved" else "Saved")
        }
    }

    fun openComments(post: MemePost) {
        if (_state.value.commentsPost?.id == post.id && _state.value.comments.isNotEmpty()) return
        _state.update { it.copy(commentsPost = post, comments = emptyList(), commentsLoading = true, visibleCommentCount = 25) }
        viewModelScope.launch {
            runCatching { repository.comments(post.id) }
                .onSuccess { comments -> _state.update { it.copy(comments = comments, commentsLoading = false) } }
                .onFailure { e ->
                    _state.update { it.copy(commentsLoading = false, message = e.message ?: "Could not load comments") }
                }
        }
    }

    fun closeComments() = _state.update { it.copy(commentsPost = null, comments = emptyList(), visibleCommentCount = 25) }

    fun revealMoreComments() {
        _state.update { s -> s.copy(visibleCommentCount = (s.visibleCommentCount + 30).coerceAtMost(s.comments.size)) }
    }

    fun setIncludeVideos(value: Boolean) = viewModelScope.launch { store.setIncludeVideos(value) }
    fun setAutoplayVideos(value: Boolean) = viewModelScope.launch { store.setAutoplayVideos(value) }
    fun setSortMode(value: SortMode) = viewModelScope.launch { store.setSortMode(value) }
    fun setAiBaseUrl(value: String) = viewModelScope.launch { store.setAiBaseUrl(value) }
    fun clearSeen() = viewModelScope.launch { store.clearSeen(); reload(); postMessage("Seen history cleared") }

    fun saveCategory(category: FeedCategory) {
        viewModelScope.launch {
            val cats = _state.value.categories.toMutableList()
            val index = cats.indexOfFirst { it.id == category.id }
            if (index >= 0) cats[index] = category else cats += category
            store.setCategories(cats)
            store.setSelectedCategory(category.id)
        }
    }

    fun newCategory(name: String = "New") = FeedCategory(
        id = "custom-${UUID.randomUUID()}",
        name = name,
        subreddits = emptyList(),
        tags = emptyList(),
    )

    fun deleteCategory(category: FeedCategory) {
        if (_state.value.categories.size <= 1) return
        viewModelScope.launch {
            val next = _state.value.categories.filterNot { it.id == category.id }
            store.setCategories(next)
            if (_state.value.selectedCategoryId == category.id) store.setSelectedCategory(next.first().id)
        }
    }

    fun validateSubreddit(name: String, onDone: (SubredditCandidate?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.validateSubreddit(name) }.getOrNull()
            onDone(result?.takeIf { !it.over18 })
        }
    }

    fun discoverSubreddits(prompt: String) {
        if (prompt.isBlank()) return
        _state.update { it.copy(discovering = true, discoveryCandidates = emptyList()) }
        viewModelScope.launch {
            runCatching { repository.discover(prompt, _state.value.settings.aiBaseUrl) }
                .onSuccess { results -> _state.update { it.copy(discovering = false, discoveryCandidates = results) } }
                .onFailure { e -> _state.update { it.copy(discovering = false, message = e.message ?: "Discovery failed") } }
        }
    }

    fun clearDiscovery() = _state.update { it.copy(discoveryCandidates = emptyList(), discovering = false) }

    fun runAi(post: MemePost, action: AiAction) {
        val baseUrl = _state.value.settings.aiBaseUrl
        if (baseUrl.isBlank()) {
            postMessage("Set the Aether AI server URL in Settings first")
            return
        }
        _state.update { it.copy(aiLoading = true, aiResult = null) }
        viewModelScope.launch {
            runCatching { repository.memeAi(baseUrl, post, action) }
                .onSuccess { result -> _state.update { it.copy(aiLoading = false, aiResult = result) } }
                .onFailure { e -> _state.update { it.copy(aiLoading = false, message = e.message ?: "AI request failed") } }
        }
    }

    fun clearAiResult() = _state.update { it.copy(aiResult = null, aiLoading = false) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun postMessage(message: String) = _state.update { it.copy(message = message) }
}
