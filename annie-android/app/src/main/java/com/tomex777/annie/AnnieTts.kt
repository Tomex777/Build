package com.tomex777.annie

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal class AnnieTtsController(
    context: Context,
    private val onStateChanged: () -> Unit,
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("annie_tts_v1", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = TextToSpeech(appContext, this)
    private var initialized = false

    var availableVoices: List<Voice> = emptyList()
        private set
    var selectedVoiceName: String? = preferences.getString("voice_name", null)
        private set
    var statusMessage: String = "Initializing system voice…"
        private set

    override fun onInit(status: Int) {
        mainHandler.post {
            if (status != TextToSpeech.SUCCESS) {
                initialized = false
                statusMessage = "The system Text-to-Speech service is unavailable."
                onStateChanged()
                return@post
            }
            initialized = true
            val engine = textToSpeech ?: run {
                statusMessage = "The system Text-to-Speech engine is unavailable."
                onStateChanged()
                return@post
            }
            availableVoices = engine.voices.orEmpty()
                .filter { it.name.isNotBlank() }
                .distinctBy { it.name }
                .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
            val saved = selectedVoiceName
            val selected = availableVoices.firstOrNull { it.name == saved }
                ?: engine.defaultVoice
                ?: availableVoices.firstOrNull()
            if (selected != null && engine.setVoice(selected) == TextToSpeech.SUCCESS) {
                selectedVoiceName = selected.name
                preferences.edit().putString("voice_name", selected.name).apply()
                statusMessage = selected.locale.displayName
            } else {
                statusMessage = "No usable system voice is available."
            }
            onStateChanged()
        }
    }

    fun selectVoice(voice: Voice): Boolean {
        val engine = textToSpeech ?: return false
        if (!initialized) return false
        return if (engine.setVoice(voice) == TextToSpeech.SUCCESS) {
            selectedVoiceName = voice.name
            preferences.edit().putString("voice_name", voice.name).apply()
            statusMessage = voice.locale.displayName
            onStateChanged()
            true
        } else {
            statusMessage = "That system voice could not be selected."
            onStateChanged()
            false
        }
    }

    fun speak(text: String): Boolean {
        val engine = textToSpeech ?: return false
        if (!initialized || text.isBlank()) return false
        val voice = availableVoices.firstOrNull { it.name == selectedVoiceName } ?: engine.defaultVoice
        if (voice != null && engine.setVoice(voice) != TextToSpeech.SUCCESS) {
            statusMessage = "The selected system voice is unavailable."
            onStateChanged()
            return false
        }
        return engine.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, null, "annie-\${System.nanoTime()}") == TextToSpeech.SUCCESS
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun close() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}

@Composable
internal fun AnnieVoiceContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var stateVersion by remember { mutableStateOf(0) }
    val controller = remember {
        AnnieTtsController(context) { stateVersion += 1 }
    }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    @Suppress("UNUSED_VARIABLE")
    val stateSnapshot = stateVersion

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Voice & Speech", color = Color(0xFFEEF5FF), fontSize = 22.sp)
        Text(
            "Uses Android’s native Text-to-Speech engine and the voices installed on this device.",
            color = Color(0xFF9CB2CC),
            fontSize = 12.sp,
        )
        Text(controller.statusMessage, color = Color(0xFF9CB2CC), fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { controller.speak("Hello. This is Annie using your Android system voice.") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF168EEA)),
                modifier = Modifier.weight(1f),
            ) { Text("Test voice") }
            TextButton(onClick = controller::stop) { Text("Stop") }
        }
        Text("System voices", color = Color(0xFFEEF5FF), fontSize = 14.sp)
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(controller.availableVoices, key = { it.name }) { voice ->
                val selected = voice.name == controller.selectedVoiceName
                Surface(
                    color = if (selected) Color(0xFF173854) else Color(0xFF102237),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(voice.locale.displayName, color = Color(0xFFEEF5FF), fontSize = 13.sp)
                            Text(voice.name, color = Color(0xFF9CB2CC), fontSize = 10.sp)
                        }
                        TextButton(onClick = { controller.selectVoice(voice) }) {
                            Text(if (selected) "Selected" else "Use")
                        }
                    }
                }
            }
        }
    }
}