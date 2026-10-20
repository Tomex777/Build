package com.night.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

// New start page owns its own palette until Agent 1 centralizes shared design tokens.
// All icons below are first-party Compose vectors; no Omni source or assets are copied.
// Match the browser chrome and tabs; reserve violet for focused/active controls.
private val HomeBg = Color(0xFF0C1017)
private val HomeTile = Color(0xFF171D27)
private val HomeStroke = Color(0xFF2B3441)
private val HomeText = Color(0xFFF3F5F9)
private val HomeSubtle = Color(0xFFA6AFBD)
private val HomeAccent = Color(0xFFB9BEFF)

/**
 * Standalone Home. Integration contract for Agent 1:
 * HOME -> NightStartPage(browser, onOpen = { browser.navigate(it); page = WEB },
 *                        onJourneys = { page = JOURNEYS })
 * On HOME hide the old bottom address field, to avoid a duplicate omnibar.
 * This component never mutates MainActivity, Gecko controller or Journey store.
 */
@Composable
fun NightStartPage(
    browser: BrowserController,
    onOpen: (String) -> Unit,
    onJourneys: () -> Unit
) {
    val context = LocalContext.current
    val shortcuts = remember(context) { StartSiteStore(context.applicationContext) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    var search by rememberSaveable { mutableStateOf("") }
    var invalidAddress by rememberSaveable { mutableStateOf(false) }
    val compactHome = LocalConfiguration.current.screenHeightDp < 620
    var editMode by rememberSaveable { mutableStateOf(false) }
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StartSite?>(null) }

    fun submit() {
        val entered = search.trim()
        if (entered.isBlank()) return
        if (AddressInput.resolve(entered) == null) {
            invalidAddress = true
            return
        }
        invalidAddress = false
        onOpen(entered)
        keyboard?.hide()
        focus.clearFocus()
        search = ""
    }

    // The store already contains local records; Home does not contact a third-party
    // service to enumerate or classify browsing history.
    val recentPages = browser.store.journeys.asSequence()
        .flatMap { it.nodes.asSequence() }
        .filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
        .sortedByDescending { it.timestamp }.distinctBy { it.url }.take(3).toList()
    val latestJourney = browser.store.journeys
        .filter { it.nodes.isNotEmpty() }
        .maxByOrNull { it.nodes.maxOfOrNull(JourneyNode::timestamp) ?: 0L }

    // Filter only user-owned shortcuts and existing on-device history.
    // Never send per-keystroke suggestion requests to a search provider.
    val localSuggestions = if (search.isBlank()) emptyList() else {
        val query = search.trim()
        (shortcuts.sites.asSequence() +
            recentPages.asSequence().map {
                StartSite(it.title.ifBlank { it.url }, it.url)
            })
            .filter { it.title.contains(query, ignoreCase = true) ||
                it.url.contains(query, ignoreCase = true) }
            .distinctBy { it.url.lowercase() }
            .take(4)
            .toList()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(HomeBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (LocalConfiguration.current.screenWidthDp < 360) 16.dp else 22.dp)
        ) {
            Spacer(Modifier.height(if (compactHome) 16.dp else 27.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = HomeTile,
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, HomeStroke),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_night),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Text(
                    "NIGHT", fontSize = 27.sp, letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold, color = HomeText
                )
            }
            Spacer(Modifier.height(if (compactHome) 22.dp else 32.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
                color = HomeTile,
                border = BorderStroke(1.dp, HomeStroke)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null,
                        tint = HomeAccent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(13.dp))
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it; invalidAddress = false },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = HomeText, fontSize = 15.sp),
                        cursorBrush = SolidColor(HomeAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (search.isBlank()) {
                                    Text("Search or enter address", fontSize = 15.sp,
                                        color = HomeSubtle, maxLines = 1)
                                }
                                inner()
                            }
                        }
                    )
                    if (search.isBlank()) {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.text?.toString()?.let {
                                    search = it
                                    invalidAddress = false
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste address",
                                tint = HomeSubtle, modifier = Modifier.size(19.dp))
                        }
                    } else {
                        IconButton(
                            onClick = { submit() },
                            modifier = Modifier.size(44.dp)
                                .background(HomeAccent, RoundedCornerShape(14.dp))
                                // Expose the tap target itself to TalkBack/UIAutomator, not just the glyph.
                                .semantics { contentDescription = "Go to address or search" }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = HomeBg, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            if (invalidAddress) {
                Text("This address can't be opened.", color = Color(0xFFFFA1A1),
                    fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
            }
            if (localSuggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Surface(color = HomeTile, shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, HomeStroke)) {
                    Column(Modifier.fillMaxWidth()) {
                        localSuggestions.forEach { site ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    onOpen(site.url)
                                    keyboard?.hide()
                                    focus.clearFocus()
                                    search = ""
                                }.padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null,
                                    tint = HomeSubtle, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(site.title, color = HomeText, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(site.url, color = HomeSubtle, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Open ${site.title}",
                                    tint = HomeSubtle, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (compactHome || localSuggestions.isNotEmpty()) 19.dp else 35.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick access", color = HomeText, fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { editMode = !editMode }) {
                    Icon(if (editMode) Icons.Rounded.Done else Icons.Rounded.Edit,
                        contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editMode) "Done" else "Edit", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(11.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = when {
                    maxWidth >= 600.dp -> 6
                    maxWidth < 330.dp -> 3
                    else -> 4
                }
                val tiles: List<StartSite?> =
                    shortcuts.sites.map { it } + if (shortcuts.sites.size < 16) listOf(null) else emptyList()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    tiles.chunked(columns).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { site ->
                                Box(Modifier.weight(1f)) {
                                    if (site == null) {
                                        AddSiteTile {
                                            editing = null
                                            editorVisible = true
                                        }
                                    } else {
                                        StartSiteTile(
                                            site = site, editable = editMode,
                                            onOpen = { if (editMode) {
                                                editing = site
                                                editorVisible = true
                                            } else onOpen(site.url) },
                                            onRemove = { shortcuts.remove(site.url) }
                                        )
                                    }
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            

            if (latestJourney != null) {
                Spacer(Modifier.height(32.dp))
                SectionTitle("Continue browsing")
                Spacer(Modifier.height(11.dp))
                val last = latestJourney.nodes.maxByOrNull { it.timestamp }!!
                Surface(
                    onClick = onJourneys,
                    color = HomeTile,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HomeStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountTree, null, tint = HomeAccent,
                            modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(latestJourney.name, color = HomeText, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(last.title.ifBlank { last.url }, color = HomeSubtle,
                                fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null,
                            tint = HomeSubtle, modifier = Modifier.size(19.dp))
                    }
                }
            }

            if (recentPages.isNotEmpty()) {
                Spacer(Modifier.height(29.dp))
                SectionTitle("Recent")
                Spacer(Modifier.height(10.dp))
                recentPages.forEach { node ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(node.url) }
                            .padding(vertical = 11.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null,
                            tint = HomeSubtle, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(node.title.ifBlank { node.url }, color = HomeText,
                                fontSize = 13.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(node.url, color = HomeSubtle, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null,
                            tint = HomeSubtle, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }

    if (editorVisible) {
        ShortcutEditor(
            original = editing,
            onDismiss = { editorVisible = false },
            onSave = { title, url ->
                if (shortcuts.save(title, url, editing?.url)) {
                    editorVisible = false
                    true
                } else false
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = HomeText)
}

@Composable
private fun StartSiteTile(
    site: StartSite,
    editable: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(109.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(60.dp)
                    .background(HomeTile, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                SameOriginFavicon(site)
            }
            Spacer(Modifier.height(10.dp))
            Text(site.title, color = HomeText, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (editable) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(26.dp)
                    .background(HomeStroke, CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove ${site.title}",
                    tint = HomeText, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun AddSiteTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().height(109.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(60.dp).background(HomeTile, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add website shortcut",
                tint = HomeAccent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Add", color = HomeText, fontSize = 11.sp)
    }
}

/** Requests only the website's own HTTPS origin favicon. No third-party icon tracker. */
private object OriginFavicons {
    private val cache = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    /** Android's BitmapFactory cannot directly decode many .ico files.
     * Decode their embedded PNG frames without trusting large pixel dimensions.
     */
    private fun decodeIcon(bytes: ByteArray): Bitmap? {
        fun bitmap(offset: Int, length: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, offset, length, bounds)
            if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512) return null
            return BitmapFactory.decodeByteArray(bytes, offset, length)
        }
        bitmap(0, bytes.size)?.let { return it }
        if (bytes.size < 22) return null
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (header.getShort(0).toInt() != 0 || header.getShort(2).toInt() != 1) return null
        val count = header.getShort(4).toInt() and 0xffff
        if (count !in 1..20 || 6 + count * 16 > bytes.size) return null
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        )
        for (index in 0 until count) {
            val start = 6 + index * 16
            val length = header.getInt(start + 8)
            val offset = header.getInt(start + 12)
            if (offset < 0 || length < 24 || offset.toLong() + length > bytes.size) continue
            if (pngHeader.indices.all { bytes[offset + it] == pngHeader[it] }) {
                bitmap(offset, length)?.let { return it }
            }
        }
        return null
    }

    fun get(url: String): Bitmap? {
        val origin = runCatching {
            val uri = URI(url)
            URL("https", uri.host ?: return null, if (uri.port > 0) uri.port else -1, "/favicon.ico")
        }.getOrNull() ?: return null
        cache.get(origin.toString())?.let { return it }
        val conn = (origin.openConnection() as? HttpURLConnection) ?: return null
        return try {
            conn.connectTimeout = 2000
            conn.readTimeout = 2500
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "image/png,image/x-icon,image/*;q=0.5")
            if (conn.responseCode != 200 || conn.contentLengthLong > 180_000) return null
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val bytes = ByteArray(8192)
                while (true) {
                    val count = input.read(bytes)
                    if (count < 0) break
                    if (out.size() + count > 180_000) return null
                    out.write(bytes, 0, count)
                }
            }
            val bytes = out.toByteArray()
            val bitmap = decodeIcon(bytes)
            if (bitmap != null) {
                cache.put(origin.toString(), bitmap)
                bitmap
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

@Composable
private fun SameOriginFavicon(site: StartSite) {
    val image by produceState<Bitmap?>(initialValue = null, key1 = site.url) {
        value = withContext(Dispatchers.IO) { OriginFavicons.get(site.url) }
    }
    if (image == null) {
        Text(site.title.take(1).uppercase(), color = HomeText, fontSize = 23.sp,
            fontWeight = FontWeight.Medium)
    } else {
        // Many sites publish a dark or transparent monochrome favicon (e.g. GitHub).
        // A light icon backing keeps those genuinely sourced marks legible in dark mode.
        Box(
            modifier = Modifier.size(36.dp)
                .background(Color(0xFFF3F5F9), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(image!!.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ShortcutEditor(
    original: StartSite?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Boolean
) {
    var title by remember(original) { mutableStateOf(original?.title.orEmpty()) }
    var url by remember(original) { mutableStateOf(original?.url.orEmpty()) }
    var error by remember(original) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HomeTile,
        title = {
            Text(if (original == null) "Add shortcut" else "Edit shortcut",
                fontSize = 19.sp, color = HomeText)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                EditorField("Name", title, { title = it; error = false }, KeyboardType.Text)
                EditorField("Website", url, { url = it; error = false }, KeyboardType.Uri)
                if (error) Text("Check the address or remove the duplicate shortcut.",
                    color = Color(0xFFFFA1A1), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!onSave(title, url)) error = true }) {
                Text("Save", color = HomeAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HomeSubtle)
            }
        }
    )
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    update: (String) -> Unit,
    type: KeyboardType
 ) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Column {
        Text(label, color = HomeSubtle, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
        Surface(color = HomeBg, shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, HomeStroke)) {
            BasicTextField(
                value = value,
                onValueChange = update,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp),
                singleLine = true,
                textStyle = TextStyle(color = HomeText, fontSize = 14.sp),
                cursorBrush = SolidColor(HomeAccent),
                keyboardOptions = KeyboardOptions(keyboardType = type,
                    imeAction = if (label == "Name") ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    focus.clearFocus()
                })
            )
        }
    }
}
