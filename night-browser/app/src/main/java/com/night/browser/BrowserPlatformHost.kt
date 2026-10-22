package com.night.browser

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Android-owned UI for Gecko's prompts. Register launchers before Activity STARTED.
 *
 * Neither Android nor site permissions are ever silently granted. Open-document
 * results use SAF content URIs; no broad storage permission is requested.
 */
class BrowserPlatformHost(private val activity: ComponentActivity) :
    GeckoSession.PermissionDelegate, GeckoSession.PromptDelegate {

    private var pendingFile: Pair<GeckoSession.PromptDelegate.FilePrompt,
        GeckoResult<GeckoSession.PromptDelegate.PromptResponse>>? = null
    private var pendingAndroid: GeckoSession.PermissionDelegate.Callback? = null
    private data class PendingMedia(
        val callback: GeckoSession.PermissionDelegate.MediaCallback,
        val video: GeckoSession.PermissionDelegate.MediaSource?,
        val audio: GeckoSession.PermissionDelegate.MediaSource?
    )
    private var pendingMedia: PendingMedia? = null

    /** Deny unresolved Gecko callbacks before the Android Activity is destroyed. */
    fun cancelPending() {
        pendingFile?.let { (prompt, result) ->
            runCatching { result.complete(prompt.dismiss()) }
        }
        pendingFile = null
        pendingAndroid?.let { runCatching { it.reject() } }
        pendingAndroid = null
        pendingMedia?.let { runCatching { it.callback.reject() } }
        pendingMedia = null
    }

    private val documents = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { reply ->
        val pending = pendingFile ?: return@registerForActivityResult
        pendingFile = null
        val (prompt, result) = pending
        val uris = mutableListOf<Uri>()
        reply.data?.data?.let { uris.add(it) }
        reply.data?.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { if (it !in uris) uris.add(it) }
            }
        }
        // ACTION_OPEN_DOCUMENT returns delegated content:// URIs. Never
        // manufacture a file:// path or trust a non-document scheme from an
        // untrusted provider, and never let a malformed provider reply crash
        // the entire browsing Activity.
        val selected = uris.filter { it.scheme.equals("content", ignoreCase = true) }
        if (reply.resultCode != android.app.Activity.RESULT_OK || selected.isEmpty()) {
            runCatching { result.complete(prompt.dismiss()) }
            return@registerForActivityResult
        }
        try {
            val confirmation = if (
                prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
            ) prompt.confirm(activity.applicationContext, selected.toTypedArray())
            else prompt.confirm(activity.applicationContext, selected.first())
            result.complete(confirmation)
        } catch (_: RuntimeException) {
            // Gecko may reject a provider that disappeared before its copy.
            runCatching { result.complete(prompt.dismiss()) }
        }
    }

    private val androidPermissions = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants.isNotEmpty() && grants.values.all { it }
        val media = pendingMedia
        pendingMedia = null
        if (media != null) {
            if (allowed) media.callback.grant(media.video, media.audio)
            else media.callback.reject()
            return@registerForActivityResult
        }
        val callback = pendingAndroid ?: return@registerForActivityResult
        pendingAndroid = null
        if (allowed) callback.grant() else callback.reject()
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        // A new chooser cancels the old one instead of leaving Gecko blocked.
        pendingFile?.let { (old, reply) -> reply.complete(old.dismiss()) }
        pendingFile = prompt to result
        val mime = prompt.mimeTypes?.filter { it.isNotBlank() }?.distinct().orEmpty()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mime.size == 1) mime.first() else "*/*"
            if (mime.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mime.toTypedArray())
            putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE
            )
        }
        // Folder-upload support needs a separate tree-document bridge. Never
        // pretend that a folder picker has returned individual files.
        if (prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER) {
            pendingFile = null
            result.complete(prompt.dismiss())
            return result
        }
        try {
            documents.launch(intent)
        } catch (_: RuntimeException) {
            pendingFile = null
            result.complete(prompt.dismiss())
        }
        return result
    }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val requested = permissions?.distinct().orEmpty()
        if (requested.isEmpty()) { callback.reject(); return }
        // Only the app-declared capabilities are eligible; reject arbitrary
        // permissions requested by web content or future Gecko changes.
        val supported = setOf(
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (requested.any { it !in supported }) { callback.reject(); return }
        if (requested.all {
                ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
            }) {
            callback.grant()
            return
        }
        if (pendingAndroid != null || pendingMedia != null) { callback.reject(); return }
        pendingAndroid = callback
        try { androidPermissions.launch(requested.toTypedArray()) }
        catch (_: RuntimeException) {
            pendingAndroid = null
            callback.reject()
        }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int> {
        if (perm.value != GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT) {
            return GeckoResult.fromValue(perm.value)
        }
        // Silent autoplay doesn't expose data or play audio. It should not
        // cause a confusing permission prompt on video-heavy websites.
        if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE) {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
        }
        // No native notification delivery implementation yet: do not display
        // a meaningless "Allow notifications" choice to the user.
        if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION) {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
        // Enabling trackers is not a sensible response to a generic page
        // request, and persistent storage requires an explicit settings flow.
        if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING ||
            perm.permission == GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE
        ) return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        val reason = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "your location"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> "automatic sound playback"
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "third-party cookie access"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "protected media playback"
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> "devices on your local network"
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> "this device's local services"
            else -> return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
        val result = GeckoResult<Int>()
        perm.notifyShown()
        AlertDialog.Builder(activity)
            .setTitle("Website permission")
            .setMessage("${safeHost(perm.uri)} wants access to ${reason}.")
            .setPositiveButton("Allow") { _, _ -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) }
            .setNegativeButton("Block") { _, _ -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
            .setOnCancelListener { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
            .show()
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        // Screen sharing and system-audio capture need a separate MediaProjection
        // bridge. Don't mislabel those requests as camera or microphone access.
        if (video?.any { it.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA } == true ||
            audio?.any { it.source != GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE } == true
        ) { callback.reject(); return }
        val camera = !video.isNullOrEmpty()
        val microphone = !audio.isNullOrEmpty()
        if (!camera && !microphone) { callback.reject(); return }
        val reason = when {
            camera && microphone -> "camera and microphone"
            camera -> "camera"
            else -> "microphone"
        }
        AlertDialog.Builder(activity)
            .setTitle("Website device access")
            .setMessage("${safeHost(uri)} wants to use your ${reason}.")
            .setPositiveButton("Allow") { _, _ ->
                val required = buildList {
                    if (camera) add(Manifest.permission.CAMERA)
                    if (microphone) add(Manifest.permission.RECORD_AUDIO)
                }
                val missing = required.filter {
                    ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                } else if (pendingAndroid != null || pendingMedia != null) {
                    // Avoid confusing overlapping OS permission popups from tabs.
                    callback.reject()
                } else {
                    pendingMedia = PendingMedia(
                        callback, video?.firstOrNull(), audio?.firstOrNull())
                    try { androidPermissions.launch(missing.toTypedArray()) }
                    catch (_: RuntimeException) {
                        pendingMedia = null
                        callback.reject()
                    }
                }
            }
            .setNegativeButton("Block") { _, _ -> callback.reject() }
            .setOnCancelListener { callback.reject() }
            .show()
    }

    override fun onAlertPrompt(
        session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        AlertDialog.Builder(activity)
            .setTitle(prompt.title ?: "Website message")
            .setMessage(prompt.message.orEmpty())
            .setPositiveButton("OK") { _, _ -> result.complete(prompt.dismiss()) }
            .setOnCancelListener { result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onButtonPrompt(
        session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        AlertDialog.Builder(activity)
            .setTitle(prompt.title ?: "Website confirmation")
            .setMessage(prompt.message.orEmpty())
            .setPositiveButton("OK") { _, _ ->
                result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
            }
            .setNegativeButton("Cancel") { _, _ ->
                result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE))
            }
            .setOnCancelListener { result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession, prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        AlertDialog.Builder(activity)
            .setTitle("Leave this page?")
            .setMessage("Changes you made may not be saved.")
            .setPositiveButton("Leave") { _, _ -> result.complete(prompt.confirm(AllowOrDeny.ALLOW)) }
            .setNegativeButton("Stay") { _, _ -> result.complete(prompt.confirm(AllowOrDeny.DENY)) }
            .setOnCancelListener { result.complete(prompt.confirm(AllowOrDeny.DENY)) }
            .show()
        return result
    }

    override fun onTextPrompt(
        session: GeckoSession, prompt: GeckoSession.PromptDelegate.TextPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val field = styledInput(prompt.defaultValue.orEmpty(), password = false)
        AlertDialog.Builder(activity)
            .setTitle(prompt.title ?: "Website input")
            .setMessage(prompt.message.orEmpty())
            .setView(field)
            .setPositiveButton("OK") { _, _ -> result.complete(prompt.confirm(field.text.toString())) }
            .setNegativeButton("Cancel") { _, _ -> result.complete(prompt.dismiss()) }
            .setOnCancelListener { result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onAuthPrompt(
        session: GeckoSession, prompt: GeckoSession.PromptDelegate.AuthPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val username = styledInput("", password = false).apply { hint = "Username" }
        val password = styledInput("", password = true).apply { hint = "Password" }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
            addView(username)
            addView(password)
        }
        AlertDialog.Builder(activity)
            .setTitle(prompt.title ?: "Website sign in")
            .setMessage(prompt.message.orEmpty())
            .setView(box)
            .setPositiveButton("Sign in") { _, _ ->
                result.complete(prompt.confirm(username.text.toString(), password.text.toString()))
            }
            .setNegativeButton("Cancel") { _, _ -> result.complete(prompt.dismiss()) }
            .setOnCancelListener { result.complete(prompt.dismiss()) }
            .show()
        return result
    }

    override fun onChoicePrompt(
        session: GeckoSession, prompt: GeckoSession.PromptDelegate.ChoicePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val choices = mutableListOf<GeckoSession.PromptDelegate.ChoicePrompt.Choice>()
        val labels = mutableListOf<String>()
        fun collect(
            options: Array<out GeckoSession.PromptDelegate.ChoicePrompt.Choice>,
            group: String = ""
        ) {
            options.forEach { choice ->
                if (!choice.disabled && !choice.separator) {
                    val nested = choice.items
                    if (nested != null) collect(nested, if (group.isBlank())
                        choice.label else "$group / ${choice.label}")
                    else {
                        choices.add(choice)
                        labels.add(if (group.isBlank()) choice.label else "$group / ${choice.label}")
                    }
                }
            }
        }
        collect(prompt.choices)
        if (choices.isEmpty()) {
            result.complete(prompt.dismiss())
            return result
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle(prompt.title ?: prompt.message ?: "Choose")
            .setNegativeButton("Cancel") { _, _ -> result.complete(prompt.dismiss()) }
            .setOnCancelListener { result.complete(prompt.dismiss()) }

        when (prompt.type) {
            GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE -> {
                val checked = BooleanArray(choices.size) { choices[it].selected }
                builder.setMultiChoiceItems(labels.toTypedArray(), checked) { _, i, selected ->
                    checked[i] = selected
                }.setPositiveButton("Done") { _, _ ->
                    result.complete(prompt.confirm(
                        choices.filterIndexed { i, _ -> checked[i] }.toTypedArray()))
                }
            }
            GeckoSession.PromptDelegate.ChoicePrompt.Type.SINGLE -> {
                var selected = choices.indexOfFirst { it.selected }
                builder.setSingleChoiceItems(labels.toTypedArray(), selected) { _, i ->
                    selected = i
                }.setPositiveButton("Choose") { _, _ ->
                    if (selected >= 0) result.complete(prompt.confirm(choices[selected]))
                    else result.complete(prompt.dismiss())
                }
            }
            else -> builder.setItems(labels.toTypedArray()) { _, index ->
                result.complete(prompt.confirm(choices[index]))
            }
        }
        builder.show()
        return result
    }

    /** Always require an explicit user confirmation to leave NIGHT Browser. */
    fun confirmExternalLaunch(uri: String, onFailure: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Open another app?")
            .setMessage("Open this link outside NIGHT Browser?\n\n${uri.take(320)}")
            .setPositiveButton("Open") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    activity.startActivity(intent)
                } catch (_: ActivityNotFoundException) { onFailure() }
                catch (_: SecurityException) { onFailure() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun styledInput(value: String, password: Boolean): EditText =
        EditText(activity).apply {
            setText(value)
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(30, 23, 30, 23)
            inputType = if (password)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.rgb(33, 40, 51))
                setStroke(1, Color.rgb(66, 76, 93))
            }
        }

    private fun safeHost(value: String): String =
        runCatching { Uri.parse(value).host }.getOrNull().orEmpty()
            .ifBlank { "This website" }
}
