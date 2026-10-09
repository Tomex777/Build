package com.tomex777.annie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Voice notes are real recorded audio (AAC in .m4a) sent as a chat message.
 * They are deliberately separate from speech-to-text (system recognizer) and TTS ([AnnieTts]).
 */
internal class AnnieVoiceRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAtMillis = 0L

    val isRecording: Boolean get() = recorder != null

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        if (recorder != null || !hasPermission()) return false
        val dir = File(appContext.filesDir, VOICE_DIR).apply { mkdirs() }
        val target = File(dir, "voice-${System.currentTimeMillis()}.m4a")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(appContext) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(64_000)
            next.setAudioSamplingRate(44_100)
            next.setMaxDuration(MAX_DURATION_MS)
            next.setOutputFile(target.absolutePath)
            next.prepare()
            next.start()
            recorder = next
            file = target
            startedAtMillis = System.currentTimeMillis()
            true
        }.getOrElse {
            runCatching { next.release() }
            target.delete()
            false
        }
    }

    /** Returns the saved file path and duration, or null when too short / failed (file is removed). */
    fun stop(): Pair<String, Long>? {
        val active = recorder ?: return null
        val target = file
        val duration = System.currentTimeMillis() - startedAtMillis
        recorder = null
        file = null
        val ok = runCatching { active.stop() }.isSuccess
        runCatching { active.release() }
        if (!ok || target == null || duration < MIN_DURATION_MS || !target.exists() || target.length() == 0L) {
            target?.delete()
            return null
        }
        return target.absolutePath to duration
    }

    fun cancel() {
        val active = recorder ?: return
        recorder = null
        runCatching { active.stop() }
        runCatching { active.release() }
        file?.delete()
        file = null
    }

    companion object {
        const val VOICE_DIR = "voice_notes"
        const val MIN_DURATION_MS = 700L
        const val MAX_DURATION_MS = 5 * 60_000
    }
}

/** One voice note plays at a time; the playing path is observable so bubbles update. */
internal object AnnieVoicePlayer {
    private var player: MediaPlayer? = null
    var playingPath by mutableStateOf<String?>(null)
        private set

    fun toggle(path: String) {
        if (playingPath == path) { stop(); return }
        stop()
        if (!File(path).exists()) return
        val next = MediaPlayer()
        runCatching {
            next.setDataSource(path)
            next.setOnCompletionListener { stop() }
            next.setOnErrorListener { _, _, _ -> stop(); true }
            next.prepare()
            next.start()
            player = next
            playingPath = path
        }.onFailure { runCatching { next.release() } }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        playingPath = null
    }
}

internal fun formatVoiceDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun VoiceNoteBubble(path: String, durationMs: Long, fromUser: Boolean) {
    val playing = AnnieVoicePlayer.playingPath == path
    val missing = !File(path).exists()
    Row(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (fromUser) Color(0xFF168EEA) else Color(0xFF13243A))
            .clickable(enabled = !missing) { AnnieVoicePlayer.toggle(path) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("voice_note_bubble"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (playing) AnnieIcons.Close else AnnieIcons.Play,
            contentDescription = if (playing) "Stop voice note" else "Play voice note",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Text(
            if (missing) "Voice note unavailable" else "Voice note · ${formatVoiceDuration(durationMs)}",
            color = Color.White, fontSize = 14.sp,
        )
    }
}

@Composable
internal fun VoiceRecordingDot() {
    androidx.compose.foundation.layout.Box(
        Modifier.padding(2.dp).size(14.dp).clip(CircleShape).background(Color(0xFFFF4D4D)),
    )
}
