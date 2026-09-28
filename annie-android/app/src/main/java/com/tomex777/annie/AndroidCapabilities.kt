package com.tomex777.annie

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import java.io.ByteArrayOutputStream
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
    suspend fun pickTextDocument(mimeType: String): JSONObject
    suspend fun inspectMedia(mediaFile: File): JSONObject
    suspend fun postNotification(ownerPackageId: String, key: String?, title: String, text: String): JSONObject
    suspend fun updateNotification(ownerPackageId: String, key: String, title: String, text: String): JSONObject
    suspend fun cancelNotification(ownerPackageId: String, key: String): JSONObject
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
                private fun release() {
                    mainHandler.post { engine.shutdown() }
                }
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

    override suspend fun pickTextDocument(mimeType: String): JSONObject {
        AnnieForegroundGate.requireInteractive("Document selection")
        return AnnieDocumentPickerBroker.pickText(appContext, mimeType)
    }

    override suspend fun inspectMedia(mediaFile: File): JSONObject = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(mediaFile.absolutePath)
            fun metadata(key: Int) = retriever.extractMetadata(key)
            JSONObject()
                .put("mimeType", metadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: JSONObject.NULL)
                .put("durationMs", metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
                .put("width", metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0)
                .put("height", metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)
                .put("bitrate", metadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L)
                .put("hasAudio", metadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO).equals("yes", true))
                .put("hasVideo", metadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO).equals("yes", true))
        } finally {
            retriever.release()
        }
    }

    override suspend fun postNotification(
        ownerPackageId: String,
        key: String?,
        title: String,
        text: String,
    ): JSONObject = upsertNotification(ownerPackageId, key, title, text, requireExisting = false)

    override suspend fun updateNotification(
        ownerPackageId: String,
        key: String,
        title: String,
        text: String,
    ): JSONObject = upsertNotification(ownerPackageId, key, title, text, requireExisting = true)

    override suspend fun cancelNotification(ownerPackageId: String, key: String): JSONObject {
        AnnieForegroundGate.requireInteractive("Notification cancellation")
        val prefs = appContext.getSharedPreferences(ANDROID_NOTIFICATION_STATE_PREFS, Context.MODE_PRIVATE)
        val storageKey = notificationStorageKey(ownerPackageId, key)
        val notificationId = prefs.getInt(storageKey, 0)
        if (notificationId == 0) {
            return JSONObject().put("status", "missing").put("cancelled", false).put("key", key)
        }
        appContext.getSystemService(NotificationManager::class.java).cancel(notificationId)
        prefs.edit().remove(storageKey).apply()
        return JSONObject().put("status", "cancelled").put("cancelled", true).put("key", key)
    }

    private suspend fun upsertNotification(
        ownerPackageId: String,
        requestedKey: String?,
        title: String,
        text: String,
        requireExisting: Boolean,
    ): JSONObject {
        AnnieForegroundGate.requireInteractive(if (requireExisting) "Notification update" else "Notification posting")
        val permissionGranted = AnnieNotificationPermissionBroker.ensureGranted(appContext)
        if (!permissionGranted) {
            return JSONObject()
                .put("status", "denied")
                .put("posted", false)
                .put("key", requestedKey ?: "")
        }
        AnnieNotificationRateLimiter.check(ownerPackageId)

        val prefs = appContext.getSharedPreferences(ANDROID_NOTIFICATION_STATE_PREFS, Context.MODE_PRIVATE)
        val key = requestedKey ?: UUID.randomUUID().toString()
        val storageKey = notificationStorageKey(ownerPackageId, key)
        val existingId = prefs.getInt(storageKey, 0)
        require(!requireExisting || existingId != 0) { "Notification key does not exist for this package" }
        val notificationId = if (existingId != 0) existingId else {
            val candidate = UUID.randomUUID().hashCode() and Int.MAX_VALUE
            if (candidate == 0) 1 else candidate
        }

        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "Annie scripts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications explicitly requested by Annie script packages"
            },
        )
        val notification = Notification.Builder(appContext, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
        prefs.edit().putInt(storageKey, notificationId).apply()
        return JSONObject()
            .put("status", if (requireExisting) "updated" else "posted")
            .put("posted", true)
            .put("key", key)
    }

    private fun notificationStorageKey(ownerPackageId: String, key: String): String =
        "$ownerPackageId|$key"

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

    companion object {
        private const val NOTIFICATION_CHANNEL = "annie_script_notifications"
    }
}

internal const val ANDROID_NOTIFICATION_STATE_PREFS = "annie_script_notification_state"

internal object AnnieNotificationRateLimiter {
    private const val MAX_EVENTS = 5
    private const val WINDOW_MILLIS = 60_000L
    private val windows = ConcurrentHashMap<String, java.util.ArrayDeque<Long>>()

    fun check(ownerPackageId: String) {
        val now = System.currentTimeMillis()
        val window = windows.computeIfAbsent(ownerPackageId) { java.util.ArrayDeque<Long>() }
        synchronized(window) {
            while (window.isNotEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.removeFirst()
            }
            require(window.size < MAX_EVENTS) {
                "Notification rate limit exceeded for this package; try again later"
            }
            window.addLast(now)
        }
    }

