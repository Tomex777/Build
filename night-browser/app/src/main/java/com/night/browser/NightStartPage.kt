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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

// New start page owns its own palette until Agent 1 centralizes shared design tokens.
// All icons below are first-party Compose vectors; no Omni source or assets are copied.
private val HomeBg = Color(0xFF101318)
private val HomeTile = Color(0xFF1A1F26)
private val HomeStroke = Color(0xFF303640)
private val HomeText = Color(0xFFF1F2F5)
private val HomeSubtle = Color(0xFFA6AFBD)
private val HomeAccent = Color(0xFFD4D8FF)

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
    var editMode by rememberSaveable { mutableStateOf(false) }
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StartSite?>(null) }

    fun submit() {
        if (search.isBlank()) return
        onOpen(search.trim())
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

    Box(
        modifier = Modifier.fillMaxSize().background(HomeBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().widthIn(max = 800.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).background(HomeAccent, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center) {
                    Text("N", fontSize = 13.sp, fontWeight = FontWeight.Black, color = HomeBg)
                }
                Spacer(Modifier.width(10.dp))
                Text("NIGHT", fontSize = 15.sp, letterSpacing = 2.5.sp,
                    fontWeight = FontWeight.Bold, color = HomeText)
                Spacer(Modifier.width(7.dp))
                Text("Browser", fontSize = 13.sp, color = HomeSubtle)
            }
            Spacer(Modifier.height(36.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = HomeTile,
                border = BorderStroke(1.dp, HomeStroke)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null,
                        tint = HomeSubtle, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = HomeText, fontSize = 15.sp),
                        cursorBrush = SolidColor(HomeAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (search.isBlank()) {
                                    Text("Search or enter address", fontSize = 14.sp,
                                        color = HomeSubtle, maxLines = 1)
                                }
                                inner()
                            }
                        }
                    )
                    if (search.isBlank()) {
                        IconButton(
                            onClick = { clipboard.getText()?.text?.toString()?.let { search = it } },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste address",
                                tint = HomeSubtle, modifier = Modifier.size(19.dp))
                        }
                    } else {
                        IconButton(onClick = { submit() }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Go to address or search",
                                tint = HomeAccent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(33.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick access", color = HomeText, fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { editMode = !editMode }) {
                    Icon(if (editMode) Icons.Rounded.Done else Icons.Rounded.Edit,
                        contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editMode) "Done" else "Edit", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(11.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 600.dp) 6 else 4
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
            if (shortcuts.sites.isEmpty()) {
                Text("Add your favorite websites using +", color = HomeSubtle, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp))
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
    Box(Modifier.fillMaxWidth().height(103.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(54.dp)
                    .background(HomeTile, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                SameOriginFavicon(site)
            }
            Spacer(Modifier.height(9.dp))
            Text(site.title, color = HomeSubtle, fontSize = 11.sp,
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
        modifier = Modifier.fillMaxWidth().height(103.dp)
            .clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(54.dp).background(HomeTile, RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add website shortcut",
                tint = HomeAccent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text("Add", color = HomeSubtle, fontSize = 11.sp)
    }
}

/** Requests only the website's own HTTPS origin favicon. No third-party icon tracker. */
private object OriginFavicons {
    private val cache = LruCache<String, Bitmap>(32)
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
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null && bitmap.width <= 512 && bitmap.height <= 512) {
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
        Image(image!!.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.size(29.dp))
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done)
            )
        }
    }
}
