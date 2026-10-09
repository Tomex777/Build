package com.tomex777.annie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * WhatsApp-style voice note control: hold to record, release to send, slide left to cancel,
 * slide up to lock (hands-free) and then use the Delete / Send buttons.
 */
internal class VoiceNoteController(
    context: Context,
    private val onVoiceNote: (String, Long) -> Unit,
) {
    private val recorder = AnnieVoiceRecorder(context)
    private val appContext = context.applicationContext
    var recording by mutableStateOf(false)
        private set
    var locked by mutableStateOf(false)
        internal set
    var cancelling by mutableStateOf(false)
        internal set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    private var startedAt = 0L
    val waveformPeaks = mutableStateListOf<Float>()

    fun hasPermission() = recorder.hasPermission()

    fun begin(): Boolean {
        if (recording) return false
        if (!recorder.start()) {
            Toast.makeText(appContext, "Couldn\u0027t start recording. Check microphone access.", Toast.LENGTH_SHORT).show()
            return false
        }
        AnnieVoicePlayer.stop()
        startedAt = System.currentTimeMillis()
        elapsedMs = 0L
        waveformPeaks.clear()
        locked = false
        cancelling = false
        recording = true
        return true
    }

    fun tick() {
        if (!recording) return
        elapsedMs = System.currentTimeMillis() - startedAt
        waveformPeaks.add(recorder.peak())
        if (waveformPeaks.size > 600) waveformPeaks.removeAt(0)
        if (elapsedMs >= AnnieVoiceRecorder.MAX_DURATION_MS) finish()
    }

    fun finish() {
        if (!recording) return
        reset()
        val recording = recorder.stop()
        if (recording == null) {
            Toast.makeText(appContext, "Hold the microphone for at least a second.", Toast.LENGTH_SHORT).show()
        } else {
            val (path, duration) = recording
            AnnieVoiceWaveform.save(path, waveformPeaks.toList())
            onVoiceNote(path, duration)
        }
    }

    fun cancel() {
        if (!recording) return
        reset()
        recorder.cancel()
    }

    private fun reset() { recording = false; locked = false; cancelling = false }
}

@Composable
internal fun rememberVoiceNoteController(onVoiceNote: (String, Long) -> Unit): VoiceNoteController {
    val context = androidx.compose.ui.platform.LocalContext.current
    val latest = androidx.compose.runtime.rememberUpdatedState(onVoiceNote)
    val controller = remember(context) { VoiceNoteController(context) { path, ms -> latest.value(path, ms) } }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    LaunchedEffect(controller.recording) {
        while (controller.recording) { controller.tick(); delay(200) }
    }
    return controller
}

/** The hold-to-record microphone. Use in place of the send button while the draft is empty. */
@Composable
internal fun VoiceHoldButton(
    controller: VoiceNoteController,
    onNeedPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cancelPx = with(density) { 110.dp.toPx() }
    val lockPx = with(density) { 70.dp.toPx() }
    Surface(
        color = if (controller.recording) Color(0xFFFF4D4D) else Color(0xFF168EEA),
        shape = CircleShape,
        modifier = modifier.size(46.dp).testTag("composer_voice_hold").pointerInput(controller) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (controller.recording) { waitForUpOrCancellation(); return@awaitEachGesture }
                if (!controller.hasPermission()) {
                    onNeedPermission()
                    waitForUpOrCancellation()
                    return@awaitEachGesture
                }
                if (!controller.begin()) { waitForUpOrCancellation(); return@awaitEachGesture }
                var lockedHere = false
                while (true) {
                    val change = awaitPointerEventChange() ?: break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    controller.cancelling = dx < -cancelPx
                    if (!lockedHere && dy < -lockPx && !controller.cancelling) {
                        lockedHere = true
                        controller.locked = true
                    }
                    if (!change.pressed) break
                    change.consume()
                }
                when {
                    controller.cancelling -> controller.cancel()
                    lockedHere -> Unit // stays recording until Delete / Send is tapped
                    else -> controller.finish()
                }
            }
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                AnnieIcons.AudioTrack,
                contentDescription = "Hold to record a voice note",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPointerEventChange() =
    awaitPointerEvent().changes.firstOrNull()

/** Recording state stays visible, with a LIVE waveform and explicit delete/send when locked. */
@Composable
internal fun VoiceRecordingBar(controller: VoiceNoteController, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xFF102139))
            .padding(horizontal = 10.dp, vertical = 10.dp).testTag("voice_recording_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (controller.locked) {
            Icon(
                AnnieIcons.Close, contentDescription = "Delete recording", tint = Color(0xFFFF8A80),
                modifier = Modifier.size(23.dp).clip(CircleShape).clickable { controller.cancel() }
                    .testTag("voice_delete"),
            )
        } else {
            VoiceRecordingDot()
        }
        Text(formatVoiceDuration(controller.elapsedMs), color = Color.White, fontSize = 13.sp)
        VoiceWaveform(
            samples = controller.waveformPeaks.toList().takeLast(40),
            progress = 1f,
            width = if (controller.locked) 99.dp else 139.dp,
        )
        if (controller.locked) {
            Text(
                "Send", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF168EEA))
                    .clickable { controller.finish() }.padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("voice_send"),
            )
        } else if (controller.cancelling) {
            Text("Cancel", color = Color(0xFFFF8A80), fontSize = 11.sp)
        }
    }
}

/**
 * In-app dictation through Android's [SpeechRecognizer]: no system dialog, live partial text in the
 * message field, and whichever recognition service the device selected (Samsung, Google, ...).
 */
internal class AnnieDictation(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var prefix = ""
    var listening by mutableStateOf(false)
        private set

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(existingText: String) {
        if (listening) return
        if (!isAvailable()) { onUnavailable(); return }
        prefix = existingText.trimEnd().let { if (it.isEmpty()) "" else "$it " }
        val engine = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) = deliver(partialResults)
            override fun onResults(results: Bundle?) { deliver(results); release() }
            override fun onError(error: Int) { release() }
        })
        engine.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        )
        listening = true
    }

    /** Stops listening and keeps whatever has been recognised so far. */
    fun stop() { recognizer?.stopListening() }

    fun release() {
        listening = false
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }

    private fun deliver(bundle: Bundle?) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) onText(prefix + text)
    }
}
