package com.night.sora.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.ui.theme.*
import org.json.JSONObject

private data class MemeDetails(
    val description: String = "",
    val subreddit: String = "",
    val author: String = "",
    val score: Int = 0,
    val comments: Int = 0,
    val permalink: String = "",
    val imageUrl: String = "",
)

@Composable
fun MemeDetailScreen(
    selection: ExtensionMediaSelection,
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var details by remember(selection) { mutableStateOf(MemeDetails()) }
    var loading by remember(selection) { mutableStateOf(true) }
    var error by remember(selection) { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    val saved = isSaved(selection)

    LaunchedEffect(selection, refreshNonce) {
        loading = true
        error = null
        val extension = extensions.firstOrNull { it.packageName == selection.extensionPackage }
        if (extension == null) {
            loading = false
            error = "The meme source is no longer installed."
            return@LaunchedEffect
        }
        manager.call(
            extension,
            ExtensionContract.Method.DETAILS,
            JSONObject().put("sourceId", selection.sourceId).put("id", selection.id).toString(),
        ) { result ->
            val raw = result.getOrNull()
            if (raw == null) {
                error = result.exceptionOrNull()?.message ?: "Couldn’t load this meme."
            } else {
                details = parseMemeDetails(raw)
            }
            loading = false
        }
    }

    fun share() {
        val url = details.permalink.ifBlank { details.imageUrl.ifBlank { selection.artworkUrl.orEmpty() } }
        val text = buildString {
            append(selection.title)
            if (url.isNotBlank()) append("\n").append(url)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share meme")) }
    }

    fun openSource() {
        val url = details.permalink
        if (url.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        containerColor = SoraBg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text("Meme", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onToggleSaved(selection) }) {
                    Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (saved) "Remove from library" else "Save to library", tint = if (saved) SoraAccent else SoraText)
                }
                IconButton(onClick = ::share) { Icon(Icons.Rounded.Share, "Share") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            val image = details.imageUrl.ifBlank { selection.artworkUrl.orEmpty() }
            Box(
                Modifier.fillMaxWidth().background(Color(0xFFF0EDE5)),
                contentAlignment = Alignment.Center,
            ) {
                if (image.isNotBlank()) {
                    AsyncImage(
                        model = image,
                        contentDescription = selection.title,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 620.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Rounded.ImageNotSupported, null, tint = Color(0xFF6C685F), modifier = Modifier.size(54.dp).padding(vertical = 80.dp))
                }
            }

            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Text(selection.title, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)

                val sourceLine = buildList {
                    details.subreddit.takeIf(String::isNotBlank)?.let(::add)
                    details.author.takeIf(String::isNotBlank)?.let { add("u/$it") }
                    if (details.score > 0) add("${details.score} points")
                    if (details.comments > 0) add("${details.comments} comments")
                }.joinToString(" · ")
                Text(
                    sourceLine.ifBlank { selection.subtitle },
                    color = SoraMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 7.dp),
                )

                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 18.dp), color = SoraAccent)
                }
                if (error != null) {
                    Surface(color = SoraSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudOff, null, tint = SoraMuted)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text("Live details unavailable", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(error.orEmpty(), color = SoraMuted, fontSize = 10.sp)
                            }
                            TextButton(onClick = { refreshNonce++ }) { Text("Retry") }
                        }
                    }
                }

                if (details.description.isNotBlank() && details.description != selection.title) {
                    Text(details.description, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 16.dp))
                }

                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onToggleSaved(selection) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (saved) "Saved" else "Save")
                    }
                    OutlinedButton(onClick = ::share, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }

                if (details.permalink.isNotBlank()) {
                    TextButton(onClick = ::openSource, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Open original on Reddit")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(90.dp))
            }
        }
    }
}

private fun parseMemeDetails(raw: String): MemeDetails = runCatching {
    val item = JSONObject(raw)
    MemeDetails(
        description = item.optString("description"),
        subreddit = item.optString("subreddit"),
        author = item.optString("author"),
        score = item.optInt("score"),
        comments = item.optInt("comments"),
        permalink = item.optString("permalink"),
        imageUrl = item.optString("imageUrl"),
    )
}.getOrDefault(MemeDetails())
