package dev.tomex.youtube.testapp

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tomex.youtube.core.NativeYouTubeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine = NativeYouTubeEngine()
    private lateinit var log: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 48, 24, 24) }
        val query = EditText(this).apply { setText("House MD"); hint = "Search query" }
        val videoId = EditText(this).apply { hint = "11-character video ID" }
        log = TextView(this).apply { text = "Engine probe. Search or enter a video ID."; textSize = 14f }
        val search = Button(this).apply { text = "Search real YouTube" }
        val resolve = Button(this).apply { text = "Resolve + read video/audio bytes + refresh" }
        column.addView(query); column.addView(search); column.addView(videoId); column.addView(resolve); column.addView(log)
        setContentView(ScrollView(this).apply { addView(column, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
        search.setOnClickListener { scope.launch {
            log.text = "Searching…"
            runCatching { engine.search(query.text.toString()) }.onSuccess { page ->
                val first = page.items.filterIsInstance<dev.tomex.youtube.api.SearchResult.Video>().firstOrNull()
                if (first != null) videoId.setText(first.id)
                log.text = "${page.items.size} real results; continuation=${page.continuation != null}\n" + page.items.take(12).joinToString("\n")
            }.onFailure { log.text = "Search failed: ${it.javaClass.simpleName}: ${it.message}" }
        } }
        resolve.setOnClickListener { scope.launch {
            log.text = "Resolving…"
            runCatching {
                val id = videoId.text.toString().trim()
                val details = engine.videoDetails(id)
                val result = engine.resolve(id)
                val video = result.videoOnly.firstOrNull() ?: result.progressive.firstOrNull()
                val audio = result.audioOnly.firstOrNull()
                val proofs = listOfNotNull(video, audio).map { format ->
                    "itag=${format.itag} ${format.width ?: "audio"}x${format.height ?: ""} ${format.codecs}: ${engine.probe(format)}"
                }
                val refresh = video?.let { engine.refreshMedia(id, it.stableIdentity) }
                "${details.title}\nclient=${result.client}\nformats=${result.formats.size}; adaptive video=${result.videoOnly.size}; audio=${result.audioOnly.size}\n" +
                    proofs.joinToString("\n") + "\nrefresh=${refresh?.stableIdentity} (${refresh?.url != null})\n" + result.diagnostics.joinToString("\n")
            }.onSuccess { log.text = it }.onFailure { log.text = "Transport proof failed: ${it.javaClass.simpleName}: ${it.message}" }
        } }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
