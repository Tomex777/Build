package com.example.whatsapp.data.night

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class NightLiveVoiceClient private constructor(
    private val context: Context,
    private val repository: NightRepository,
    private val secrets: NightSecretStore,
    private val http: OkHttpClient,
) {
    interface Listener {
        fun onState(state: State)
        fun onUserTranscript(text: String) {}
        fun onAssistantTranscript(text: String) {}
        fun onError(message: String)
    }

    enum class State {
        CONNECTING,
        CONNECTED,
        LISTENING,
        SPEAKING,
        ENDED,
    }

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null
    private val running = AtomicBoolean(false)
    private var listener: Listener? = null

    suspend fun connect(
        chatId: String,
        displayName: String,
        listener: Listener,
    ) {
        stop()
        this.listener = listener
        listener.onState(State.CONNECTING)

        val chat = repository.getChat(chatId)
        val resolved = resolveLiveModel(chatId)
            ?: error("No Azure model with Live Voice is configured.")
        val profile = resolved.profile
        val model = resolved.model
        val key = secrets.get(profile.secretAlias)
            ?: error("The saved Azure Live Voice key is missing.")

        val modelName = model.deploymentName ?: model.modelId
        val connection = NightLiveVoiceProtocol.connection(
            endpoint = requireNotNull(profile.endpoint) {
                "Azure Live Voice endpoint is missing."
            },
            modelName = modelName,
        )

        val request = Request.Builder()
            .url(connection.url)
            .header("api-key", key)
            .build()

        webSocket = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    runCatching {
                        configureSession(
                            webSocket = webSocket,
                            displayName = displayName,
                            voiceName = profile.voiceName ?: "alloy",
                            chatSummary = chat?.latestSummary.orEmpty(),
                            modelName = modelName,
                            voiceLiveApi = connection.voiceLiveApi,
                        )
                        listener.onState(State.CONNECTED)
                        startAudio(webSocket, listener)
                    }.onFailure {
                        listener.onError(it.message ?: "Could not start Live Voice audio.")
                        listener.onState(State.ENDED)
                        webSocket.close(1011, "Audio initialization failed")
                        stopAudio()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerEvent(text, listener)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onState(State.ENDED)
                    stopAudio()
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onState(State.ENDED)
                    stopAudio()
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    stopAudio()
                    listener.onError(t.message ?: "Live Voice connection failed.")
                    listener.onState(State.ENDED)
                }
            }
        )
    }

    fun stop() {
        running.set(false)
        stopAudio()
        webSocket?.close(1000, "Night call ended")
        webSocket = null
        listener?.onState(State.ENDED)
        listener = null
    }

    private suspend fun resolveLiveModel(chatId: String): NightResolvedModel? {
        val routed = NightCapabilityRouter(repository)
            .resolveCapability(chatId, "live_voice")
            ?.takeIf { it.profile.providerType.equals("azure", ignoreCase = true) }

        if (routed != null) return routed

        // Compatibility path for existing dedicated Live Voice profiles that
        // predate capability tags on their model entries.
        val profile = repository.defaultProviderProfile("live_voice")
            ?.takeIf {
                it.providerType.equals("azure", ignoreCase = true) &&
                    it.isEnabled
            }
            ?: return null
        val model = repository.defaultProviderModel(profile.id)
            ?.takeIf { it.isEnabled }
            ?: return null

        return NightResolvedModel(profile, model)
    }

    private fun configureSession(
        webSocket: WebSocket,
        displayName: String,
        voiceName: String,
        chatSummary: String,
        modelName: String,
        voiceLiveApi: Boolean,
    ) {
        webSocket.send(
            NightLiveVoiceProtocol.sessionUpdate(
                displayName = displayName,
                voiceName = voiceName,
                chatSummary = chatSummary,
                modelName = modelName,
                voiceLiveApi = voiceLiveApi,
            ).toString()
        )
    }

    private fun startAudio(
        webSocket: WebSocket,
        listener: Listener,
    ) {
        val inputBuffer = AudioRecord.getMinBufferSize(
            NightLiveVoiceProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(inputBuffer > 0) { "Microphone audio format isn't supported." }

        @Suppress("MissingPermission")
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            NightLiveVoiceProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            inputBuffer * 2,
        )
        require(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Could not initialize the microphone."
        }

        val outputBuffer = AudioTrack.getMinBufferSize(
            NightLiveVoiceProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(outputBuffer > 0) { "Speaker audio format isn't supported." }

        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(NightLiveVoiceProtocol.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(outputBuffer * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioRecord = recorder
        audioTrack = player
        running.set(true)

        player.play()
        recorder.startRecording()
        listener.onState(State.LISTENING)

        captureThread = Thread {
            val buffer = ByteArray(inputBuffer)
            while (running.get()) {
                val count = recorder.read(buffer, 0, buffer.size)
                if (count > 0) {
                    val audio = Base64.encodeToString(
                        buffer.copyOf(count),
                        Base64.NO_WRAP,
                    )
                    val event = JSONObject()
                        .put("type", "input_audio_buffer.append")
                        .put("audio", audio)
                    if (!webSocket.send(event.toString())) break
                }
            }
        }.apply {
            name = "NightLiveVoiceCapture"
            start()
        }
    }

    private fun handleServerEvent(
        raw: String,
        listener: Listener,
    ) {
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return

        when (event.optString("type")) {
            "session.updated" -> listener.onState(State.LISTENING)

            "input_audio_buffer.speech_started" ->
                listener.onState(State.LISTENING)

            "response.created" ->
                listener.onState(State.SPEAKING)

            "response.output_audio.delta",
            "response.audio.delta" -> {
                val encoded = event.optString("delta")
                if (encoded.isNotBlank()) {
                    val bytes = runCatching {
                        Base64.decode(encoded, Base64.DEFAULT)
                    }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        audioTrack?.write(bytes, 0, bytes.size)
                    }
                }
            }

            "response.output_audio_transcript.done",
            "response.audio_transcript.done" -> {
                val text = event.optString("transcript").trim()
                if (text.isNotBlank()) listener.onAssistantTranscript(text)
            }

            "conversation.item.input_audio_transcription.completed",
            "conversation.item.audio_transcription.completed" -> {
                val text = event.optString("transcript").trim()
                if (text.isNotBlank()) listener.onUserTranscript(text)
            }

            "response.done" -> listener.onState(State.LISTENING)

            "error" -> {
                val error = event.optJSONObject("error")
                listener.onError(
                    error?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Azure Live Voice returned an error."
                )
            }
        }
    }

    private fun stopAudio() {
        running.set(false)

        val recorder = audioRecord
        audioRecord = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }

        runCatching { captureThread?.join(600L) }
        captureThread = null

        val player = audioTrack
        audioTrack = null
        runCatching { player?.stop() }
        runCatching { player?.flush() }
        runCatching { player?.release() }
    }


    companion object {
        @Volatile private var instance: NightLiveVoiceClient? = null

        fun get(context: Context): NightLiveVoiceClient =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: NightLiveVoiceClient(
                    context = app,
                    repository = NightRepository.get(app),
                    secrets = NightSecretStore.get(app),
                    http = OkHttpClient.Builder().build(),
                ).also { instance = it }
            }
    }
}
