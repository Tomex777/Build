package com.example.whatsapp.presentation.chatscreen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.compose.PdfRendererViewCompose
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.PdfSource
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PdfChrome = Color(0xFF111719)
private val PdfCanvas = Color(0xFF24282A)
private val PdfPanel = Color(0xFF20272A)
private val PdfMuted = Color(0xFF9EA7AB)
private val PdfAccent = Color(0xFFE94B72)

@Composable
fun NightPdfViewerScreen(
    localPath: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(localPath) { File(localPath) }

    var viewer by remember(localPath) { mutableStateOf<PdfRendererView?>(null) }
    var viewerGeneration by remember(localPath) { mutableIntStateOf(0) }
    var loading by remember(localPath) { mutableStateOf(true) }
    var errorMessage by remember(localPath) { mutableStateOf<String?>(null) }
    var currentPage by remember(localPath) { mutableIntStateOf(1) }
    var totalPages by remember(localPath) { mutableIntStateOf(0) }
    var zoomScale by remember(localPath) { mutableStateOf(1f) }

    var searchOpen by remember(localPath) { mutableStateOf(false) }
    var searchQuery by remember(localPath) { mutableStateOf("") }
    var searching by remember(localPath) { mutableStateOf(false) }
    var searchAttempted by remember(localPath) { mutableStateOf(false) }
    var searchMatches by remember(localPath) { mutableStateOf<List<Int>>(emptyList()) }
    var searchIndex by remember(localPath) { mutableIntStateOf(0) }
    var searchError by remember(localPath) { mutableStateOf<String?>(null) }
    var infoOpen by remember(localPath) { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { destination: Uri? ->
        if (destination == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                        FileInputStream(file).use { input -> input.copyTo(output) }
                    } ?: error("Could not open the selected destination.")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "PDF saved." else (result.exceptionOrNull()?.message ?: "Could not save PDF."),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun jumpToMatch(index: Int) {
        if (searchMatches.isEmpty()) return
        val safe = index.coerceIn(0, searchMatches.lastIndex)
        searchIndex = safe
        viewer?.jumpToPage(
            pageNumber = searchMatches[safe] - 1,
            smoothScroll = false,
        )
    }

    fun runSearch() {
        val query = searchQuery.trim()
        searchAttempted = true
        searchError = null
        if (query.isBlank()) {
            searchMatches = emptyList()
            searchIndex = 0
            return
        }

        searching = true
        scope.launch {
            val result = searchPdfPages(
                context = context.applicationContext,
                file = file,
                query = query,
            )
            searching = false
            result.onSuccess { matches ->
                searchMatches = matches
                searchIndex = 0
                if (matches.isNotEmpty()) {
                    jumpToMatch(0)
                }
            }.onFailure { failure ->
                searchMatches = emptyList()
                searchIndex = 0
                searchError = failure.message ?: "Could not search this document."
            }
        }
    }

    fun sharePdf() {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".files",
                file,
            )
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("application/pdf")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Share PDF",
                )
            )
        }.onFailure {
            Toast.makeText(context, "Could not share this PDF.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openExternally() {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".files",
                file,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }.onFailure {
            Toast.makeText(context, "No PDF app is available.", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PdfCanvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PdfChrome)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = file.name.ifBlank { "PDF document" },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (totalPages > 0) {
                                append(totalPages)
                                append(if (totalPages == 1) " page" else " pages")
                                append(" • ")
                            }
                            append(formatPdfBytes(file.length()))
                        },
                        color = PdfMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }

                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(Icons.Default.Search, "Search document", tint = Color.White)
                }
                IconButton(onClick = ::sharePdf) {
                    Icon(Icons.Default.Share, "Share PDF", tint = Color.White)
                }
                IconButton(onClick = { saveLauncher.launch(file.name.ifBlank { "document.pdf" }) }) {
                    Icon(Icons.Default.Download, "Save PDF", tint = Color.White)
                }
                IconButton(onClick = ::openExternally) {
                    Icon(Icons.Default.OpenInNew, "Open PDF externally", tint = Color.White)
                }
                IconButton(onClick = { infoOpen = true }) {
                    Icon(Icons.Default.Info, "Document info", tint = Color.White)
                }
            }

            if (searchOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PdfChrome)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it.take(160)
                                searchAttempted = false
                                searchError = null
                            },
                            placeholder = { Text("Find in document", color = PdfMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Search PDF text" },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = PdfPanel,
                                unfocusedContainerColor = PdfPanel,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = PdfAccent,
                            ),
                            shape = RoundedCornerShape(22.dp),
                        )
                        IconButton(onClick = ::runSearch) {
                            if (searching) {
                                CircularProgressIndicator(
                                    color = PdfAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .semantics { contentDescription = "Searching PDF" },
                                )
                            } else {
                                Icon(Icons.Default.Search, "Run PDF search", tint = PdfAccent)
                            }
                        }
                        IconButton(
                            onClick = {
                                searchOpen = false
                                searchError = null
                            },
                        ) {
                            Icon(Icons.Default.Close, "Close search", tint = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val searchStatus = when {
                            searching -> "Searching…"
                            searchError != null -> searchError.orEmpty()
                            searchMatches.isNotEmpty() ->
                                (searchIndex + 1).toString() + " of " + searchMatches.size + " matches"
                            searchAttempted && searchQuery.isNotBlank() -> "No matches"
                            else -> "Search text in this PDF"
                        }
                        Text(
                            text = searchStatus,
                            color = if (searchError == null) PdfMuted else Color(0xFFFF8CA0),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        if (searchMatches.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val next = if (searchIndex <= 0) searchMatches.lastIndex else searchIndex - 1
                                    jumpToMatch(next)
                                },
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Previous search result", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    val next = if (searchIndex >= searchMatches.lastIndex) 0 else searchIndex + 1
                                    jumpToMatch(next)
                                },
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Next search result", tint = Color.White)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PdfCanvas),
            ) {
                if (!file.isFile || file.length() <= 0L) {
                    PdfErrorState(
                        message = "This PDF is no longer available on this device.",
                        onRetry = null,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    key(localPath, viewerGeneration) {
                        PdfRendererViewCompose(
                            source = PdfSource.LocalFile(file),
                            modifier = Modifier.fillMaxSize(),
                            cacheStrategy = CacheStrategy.MINIMIZE_CACHE,
                            statusCallBack = object : PdfRendererView.StatusCallBack {
                                override fun onPdfLoadStart() {
                                    loading = true
                                    errorMessage = null
                                }

                                override fun onPdfRenderStart() {
                                    loading = true
                                    errorMessage = null
                                }

                                override fun onPdfLoadSuccess(absolutePath: String) {
                                    errorMessage = null
                                }

                                override fun onPdfRenderSuccess() {
                                    loading = false
                                }

                                override fun onError(error: Throwable) {
                                    loading = false
                                    errorMessage = error.message ?: "Could not open this PDF."
                                }

                                override fun onPageChanged(page: Int, totalPage: Int) {
                                    currentPage = page.coerceAtLeast(1)
                                    totalPages = totalPage.coerceAtLeast(0)
                                }
                            },
                            zoomListener = object : PdfRendererView.ZoomListener {
                                override fun onZoomChanged(isZoomedIn: Boolean, scale: Float) {
                                    zoomScale = scale.coerceAtLeast(1f)
                                }
                            },
                            onReady = { ready ->
                                viewer = ready
                                ready.setZoomEnabled(true)
                            },
                        )
                    }

                    errorMessage?.let { error ->
                        PdfErrorState(
                            message = error,
                            onRetry = {
                                errorMessage = null
                                loading = true
                                viewerGeneration += 1
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    if (loading && errorMessage == null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(76.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = PdfAccent,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .semantics { contentDescription = "Loading PDF" },
                                )
                            }
                        }
                    }

                    if (!loading && errorMessage == null && totalPages > 0) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp),
                        ) {
                            Text(
                                text = currentPage.toString() + " / " + totalPages +
                                    if (zoomScale > 1.05f) {
                                        " • " + (zoomScale * 100f).toInt() + "%"
                                    } else {
                                        ""
                                    },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .semantics { contentDescription = "PDF page indicator" },
                            )
                        }
                    }
                }
            }
        }
    }

    if (infoOpen) {
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            title = { Text(file.name.ifBlank { "PDF document" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PDF document")
                    Text(
                        text = (if (totalPages > 0) totalPages.toString() else "—") + " pages",
                        color = PdfMuted,
                    )
                    Text(formatPdfBytes(file.length()), color = PdfMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = { infoOpen = false }) {
                    Text("Done", color = PdfAccent)
                }
            },
        )
    }
}

@Composable
private fun PdfErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = PdfPanel,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.widthIn(max = 340.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = "Could not open PDF",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = PdfMuted,
                fontSize = 12.sp,
            )
            onRetry?.let { retry ->
                TextButton(onClick = retry) {
                    Text("Try again", color = PdfAccent)
                }
            }
        }
    }
}

private suspend fun searchPdfPages(
    context: android.content.Context,
    file: File,
    query: String,
): Result<List<Int>> = withContext(Dispatchers.IO) {
    runCatching {
        require(file.isFile && file.length() > 0L) { "The PDF is no longer available." }
        PDFBoxResourceLoader.init(context)
        val needle = query.trim().lowercase(Locale.ROOT)
        require(needle.isNotBlank()) { "Enter text to search for." }

        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            buildList {
                for (page in 1..document.numberOfPages) {
                    currentCoroutineContext().ensureActive()
                    stripper.startPage = page
                    stripper.endPage = page
                    val text = stripper.getText(document)
                    if (text.lowercase(Locale.ROOT).contains(needle)) {
                        add(page)
                    }
                }
            }
        }
    }
}

private fun formatPdfBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    return if (unit == 0) {
        bytes.toString() + " B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}
