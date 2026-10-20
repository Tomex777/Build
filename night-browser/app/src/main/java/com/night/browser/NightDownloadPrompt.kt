package com.night.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.WebResponse
import android.net.Uri

/**
 * Explicit confirmation for all Gecko downloads, including those originating in private tabs.
 * IMPORTANT: the WebResponse stream must be consumed or closed; holding it too long may cause
 * the remote site to time out. The modal does not read or persist data until Save is tapped.
 *
 * Agent 1 / Agent 4: after private tabs and Gecko controller are integrated, bind
 * browser.downloadHandler = { tab, response -> prompt.offer(response, tab.isPrivate) }.
 * Render NightDownloadPrompt(prompt) inside setContent. Do not register twice.
 * Private downloads are sent directly to a user-selected SAF document, never Library.
 */
class WebDownloadConsent(private val downloads: DownloadLibrary) {
    var pending by mutableStateOf<WebResponse?>(null)
        private set
    var eventMessage by mutableStateOf<String?>(null)
        private set
    var privateMode by mutableStateOf(false)
        private set
    var selectingDestination by mutableStateOf(false)
        private set
    var isRepeatDownload by mutableStateOf(false)
        private set

    fun offer(response: WebResponse, privateMode: Boolean = false) {
        // The SAF picker belongs to the pending original stream. Never swap a second
        // response underneath it: that would save the wrong bytes under its filename.
        if (selectingDestination) {
            runCatching { response.body?.close() }
            eventMessage = "Finish saving the previous file first."
            return
        }
        // Gecko delegate delivers on the main thread. Close previous body if a site launches
        // multiple download requests while one is awaiting user consent.
        dismiss()
        this.privateMode = privateMode
        val disposition = response.headers.entries.firstOrNull {
            it.key.equals("content-disposition", true)
        }?.value
        val mime = response.headers.entries.firstOrNull {
            it.key.equals("content-type", true)
        }?.value?.substringBefore(';')
        val filename = DownloadFileNames.safe(
            GeckoDownloadNames.fromDisposition(disposition)
                ?: Uri.parse(response.uri).lastPathSegment, mime
        )
        isRepeatDownload = !privateMode &&
            downloads.existingCompletedDuplicate(response.uri, filename) != null
        pending = response
    }

    /** Regular tabs may add a file to the persistent Library; private tabs cannot. */
    fun accept() {
        if (privateMode) {
            eventMessage = "Choose where to save this private file."
            return
        }
        val response = pending ?: return
        pending = null
        downloads.acceptGeckoResponse(response) { message -> eventMessage = message }
    }

    fun beginPrivateExport() {
        if (privateMode && pending != null) selectingDestination = true
    }

    fun finishPrivateExport(destination: Uri?) {
        val response = pending
        pending = null
        selectingDestination = false
        privateMode = false
        if (response == null) return
        if (destination == null) {
            runCatching { response.body?.close() }
        } else {
            downloads.exportPrivateGeckoResponse(response, destination) {
                message -> eventMessage = message
            }
        }
    }

    fun dismiss() {
        pending?.let { response -> runCatching { response.body?.close() } }
        pending = null
        privateMode = false
        selectingDestination = false
    }

    fun consumeMessage(): String? = eventMessage.also { eventMessage = null }
}

@Composable
fun NightDownloadPrompt(state: WebDownloadConsent) {
    val context = LocalContext.current
    val privateExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> state.finishPrivateExport(uri) }
    DisposableEffect(state) {
        onDispose { state.dismiss() }
    }
    val message = state.eventMessage
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            state.consumeMessage()
        }
    }
    val response = state.pending ?: return
    if (state.selectingDestination) return
    val contentDisposition = response.headers.entries.firstOrNull {
        it.key.equals("content-disposition", true)
    }?.value
    val mime = response.headers.entries.firstOrNull {
        it.key.equals("content-type", true)
    }?.value?.substringBefore(';')
    val filename = DownloadFileNames.safe(
        GeckoDownloadNames.fromDisposition(contentDisposition)
            ?: Uri.parse(response.uri).lastPathSegment, mime
    )
    val origin = GeckoDownloadNames.redactUrl(response.uri)

    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text(if (state.privateMode) "Save private file?" else "Save downloaded file?") },
        text = {
            Column {
                Text(filename)
                Text(origin, modifier = Modifier.padding(top = 8.dp),
                    color = Color(0xFF9BA6B7))
                Text(
                    if (state.privateMode)
                        "Choose a location. This file will not appear in Library or history."
                    else "Saved locally and listed in Library downloads.",
                    modifier = Modifier.padding(top = 12.dp), color = Color(0xFF9BA6B7)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (state.privateMode) {
                    state.beginPrivateExport()
                    try {
                        privateExport.launch(filename)
                    } catch (_: Exception) {
                        state.finishPrivateExport(null)
                        Toast.makeText(context, "No document picker is available.",
                            Toast.LENGTH_LONG).show()
                    }
                } else state.accept()
            }) { Text(if (state.privateMode) "Choose location" else "Save file") }
        },
        dismissButton = {
            TextButton(onClick = state::dismiss) { Text("Cancel") }
        },
        containerColor = Color(0xFF171D27)
    )
}
