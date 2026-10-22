package com.night.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import java.net.URI
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF0C1017)
private val Raised = Color(0xFF171D27)
private val Border = Color(0xFF2B3441)
private val Bright = Color(0xFFF3F5F9)
private val Muted = Color(0xFFA6AFBD)
private val Accent = Color(0xFFB9BEFF)
private val BlankCanvas = Color(0xFF121924)

@Composable
internal fun TabPanel(
    browser: BrowserController,
    onOpen: (BrowserTab) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onNew: (Boolean) -> Unit
) {
    // Explicitly separate normal/private lists. A private tab is backed by Gecko private mode,
    // not a merely darkened card. Private URLs and thumbnails never enter session persistence.
    var privateGrid by remember { mutableStateOf(browser.active.isPrivate) }
    val normalCount = browser.tabs.count { !it.isPrivate }
    val privateCount = browser.tabs.size - normalCount
    val shown = browser.tabs.filter { it.isPrivate == privateGrid }
    val gridState = rememberLazyGridState()
    // Opening Tabs after browsing a newer tab must reveal its selected card;
    // otherwise ten restored tabs leave the current page hidden offscreen.
    // Do not scroll a different profile's tab into view.
    LaunchedEffect(privateGrid, browser.activeId, shown.size) {
        val activeIndex = shown.indexOfFirst { it.id == browser.activeId }
        if (activeIndex >= 0) gridState.scrollToItem(activeIndex)
    }
    // Only real Gecko pixel captures enter the grid. Evict oldest previews as soon
    // as the overview opens; do not retain 30+ large Bitmaps on modest devices.
    LaunchedEffect(browser.tabs.size, browser.activeId, privateGrid) {
        val slots = browser.tabs.mapNotNull { tab ->
            tab.thumbnail?.takeUnless { it.isRecycled }?.let { pixels ->
                TabPreviewBudget.Preview(tab.id, pixels.allocationByteCount.toLong())
            }
        }
        val keep = TabPreviewBudget.retainedIds(slots, browser.activeId)
        browser.tabs.forEach { tab ->
            if (tab.id !in keep) tab.thumbnail = null
        }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 22.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (privateGrid) "Private tabs" else "Tabs", fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold, color = Bright)
            Spacer(Modifier.weight(1f))
            TabGridIcon(Icons.Rounded.Add,
                if (privateGrid) "New private tab" else "New tab") {
                onNew(privateGrid)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !privateGrid,
                onClick = { privateGrid = false },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Raised,
                    labelColor = Muted,
                    selectedContainerColor = Accent.copy(alpha = 0.20f),
                    selectedLabelColor = Bright
                ),
                label = { Text("Regular  $normalCount", fontSize = 13.sp) }
            )
            FilterChip(
                selected = privateGrid,
                onClick = { privateGrid = true },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Raised,
                    labelColor = Muted,
                    selectedContainerColor = Accent.copy(alpha = 0.20f),
                    selectedLabelColor = Bright
                ),
                leadingIcon = {
                    Icon(Icons.Rounded.Lock, contentDescription = null,
                        modifier = Modifier.size(15.dp))
                },
                label = { Text("Private  $privateCount", fontSize = 13.sp) }
            )
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (privateGrid) Icons.Rounded.Lock else Icons.Rounded.Tab,
                        null, tint = Muted, modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(if (privateGrid) "No private tabs" else "No regular tabs",
                        color = Bright, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { onNew(privateGrid) }) {
                        Text(if (privateGrid) "New private tab" else "New tab")
                    }
                }
            }
        } else {
            // Fit the device instead of showing fixed, huge cards in every orientation.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = when {
                    maxWidth >= 840.dp -> 4
                    maxWidth >= 520.dp -> 3
                    else -> 2
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    items(shown.size, key = { shown[it].id }) { index ->
                        val tab = shown[index]
                        val selected = tab.id == browser.activeId
                        val siteName = tabSiteName(tab.url)
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(214.dp)
                                .clickable { onOpen(tab) },
                            shape = RoundedCornerShape(17.dp),
                            color = Raised,
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) Accent else Border
                            )
                        ) {
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().height(44.dp)
                                        .padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        tab.title.ifBlank { if (tab.isPrivate) "Private tab" else "New tab" },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = Bright, fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TabGridIcon(Icons.Rounded.Close, "Close " + tab.title) {
                                        onClose(tab)
                                    }
                                }
                                Box(
                                    Modifier.fillMaxWidth().weight(1f).background(BlankCanvas),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val preview = tab.thumbnail?.takeUnless { it.isRecycled }
                                    if (preview != null) {
                                        // Pixels come from GeckoView's compositor, never invented thumbnails.
                                        Image(
                                            bitmap = preview.asImageBitmap(),
                                            contentDescription = "Preview of " + tab.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        // An explicit placeholder is more honest than a fake webpage.
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxSize().padding(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(52.dp)
                                                    .background(Raised, RoundedCornerShape(17.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                when {
                                                    tab.isPrivate -> Icon(
                                                        Icons.Rounded.Lock,
                                                        contentDescription = "Private tab",
                                                        tint = Accent, modifier = Modifier.size(23.dp)
                                                    )
                                                    siteName != null -> Text(
                                                        siteName.take(1).uppercase(),
                                                        color = Accent, fontSize = 23.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    else -> Image(
                                                        painter = painterResource(R.drawable.ic_night),
                                                        contentDescription = "NIGHT new tab",
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                            if (!tab.isPrivate && siteName != null) {
                                                Spacer(Modifier.height(10.dp))
                                                Text(
                                                    siteName, color = Muted, fontSize = 11.sp,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                // Never surface private URLs or persist their preview pixels.
                                if (tab.isPrivate || siteName != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(27.dp)
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (tab.isPrivate) "Private browsing" else siteName.orEmpty(),
                                            color = Muted, fontSize = 10.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// The grid labels only valid website hosts; private cards never request this label.
private fun tabSiteName(url: String): String? = runCatching {
    val uri = URI(url)
    if (!uri.scheme.equals("https", ignoreCase = true) &&
        !uri.scheme.equals("http", ignoreCase = true)) return@runCatching null
    uri.host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
}.getOrNull()

@Composable
private fun TabGridIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label,
            tint = Bright, modifier = Modifier.size(21.dp))
    }
}
