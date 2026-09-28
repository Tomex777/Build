package com.tomex777.annie

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Value-only implementations behind Annie's allowlisted Android bridge. */
internal interface AndroidCapabilityBackend {
    suspend fun speak(text: String, languageTag: String?): JSONObject
    suspend fun recognizeText(imageFile: File): JSONObject
    suspend fun listen(languageTag: String?, prompt: String?): JSONObject
}

internal class PlatformAndroidCapabilityBackend(private val context: Context) : AndroidCapabilityBackend {
    private val appContext = context.applicationContext

    override suspend fun speak(text: String, languageTag: String?): JSONObject {
        AnnieForegroundGate.requireInteractive("Text to speech")
        val engine = createTextToSpeech(appContext)
        val locale = languageTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()
        val languageStatus = withContext(Dispatchers.Main.immediate) { engine.setLanguage(locale) }
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            withContext(Dispatchers.Main.immediate) { engine.shutdown() }
            error("TTS language is not available: ${locale.toLanguageTag()}")
        }
        val utteranceId = "annie-${UUID.randomUUID()}"
        val mainHandler = Handler(Looper.getMainLooper())
        withContext(Dispatchers.Main.immediate) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                private fun release() = mainHandler.post { engine.shutdown() }
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = release()
                @Deprecated("Android callback")
                override fun onError(utteranceId: String?) = release()
                override fun onError(utteranceId: String?, errorCode: Int) = release()
            })
            check(engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId) != TextToSpeech.ERROR) {
                "Android TTS could not queue this utterance"
            }
        }
        return JSONObject()
            .put("status", "queued")
            .put("queued", true)
            .put("language", locale.toLanguageTag())
    }

    override suspend fun recognizeText(imageFile: File): JSONObject = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(appContext, Uri.fromFile(imageFile))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(image).awaitValue()
            val blocks = JSONArray()
            result.textBlocks.take(200).forEach { block ->
                blocks.put(JSONObject()
                    .put("text", block.text)
                    .put("lines", JSONArray(block.lines.take(200).map { it.text })))
            }
            JSONObject().put("text", result.text).put("blocks", blocks)
        } finally {
            recognizer.close()
        }
    }

    override suspend fun listen(languageTag: String?, prompt: String?): JSONObject {
        AnnieForegroundGate.requireInteractive("Speech recognition")
        return AnnieSpeechRecognitionBroker.listen(appContext, languageTag, prompt)
    }

    private suspend fun createTextToSpeech(context: Context): TextToSpeech =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                var engine: TextToSpeech? = null
                engine = TextToSpeech(context) { status ->
                    Handler(Looper.getMainLooper()).post {
                        val ready = engine
                        when {
                            !continuation.isActive -> ready?.shutdown()
                            status == TextToSpeech.SUCCESS && ready != null -> continuation.resume(ready)
                            else -> {
                                ready?.shutdown()
                                continuation.resumeWithException(IllegalStateException("Android TTS is unavailable"))
                            }
                        }
                    }
                }
                continuation.invokeOnCancellation { engine?.shutdown() }
            }
        }
}

internal object AnnieForegroundGate {
    @Volatile private var mainActivityResumed = false
    fun onMainActivityResumed() { mainActivityResumed = true }
    fun onMainActivityPaused() { mainActivityResumed = false }
    fun requireInteractive(operation: String) {
        check(mainActivityResumed) { "$operation requires Annie to be open in the foreground" }
    }
}

/**
 * Speech capture is delegated to Android's visible recognizer UI through a non-exported activity.
 * Packages receive only plain result strings.
 */
internal object AnnieSpeechRecognitionBroker {
    private const val EXTRA_TOKEN = "annie.speech.token"
    private const val EXTRA_LANGUAGE = "annie.speech.language"
    private const val EXTRA_PROMPT = "annie.speech.prompt"
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    suspend fun listen(context: Context, languageTag: String?, prompt: String?): JSONObject {
        val probe = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        if (context.packageManager.resolveActivity(probe, 0) == null) {
            return JSONObject().put("status", "unavailable").put("text", "").put("alternatives", JSONArray())
        }
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        pending[token] = deferred
        val brokerIntent = Intent(context, AnnieSpeechRecognitionActivity::class.java)
            .putExtra(EXTRA_TOKEN, token)
            .putExtra(EXTRA_LANGUAGE, languageTag.orEmpty())
            .putExtra(EXTRA_PROMPT, prompt.orEmpty())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main.immediate) { context.startActivity(brokerIntent) }
        return try {
            withTimeout(90_000) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            JSONObject().put("status", "timeout").put("text", "").put("alternatives", JSONArray())
        } finally {
            pending.remove(token)
        }
    }

    fun token(intent: Intent): String = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
    fun language(intent: Intent): String? = intent.getStringExtra(EXTRA_LANGUAGE)?.takeIf(String::isNotBlank)
    fun prompt(intent: Intent): String? = intent.getStringExtra(EXTRA_PROMPT)?.takeIf(String::isNotBlank)
    fun complete(token: String, value: JSONObject) { pending.remove(token)?.complete(value) }
}

internal class AnnieSpeechRecognitionActivity : Activity() {
    private var token: String = ""
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = AnnieSpeechRecognitionBroker.token(intent)
        if (token.isBlank()) {
            finish()
            return
        }
        if (savedInstanceState == null) launchRecognizer()
    }

    private fun launchRecognizer() {
        if (launched) return
        launched = true
        val recognition = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        AnnieSpeechRecognitionBroker.language(intent)?.let { recognition.putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        AnnieSpeechRecognitionBroker.prompt(intent)?.let { recognition.putExtra(RecognizerIntent.EXTRA_PROMPT, it) }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(recognition, REQUEST_RECOGNITION)
        } catch (_: ActivityNotFoundException) {
            AnnieSpeechRecognitionBroker.complete(
                token,
                JSONObject().put("status", "unavailable").put("text", "").put("alternatives", JSONArray()),
            )
            finish()
        }
    }

    @Deprecated("Android callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_RECOGNITION) return
        val alternatives = if (resultCode == RESULT_OK) {
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty().take(5)
        } else emptyList()
        val status = when {
            resultCode == RESULT_OK && alternatives.isNotEmpty() -> "recognized"
            resultCode == RESULT_CANCELED -> "cancelled"
            else -> "empty"
        }
        AnnieSpeechRecognitionBroker.complete(
            token,
            JSONObject()
                .put("status", status)
                .put("text", alternatives.firstOrNull().orEmpty())
                .put("alternatives", JSONArray(alternatives)),
        )
        finish()
    }

    companion object { private const val REQUEST_RECOGNITION = 7301 }
}

private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { failure -> if (continuation.isActive) continuation.resumeWithException(failure) }
    addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
}
