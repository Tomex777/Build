package com.tomex777.annie

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/** Safe, native CBZ/ZIP import. Entry names are used only for ordering, never as output paths. */
internal object AnnieMangaArchive {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private const val MAX_PAGES = 1_200
    private const val MAX_ENTRIES = 5_000
    private const val MAX_PAGE_BYTES = 32L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 220L * 1024 * 1024
    private const val LIBRARY_PREFS = "annie_manga_library"

    fun savedItems(context: Context): List<CatalogItem> {
        val rows = context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
            .getString("items", null)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val id = item.optInt("id", Int.MIN_VALUE)
                val title = item.optString("title").trim()
                if (id == Int.MIN_VALUE || title.isBlank() || !existing(context, CatalogItem(
                        id, "MANGA", title, item.optString("image"), item.optInt("year").takeIf { it > 0 },
                        item.optString("status", "UNKNOWN"), null, item.optInt("chapters").takeIf { it > 0 },
                    )).isFile) continue
                add(CatalogItem(
                    id = id,
                    mediaType = "MANGA",
                    title = title,
                    image = item.optString("image"),
                    year = item.optInt("year").takeIf { it > 0 },
                    status = item.optString("status", "UNKNOWN"),
                    episodes = null,
                    chapters = item.optInt("chapters").takeIf { it > 0 },
                    sourceLabel = "Local archive",
                ))
            }
        }.sortedBy { it.title.lowercase() }
    }

    fun existing(context: Context, item: CatalogItem): File =
        File(File(context.filesDir, "annie-manga-library"), "${item.id}.cbz")

    fun copyAndValidate(context: Context, source: Uri, item: CatalogItem): File {
        val root = File(context.filesDir, "annie-manga-library").apply { mkdirs() }
        val destination = existing(context, item)
        val staging = File(root, ".${item.id}-${UUID.randomUUID()}.tmp")
        val validationRoot = File(context.cacheDir, "annie-manga-validate-${UUID.randomUUID()}")
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: error("Annie could not open that archive")
            input.use { copyCapped(it, staging) }
            FileInputStream(staging).use { unpack(it, validationRoot) }
            staging.copyTo(destination, overwrite = true)
            saveItem(context, item)
            return destination
        } finally {
            staging.delete()
            validationRoot.deleteRecursively()
        }
    }

    fun unpack(input: InputStream, outputDirectory: File): List<File> {
        outputDirectory.deleteRecursively()
        require(outputDirectory.mkdirs() || outputDirectory.isDirectory) { "Could not prepare the manga reader cache" }
        val extracted = mutableListOf<Pair<String, File>>()
        var totalBytes = 0L
        var entryCount = 0
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) { "This archive has too many entries" }
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val extension = entry.name.substringAfterLast('.', "").lowercase()
                    val isPage = extension in imageExtensions
                    if (isPage) require(extracted.size < MAX_PAGES) { "This chapter has too many image pages" }
                    val stagedPage = if (isPage) File(outputDirectory, "${UUID.randomUUID()}.$extension") else null
                    var entryBytes = 0L
                    val output = stagedPage?.let(::FileOutputStream)?.let(::BufferedOutputStream)
                    try {
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            if (isPage) require(entryBytes <= MAX_PAGE_BYTES) { "A manga page exceeds the safe size limit" }
                            require(totalBytes <= MAX_TOTAL_BYTES) { "This chapter exceeds the safe unpacked size limit" }
                            output?.write(buffer, 0, count)
                        }
                    } finally {
                        output?.close()
                    }
                    if (isPage) {
                        require(entryBytes > 0L) { "A manga page is empty" }
                        extracted += entry.name to checkNotNull(stagedPage)
                    }
                    zip.closeEntry()
                }
            }
            require(extracted.isNotEmpty()) { "The selected ZIP has no supported manga page images" }
            val sorted = extracted.sortedWith { first, second -> naturalCompare(first.first, second.first) }
            return sorted.mapIndexed { index, (_, staged) ->
                val page = File(outputDirectory, "page-${(index + 1).toString().padStart(5, '0')}.${staged.extension}")
                check(staged.renameTo(page)) { "Could not prepare manga page ${index + 1}" }
                page
            }
        } catch (failure: Throwable) {
            outputDirectory.deleteRecursively()
            throw failure
        }
    }

    private fun copyCapped(input: InputStream, destination: File) {
        var copied = 0L
        BufferedInputStream(input).use { source ->
            BufferedOutputStream(FileOutputStream(destination)).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= MAX_ARCHIVE_BYTES) { "Manga archive is larger than Annie's import limit" }
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun saveItem(context: Context, item: CatalogItem) {
        val rows = context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
        val existing = rows.getString("items", null)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
        val merged = JSONArray()
        for (index in 0 until existing.length()) {
            val row = existing.optJSONObject(index) ?: continue
            if (row.optInt("id") != item.id) merged.put(row)
        }
        merged.put(JSONObject()
            .put("id", item.id)
            .put("title", item.title)
            .put("image", item.image)
            .put("year", item.year ?: JSONObject.NULL)
            .put("status", item.status)
            .put("chapters", item.chapters ?: JSONObject.NULL))
        check(rows.edit().putString("items", merged.toString()).commit()) { "Could not save the local manga library" }
    }

    private fun naturalCompare(first: String, second: String): Int {
        var a = 0
        var b = 0
        while (a < first.length && b < second.length) {
            val firstDigit = first[a].isDigit()
            val secondDigit = second[b].isDigit()
            if (firstDigit && secondDigit) {
                var aEnd = a
                var bEnd = b
                while (aEnd < first.length && first[aEnd].isDigit()) aEnd++
                while (bEnd < second.length && second[bEnd].isDigit()) bEnd++
                val aDigits = first.substring(a, aEnd).trimStart('0').ifEmpty { "0" }
                val bDigits = second.substring(b, bEnd).trimStart('0').ifEmpty { "0" }
                val numeric = aDigits.length.compareTo(bDigits.length).takeIf { it != 0 }
                    ?: aDigits.compareTo(bDigits).takeIf { it != 0 }
                if (numeric != null) return numeric
                a = aEnd
                b = bEnd
            } else {
                val comparison = first[a].lowercaseChar().compareTo(second[b].lowercaseChar())
                if (comparison != 0) return comparison
                a++
                b++
            }
        }
        return (first.length - a).compareTo(second.length - b)
    }
}

