from pathlib import Path

ROOT = Path('sora-overlay/app/src/main/java/com/night/sora')
controller_path = ROOT / 'playback/MusicPlaybackController.kt'
repo_path = ROOT / 'data/CoreRepository.kt'
now_path = ROOT / 'ui/screens/NowPlayingScreen.kt'

controller = controller_path.read_text()
repo = repo_path.read_text()
now = now_path.read_text()


def once(text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, found {n}')
    return text.replace(old, new, 1)

repo = once(
    repo,
    '            lastPlayedEpochMs = System.currentTimeMillis(),\n',
    '            lastPlayedEpochMs = if (countPlay && !skipped) System.currentTimeMillis() else current.lastPlayedEpochMs,\n',
    'honest last played timestamp',
)

controller = once(
    controller,
    '''    fun togglePlayPause() {\n        if (currentTrack == null) return\n        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)\n        if (player.isPlaying || player.playWhenReady) player.pause() else player.play()\n    }''',
    '''    fun togglePlayPause() {\n        if (currentTrack == null) return\n        if (player.playbackState == Player.STATE_ENDED) {\n            startedEventTrackKey = null\n            player.seekTo(0L)\n        }\n        if (player.isPlaying || player.playWhenReady) player.pause() else player.play()\n    }''',
    'ended replay reset',
)

controller = once(
    controller,
    '''    fun playQueueIndex(index: Int) {\n        if (index !in queue.indices) return''',
    '''    fun selectQueueIndex(index: Int) {\n        if (index !in queue.indices || index == currentIndex) return\n        emitSkipIfStarted(currentTrack)\n        playQueueIndex(index)\n    }\n\n    fun playQueueIndex(index: Int) {\n        if (index !in queue.indices) return''',
    'explicit queue selection API',
)

now = once(
    now,
    '''                                player.playQueueIndex(index)\n                                queueOpen = false''',
    '''                                player.selectQueueIndex(index)\n                                queueOpen = false''',
    'queue UI selection',
)

repo_path.write_text(repo)
controller_path.write_text(controller)
now_path.write_text(now)
print('Hardened Sora Music recency, queue skip, and ended replay events.')