    fun clear(ownerPackageId: String) {
        windows.remove(ownerPackageId)
    }
}

internal fun clearPackageNotifications(context: Context, ownerPackageId: String) {
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences(ANDROID_NOTIFICATION_STATE_PREFS, Context.MODE_PRIVATE)
    val prefix = "$ownerPackageId|"
    val owned = prefs.all.filterKeys { it.startsWith(prefix) }
    val manager = appContext.getSystemService(NotificationManager::class.java)
    owned.values.filterIsInstance<Int>().filter { it > 0 }.forEach(manager::cancel)
    if (owned.isNotEmpty()) {
        val editor = prefs.edit()
        owned.keys.forEach(editor::remove)
        editor.commit()
    }
    AnnieNotificationRateLimiter.clear(ownerPackageId)
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


private const val MAX_PICKED_TEXT_BYTES = 48 * 1024

internal object AnnieDocumentPickerBroker {
    private const val EXTRA_TOKEN = "annie.document.token"
    private const val EXTRA_MIME = "annie.document.mime"
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    suspend fun pickText(context: Context, mimeType: String): JSONObject {
        val probe = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mimeType)
        if (context.packageManager.resolveActivity(probe, 0) == null) {
            return JSONObject().put("status", "unavailable").put("name", "").put("mimeType", "").put("text", "")
        }
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        pending[token] = deferred
        val brokerIntent = Intent(context, AnnieDocumentPickerActivity::class.java)
            .putExtra(EXTRA_TOKEN, token)
            .putExtra(EXTRA_MIME, mimeType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main.immediate) { context.startActivity(brokerIntent) }
        return try {
            withTimeout(90_000) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            JSONObject().put("status", "timeout").put("name", "").put("mimeType", "").put("text", "")
        } finally {
            pending.remove(token)
        }
    }

    fun token(intent: Intent): String = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
    fun mimeType(intent: Intent): String = intent.getStringExtra(EXTRA_MIME).orEmpty().ifBlank { "text/*" }
    fun complete(token: String, value: JSONObject) { pending.remove(token)?.complete(value) }
}

internal class AnnieDocumentPickerActivity : Activity() {
    private var token: String = ""
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = AnnieDocumentPickerBroker.token(intent)
        if (token.isBlank()) {
            finish()
            return
        }
        if (savedInstanceState == null) launchPicker()
    }

    private fun launchPicker() {
        if (launched) return
        launched = true
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(AnnieDocumentPickerBroker.mimeType(intent))
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(picker, REQUEST_DOCUMENT)
        } catch (_: ActivityNotFoundException) {
            AnnieDocumentPickerBroker.complete(
                token,
                JSONObject().put("status", "unavailable").put("name", "").put("mimeType", "").put("text", ""),
            )
            finish()
        }
    }

    @Deprecated("Android callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DOCUMENT) return
        val selected = data?.data
        if (resultCode != RESULT_OK || selected == null) {
            AnnieDocumentPickerBroker.complete(
                token,
                JSONObject().put("status", "cancelled").put("name", "").put("mimeType", "").put("text", ""),
            )
            finish()
            return
        }
        Thread {
            val result = runCatching { readSelectedText(selected) }.getOrElse {
                JSONObject().put("status", "error").put("name", "").put("mimeType", "").put("text", "")
            }
            AnnieDocumentPickerBroker.complete(token, result)
            runOnUiThread { finish() }
        }.start()
    }

    private fun readSelectedText(uri: Uri): JSONObject {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }
            .orEmpty()
        val mimeType = contentResolver.getType(uri).orEmpty()
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_PICKED_TEXT_BYTES) { "Selected text document is too large" }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: error("Could not open selected document")
        return JSONObject()
            .put("status", "selected")
            .put("name", name.take(160))
            .put("mimeType", mimeType.take(120))
            .put("text", bytes.toString(Charsets.UTF_8))
    }

    companion object { private const val REQUEST_DOCUMENT = 7302 }
}

internal object AnnieNotificationPermissionBroker {
    private const val EXTRA_TOKEN = "annie.notification.token"
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    suspend fun ensureGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()
        pending[token] = deferred
        val intent = Intent(context, AnnieNotificationPermissionActivity::class.java)
            .putExtra(EXTRA_TOKEN, token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main.immediate) { context.startActivity(intent) }
        return try {
            withTimeout(30_000) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            false
        } finally {
            pending.remove(token)
        }
    }

    fun token(intent: Intent): String = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
    fun complete(token: String, granted: Boolean) { pending.remove(token)?.complete(granted) }
}

internal class AnnieNotificationPermissionActivity : Activity() {
    private var token: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = AnnieNotificationPermissionBroker.token(intent)
        if (token.isBlank()) {
            finish()
            return
        }
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            AnnieNotificationPermissionBroker.complete(token, true)
            finish()
            return
        }
        if (savedInstanceState == null) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        AnnieNotificationPermissionBroker.complete(
            token,
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED,
        )
        finish()
    }

    companion object { private const val REQUEST_NOTIFICATIONS = 7303 }
}