internal object AnnieMangaProgress {
    private const val PREFS = "annie_manga_progress"
    fun page(context: Context, itemId: Int): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("$itemId", 0)

    fun save(context: Context, itemId: Int, page: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("$itemId", page).apply()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AnnieMangaReaderDialog(item: CatalogItem, archive: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pages by remember(archive) { mutableStateOf<List<File>>(emptyList()) }
    var error by remember(archive) { mutableStateOf<String?>(null) }
    var loading by remember(archive) { mutableStateOf(true) }
    var rightToLeft by remember(item.id) { mutableStateOf(true) }
    val pageScope = rememberCoroutineScope()
    val savedPage = remember(item.id) { AnnieMangaProgress.page(context, item.id) }
    var positionRestored by remember(archive) { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0) { pages.size.coerceAtLeast(1) }
    val pageCache = remember(archive) { File(context.cacheDir, "annie-manga-reader-${UUID.randomUUID()}") }

    DisposableEffect(pageCache) { onDispose { pageCache.deleteRecursively() } }
    LaunchedEffect(archive) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                AnnieMangaArchive.unpack(FileInputStream(archive), pageCache)
            }
        }.onSuccess { pages = it }
            .onFailure { error = it.message ?: "Could not open this manga archive" }
        loading = false
    }
    LaunchedEffect(pagerState.currentPage, pages.size) {
        if (pages.isNotEmpty() && positionRestored) AnnieMangaProgress.save(context, item.id, pagerState.currentPage)
    }
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) {
            pagerState.scrollToPage(savedPage.coerceIn(0, pages.lastIndex))
            positionRestored = true
        }
    }
    BackHandler(onBack = onDismiss)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF101820)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Local chapter archive", color = Color(0xFF9FB0C0), fontSize = 11.sp)
                    }
                    IconButton(onClick = { rightToLeft = !rightToLeft }, modifier = Modifier.semantics { contentDescription = "Toggle reading direction" }) {
                        Text(if (rightToLeft) "RTL" else "LTR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) { Text("×", color = Color.White, fontSize = 24.sp) }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(color = Color(0xFF36A8F4))
                        error != null -> Text(error.orEmpty(), color = Color(0xFFE4EAF0), modifier = Modifier.padding(24.dp))
                        pages.isNotEmpty() -> HorizontalPager(
                            state = pagerState,
                            reverseLayout = rightToLeft,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            AsyncImage(
                                model = pages[page],
                                contentDescription = "Page ${page + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF101820)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = pages.isNotEmpty() && pagerState.currentPage > 0,
                        onClick = { if (pagerState.currentPage > 0) pageScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.width(104.dp).semantics { contentDescription = "Previous page" },
                    ) { Text("‹ Previous", color = Color.White, fontSize = 12.sp) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (pages.isEmpty()) "${item.title}" else "${pagerState.currentPage + 1} / ${pages.size}",
                        color = Color(0xFFCFD8E1), fontSize = 12.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        enabled = pages.isNotEmpty() && pagerState.currentPage < pages.lastIndex,
                        onClick = { if (pagerState.currentPage < pages.lastIndex) pageScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier.width(104.dp).semantics { contentDescription = "Next page" },
                    ) { Text("Next ›", color = Color.White, fontSize = 12.sp) }
                }
            }
        }
    }
}
