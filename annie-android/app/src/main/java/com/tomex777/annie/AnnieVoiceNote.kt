package com.tomex777.annie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * Voice notes use real microphone audio, encoded into AAC/M4A on the device. These are not
 * speech-to-text or text-to-speech. Sidecar peak samples let a saved note show its actual waveform.
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
        val target = File(dir, "voice-${System.currentTimeMillis()}-${System.nanoTime()}.m4a")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(appContext)
            else @Suppress("DEPRECATION") MediaRecorder()
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

    /** Returns the current peak reading; safe if the microphone/recorder has stopped. */
    fun peak(): Float {
        val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return sqrt((amplitude.toFloat() / 32767f).coerceIn(0f, 1f))
            .coerceIn(0.06f, 1f)
    }

    /** Returns the real file and duration only after the MediaRecorder stop has succeeded. */
    fun stop(): Pair<String, Long>? {
        val active = recorder ?: return null
        val target = file
        val duration = System.currentTimeMillis() - startedAtMillis
        recorder = null
        file = null
        val ok = runCatching { active.stop() }.isSuccess
        runCatching { active.release() }
        if (!ok || target == null || duration < MIN_DURATION_MS ||
            !target.exists() || target.length() == 0L) {
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

/** Short, fixed-length peak array stored beside the audio; never embedded in chat text. */
internal object AnnieVoiceWaveform {
    private const val MAX_PEAKS = 48

    fun reduce(peaks: List<Float>): List<Float> {
        if (peaks.isEmpty()) return List(MAX_PEAKS) { 0.09f }
        return List(MAX_PEAKS) { index ->
            val from = index * peaks.size / MAX_PEAKS
            val to = ((index + 1) * peaks.size / MAX_PEAKS).coerceAtLeast(from + 1).coerceAtMost(peaks.size)
            peaks.subList(from.coerceAtMost(peaks.lastIndex), to).maxOrNull()
                ?.coerceIn(0.06f, 1f) ?: 0.09f
        }
    }

    fun save(path: String, peaks: List<Float>) {
        runCatching {
            File("$path.wave").writeText(reduce(peaks).joinToString(",") { "%.3f".format(java.util.Locale.US, it) })
        }
    }

    fun load(path: String): List<Float> = runCatching {
        File("$path.wave").readText().split(",").take(MAX_PEAKS).map { it.toFloat().coerceIn(0.06f, 1f) }
            .takeIf { it.size == MAX_PEAKS } ?: reduce(emptyList())
    }.getOrElse { reduce(emptyList()) }
}

/** Shared player: only one note at once, with observable progress for animated waveforms. */
internal object AnnieVoicePlayer {
    private var player: MediaPlayer? = null
    var playingPath by mutableStateOf<String?>(null)
        private set
    var positionMs by mutableFloatStateOf(0f)
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
            positionMs = 0f
        }.onFailure { runCatching { next.release() } }
    }

    fun seek(path: String, fraction: Float) {
        if (playingPath != path) toggle(path)
        player?.let { p ->
            runCatching {
                p.seekTo((p.duration * fraction.coerceIn(0f, 1f)).toInt())
                positionMs = p.currentPosition.toFloat()
            }
        }
    }

    fun updatePosition() {
        positionMs = runCatching { player?.currentPosition?.toFloat() ?: 0f }.getOrDefault(0f)
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        playingPath = null
        positionMs = 0f
    }
}

internal fun formatVoiceDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun VoiceWaveform(
    samples: List<Float>,
    progress: Float,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
) {
    val peaks = remember(samples) { AnnieVoiceWaveform.reduce(samples) }
    Canvas(
        modifier = modifier.width(162.dp).height(29.dp)
            .then(if (onSeek != null) Modifier.pointerInput(onSeek) {
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            } else Modifier)
            .testTag("voice_waveform")
    ) {
        val step = size.width / peaks.size
        val width = (step * 0.56f).coerceAtLeast(1f)
        peaks.forEachIndexed { i, peak ->
            val barHeight = (size.height * (0.16f + peak * 0.84f)).coerceAtLeast(3f)
            drawRoundRect(
                color = if (i.toFloat() / peaks.size <= progress) Color.White else Color(0xFF80AFD0),
                topLeft = Offset(i * step, (size.height - barHeight) / 2f),
                size = Size(width, barHeight),
                cornerRadius = CornerRadius(width / 2f, width / 2f),
            )
        }
    }
}

@Composable
internal fun VoiceNoteBubble(path: String, durationMs: Long, fromUser: Boolean) {
    val playing = AnnieVoicePlayer.playingPath == path
    val missing = !File(path).exists()
    val samples = remember(path) { AnnieVoiceWaveform.load(path) }
    LaunchedEffect(playing, path) {
        while (AnnieVoicePlayer.playingPath == path) {
            AnnieVoicePlayer.updatePosition()
            delay(120)
        }
    }
    val fraction = (if (playing && durationMs > 0) AnnieVoicePlayer.positionMs / durationMs else 0f)
        .coerceIn(0f, 1f)
    Row(
        Modifier.clip(RoundedCornerShape(22.dp))
            .background(if (fromUser) Color(0xFF168EEA) else Color(0xFF13243A))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("voice_note_bubble"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (playing) AnnieIcons.Close else AnnieIcons.Play,
            contentDescription = if (playing) "Stop voice note" else "Play voice note",
            tint = Color.White,
            modifier = Modifier.size(26.dp).clip(CircleShape)
                .clickable(enabled = !missing) { AnnieVoicePlayer.toggle(path) },
        )
        androidx.compose.foundation.layout.Column {
            if (!missing) VoiceWaveform(
                samples = samples, progress = fraction,
                onSeek = { AnnieVoicePlayer.seek(path, it) }
            )
            Text(
                if (missing) "Voice note unavailable"
                else formatVoiceDuration(if (playing) AnnieVoicePlayer.positionMs.toLong() else durationMs),
                color = Color.White, fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun VoiceRecordingDot() {
    Box(Modifier.padding(2.dp).size(14.dp).clip(CircleShape).background(Color(0xFFFF4D4D)))
}
