from pathlib import Path

path = Path("sora-overlay/app/src/main/java/com/night/sora/playback/MusicPlaybackController.kt")
text = path.read_text()

old_import = "import android.net.Uri\n"
new_import = "import android.net.Uri\nimport android.os.SystemClock\n"
if text.count(old_import) != 1:
    raise SystemExit("SystemClock import marker mismatch")
text = text.replace(old_import, new_import, 1)

old_field = "    private var startedEventTrackKey: String? = null\n"
new_field = "    private var startedEventTrackKey: String? = null\n    private var previousRestartRequestedAtMs = Long.MIN_VALUE\n"
if text.count(old_field) != 1:
    raise SystemExit("previous restart field marker mismatch")
text = text.replace(old_field, new_field, 1)

old_queue = "        startedEventTrackKey = null\n        positionMs = 0L\n"
new_queue = "        startedEventTrackKey = null\n        previousRestartRequestedAtMs = Long.MIN_VALUE\n        positionMs = 0L\n"
if text.count(old_queue) != 1:
    raise SystemExit("playQueueIndex marker mismatch")
text = text.replace(old_queue, new_queue, 1)

old_previous = '''    fun skipPrevious() {
        if (queue.isEmpty()) return
        if (player.currentPosition > 3_000L) {
            player.seekTo(0L)
            return
        }
        val previous = when {
            currentIndex > 0 -> currentIndex - 1
            repeatMode == MusicRepeatMode.ALL -> queue.lastIndex
            else -> 0
        }
        if (previous != currentIndex) emitSkipIfStarted(currentTrack)
        playQueueIndex(previous)
    }
'''
new_previous = '''    fun skipPrevious() {
        if (queue.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val restartSeekStillSettling = previousRestartRequestedAtMs != Long.MIN_VALUE &&
            now - previousRestartRequestedAtMs in 0L..1_000L
        if (!restartSeekStillSettling && player.currentPosition > 3_000L) {
            previousRestartRequestedAtMs = now
            positionMs = 0L
            player.seekTo(0L)
            return
        }
        previousRestartRequestedAtMs = Long.MIN_VALUE
        val previous = when {
            currentIndex > 0 -> currentIndex - 1
            repeatMode == MusicRepeatMode.ALL -> queue.lastIndex
            else -> 0
        }
        if (previous != currentIndex) emitSkipIfStarted(currentTrack)
        playQueueIndex(previous)
    }
'''
if text.count(old_previous) != 1:
    raise SystemExit("skipPrevious marker mismatch")
text = text.replace(old_previous, new_previous, 1)

path.write_text(text)
