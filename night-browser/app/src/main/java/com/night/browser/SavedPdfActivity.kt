package com.night.browser

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Internal, offline-only reader for verified Time Machine PDF copies.
 *
 * Opening a snapshot never launches an external PDF app or initiates a network
 * request. The Activity isn't exported and only receives our FileProvider URI.
 * PdfRenderer runs on Dispatchers.IO and descriptors are closed every time.
 */
class SavedPdfActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val snapshotUri = intent?.data?.takeIf { it.scheme == "content" }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = PdfInk,
                surface = PdfSurface,
                primary = PdfAccent,
                onBackground = PdfText,
                onSurface = PdfText
            )) {
                SavedPdfReader(snapshotUri, onClose = { finish() })
            }
        }
    }
}

private val PdfInk = Color(0xFF0C1017)
private val PdfSurface = Color(0xFF171D27)
private val PdfText = Color(0xFFF3F5F9)
private val PdfMuted = Color(0xFFA6AFBD)
private val PdfAccent = Color(0xFFB9BEFF)

@Composable
private fun SavedPdfReader(uri: Uri?, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pageIndex by rememberSaveable(uri?.toString()) { mutableIntStateOf(0) }

    // PDF validation takes place in TimeMachineArchive.fileForPreview before this
    // viewer is launched. Recheck the reader's input and PDF header regardless.
    val pageCount by produceState(initialValue = -1, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            if (uri == null || uri.authority != "com.night.browser.files") return@withContext 0
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { it.pageCount }
                } ?: 0
            }.getOrDefault(0)
        }
    }
    val safeIndex = if (pageCount > 0) pageIndex.coerceIn(0, pageCount - 1) else 0
    val displayWidth = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp.toPx()).toInt().coerceIn(360, 1400)
    }
    val renderResult by produceState<Pair<Boolean, Bitmap?>>(
        initialValue = false to null, key1 = uri, key2 = safeIndex, key3 = displayWidth to pageCount
    ) {
        value = false to null
        value = true to withContext(Dispatchers.IO) {
            if (uri == null || pageCount <= 0 ||
                uri.authority != "com.night.browser.files") return@withContext null
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (safeIndex !in 0 until renderer.pageCount) return@use null
                        renderer.openPage(safeIndex).use { page ->
                            require(page.width > 0 && page.height > 0)
                            val width = displayWidth
                            val height = (page.height.toLong() * width / page.width)
                                .coerceIn(1L, 2500L).toInt()
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(AndroidColor.WHITE)
                                page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().background(PdfInk)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Close saved PDF", tint = PdfText)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Saved PDF", color = PdfText, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text("Offline copy", color = PdfMuted, fontSize = 12.sp)
            }
            if (pageCount > 0) Text(
                "${safeIndex + 1} / $pageCount", color = PdfMuted, fontSize = 13.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        HorizontalDivider(color = Color(0xFF2B3441))
        Box(
            Modifier.fillMaxWidth().weight(1f).background(Color(0xFF232934)),
            contentAlignment = Alignment.Center
        ) {
            when {
                pageCount == -1 -> CircularProgressIndicator(color = PdfAccent)
                pageCount == 0 -> Text(
                    "This PDF cannot be opened.", color = PdfText, fontSize = 14.sp)
                !renderResult.first -> CircularProgressIndicator(color = PdfAccent)
                renderResult.second == null -> Text(
                    "Could not render this PDF page.", color = PdfText, fontSize = 14.sp)
                else -> {
                    val bitmap = renderResult.second!!
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Saved PDF page ${safeIndex + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                                .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                        )
                    }
                }
            }
        }
        if (pageCount > 0) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { pageIndex = safeIndex - 1 },
                    enabled = safeIndex > 0) { Text("Previous") }
                Text("Page ${safeIndex + 1} of $pageCount",
                    color = PdfMuted, fontSize = 13.sp)
                TextButton(onClick = { pageIndex = safeIndex + 1 },
                    enabled = safeIndex + 1 < pageCount) { Text("Next") }
            }
        }
    }
}