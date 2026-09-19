package com.night.pahebatcher.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.night.pahebatcher.data.AniListRepository
import com.night.pahebatcher.data.AnimeDetails
import com.night.pahebatcher.data.AnimeSearchResult
import com.night.pahebatcher.data.DownloadPreferences
import com.night.pahebatcher.data.DownloadPreferencesStore
import com.night.pahebatcher.data.DownloadTaskStore
import com.night.pahebatcher.data.EpisodeDownloadWorker
import com.night.pahebatcher.data.EpisodeInfo
import com.night.pahebatcher.data.PaheRepository
import com.night.pahebatcher.data.SessionStore
import com.night.pahebatcher.data.StoredDownloadTask
import com.night.pahebatcher.data.VerificationKind
import com.night.pahebatcher.data.VerificationRequired
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class MainTab { EXPLORE, DOWNLOADS, SETTINGS }
enum class VerifyStage { ANIMEPAHE }

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
    val paused: Boolean = false,
)

class PaheViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val repository = PaheRepository(application, sessionStore)
    private val aniListRepository = AniListRepository()
    private val downloadPreferencesStore = DownloadPreferencesStore(application)
    private val downloadStore = DownloadTaskStore(application)
    private val workManager = WorkManager.getInstance(application)

    var tab by mutableStateOf(MainTab.EXPLORE)
        private set
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<AnimeSearchResult>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    var recentAnime by mutableStateOf<List<AnimeSearchResult>>(emptyList())
        private set
    var recentLoading by mutableStateOf(false)
        private set
    var recentError by mutableStateOf<String?>(null)
        private set

    var details by mutableStateOf<AnimeDetails?>(null)
        private set
    var detailsLoading by mutableStateOf(false)
        private set
    var detailsError by mutableStateOf<String?>(null)
        private set

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

    var aboutActive by mutableStateOf(false)
        private set

    private var searchJob: Job? = null
    private var airDateJob: Job? = null

    init {
        refreshRecent()
        syncDownloads()
        viewModelScope.launch {
            while (true) {
                syncDownloads()
                delay(750)
            }
        }
    }

    fun navigateToTab(value: MainTab) {
        tab = value
        details = null
        detailsError = null
    }

    fun onQueryChanged(value: String) {
        query = value
        searchJob?.cancel()

        val clean = value.trim()
        if (clean.isBlank()) {
            results = emptyList()
            searchError = null
            searching = false
            return
        }

        if (clean.length < 2) {
            results = emptyList()
            searchError = null
            searching = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(250)
            searching = true
            searchError = null
            try {
                results = aniListRepository.search(clean)
            } catch (e: Exception) {
                searchError = e.message ?: "AniList search failed."
            } finally {
                searching = false
            }
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        val clean = query.trim()
        if (clean.isBlank()) {
            clearSearch()
            return
        }
        searching = true
        searchError = null
        viewModelScope.launch {
            try {
                results = aniListRepository.search(clean)
                if (results.isEmpty()) searchError = "No matches found."
            } catch (e: Exception) {
                searchError = e.message ?: "AniList search failed."
            } finally {
                searching = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        if (query.isBlank()) {
            results = emptyList()
            searchError = null
            searching = false
        }
    }

    fun refreshRecent() {
        if (recentLoading) return
        recentLoading = true
        recentError = null
        viewModelScope.launch {
            try {
                val available = repository.recentlyAvailable()
                recentAnime = aniListRepository.enrichAvailable(available)
            } catch (e: VerificationRequired) {
                recentAnime = emptyList()
                recentError = if (sessions.animeCookieSaved) {
                    "AnimePahe source request was blocked. Your browser verification is still saved — tap Retry."
                } else {
                    "Verify AnimePahe to load releases that are actually available."
                }
            } catch (e: Exception) {
                recentAnime = emptyList()
                recentError = e.message ?: "Could not load available releases."
            } finally {
                recentLoading = false
            }
        }
    }

    fun openAnime(item: AnimeSearchResult) {
        airDateJob?.cancel()

        val episodeSlots = if (item.episodes > 0) {
            (1..item.episodes).map { number ->
                EpisodeInfo(
                    number = number.toDouble(),
                    session = "",
                    title = "",
                    fansub = "",
                    audio = "jpn",
                    playUrl = "",
                )
            }
        } else {
            emptyList()
        }

        details = AnimeDetails(
            result = item,
            host = "",
            episodes = episodeSlots,
        )
        currentAnimeOverride = downloadPreferencesStore.overrideFor(item)
        detailsLoading = false
        detailsError = null

        val aniListId = item.aniListId
        if (aniListId != null && episodeSlots.isNotEmpty()) {
            airDateJob = viewModelScope.launch {
                val dates = runCatching {
                    aniListRepository.episodeAirDates(aniListId, item.episodes)
                }.getOrDefault(emptyMap())
                if (dates.isEmpty()) return@launch

                val current = details ?: return@launch
                if (current.result.aniListId != aniListId) return@launch

                details = current.copy(
                    episodes = current.episodes.map { episode ->
                        episode.copy(
                            airedAt = dates[episode.number.toInt()]?.times(1000L),
                        )
                    }
                )
            }
        }
    }

    fun retryDetails() {
        details?.result?.let(::openAnime)
    }

    fun closeDetails() {
        airDateJob?.cancel()
        airDateJob = null
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
        if (cookie.isBlank()) {
            verifyError = "No cookies were found yet. Finish the browser check before continuing."
            return
        }
        val host = runCatching { URI(currentUrl).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) {
            verifyError = "This page does not have a valid host yet."
            return
        }
        if (!host.contains("animepahe") && host != "pahe.win") {
            verifyError = "Finish the AnimePahe check and return to the AnimePahe page before confirming."
            return
        }

        sessionStore.saveAnime(cookie, host, userAgent)
        refreshSessions()
        verifyError = null

        verificationActive = false
        refreshSessions()
        resumePausedDownloads()

        viewModelScope.launch {
            try {
                repository.validateAnimeSession()
                refreshSessions()
            } catch (_: Exception) {
                // Manual WebView verification succeeded and the browser session
                // is saved. A single app-side probe failure must never erase it
                // or immediately force the user back through verification.
                refreshSessions()
            }
            refreshRecent()
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

    fun animeCookieFor(url: String): String = sessionStore.cookieFor(url)

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
        val duplicate = downloadStore.all().any {
            it.animeTitle == current.result.title &&
                it.episodeNumber == episode.number &&
                it.state != StoredDownloadTask.STATE_FAILED
        }
        if (duplicate) return

        enqueueDownload(
            catalog = current.result,
            episode = episode,
            quality = quality,
            audio = audio,
        )
    }

    fun downloadAllCurrent() {
        val current = details ?: return
        if (current.episodes.isEmpty()) return

        val preferences = effectiveDownloadPreferences()
        val existing = downloadStore.all()
        val episodes = current.episodes
            .groupBy { it.number }
            .values
            .map { variants ->
                variants.firstOrNull { it.audio == preferences.audio } ?: variants.first()
            }
            .sortedBy { it.number }

        episodes.forEach { episode ->
            val duplicate = existing.any {
                it.animeTitle == current.result.title &&
                    it.episodeNumber == episode.number &&
                    it.state != StoredDownloadTask.STATE_FAILED
            }
            if (!duplicate) {
                enqueueDownload(
                    catalog = current.result,
                    episode = episode,
                    quality = preferences.quality,
                    audio = preferences.audio,
                )
            }
        }
    }

    private fun enqueueDownload(
        catalog: AnimeSearchResult,
        episode: EpisodeInfo,
        quality: Int,
        audio: String,
    ) {
        val id = UUID.randomUUID().toString()
        val request = buildDownloadWork(id)

        downloadStore.put(
            StoredDownloadTask(
                id = id,
                workId = request.id.toString(),
                animeTitle = catalog.title,
                aniListId = catalog.aniListId ?: 0,
                sourceQueries = catalog.sourceQueries,
                sourceAnimeId = catalog.animeId ?: 0,
                sourceAnimeSession = catalog.session,
                episodeNumber = episode.number,
                episodeSession = episode.session,
                episodeTitle = episode.title,
                episodeFansub = episode.fansub,
                episodeAudio = episode.audio,
                playUrl = episode.playUrl,
                requestedQuality = quality,
                requestedAudio = audio,
                status = "Queued — waiting for connection",
            )
        )

        workManager.enqueueUniqueWork(
            "$DOWNLOAD_QUEUE_NAME:$id",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        syncDownloads()
    }

    private fun buildDownloadWork(taskId: String) =
        OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(EpisodeDownloadWorker.INPUT_TASK_ID, taskId)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .addTag(DOWNLOAD_WORK_TAG)
            .build()

    private fun resumePausedDownloads() {
        downloadStore.all()
            .filter { it.state == StoredDownloadTask.STATE_PAUSED && !it.manualPaused }
            .forEach { task ->
                task.workId.takeIf { it.isNotBlank() }?.let { oldId ->
                    runCatching { workManager.cancelWorkById(UUID.fromString(oldId)) }
                }

                val request = buildDownloadWork(task.id)
                downloadStore.updateWorkId(
                    id = task.id,
                    workId = request.id.toString(),
                    status = "Verified — resuming download",
                )
                workManager.enqueueUniqueWork(
                    "$DOWNLOAD_QUEUE_NAME:${task.id}",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        syncDownloads()
    }

    fun pauseDownload(id: String) {
        val task = downloadStore.get(id) ?: return
        if (task.state == StoredDownloadTask.STATE_COMPLETED) return

        downloadStore.markPaused(id, "Paused by you", manual = true)
        task.workId
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { workManager.cancelWorkById(UUID.fromString(it)) } }
        syncDownloads()
    }

    fun resumeDownload(id: String) {
        val task = downloadStore.get(id) ?: return
        if (task.state == StoredDownloadTask.STATE_COMPLETED) return

        val request = buildDownloadWork(id)
        downloadStore.updateWorkId(
            id = id,
            workId = request.id.toString(),
            status = if (task.progress > 0f) "Resuming from saved segments…" else "Queued",
        )
        workManager.enqueueUniqueWork(
            "$DOWNLOAD_QUEUE_NAME:$id",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        syncDownloads()
    }

    fun openAbout() {
        aboutActive = true
    }

    fun closeAbout() {
        aboutActive = false
    }

    fun removeDownload(id: String) {
        downloadStore.get(id)?.workId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { workManager.cancelWorkById(UUID.fromString(it)) } }
        downloadStore.remove(id)
        syncDownloads()
    }

    private fun syncDownloads() {
        val mapped = downloadStore.all().map { task ->
            DownloadUi(
                id = task.id,
                animeTitle = task.animeTitle,
                episode = if (task.episodeNumber == task.episodeNumber.toInt().toDouble()) {
                    task.episodeNumber.toInt().toString()
                } else {
                    task.episodeNumber.toString()
                },
                quality = task.resolvedQuality.takeIf { it > 0 } ?: task.requestedQuality,
                audio = task.resolvedAudio.ifBlank { task.requestedAudio },
                progress = task.progress,
                status = task.status,
                uri = task.uri.takeIf { it.isNotBlank() }?.let(Uri::parse),
                failed = task.state == StoredDownloadTask.STATE_FAILED,
                paused = task.state == StoredDownloadTask.STATE_PAUSED,
            )
        }

        if (downloads.toList() != mapped) {
            downloads.clear()
            downloads.addAll(mapped)
        }
    }

    private fun refreshSessions() {
        sessions = sessionStore.snapshot()
    }

    private fun verificationMessage(kind: VerificationKind): String =
        when (kind) {
            VerificationKind.ANIMEPAHE -> "AnimePahe verification is needed. Tap the browser icon at the top to verify."
        }

    companion object {
        private const val DOWNLOAD_QUEUE_NAME = "pahe_episode_download_queue"
        private const val DOWNLOAD_WORK_TAG = "pahe_episode_download"
    }
}
