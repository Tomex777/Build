@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nami.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.nami.data.local.NamiDatabase
import app.nami.domain.AnimeEpisode
import app.nami.domain.MediaTrack
import app.nami.domain.ResolvedMedia
import app.nami.domain.isPlayableMediaLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

private enum class PlayerSheet { QUALITY, SUBTITLES, AUDIO, SPEED }

@Composable
internal fun NamiPlayerScreen(
    session: NamiPlaybackSession,
    database: NamiDatabase,
    persistWatchActivity: Boolean = true,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = remember(context.applicationContext) { NamiVlcPlayer(context.applicationContext) }
    val playerState by engine.state.collectAsState()

    val itemCount = when (session) {
        is NamiPlaybackSession.Streaming -> session.episodes.size
        is NamiPlaybackSession.Downloaded -> session.items.size
    }
    var currentIndex by remember(session) {
        mutableIntStateOf(
            when (session) {
                is NamiPlaybackSession.Streaming -> session.initialEpisodeIndex
                is NamiPlaybackSession.Downloaded -> session.initialIndex
            }.coerceIn(0, (itemCount - 1).coerceAtLeast(0)),
        )
    }
    var resolved by remember(session) { mutableStateOf<List<ResolvedMedia>>(emptyList()) }
    var selectedMedia by remember(session) { mutableStateOf<ResolvedMedia?>(null) }
    var loading by remember(session) { mutableStateOf(true) }
    var resolveError by remember(session) { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionVersion by remember { mutableLongStateOf(0L) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var preferredHeight by remember { mutableStateOf<Int?>(null) }
    var preferredHost by remember { mutableStateOf<String?>(null) }
    var landscape by remember { mutableStateOf(false) }
    var resolveVersion by remember { mutableIntStateOf(0) }
    var pendingResumePositionMs by remember { mutableLongStateOf(-1L) }
    var resumeAfterBackground by remember { mutableStateOf(false) }
    var activeExternalSubtitle by remember(session) { mutableStateOf<String?>(null) }
    var videoSurfaceReady by remember(session) { mutableStateOf(false) }

    fun currentEpisode(): AnimeEpisode? = when (session) {
        is NamiPlaybackSession.Streaming -> session.episodes.getOrNull(currentIndex)
        is NamiPlaybackSession.Downloaded -> null
    }

    fun animeTitle(): String = when (session) {
        is NamiPlaybackSession.Streaming -> session.anime.title
        is NamiPlaybackSession.Downloaded -> session.items.getOrNull(currentIndex)?.animeTitle.orEmpty()
    }

    fun episodeTitle(): String = when (session) {
        is NamiPlaybackSession.Streaming -> currentEpisode()?.title.orEmpty()
        is NamiPlaybackSession.Downloaded -> session.items.getOrNull(currentIndex)?.episodeTitle.orEmpty()
    }

    fun saveProgress(
        positionMs: Long = playerState.positionMs,
        durationMs: Long = playerState.durationMs,
    ) {
        // Incognito is behavior, not presentation: when it is enabled Nami must not
        // write new watch/history activity at all.
        if (!persistWatchActivity) return
        val snapshot = when (session) {
            is NamiPlaybackSession.Streaming -> {
                val episode = currentEpisode() ?: return
                WatchProgressSnapshot(
                    sourceId = episode.ref.sourceId,
                    sourceAnimeId = episode.ref.sourceAnimeId,
                    sourceEpisodeId = episode.ref.sourceEpisodeId,
                    animeTitle = session.anime.title,
                    episodeTitle = episode.title,
                    animeSourceState = session.anime.sourceState,
                    episodeSourceState = episode.sourceState,
                )
            }
            is NamiPlaybackSession.Downloaded -> {
                val item = session.items.getOrNull(currentIndex) ?: return
                WatchProgressSnapshot(
                    sourceId = item.sourceId,
                    sourceAnimeId = item.sourceAnimeId,
                    sourceEpisodeId = item.sourceEpisodeId,
                    animeTitle = item.animeTitle,
                    episodeTitle = item.episodeTitle,
                    animeSourceState = item.animeSourceState,
                    episodeSourceState = item.episodeSourceState,
                )
            }
        }
        database.upsertWatchProgress(
            sourceId = snapshot.sourceId,
            sourceAnimeId = snapshot.sourceAnimeId,
            sourceEpisodeId = snapshot.sourceEpisodeId,
            animeTitle = snapshot.animeTitle,
            episodeTitle = snapshot.episodeTitle,
            animeSourceState = snapshot.animeSourceState,
            episodeSourceState = snapshot.episodeSourceState,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            completed = isCompleted(positionMs, durationMs),
        )
    }

    fun exitPlayer() {
        saveProgress()
        onBack()
    }

    BackHandler { exitPlayer() }

    DisposableEffect(activity) {
        activity?.let { host ->
            WindowCompat.setDecorFitsSystemWindows(host.window, false)
            WindowCompat.getInsetsController(host.window, host.window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.let { host ->
                WindowCompat.getInsetsController(host.window, host.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(host.window, true)
            }
            engine.release()
        }
    }

    LaunchedEffect(activity, controlsVisible) {
        activity?.let { host ->
            val controller = WindowCompat.getInsetsController(host.window, host.window.decorView)
            if (controlsVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner, currentIndex) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val state = engine.state.value
                    resumeAfterBackground = state.isPlaying
                    saveProgress(state.positionMs, state.durationMs)
                    engine.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (resumeAfterBackground) {
                        engine.resume()
                        resumeAfterBackground = false
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        currentIndex,
        preferredHeight,
        preferredHost,
        resolveVersion,
        videoSurfaceReady,
    ) {
        if (!videoSurfaceReady) return@LaunchedEffect
        if (itemCount == 0) {
            loading = false
            resolveError = "No episode is available to play."
            return@LaunchedEffect
        }
        loading = true
        resolveError = null
        resolved = emptyList()
        selectedMedia = null
        activeExternalSubtitle = null

        try {
            val (candidates, chosen, position) = when (session) {
                is NamiPlaybackSession.Streaming -> {
                    val episode = session.episodes[currentIndex]
                    val progress = withContext(Dispatchers.IO) {
                        database.getWatchProgress(episode.ref.sourceId, episode.ref.sourceEpisodeId)
                    }
                    val candidates = withTimeout(SOURCE_RESOLVE_TIMEOUT_MILLIS) {
                        session.source.resolve(episode.ref, episode.sourceState)
                    }.filter { it.url.isPlayableMediaLocation() }
                    val chosen = PlaybackMediaSelector.choose(
                        media = candidates,
                        preferredHeight = preferredHeight,
                        preferredHost = preferredHost,
                    ) ?: error("This source did not return a playable stream.")
                    Triple(
                        candidates,
                        chosen,
                        pendingResumePositionMs.takeIf { it >= 0L }
                            ?: progress?.let {
                                resumablePositionOrNull(
                                    positionMs = it.positionMs,
                                    durationMs = it.durationMs,
                                    completed = it.completed,
                                )
                            }
                            ?: 0L,
                    )
                }
                is NamiPlaybackSession.Downloaded -> {
                    val item = session.items[currentIndex]
                    val uri = item.contentUri?.takeIf { it.isNotBlank() }
                        ?: error("The downloaded file is no longer available.")
                    val progress = withContext(Dispatchers.IO) {
                        database.getWatchProgress(item.sourceId, item.sourceEpisodeId)
                    }
                    val local = ResolvedMedia(
                        url = uri,
                        mimeType = item.mimeType,
                        quality = "Offline",
                    )
                    Triple(
                        listOf(local),
                        local,
                        pendingResumePositionMs.takeIf { it >= 0L }
                            ?: progress?.let {
                                resumablePositionOrNull(
                                    positionMs = it.positionMs,
                                    durationMs = it.durationMs,
                                    completed = it.completed,
                                )
                            }
                            ?: 0L,
                    )
                }
            }
            resolved = candidates
            selectedMedia = chosen
            engine.play(chosen, position)
            pendingResumePositionMs = -1L
            loading = false
        } catch (timeout: TimeoutCancellationException) {
            logSourceFailure("player resolve timeout", timeout)
            resolveError = sourceFailureMessage(
                timeout,
                fallback = "Could not resolve this episode. Try another source or retry.",
            )
            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logSourceFailure("player resolve", failure)
            resolveError = sourceFailureMessage(
                failure,
                fallback = "Could not resolve this episode. Try another source or retry.",
            )
            loading = false
        }
    }

    LaunchedEffect(playerState.isPlaying) {
        // A stream can succeed after an earlier candidate/resolve failure. Once VLC is
        // genuinely playing, that old source error is no longer terminal and must not
        // remain painted over the video.
        if (playerState.isPlaying) {
            resolveError = null
        }
    }

    LaunchedEffect(playerState.positionMs / 5_000L) {
        if (playerState.positionMs > 0L) saveProgress()
    }

    LaunchedEffect(playerState.ended) {
        if (playerState.ended) saveProgress(playerState.durationMs, playerState.durationMs)
    }

    LaunchedEffect(controlsVisible, playerState.isPlaying, interactionVersion) {
        if (controlsVisible && playerState.isPlaying) {
            delay(3_500)
            controlsVisible = false
        }
    }

    val externalSubtitleChoices = resolved
        .flatMap { media ->
            media.subtitles.map { track -> Triple(track, media.headers, media.url) }
        }
        .distinctBy { (track) -> track.url }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    controlsVisible = !controlsVisible
                    interactionVersion++
                }
                .semantics {
                    contentDescription = if (playerState.videoOutputCount > 0) {
                        "Nami player video output active"
                    } else {
                        "Nami player"
                    }
                },
        ) {
            AndroidView(
                factory = {
                    TextureView(it).also { view ->
                        engine.attach(view)
                        videoSurfaceReady = true
                    }
                },
                update = {
                    engine.attach(it)
                    if (!videoSurfaceReady) videoSurfaceReady = true
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (loading || playerState.isBuffering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            val error = playerState.error ?: resolveError?.takeUnless { playerState.isPlaying }
            if (error != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(error, color = Color.White)
                    Button(onClick = {
                        val position = playerState.positionMs
                        saveProgress(position, playerState.durationMs)
                        when (session) {
                            is NamiPlaybackSession.Streaming -> {
                                // Remote stream URLs are ephemeral. A retry must ask the source
                                // for a fresh candidate instead of looping on a dead resolved URL.
                                pendingResumePositionMs = position
                                resolveError = null
                                resolveVersion++
                            }
                            is NamiPlaybackSession.Downloaded -> {
                                selectedMedia?.let { media ->
                                    engine.play(media, position)
                                } ?: run {
                                    resolveVersion++
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Replay, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Retry")
                    }
                    PlaybackMediaSelector.nextPlayable(resolved, selectedMedia)?.let { fallback ->
                        TextButton(onClick = {
                            val position = playerState.positionMs
                            saveProgress(position, playerState.durationMs)
                            selectedMedia = fallback
                            resolveError = null
                            engine.play(fallback, position)
                        }) {
                            Text("Try another stream · ${PlaybackMediaSelector.label(fallback)}")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.fillMaxSize(),
            ) {
                PlayerControls(
                    animeTitle = animeTitle(),
                    episodeTitle = episodeTitle(),
                    state = playerState,
                    canPrevious = previousIndex(session, currentIndex) != null,
                    canNext = nextIndex(session, currentIndex) != null,
                    qualityLabel = selectedMedia?.let(PlaybackMediaSelector::label).orEmpty(),
                    onBack = ::exitPlayer,
                    onToggle = {
                        engine.togglePlayPause()
                        interactionVersion++
                    },
                    onSeekBack = {
                        engine.seekBy(-10_000)
                        interactionVersion++
                    },
                    onSeekForward = {
                        engine.seekBy(10_000)
                        interactionVersion++
                    },
                    onSeekFraction = { fraction ->
                        val duration = playerState.durationMs
                        if (duration > 0L) {
                            engine.seekTo((duration * fraction).roundToLong())
                        }
                        interactionVersion++
                    },
                    onPrevious = {
                        previousIndex(session, currentIndex)?.let { next ->
                            saveProgress()
                            currentIndex = next
                            preferredHeight = null
                            preferredHost = null
                            pendingResumePositionMs = -1L
                        }
                    },
                    onNext = {
                        nextIndex(session, currentIndex)?.let { next ->
                            saveProgress()
                            currentIndex = next
                            preferredHeight = null
                            preferredHost = null
                            pendingResumePositionMs = -1L
                        }
                    },
                    onQuality = { sheet = PlayerSheet.QUALITY },
                    subtitlesActive = activeExternalSubtitle != null ||
                        playerState.selectedSubtitleTrack >= 0,
                    onSubtitles = { sheet = PlayerSheet.SUBTITLES },
                    onAudio = { sheet = PlayerSheet.AUDIO },
                    onSpeed = { sheet = PlayerSheet.SPEED },
                    onFullscreen = {
                        landscape = !landscape
                        activity?.requestedOrientation = if (landscape) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    },
                    onOpenExternal = {
                        selectedMedia?.let {
                            runCatching { ExternalPlayerLauncher.open(context, it) }
                        }
                    },
                )
            }
        }
    }

    when (sheet) {
        PlayerSheet.QUALITY -> ChoiceSheet(
            title = "Quality",
            onDismiss = { sheet = null },
        ) {
            ChoiceRow("Auto", preferredHeight == null && preferredHost == null) {
                pendingResumePositionMs = playerState.positionMs
                saveProgress()
                preferredHeight = null
                preferredHost = null
                sheet = null
                resolveVersion++
            }
            resolved.distinctBy { it.url }.forEach { media ->
                ChoiceRow(
                    PlaybackMediaSelector.label(media),
                    selected = media.url == selectedMedia?.url,
                ) {
                    pendingResumePositionMs = playerState.positionMs
                    saveProgress()
                    preferredHeight = PlaybackMediaSelector.height(media)
                    preferredHost = media.hosterName
                    sheet = null
                    resolveVersion++
                }
            }
        }
        PlayerSheet.SUBTITLES -> ChoiceSheet(
            title = "Subtitles",
            onDismiss = { sheet = null },
        ) {
            ChoiceRow(
                label = "Off",
                selected = playerState.selectedSubtitleTrack < 0 && activeExternalSubtitle == null,
                modifier = Modifier.testTag("subtitle-off-option"),
            ) {
                engine.selectSubtitleTrack(-1)
                activeExternalSubtitle = null
                sheet = null
            }
            externalSubtitleChoices.forEach { (track, headers, _) ->
                val label = track.displayName("Subtitle")
                ChoiceRow(
                    label = label,
                    selected = label == activeExternalSubtitle,
                    modifier = Modifier.testTag("subtitle-external-option"),
                ) {
                    if (engine.addExternalSubtitle(
                            track.url,
                            headers,
                        )
                    ) {
                        activeExternalSubtitle = label
                        sheet = null
                    }
                }
            }
            playerState.subtitleTracks.filter { it.id >= 0 }.forEach { track ->
                ChoiceRow(
                    label = track.name,
                    selected = track.id == playerState.selectedSubtitleTrack &&
                        activeExternalSubtitle == null,
                    modifier = Modifier.testTag("subtitle-vlc-option"),
                ) {
                    engine.selectSubtitleTrack(track.id)
                    activeExternalSubtitle = null
                    sheet = null
                }
            }
        }
        PlayerSheet.AUDIO -> ChoiceSheet(
            title = "Audio",
            onDismiss = { sheet = null },
        ) {
            selectedMedia?.audioTracks.orEmpty().forEach { track ->
                ChoiceRow(track.displayName("Audio"), false) {
                    engine.addExternalAudio(
                        track.url,
                        selectedMedia?.headers.orEmpty(),
                    )
                    sheet = null
                }
            }
            playerState.audioTracks.filter { it.id >= 0 }.forEach { track ->
                ChoiceRow(track.name, track.id == playerState.selectedAudioTrack) {
                    engine.selectAudioTrack(track.id)
                    sheet = null
                }
            }
        }
        PlayerSheet.SPEED -> ChoiceSheet(
            title = "Playback speed",
            onDismiss = { sheet = null },
        ) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                ChoiceRow(rate.toString() + "×", rate == playerState.rate) {
                    engine.setRate(rate)
                    sheet = null
                }
            }
        }
        null -> Unit
    }
}

private const val SOURCE_RESOLVE_TIMEOUT_MILLIS = 90_000L

@Composable
private fun PlayerControls(
    animeTitle: String,
    episodeTitle: String,
    state: NamiVlcState,
    canPrevious: Boolean,
    canNext: Boolean,
    qualityLabel: String,
    subtitlesActive: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onQuality: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onSpeed: () -> Unit,
    onFullscreen: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f)),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    animeTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    episodeTitle,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onOpenExternal) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open externally", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = canPrevious,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    "Previous episode",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(
                onClick = onSeekBack,
                enabled = state.seekable,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.FastRewind,
                    "Seek back 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Surface(
                onClick = onToggle,
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.size(54.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            IconButton(
                onClick = onSeekForward,
                enabled = state.seekable,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.FastForward,
                    "Seek forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(
                onClick = onNext,
                enabled = canNext,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    "Next episode",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatDuration(state.positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
                VlcSeekBar(
                    value = if (state.durationMs > 0L) {
                        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    enabled = state.seekable && state.durationMs > 0L,
                    onSeekFraction = onSeekFraction,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    formatDuration(state.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAction(
                    icon = Icons.Outlined.VideoSettings,
                    label = qualityLabel.ifBlank { "Quality" },
                    onClick = onQuality,
                    modifier = Modifier.weight(1f),
                )
                PlayerAction(
                    icon = Icons.Outlined.Subtitles,
                    label = "Subtitles",
                    onClick = onSubtitles,
                    contentDescription = if (subtitlesActive) "Subtitles active" else "Subtitles",
                    modifier = Modifier.weight(1f),
                )
                PlayerAction(
                    icon = Icons.Outlined.Audiotrack,
                    label = "Audio",
                    onClick = onAudio,
                    modifier = Modifier.weight(1f),
                )
                PlayerAction(
                    icon = Icons.Outlined.Speed,
                    label = state.rate.toString() + "×",
                    onClick = onSpeed,
                    modifier = Modifier.weight(1f),
                )
                PlayerAction(
                    icon = Icons.Outlined.Fullscreen,
                    label = "Full",
                    onClick = onFullscreen,
                    contentDescription = "Fullscreen",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VlcSeekBar(
    value: Float,
    enabled: Boolean,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = value.coerceIn(0f, 1f)
    val trackColor = Color.White.copy(alpha = if (enabled) 0.42f else 0.24f)
    val progressColor = MaterialTheme.colorScheme.primary
    val thumbColor = Color.White

    Canvas(
        modifier = modifier
            .height(28.dp)
            .testTag("vlc-seek-bar")
            .semantics {
                contentDescription = "Playback position"
            }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(onSeekFraction) {
                            detectTapGestures { offset ->
                                if (size.width > 0) {
                                    onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                                }
                            }
                        }
                        .pointerInput(onSeekFraction) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (size.width > 0) {
                                        onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (size.width > 0) {
                                        onSeekFraction(
                                            (change.position.x / size.width).coerceIn(0f, 1f),
                                        )
                                    }
                                    change.consume()
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * progress
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(size.width, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (progressX > 0f) {
            drawLine(
                color = progressColor,
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(progressX, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = thumbColor,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(progressX, centerY),
        )
    }
}

@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    contentDescription: String = label,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .semantics {
                this.contentDescription = contentDescription
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChoiceSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        content()
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            if (selected) {
                Text("Selected", color = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}

private fun previousIndex(session: NamiPlaybackSession, index: Int): Int? = when (session) {
    is NamiPlaybackSession.Streaming ->
        PlaybackEpisodeNavigator.previousIndex(session.episodes, index)
    is NamiPlaybackSession.Downloaded ->
        (index - 1).takeIf { it in session.items.indices }
}

private fun nextIndex(session: NamiPlaybackSession, index: Int): Int? = when (session) {
    is NamiPlaybackSession.Streaming ->
        PlaybackEpisodeNavigator.nextIndex(session.episodes, index)
    is NamiPlaybackSession.Downloaded ->
        (index + 1).takeIf { it in session.items.indices }
}

private fun MediaTrack.displayName(fallback: String): String =
    language?.takeIf { it.isNotBlank() } ?: fallback

internal fun resumablePositionOrNull(
    positionMs: Long,
    durationMs: Long,
    completed: Boolean,
): Long? {
    if (completed || durationMs <= 0L) return null
    if (positionMs < 5_000L || positionMs >= durationMs) return null
    if (isCompleted(positionMs, durationMs)) return null
    return positionMs
}

internal fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
    if (durationMs <= 0L || positionMs <= 0L) return false
    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
    val reachedCompletionRatio = positionMs >= (durationMs * 0.92).roundToLong()
    val nearEndOfLongFormVideo = durationMs >= 10 * 60_000L && remaining <= 90_000L
    return reachedCompletionRatio || nearEndOfLongFormVideo
}

private fun formatDuration(valueMs: Long): String {
    val seconds = valueMs.coerceAtLeast(0L) / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val secs = seconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class WatchProgressSnapshot(
    val sourceId: String,
    val sourceAnimeId: String,
    val sourceEpisodeId: String,
    val animeTitle: String?,
    val episodeTitle: String?,
    val animeSourceState: String?,
    val episodeSourceState: String?,
)
