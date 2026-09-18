package com.night.kwikmanual

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.net.URI

class KwikManualViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ManualSessionStore(application)
    private val engine = KwikManualEngine(store)

    var kwikUrl by mutableStateOf(store.lastUrl())
    var referer by mutableStateOf(store.referer())
    var browserOpen by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var checks by mutableStateOf<List<ManualCheck>>(emptyList())
        private set
    var report by mutableStateOf("")
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var session by mutableStateOf(store.snapshot())
        private set

    fun updateKwikUrl(value: String) {
        kwikUrl = value
    }

    fun updateReferer(value: String) {
        referer = value
    }

    fun openVerification() {
        val target = kwikUrl.trim()
        val host = runCatching { URI(target).host.orEmpty().lowercase() }.getOrDefault("")
        if (target.isBlank()) {
            status = "Paste a Kwik release URL first."
            return
        }
        if (!host.startsWith("kwik.") && !host.contains(".kwik.")) {
            status = "That URL does not look like a Kwik release link."
            return
        }

        store.saveUrl(target, referer.trim())
        refresh()
        status = null
        browserOpen = true
    }

    fun closeBrowser() {
        browserOpen = false
        refresh()
    }

    fun saveBrowserSession(currentUrl: String, cookie: String, userAgent: String) {
        val host = runCatching { URI(currentUrl).host.orEmpty().lowercase() }.getOrDefault("")
        if (!host.startsWith("kwik.") && !host.contains(".kwik.")) {
            status = "Return to the Kwik page before saving the session."
            return
        }
        if (cookie.isBlank()) {
            status = "No cookies were found yet. Complete the browser challenge first."
            return
        }

        store.saveUrl(kwikUrl.trim(), referer.trim())
        store.saveSession(currentUrl, cookie, userAgent)
        refresh()
        browserOpen = false
        status = "Kwik session saved. Run the downstream test."
    }

    fun runTest() {
        val target = kwikUrl.trim()
        if (target.isBlank()) {
            status = "Paste a Kwik release URL first."
            return
        }

        store.saveUrl(target, referer.trim())
        busy = true
        status = "Testing Kwik -> HLS..."
        viewModelScope.launch {
            try {
                val result = engine.test(target, referer.trim())
                checks = result.checks
                report = result.report
                status = "Finished. Copy the report and send it back."
            } catch (error: Exception) {
                status = error.message ?: "Kwik test failed."
            } finally {
                busy = false
                refresh()
            }
        }
    }

    fun clearSession() {
        store.clear()
        kwikUrl = ""
        referer = "https://animepahe.pw/"
        checks = emptyList()
        report = ""
        status = "Saved Kwik session cleared."
        refresh()
    }

    fun clearStatus() {
        status = null
    }

    private fun refresh() {
        session = store.snapshot()
    }
}
