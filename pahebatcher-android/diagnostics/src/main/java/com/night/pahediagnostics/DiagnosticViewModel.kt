package com.night.pahediagnostics

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.net.URI

enum class BrowserMode { ANIMEPAHE, KWIK }

class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {
    private val store = DiagnosticSessionStore(application)
    private val engine = DiagnosticEngine(store)

    var sessions by mutableStateOf(store.snapshot())
        private set

    var checks by mutableStateOf<List<DiagnosticCheck>>(emptyList())
        private set

    var report by mutableStateOf("")
        private set

    var busy by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf<String?>(null)
        private set

    var browserOpen by mutableStateOf(false)
        private set

    var browserMode by mutableStateOf(BrowserMode.ANIMEPAHE)
        private set

    var browserUrl by mutableStateOf("")
        private set


    fun openAnimeBrowser() {
        browserMode = BrowserMode.ANIMEPAHE
        val host = sessions.animeHost.ifBlank { "animepahe.pw" }
        browserUrl = "https://$host/"
        statusMessage = null
        browserOpen = true
    }


    fun openKwikBrowser() {
        val target = sessions.lastKwikUrl
        val host = runCatching { URI(target).host.orEmpty().lowercase() }.getOrDefault("")

        if (target.isBlank()) {
            statusMessage =
                "No Kwik URL has been discovered yet. Run Trace AnimePahe -> Kwik so we can catch where discovery fails."
            return
        }
        if (!host.startsWith("kwik.") && !host.contains(".kwik.")) {
            statusMessage = "The discovered URL does not look like a Kwik URL."
            return
        }

        browserMode = BrowserMode.KWIK
        browserUrl = target
        statusMessage = null
        browserOpen = true
    }

    fun traceKwikDiscovery() {
        runTask("Tracing AnimePahe -> Kwik...") {
            val result = engine.runAnimePahe()
            applyResult(result)

            val kwik = result.kwikUrl
            if (kwik.isNullOrBlank()) {
                statusMessage =
                    "Kwik discovery failed. The result list/report shows the exact stage that failed."
                return@runTask
            }

            store.saveLastKwikUrl(kwik)
            refreshSessions()
            browserMode = BrowserMode.KWIK
            browserUrl = kwik
            statusMessage = "Kwik URL discovered. Opening verification browser."
            browserOpen = true
        }
    }

    fun closeBrowser() {
        browserOpen = false
        refreshSessions()
    }

    fun saveBrowserSession(currentUrl: String, cookie: String, userAgent: String) {
        val host = runCatching { URI(currentUrl).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) {
            statusMessage = "The browser has not reached a valid host yet."
            return
        }

        if (cookie.isBlank()) {
            statusMessage = "No cookies were found for this page yet. Finish the browser check first."
            return
        }

        when (browserMode) {
            BrowserMode.ANIMEPAHE -> {
                if (!host.contains("animepahe") && host != "pahe.win") {
                    statusMessage = "Return to an AnimePahe page before saving the session."
                    return
                }
                store.saveAnime(cookie, host, userAgent)
                statusMessage = "AnimePahe browser session saved."
            }
            BrowserMode.KWIK -> {
                if (!host.startsWith("kwik.") && !host.contains(".kwik.")) {
                    statusMessage = "Return to the Kwik page before saving the session."
                    return
                }
                store.saveKwik(cookie, host, userAgent)
                statusMessage = "Kwik browser session saved."
            }
        }

        browserOpen = false
        refreshSessions()
    }

    fun testAnimePahe() {
        runTask("Testing AnimePahe...") {
            applyResult(engine.runAnimePahe())
        }
    }

    fun testKwik() {
        val target = sessions.lastKwikUrl
        if (target.isBlank()) {
            statusMessage =
                "No Kwik URL is available yet. Trace AnimePahe -> Kwik first."
            return
        }

        store.saveLastKwikUrl(target)
        refreshSessions()
        runTask("Testing Kwik...") {
            applyResult(engine.runKwik(target))
        }
    }

    fun runFullPipeline() {
        runTask("Running the full pipeline...") {
            applyResult(engine.runFullPipeline())
        }
    }

    fun clearSessions() {
        store.clear()
        sessions = store.snapshot()
        checks = emptyList()
        report = ""
        statusMessage = "Saved diagnostic browser sessions cleared."
    }

    fun clearStatus() {
        statusMessage = null
    }

    fun animeReferer(): String {
        val host = sessions.animeHost.ifBlank { "animepahe.pw" }
        return "https://$host/"
    }

    private fun applyResult(result: DiagnosticResult) {
        checks = result.checks
        report = result.report
        refreshSessions()
    }

    private fun refreshSessions() {
        sessions = store.snapshot()
    }

    private fun runTask(message: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        statusMessage = message
        viewModelScope.launch {
            try {
                block()
                if (statusMessage == message) {
                    statusMessage = "Finished. Copy the report if you want me to inspect it."
                }
            } catch (error: Exception) {
                statusMessage = error.message ?: "Diagnostic task failed."
            } finally {
                busy = false
                refreshSessions()
            }
        }
    }
}
