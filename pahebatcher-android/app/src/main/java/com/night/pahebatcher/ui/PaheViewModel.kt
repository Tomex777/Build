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
import com.night.pahebatcher.data.DownloadPreferences
import com.night.pahebatcher.data.DownloadPreferencesStore
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
enum class VerifyStage { ANIMEPAHE, PREPARING_SECOND, KWIK, ANIMEPAHE_SAVED }

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
    private val downloadPreferencesStore = DownloadPreferencesStore(application)

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

    var globalDownloadPreferences by mutableStateOf(downloadPreferencesStore.global())
        private set

    var currentAnimeOverride by mutableStateOf<DownloadPreferences?>(null)
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
        currentAnimeOverride = downloadPreferencesStore.overrideFor(item)
        detailsLoading = true
        detailsError = null

        detailsJob = viewModelScope.launch {
            try {
                details = repository.loadAnime(item)
                details?.result?.let {
                    currentAnimeOverride = downloadPreferencesStore.overrideFor(it)
                }
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
        currentAnimeOverride = null
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
                    verifyStage = VerifyStage.ANIMEPAHE_SAVED
                    verifyError =
                        "AnimePahe is saved and usable. Kwik could not be prepared automatically: " +
                            (e.message ?: "unknown error") +
                            ". You can close this screen and continue."
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

    fun setGlobalQuality(quality: Int) {
        downloadPreferencesStore.setGlobalQuality(quality)
        globalDownloadPreferences = downloadPreferencesStore.global()
    }

    fun setGlobalAudio(audio: String) {
        downloadPreferencesStore.setGlobalAudio(audio)
        globalDownloadPreferences = downloadPreferencesStore.global()
    }

    fun setCurrentAnimeQuality(quality: Int) {
        val anime = details?.result ?: return
        val current = currentAnimeOverride ?: globalDownloadPreferences
        val updated = current.copy(quality = quality)
        downloadPreferencesStore.saveOverride(anime, updated)
        currentAnimeOverride = updated
    }

    fun setCurrentAnimeAudio(audio: String) {
        val anime = details?.result ?: return
        val current = currentAnimeOverride ?: globalDownloadPreferences
        val updated = current.copy(audio = if (audio == "eng") "eng" else "jpn")
        downloadPreferencesStore.saveOverride(anime, updated)
        currentAnimeOverride = updated
    }

    fun clearCurrentAnimeOverride() {
        val anime = details?.result ?: return
        downloadPreferencesStore.clearOverride(anime)
        currentAnimeOverride = null
    }

    fun effectiveDownloadPreferences(): DownloadPreferences =
        currentAnimeOverride ?: globalDownloadPreferences

    fun animePosterReferer(): String =
        "https://" + sessions.animeHost.ifBlank { "animepahe.pw" } + "/"

    fun animeUserAgent(): String = sessionStore.animeUserAgent()

    fun downloadEpisode(episode: EpisodeInfo) {
        val preferences = effectiveDownloadPreferences()
        downloadEpisode(episode, preferences.quality, preferences.audio)
    }

    fun downloadEpisode(
        episode: EpisodeInfo,
        quality: Int,
        audio: String,
    ) {
        val current = details ?: return
        if (downloads.any {
                it.animeTitle == current.result.title &&
                    it.episode == episode.epLabel &&
                    !it.failed &&
                    it.progress < 1f
            }
        ) {
            return
        }

        val id = enqueueDownload(
            animeTitle = current.result.title,
            episode = episode,
            quality = quality,
            audio = audio,
            status = "Resolving release…",
        )

        viewModelScope.launch {
            performDownload(id, current.result.title, episode, quality, audio)
        }
    }

    fun downloadAllCurrent() {
        val current = details ?: return
        if (current.episodes.isEmpty()) return

        val preferences = effectiveDownloadPreferences()
        val episodes = current.episodes
            .groupBy { it.number }
            .values
            .map { variants ->
                variants.firstOrNull { it.audio == preferences.audio } ?: variants.first()
            }
            .sortedBy { it.number }

        val queued = episodes.mapNotNull { episode ->
            val alreadyQueued = downloads.any {
                it.animeTitle == current.result.title &&
                    it.episode == episode.epLabel &&
                    !it.failed
            }
            if (alreadyQueued) {
                null
            } else {
                episode to enqueueDownload(
                    animeTitle = current.result.title,
                    episode = episode,
                    quality = preferences.quality,
                    audio = preferences.audio,
                    status = "Queued",
                )
            }
        }

        if (queued.isEmpty()) return

        viewModelScope.launch {
            for ((episode, id) in queued) {
                performDownload(
                    id = id,
                    animeTitle = current.result.title,
                    episode = episode,
                    quality = preferences.quality,
                    audio = preferences.audio,
                )
            }
        }
    }

    private fun enqueueDownload(
        animeTitle: String,
        episode: EpisodeInfo,
        quality: Int,
        audio: String,
        status: String,
    ): String {
        val id = UUID.randomUUID().toString()
        downloads.add(
            0,
            DownloadUi(
                id = id,
                animeTitle = animeTitle,
                episode = episode.epLabel,
                quality = quality,
                audio = audio,
                progress = 0f,
                status = status,
            )
        )
        return id
    }

    private suspend fun performDownload(
        id: String,
        animeTitle: String,
        episode: EpisodeInfo,
        quality: Int,
        audio: String,
    ) {
        try {
            updateDownload(id) { it.copy(status = "Resolving release…") }
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
                animeTitle = animeTitle,
                episode = episode,
            ) { progress ->
                viewModelScope.launch {
                    updateDownload(id) {
                        it.copy(
                            progress = progress,
                            status = "Downloading ${(progress * 100).toInt()}%",
                        )
                    }
                }
            }
            updateDownload(id) {
                it.copy(
                    progress = 1f,
                    status = "Saved to Downloads/PaheBatcher",
                    uri = uri,
                )
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
