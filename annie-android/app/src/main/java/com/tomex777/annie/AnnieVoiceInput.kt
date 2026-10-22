package com.tomex777.annie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
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
    var paused by mutableStateOf(false)
        private set
    val waveformPeaks = mutableStateListOf<Float>()

    fun hasPermission() = recorder.hasPermission()

    fun begin(): Boolean {
        if (recording) return false
        if (!recorder.start()) {
            Toast.makeText(appContext, "Couldn\u0027t start recording. Check microphone access.", Toast.LENGTH_SHORT).show()
            return false
        }
        AnnieVoicePlayer.stop()
        paused = false
        elapsedMs = 0L
        waveformPeaks.clear()
        locked = false
        cancelling = false
        recording = true
        return true
    }

    fun tick() {
        if (!recording || paused) return
        elapsedMs = recorder.durationMillis
        waveformPeaks.add(recorder.peak())
        if (waveformPeaks.size > 600) waveformPeaks.removeAt(0)
        if (elapsedMs >= AnnieVoiceRecorder.MAX_DURATION_MS) finish()
    }

    fun togglePause() {
        if (!recording || !locked) return
        val succeeded = if (paused) recorder.resume() else recorder.pause()
        if (succeeded) {
            paused = recorder.isPaused
            elapsedMs = recorder.durationMillis
        } else {
            Toast.makeText(appContext, "Couldn't ${if (paused) "resume" else "pause"} recording.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Keep a locked draft paused when the app leaves the foreground; never send on interruption. */
    fun onHostStopped() {
        if (!recording) return
        if (!locked) {
            cancel()
        } else if (!paused) {
            if (recorder.pause()) {
                paused = true
                elapsedMs = recorder.durationMillis
            } else {
                cancel()
                Toast.makeText(appContext, "Recording interrupted. Please record again.", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun reset() { recording = false; locked = false; cancelling = false; paused = false }
}

@Composable
internal fun rememberVoiceNoteController(
    onVoiceNote: (String, Long) -> Unit,
    sessionKey: String = "",
): VoiceNoteController {
    val context = androidx.compose.ui.platform.LocalContext.current
    val latest = androidx.compose.runtime.rememberUpdatedState(onVoiceNote)
    // Chat switching disposes the old recorder instead of letting a recording cross conversations.
    val controller = remember(context, sessionKey) { VoiceNoteController(context) { path, ms -> latest.value(path, ms) } }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) controller.onHostStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(controller.recording) {
        while (controller.recording) { controller.tick(); delay(200) }
    }
    return controller
}

/** The hold-to-record microphone. Use in place of the send button while the draft is empty. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun VoiceHoldButton(
    controller: VoiceNoteController,
    onNeedPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cancelPx = with(density) { 110.dp.toPx() }
    val lockPx = with(density) { 70.dp.toPx() }
    val gesture = remember(controller) { VoiceHoldGesture() }
    DisposableEffect(controller) {
        onDispose { if (!controller.locked) controller.cancel() }
    }
    Surface(
        color = if (controller.recording) Color(0xFFFF4D4D) else Color(0xFF168EEA),
        shape = CircleShape,
        modifier = modifier.size(48.dp).testTag("composer_voice_hold")
            // TalkBack cannot perform drag-and-hold gestures. An accessibility click
            // starts a locked recording with visible Pause, Delete and Send actions.
            .semantics {
                role = Role.Button
                contentDescription = "Record voice note"
                onClick(label = "Start voice note") {
                    if (controller.recording) return@onClick true
                    if (!controller.hasPermission()) onNeedPermission()
                    else if (controller.begin()) controller.locked = true
                    true
                }
            }
            .pointerInteropFilter { event ->
            // Compose represents ACTION_CANCEL as unpressed pointers. Keep the
            // native distinction: only ACTION_UP may send an unlocked draft.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gesture.started = false
                    gesture.startX = event.x
                    gesture.startY = event.y
                    if (!controller.recording) {
                        if (!controller.hasPermission()) onNeedPermission()
                        else gesture.started = controller.begin()
                    }
                }
                MotionEvent.ACTION_MOVE -> if (gesture.started && controller.recording) {
                    val dx = event.x - gesture.startX
                    val dy = event.y - gesture.startY
                    controller.cancelling = dx < -cancelPx
                    if (dy < -lockPx && !controller.cancelling) controller.locked = true
                }
                MotionEvent.ACTION_UP -> {
                    if (gesture.started) {
                        when {
                            controller.cancelling -> controller.cancel()
                            controller.locked -> Unit
                            else -> controller.finish()
                        }
                    }
                    gesture.started = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (gesture.started && !controller.locked) controller.cancel()
                    gesture.started = false
                }
            }
            true

        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                AnnieIcons.Microphone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private class VoiceHoldGesture {
    var started = false
    var startX = 0f
    var startY = 0f
}

/** Recording state stays visible, with a LIVE waveform and explicit delete/send when locked. */
@Composable
internal fun VoiceRecordingBar(controller: VoiceNoteController, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xFF102139))
            .padding(horizontal = 6.dp, vertical = 2.dp).testTag("voice_recording_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (controller.locked) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Delete recording") { controller.cancel() }
                    .testTag("voice_delete"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AnnieIcons.Close, contentDescription = null,
                    tint = Color(0xFFFF8A80), modifier = Modifier.size(23.dp),
                )
            }
        } else {
            VoiceRecordingDot()
        }
        Text(formatVoiceDuration(controller.elapsedMs), color = Color.White, fontSize = 13.sp)
        VoiceWaveform(
            samples = controller.waveformPeaks.toList().takeLast(40),
            progress = 1f,
            width = if (controller.locked) 32.dp else 139.dp,
        )
        if (controller.locked) {
            Box(
                Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClickLabel = if (controller.paused) "Resume recording" else "Pause recording") {
                        controller.togglePause()
                    }
                    .testTag("voice_pause_resume"),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (controller.paused) "Resume" else "Pause", color = Color.White, fontSize = 13.sp)
            }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF168EEA))
                    .clickable(role = Role.Button, onClickLabel = "Send recording") { controller.finish() }
                    .testTag("voice_send"),
                contentAlignment = Alignment.Center,
            ) {
                Text("Send", color = Color.White, fontSize = 13.sp)
            }
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
    // Ignore speech callbacks that arrive after leaving the chat or backgrounding the app.
    private var generation = 0
    var listening by mutableStateOf(false)
        private set

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(existingText: String) {
        if (listening) return
        if (!isAvailable()) { onUnavailable(); return }
        val session = ++generation
        prefix = existingText
        val engine = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: RuntimeException) {
            release()
            onUnavailable()
            return
        }
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (session == generation) listening = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) = deliver(partialResults, session)
            override fun onResults(results: Bundle?) {
                if (session == generation) {
                    deliver(results, session)
                    release()
                }
            }
            override fun onError(error: Int) {
                if (session == generation) release()
            }
        })
        listening = true
        try {
            engine.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            )
        } catch (_: RuntimeException) {
            release()
            onUnavailable()
        }
    }

    /** End active dictation and allow Android to return its final recognised words. */
    fun stop() {
        val active = recognizer ?: return
        runCatching { active.stopListening() }.onFailure { release() }
    }

    fun release() {
        generation++
        listening = false
        val active = recognizer
        recognizer = null
        active?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
    }

    private fun deliver(bundle: Bundle?, session: Int) {
        if (session != generation || !listening) return
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) onText(appendRecognizedText(prefix, text))
    }
}

/** Insert dictated speech without discarding a message the user already typed. */
internal fun appendRecognizedText(existing: String, recognized: String): String {
    val spoken = recognized.trim()
    if (spoken.isEmpty()) return existing
    val spacer = if (existing.isEmpty() || existing.last().isWhitespace()) "" else " "
    return existing + spacer + spoken
}
