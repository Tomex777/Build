from pathlib import Path

ROOT = Path('sora-overlay/app/src/main/java/com/night/sora')
controller_path = ROOT / 'playback/MusicPlaybackController.kt'
repo_path = ROOT / 'data/CoreRepository.kt'
app_path = ROOT / 'ui/SoraApp.kt'

controller = controller_path.read_text()
repo = repo_path.read_text()
app = app_path.read_text()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)


controller = replace_once(
    controller,
    'enum class MusicRepeatMode { OFF, ALL, ONE }\n',
    '''enum class MusicRepeatMode { OFF, ALL, ONE }\n\nenum class MusicListeningEventType { STARTED, COMPLETED, SKIPPED }\n\ndata class MusicListeningEvent(\n    val serial: Long,\n    val track: ExtensionMediaSelection,\n    val type: MusicListeningEventType,\n)\n''',
    'listening event types',
)

controller = replace_once(
    controller,
    '    private var requestSerial = 0L\n',
    '    private var requestSerial = 0L\n    private var listeningEventSerial = 0L\n    private var startedEventTrackKey: String? = null\n',
    'event state fields',
)

controller = replace_once(
    controller,
    '    var errorMessage by mutableStateOf<String?>(null)\n        private set\n',
    '''    var errorMessage by mutableStateOf<String?>(null)\n        private set\n    var listeningEvent by mutableStateOf<MusicListeningEvent?>(null)\n        private set\n''',
    'public listening event state',
)

controller = replace_once(
    controller,
    '''            override fun onIsPlayingChanged(playing: Boolean) {\n                isPlaying = playing\n            }''',
    '''            override fun onIsPlayingChanged(playing: Boolean) {\n                isPlaying = playing\n                if (playing) {\n                    currentTrack?.let { track ->\n                        val key = track.identityKey()\n                        if (startedEventTrackKey != key) {\n                            startedEventTrackKey = key\n                            emitListeningEvent(track, MusicListeningEventType.STARTED)\n                        }\n                    }\n                }\n            }''',
    'started playback event',
)

controller = replace_once(
    controller,
    '''        currentIndex = index\n        val track = queue[index]\n        currentTrack = track\n        positionMs = 0L''',
    '''        currentIndex = index\n        val track = queue[index]\n        currentTrack = track\n        startedEventTrackKey = null\n        positionMs = 0L''',
    'reset start event per queue item',
)

controller = replace_once(
    controller,
    '''        if (next >= 0) playQueueIndex(next)\n    }\n\n    fun skipPrevious()''',
    '''        if (next >= 0) {\n            if (next != currentIndex) emitSkipIfStarted(currentTrack)\n            playQueueIndex(next)\n        }\n    }\n\n    fun skipPrevious()''',
    'next skip event',
)

controller = replace_once(
    controller,
    '''        if (previous >= 0) playQueueIndex(previous)\n    }\n\n    fun seekToFraction''',
    '''        if (previous >= 0) {\n            if (previous != currentIndex) emitSkipIfStarted(currentTrack)\n            playQueueIndex(previous)\n        }\n    }\n\n    fun seekToFraction''',
    'previous skip event',
)

controller = replace_once(
    controller,
    '''    private fun handleEnded() {\n        when (repeatMode) {''',
    '''    private fun handleEnded() {\n        currentTrack?.let { track ->\n            if (startedEventTrackKey == track.identityKey()) {\n                emitListeningEvent(track, MusicListeningEventType.COMPLETED)\n            }\n        }\n        when (repeatMode) {''',
    'completion event',
)

controller = replace_once(
    controller,
    '''            MusicRepeatMode.ONE -> {\n                player.seekTo(0L)\n                player.play()\n            }''',
    '''            MusicRepeatMode.ONE -> {\n                startedEventTrackKey = null\n                player.seekTo(0L)\n                player.play()\n            }''',
    'repeat one start reset',
)

controller = replace_once(
    controller,
    '''    private fun parseStreams(raw: String): List<PlaybackStream> = runCatching {''',
    '''    private fun emitSkipIfStarted(track: ExtensionMediaSelection?) {\n        if (track == null) return\n        if (startedEventTrackKey != track.identityKey()) return\n        emitListeningEvent(track, MusicListeningEventType.SKIPPED)\n    }\n\n    private fun emitListeningEvent(track: ExtensionMediaSelection, type: MusicListeningEventType) {\n        listeningEvent = MusicListeningEvent(\n            serial = ++listeningEventSerial,\n            track = track,\n            type = type,\n        )\n    }\n\n    private fun parseStreams(raw: String): List<PlaybackStream> = runCatching {''',
    'event helpers',
)

repo = replace_once(
    repo,
    '''        saved: Boolean? = null,\n    ) {''',
    '''        saved: Boolean? = null,\n        countPlay: Boolean = true,\n    ) {''',
    'recordListening countPlay parameter',
)

repo = replace_once(
    repo,
    '            plays = current.plays + if (!skipped) 1 else 0,\n',
    '            plays = current.plays + if (countPlay && !skipped) 1 else 0,\n',
    'play count condition',
)

repo = replace_once(
    repo,
    '''        persistLibrary()\n    }\n\n    fun recordListening(''',
    '''        persistLibrary()\n        if (selection.type == ContentType.MUSIC) {\n            val artistName = selection.subtitle.substringBefore(" · ").ifBlank { selection.title }\n            recordListening(\n                artistId = artistName.trim().lowercase(),\n                artistName = artistName,\n                saved = index < 0,\n                countPlay = false,\n            )\n        }\n    }\n\n    fun recordListening(''',
    'music save signal',
)

app = replace_once(
    app,
    'import com.night.sora.playback.MusicPlaybackController\n',
    'import com.night.sora.playback.MusicListeningEventType\nimport com.night.sora.playback.MusicPlaybackController\n',
    'listening event import',
)

old_effect = '''    LaunchedEffect(\n        musicPlayer.currentTrack?.extensionPackage,\n        musicPlayer.currentTrack?.sourceId,\n        musicPlayer.currentTrack?.id,\n    ) {\n        musicPlayer.currentTrack?.let { track ->\n            repository.recordActivity(track, "played")\n            val artistName = track.subtitle.substringBefore(" · ").ifBlank { track.title }\n            repository.recordListening(artistName.trim().lowercase(), artistName)\n        }\n    }'''
new_effect = '''    LaunchedEffect(musicPlayer.listeningEvent?.serial) {\n        val event = musicPlayer.listeningEvent ?: return@LaunchedEffect\n        val track = event.track\n        val artistName = track.subtitle.substringBefore(" · ").ifBlank { track.title }\n        val artistId = artistName.trim().lowercase()\n        when (event.type) {\n            MusicListeningEventType.STARTED -> {\n                repository.recordActivity(track, "played")\n                repository.recordListening(artistId, artistName)\n            }\n            MusicListeningEventType.COMPLETED -> repository.recordListening(\n                artistId = artistId,\n                artistName = artistName,\n                completed = true,\n                countPlay = false,\n            )\n            MusicListeningEventType.SKIPPED -> repository.recordListening(\n                artistId = artistId,\n                artistName = artistName,\n                skipped = true,\n                countPlay = false,\n            )\n        }\n    }'''
app = replace_once(app, old_effect, new_effect, 'SoraApp listening event bridge')

controller_path.write_text(controller)
repo_path.write_text(repo)
app_path.write_text(app)
print('Patched real Music STARTED/COMPLETED/SKIPPED/save signals.')
