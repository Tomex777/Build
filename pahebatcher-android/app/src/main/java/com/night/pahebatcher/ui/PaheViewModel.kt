package com.night.pahebatcher.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.night.pahebatcher.data.AnimeDetails
import com.night.pahebatcher.data.AnimeSearchResult
import com.night.pahebatcher.data.EpisodeInfo
import com.night.pahebatcher.data.PaheRepository
import com.night.pahebatcher.data.SessionStore
import com.night.pahebatcher.data.VerificationKind
import com.night.pahebatcher.data.VerificationRequired
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URI
import java.util.UUID

enum class MainTab { EXPLORE, DOWNLOADS, SETTINGS }
enum class VerifyStage { ANIMEPAHE, PREPARING_SECOND, KWIK }

data class DownloadUi(
    val id: String,
    val animeTitle: String,
    val episode: String,
    val quality: Int,
    val audio: String,
    val progress: Float,
    val status: String,
    val uri: Uri? = null,
    val failed: Boolean = false,
)

class PaheViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val repository = PaheRepository(application, sessionStore)

    var tab by mutableStateOf(MainTab.EXPLORE)
        private set
    var query by mutableStateOf("")
    var results by mutableStateOf<List<AnimeSearchResult>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    var details by mutableStateOf<AnimeDetails?>(null)
        private set
    var detailsLoading by mutableStateOf(false)
        private set
    var detailsError by mutableStateOf<String?>(null)
        private set

    private var detailsJob: Job? = null

    var verificationActive by mutableStateOf(false)
        private set
    var verifyStage by mutableStateOf(VerifyStage.ANIMEPAHE)
        private set
    var verifyUrl by mutableStateOf("")
        private set
    var verifyError by mutableStateOf<String?>(null)
        private set

    var sessions by mutableStateOf(sessionStore.snapshot())
        private set

    val downloads = mutableStateListOf<DownloadUi>()

    fun navigateToTab(value: MainTab) {
        tab = value
        details = null
        detailsError = null
    }

    fun submitSearch() {
        val clean = query.trim()
        if (clean.isBlank()) return
        searching = true
        searchError = null
        viewModelScope.launch {
            try {
                results = repository.search(clean)
                if (results.isEmpty()) searchError = "No matches found."
                refreshSessions()
            } catch (e: VerificationRequired) {
                searchError = verificationMessage(e.kind)
            } catch (e: Exception) {
                searchError = e.message ?: "Search failed."
            } finally {
                searching = false
            }
        }
    }

    fun openAnime(item: AnimeSearchResult) {
        detailsJob?.cancel()

        // Open the screen immediately using data we already received from AnimePahe search.
        // Episodes enrich this shell in the background.
        details = AnimeDetails(
            result = item,
            host = sessions.animeHost,
            episodes = emptyList(),
        )
        detailsLoading = true
        detailsError = null

        detailsJob = viewModelScope.launch {
            try {
                details = repository.loadAnime(item)
                refreshSessions()
            } catch (e: VerificationRequired) {
                detailsError = verificationMessage(e.kind)
            } catch (e: Exception) {
                detailsError = e.message ?: "Could not load episodes."
            } finally {
                detailsLoading = false
            }
        }
    }

    fun retryDetails() {
        details?.result?.let(::openAnime)
    }

    fun closeDetails() {
        detailsJob?.cancel()
        detailsJob = null
        details = null
        detailsLoading = false
        detailsError = null
    }

    fun startVerification() {
        verifyError = null
        verifyStage = VerifyStage.ANIMEPAHE
        val host = sessions.animeHost.ifBlank { "animepahe.pw" }
        verifyUrl = "https://$host/"
        verificationActive = true
    }

    fun closeVerification() {
        verificationActive = false
        verifyError = null
        refreshSessions()
    }

    fun completeVerificationStep(
        currentUrl: String,
        cookie: String,
        userAgent: String,
    ) {
        if (verifyStage == VerifyStage.PREPARING_SECOND) return
        if (cookie.isBlank()) {
            verifyError = "No cookies were found yet. Finish the browser check before continuing."
            return
        }
        val host = runCatching { URI(currentUrl).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) {
            verifyError = "This page does not have a valid host yet."
            return
        }

        if (verifyStage == VerifyStage.ANIMEPAHE) {
            if (!host.contains("animepahe") && host != "pahe.win") {
                verifyError = "Finish the AnimePahe check and return to the AnimePahe page before confirming."
                return
            }
            sessionStore.saveAnime(cookie, host, userAgent)
            refreshSessions()
            verifyStage = VerifyStage.PREPARING_SECOND
            verifyError = null
            viewModelScope.launch {
                try {
                    verifyUrl = repository.bootstrapKwikUrl()
                    verifyStage = VerifyStage.KWIK
                } catch (e: Exception) {
                    verifyStage = VerifyStage.ANIMEPAHE
                    verifyError = e.message ?: "Could not open the second verification."
                }
            }
        } else if (verifyStage == VerifyStage.KWIK) {
            if (!host.startsWith("kwik.") && !host.contains(".kwik.")) {
                verifyError = "Finish the Kwik check and return to the Kwik page before confirming."
                return
            }
            sessionStore.saveKwik(cookie, host, userAgent)
            refreshSessions()
            verificationActive = false
            tab = MainTab.SETTINGS
        }
    }

    fun clearVerificationSessions() {
        sessionStore.clear()
        refreshSessions()
    }

    fun downloadEpisode(
        episode: EpisodeInfo,
        quality: Int,
        audio: String,
    ) {
        val current = details ?: return
        val id = UUID.randomUUID().toString()
        downloads.add(
            0,
            DownloadUi(
                id = id,
                animeTitle = current.result.title,
                episode = episode.epLabel,
                quality = quality,
                audio = audio,
                progress = 0f,
                status = "Resolving release…",
            )
        )

        viewModelScope.launch {
            try {
                val stream = repository.resolveStream(episode, quality, audio)
                updateDownload(id) {
                    it.copy(
                        quality = stream.quality,
                        audio = stream.audio,
                        status = "Downloading ${stream.quality}p…",
                    )
                }
                val uri = repository.downloadStream(
                    stream = stream,
                    animeTitle = current.result.title,
                    episode = episode,
                ) { progress ->
                    viewModelScope.launch {
                        updateDownload(id) {
                            it.copy(progress = progress, status = "Downloading ${(progress * 100).toInt()}%")
                        }
                    }
                }
                updateDownload(id) {
                    it.copy(progress = 1f, status = "Saved to Downloads/PaheBatcher", uri = uri)
                }
            } catch (e: VerificationRequired) {
                updateDownload(id) {
                    it.copy(status = verificationMessage(e.kind), failed = true)
                }
            } catch (e: Exception) {
                updateDownload(id) {
                    it.copy(status = e.message ?: "Download failed", failed = true)
                }
            }
        }
    }

    fun removeDownload(id: String) {
        downloads.removeAll { it.id == id }
    }

    private fun updateDownload(id: String, transform: (DownloadUi) -> DownloadUi) {
        val index = downloads.indexOfFirst { it.id == id }
        if (index >= 0) downloads[index] = transform(downloads[index])
    }

    private fun refreshSessions() {
        sessions = sessionStore.snapshot()
    }

    private fun verificationMessage(kind: VerificationKind): String =
        when (kind) {
            VerificationKind.ANIMEPAHE -> "AnimePahe verification is needed. Open the verification browser in Settings."
            VerificationKind.KWIK -> "Kwik verification is needed. Run the two-step verification again in Settings."
        }
}
