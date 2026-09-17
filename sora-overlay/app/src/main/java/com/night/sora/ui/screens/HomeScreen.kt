package com.night.sora.ui.screens

import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.isCatalogProvider
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.data.BibleRepository
import com.night.sora.data.CachedMediaRecord
import com.night.sora.data.MediaCatalogCache
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.MediaProgressEntry
import com.night.sora.model.ListeningSignal
import com.night.sora.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

private data class HomeBrowseCard(
    val id: String,
    val sourceId: String,
    val extensionPackage: String,
    val type: ContentType,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
) {
    fun selection() = ExtensionMediaSelection(id, sourceId, extensionPackage, type, title, subtitle, artworkUrl)
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    progressEntries: List<MediaProgressEntry>,
    listeningSignals: List<ListeningSignal>,
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    onOpenSelection: (ExtensionMediaSelection) -> Unit,
    onResumeProgress: (MediaProgressEntry) -> Unit,
    onOpenBible: () -> Unit,
    onSearch: () -> Unit,
) {
    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
    val bibleRepository = remember { BibleRepository(context.applicationContext) }
    val lastBibleReading = bibleRepository.lastReading()
    val bibleTranslation = bibleRepository.selectedTranslation()
    var networkEpoch by remember { mutableIntStateOf(0) }
    var recommendations by remember {
        mutableStateOf(
            listOf(ContentType.ANIME, ContentType.MANGA, ContentType.MOVIE)
                .flatMap { cachedHomeType(mediaCache, it).take(4) }
        )
    }
    var music by remember { mutableStateOf(cachedHomeType(mediaCache, ContentType.MUSIC).take(8)) }
    var memes by remember { mutableStateOf(cachedHomeType(mediaCache, ContentType.MEME).take(4)) }

    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { mainHandler.post { networkEpoch++ } }
        }
        val registered = runCatching { connectivity.registerDefaultNetworkCallback(callback); true }.getOrDefault(false)
        onDispose { if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }

    LaunchedEffect(extensions, networkEpoch) {
        val result = mutableStateListOf<HomeBrowseCard>().apply { addAll(recommendations) }
        loadHomeType(extensions, manager, mediaCache, ContentType.ANIME) { cards ->
            result.removeAll { it.type == ContentType.ANIME }; result.addAll(cards.take(4)); recommendations = result.toList()
        }
        loadHomeType(extensions, manager, mediaCache, ContentType.MANGA) { cards ->
            result.removeAll { it.type == ContentType.MANGA }; result.addAll(cards.take(4)); recommendations = result.toList()
        }
        loadHomeType(extensions, manager, mediaCache, ContentType.MOVIE) { cards ->
            result.removeAll { it.type == ContentType.MOVIE }; result.addAll(cards.take(4)); recommendations = result.toList()
        }
        loadHomeType(extensions, manager, mediaCache, ContentType.MUSIC) { cards -> music = cards.take(8) }
        loadHomeType(extensions, manager, mediaCache, ContentType.MEME) { cards -> memes = cards.take(4) }
    }

    val continueEntries = progressEntries
        .filter { it.progress < .999f }
        .sortedByDescending { it.updatedAt }
        .take(8)
    val recentMusic = remember(music, listeningSignals) {
        val artistOrder = listeningSignals
            .filter { it.lastPlayedEpochMs > 0L }
            .sortedByDescending { it.lastPlayedEpochMs }
            .mapIndexed { index, signal -> signal.artistName.trim().lowercase() to index }
            .toMap()
        music
            .filter { card -> card.subtitle.substringBefore(" · ").trim().lowercase() in artistOrder }
            .sortedBy { card -> artistOrder[card.subtitle.substringBefore(" · ").trim().lowercase()] ?: Int.MAX_VALUE }
            .take(8)
    }
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(54.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                SoraMark()
                Spacer(Modifier.width(10.dp))
                Text("Sora", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            }
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item {
                Text(
                    greeting.uppercase(),
                    color = SoraMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 2.dp),
                )
            }

            item { HomeSectionHeader("Continue", "Pick up exactly where you stopped") }
            item {
                if (continueEntries.isEmpty()) {
                    Text("Nothing in progress yet.", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(
                            continueEntries,
                            key = { "${it.extensionPackage}:${it.sourceId}:${it.mediaId}:${it.itemId}" },
                        ) { entry ->
                            ContinueCard(entry) { onResumeProgress(entry) }
                        }
                    }
                }
            }

            item { HomeSectionHeader("Explore", "Fresh picks from across Sora") }
            item {
                if (recommendations.isEmpty()) {
                    Text("Fresh catalog picks will appear here when sources are available.", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recommendations.take(10), key = { "${it.extensionPackage}:${it.sourceId}:${it.id}" }) { card ->
                            HomePosterTile(card) { onOpenSelection(card.selection()) }
                        }
                    }
                }
            }

            if (recentMusic.isNotEmpty()) {
                item { HomeSectionHeader("Recently played", "From your actual listening history") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentMusic, key = { "music-${it.id}" }) { card ->
                            HomeSquareTile(card) { onOpenSelection(card.selection()) }
                        }
                    }
                }
            }

            if (memes.isNotEmpty()) {
                item { HomeSectionHeader("From your meme feed", "A fresh item from your active source") }
                item { MemeStrip(memes.first()) { onOpenSelection(memes.first().selection()) } }
            }

            item {
                HomeSectionHeader(
                    "Bible",
                    if (lastBibleReading != null) "Continue your reading" else "Read in your chosen translation",
                    "Open",
                    onSee = onOpenBible,
                )
            }
            item {
                Surface(
                    color = Color(0xFF151513),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clickable(onClick = onOpenBible),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(SoraSurfaceHigh, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MenuBook, null, tint = SoraAccent)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                            Text(
                                if (lastBibleReading != null) "${lastBibleReading.book} ${lastBibleReading.chapter}:${lastBibleReading.verse}" else "Open Bible",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (lastBibleReading != null) "Resume · ${bibleTranslation.shortLabel}" else "Choose a book · ${bibleTranslation.shortLabel}",
                                color = SoraMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = SoraMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SoraMark() {
    Box(Modifier.size(26.dp)) {
        Box(Modifier.align(Alignment.TopCenter).width(20.dp).height(10.dp).background(SoraText, RoundedCornerShape(10.dp, 10.dp, 3.dp, 3.dp)))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).width(20.dp).height(2.dp).background(SoraAccent, RoundedCornerShape(99.dp)))
    }
}

@Composable
private fun HomeSectionHeader(title: String, subtitle: String, see: String? = null, onSee: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (see != null && onSee != null) {
            TextButton(onClick = onSee, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text(see, color = SoraMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ContinueCard(entry: MediaProgressEntry, onClick: () -> Unit) {
    Row(Modifier.width(286.dp).height(154.dp).clip(RoundedCornerShape(18.dp)).background(SoraSurface).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxHeight().width(118.dp).background(Color(0xFF2A2925)), contentAlignment = Alignment.Center) {
            if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(entry.title.take(1), fontSize = 36.sp, fontWeight = FontWeight.Black, color = SoraMuted)
        }
        Column(Modifier.fillMaxHeight().weight(1f).padding(13.dp)) {
            Text(entry.contentType.label.uppercase(), color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
            Text(entry.title, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
            Text(entry.itemLabel, color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(progress = { entry.progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = SoraAccent, trackColor = Color(0xFF49473F))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).background(SoraAccent, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = SoraAccentInk, modifier = Modifier.size(13.dp)) }
                Text("Resume · ${(entry.progress * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun HomePosterTile(card: HomeBrowseCard, onClick: () -> Unit) {
    Column(Modifier.width(126.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(126f / 178f).clip(RoundedCornerShape(8.dp)).background(SoraSurface)) {
            if (!card.artworkUrl.isNullOrBlank()) AsyncImage(card.artworkUrl, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(color = Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(5.dp), modifier = Modifier.padding(7.dp).align(Alignment.TopStart)) {
                Text(card.type.label.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
        Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeSquareTile(card: HomeBrowseCard, onClick: () -> Unit) {
    Column(Modifier.width(126.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(126.dp).clip(RoundedCornerShape(8.dp)).background(SoraSurface)) {
            if (!card.artworkUrl.isNullOrBlank()) AsyncImage(card.artworkUrl, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MemeStrip(meme: HomeBrowseCard, onClick: () -> Unit) {
    Surface(
        color = Color(0xFFF0EDE5),
        contentColor = Color(0xFF141412),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clickable(onClick = onClick),
    ) {
        Column {
            Text("Sora · meme feed", color = Color(0xFF656158), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
            Text(meme.title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            Box(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFFC5C0B3)), contentAlignment = Alignment.Center) {
                if (!meme.artworkUrl.isNullOrBlank()) AsyncImage(meme.artworkUrl, meme.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.TagFaces, null, modifier = Modifier.size(66.dp), tint = Color(0xFF5C594F))
            }
            Text("Open", color = Color(0xFF6D685D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

private fun cachedHomeType(cache: MediaCatalogCache, type: ContentType): List<HomeBrowseCard> =
    cache.read(type).map { row -> HomeBrowseCard(row.id, row.sourceId, row.extensionPackage, type, row.title, row.subtitle, row.artworkUrl) }

private fun loadHomeType(
    extensions: List<InstalledExtension>,
    manager: ExtensionManager,
    cache: MediaCatalogCache,
    type: ContentType,
    callback: (List<HomeBrowseCard>) -> Unit,
) {
    val key = when (type) { ContentType.MOVIE -> "movie"; ContentType.MEME -> "memes"; else -> type.name.lowercase() }
    val cached = cachedHomeType(cache, type)
    val providers = extensions.flatMap { ext ->
        ext.descriptor?.sources.orEmpty()
            .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }
            .map { source -> ext to source }
    }
    if (providers.isEmpty()) return callback(cached)

    val collected = MutableList(providers.size) { emptyList<HomeBrowseCard>() }
    var completed = 0
    providers.forEachIndexed { index, (ext, source) ->
        manager.call(ext, ExtensionContract.Method.BROWSE, JSONObject().put("sourceId", source.id).put("type", key).toString()) { result ->
            collected[index] = result.getOrNull()?.let { raw ->
                runCatching {
                    val array = JSONArray(raw)
                    buildList {
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            add(HomeBrowseCard(item.optString("id"), source.id, ext.packageName, type, item.optString("title", "Untitled"), item.optString("subtitle"), homeArtwork(item)))
                        }
                    }
                }.getOrDefault(emptyList())
            }.orEmpty()
            completed++
            if (completed == providers.size) {
                val fresh = collected.flatten().distinctBy { it.title.trim().lowercase() }
                if (fresh.isNotEmpty()) {
                    cache.write(type, fresh.map { CachedMediaRecord(it.id, it.title, it.subtitle, it.artworkUrl, it.sourceId, it.extensionPackage) })
                    callback(fresh)
                } else callback(cached)
            }
        }
    }
}

private fun homeArtwork(item: JSONObject): String? = listOf("artworkUrl", "poster", "posterUrl", "image", "imageUrl", "thumbnail", "cover", "coverUrl")
    .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.startsWith("http://") || it.startsWith("https://") } }
