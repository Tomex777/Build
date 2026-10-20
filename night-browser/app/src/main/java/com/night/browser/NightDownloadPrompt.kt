package com.night.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * Agent 1 / Agent 4: attach browser.downloadHandler = prompt::offer in MainActivity, and render
 * NightDownloadPrompt(prompt) inside setContent. Avoid registering handler twice.
 */
class WebDownloadConsent(private val downloads: DownloadLibrary) {
    var pending by mutableStateOf<WebResponse?>(null)
        private set
    var eventMessage by mutableStateOf<String?>(null)
        private set

    fun offer(response: WebResponse) {
        // Gecko delegate delivers on the main thread. Close previous body if a site launches
        // multiple download requests while one is awaiting user consent.
        dismiss()
        pending = response
    }

    fun accept() {
        val response = pending ?: return
        pending = null
        downloads.acceptGeckoResponse(response) { message -> eventMessage = message }
    }

    fun dismiss() {
        pending?.let { response -> runCatching { response.body?.close() } }
        pending = null
    }

    fun consumeMessage(): String? = eventMessage.also { eventMessage = null }
}

@Composable
fun NightDownloadPrompt(state: WebDownloadConsent) {
    val context = LocalContext.current
    val message = state.eventMessage
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            state.consumeMessage()
        }
    }
    val response = state.pending ?: return
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
        title = { Text("Save downloaded file?") },
        text = {
            Column {
                Text(filename)
                Text(origin, modifier = Modifier.padding(top = 8.dp),
                    color = Color(0xFF9BA6B7))
                Text("Saved locally and listed in Library downloads.",
                    modifier = Modifier.padding(top = 12.dp), color = Color(0xFF9BA6B7))
            }
        },
        confirmButton = {
            TextButton(onClick = state::accept) { Text("Save file") }
        },
        dismissButton = {
            TextButton(onClick = state::dismiss) { Text("Cancel") }
        },
        containerColor = Color(0xFF171D27)
    )
}
