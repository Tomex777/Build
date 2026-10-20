package com.night.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
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
private val Muted = Color(0xFF8F9BAC)
private val Accent = Color(0xFFB9BEFF)

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
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 10.dp, top = 13.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (privateGrid) "Private tabs" else "Tabs", fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold, color = Bright)
            Spacer(Modifier.weight(1f))
            TabGridIcon(Icons.Rounded.Add,
                if (privateGrid) "New private tab" else "New tab") {
                onNew(privateGrid)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !privateGrid,
                onClick = { privateGrid = false },
                label = { Text("Regular  " + normalCount) }
            )
            FilterChip(
                selected = privateGrid,
                onClick = { privateGrid = true },
                leadingIcon = {
                    Icon(Icons.Rounded.Lock, null,
                        modifier = Modifier.size(16.dp))
                },
                label = { Text("Private  " + privateCount) }
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shown.size, key = { shown[it].id }) { index ->
                    val tab = shown[index]
                    val selected = tab.id == browser.activeId
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(248.dp)
                            .clickable { onOpen(tab) },
                        shape = RoundedCornerShape(16.dp),
                        color = Raised,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) Accent else Border
                        )
                    ) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().height(51.dp)
                                    .padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tab.title, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Bright, fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f))
                                TabGridIcon(Icons.Rounded.Close, "Close " + tab.title) {
                                    onClose(tab)
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().weight(1f).background(Ink),
                                contentAlignment = Alignment.Center
                            ) {
                                val preview = tab.thumbnail
                                if (preview != null) {
                                    Image(
                                        bitmap = preview.asImageBitmap(),
                                        contentDescription = "Preview of " + tab.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        if (tab.isPrivate) Icons.Rounded.Lock
                                        else Icons.Rounded.Public,
                                        null, tint = Muted, modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Text(
                                if (tab.isPrivate) "Private browsing"
                                else tab.url.ifBlank { "New tab" },
                                modifier = Modifier.fillMaxWidth().padding(9.dp),
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

@Composable
private fun TabGridIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label,
            tint = Bright, modifier = Modifier.size(21.dp))
    }
}
