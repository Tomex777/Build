package com.example.whatsapp.presentation.chatscreen

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private val PdfEditorChrome = Color(0xFF111719)
private val PdfEditorCanvas = Color(0xFF24282A)
private val PdfEditorPanel = Color(0xFF20272A)
private val PdfEditorMuted = Color(0xFF9EA7AB)
private val PdfEditorAccent = Color(0xFFE94B72)

private enum class PdfEditorTool(val label: String) {
    VIEW("View"),
    DRAW("Draw"),
    HIGHLIGHT("Highlight"),
    TEXT("Text"),
    SIGN("Sign"),
}

private sealed interface PdfEditAction {
    val pageIndex: Int

    data class StrokeAction(
        override val pageIndex: Int,
        val tool: PdfEditorTool,
        val points: List<Offset>,
    ) : PdfEditAction

    data class TextAction(
        override val pageIndex: Int,
        val position: Offset,
        val text: String,
    ) : PdfEditAction
}

@Composable
fun NightPdfEditorScreen(
    localPath: String,
    fileName: String,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onCancel: () -> Unit,
    onPreparedSend: (path: String, name: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val file = remember(localPath) { File(localPath) }

    var pageCount by remember(localPath) { mutableIntStateOf(0) }
    var currentPage by remember(localPath) { mutableIntStateOf(0) }
    var pageBitmap by remember(localPath) { mutableStateOf<Bitmap?>(null) }
    var pageLoading by remember(localPath) { mutableStateOf(true) }
    var loadError by remember(localPath) { mutableStateOf<String?>(null) }

    var tool by remember { mutableStateOf(PdfEditorTool.VIEW) }
    var edits by remember { mutableStateOf<List<PdfEditAction>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<PdfEditAction>>(emptyList()) }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    var textDialogOpen by remember { mutableStateOf(false) }
    var pendingTextPosition by remember { mutableStateOf(Offset(0.15f, 0.20f)) }
    var textValue by remember { mutableStateOf("") }

    var discardOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    fun requestCancel() {
        if (edits.isNotEmpty()) discardOpen = true else onCancel()
    }

    fun addEdit(action: PdfEditAction) {
        edits = edits + action
        redoStack = emptyList()
    }

    fun undo() {
        val last = edits.lastOrNull() ?: return
        edits = edits.dropLast(1)
        redoStack = redoStack + last
    }

    fun redo() {
        val last = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        edits = edits + last
    }

    fun sendPdf() {
        if (exporting) return
        if (edits.isEmpty()) {
            onPreparedSend(localPath, fileName)
            return
        }

        exporting = true
        exportError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                exportPdfEdits(context.applicationContext, file, edits)
            }
            exporting = false
            result.onSuccess {
                onPreparedSend(localPath, fileName)
            }.onFailure {
                exportError = it.message ?: "Could not save the edited PDF."
            }
        }
    }

    BackHandler { requestCancel() }

    LaunchedEffect(localPath) {
        pageLoading = true
        loadError = null
        val result = withContext(Dispatchers.IO) {
            runCatching { readPdfPageCount(file) }
        }
        result.onSuccess { count ->
            pageCount = count
            currentPage = currentPage.coerceIn(0, (count - 1).coerceAtLeast(0))
        }.onFailure {
            loadError = it.message ?: "Could not open this PDF."
            pageLoading = false
        }
    }

    LaunchedEffect(localPath, currentPage, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        pageLoading = true
        loadError = null
        val result = withContext(Dispatchers.IO) {
            runCatching { renderPdfPage(file, currentPage) }
        }
        result.onSuccess { bitmap ->
            pageBitmap?.takeIf { it !== bitmap }?.recycle()
            pageBitmap = bitmap
            pageLoading = false
        }.onFailure {
            loadError = it.message ?: "Could not render this page."
            pageLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PdfEditorChrome)
            .statusBarsPadding()
            .navigationBarsPadding()
            .semantics { contentDescription = "PDF edit composer" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::requestCancel) {
                Icon(Icons.Default.ArrowBack, "Back from PDF editor", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = fileName.ifBlank { "PDF document" },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (pageCount > 0) {
                        "Page " + (currentPage + 1) + " of " + pageCount
                    } else {
                        "PDF editor"
                    },
                    color = PdfEditorMuted,
                    fontSize = 10.sp,
                )
            }

            IconButton(
                onClick = ::undo,
                enabled = edits.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = "Undo PDF edit" },
            ) {
                Icon(Icons.Default.Undo, "Undo PDF edit", tint = if (edits.isNotEmpty()) Color.White else PdfEditorMuted)
            }
            IconButton(
                onClick = ::redo,
                enabled = redoStack.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = "Redo PDF edit" },
            ) {
                Icon(Icons.Default.Redo, "Redo PDF edit", tint = if (redoStack.isNotEmpty()) Color.White else PdfEditorMuted)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PdfEditorTool.entries.forEach { candidate ->
                Surface(
                    color = if (tool == candidate) PdfEditorAccent else PdfEditorPanel,
                    shape = RoundedCornerShape(18.dp),
                    onClick = { tool = candidate },
                    modifier = Modifier.semantics {
                        contentDescription = "PDF tool " + candidate.label
                    },
                ) {
                    Text(
                        text = candidate.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(
                onClick = {
                    currentPage = (currentPage - 1).coerceAtLeast(0)
                    activeStroke = emptyList()
                },
                enabled = currentPage > 0,
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, "Previous PDF page", tint = Color.White)
            }
            Text(
                text = if (pageCount > 0) "${currentPage + 1} / $pageCount" else "—",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .semantics { contentDescription = "PDF editor page indicator" },
            )
            IconButton(
                onClick = {
                    currentPage = (currentPage + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))
                    activeStroke = emptyList()
                },
                enabled = pageCount > 0 && currentPage < pageCount - 1,
            ) {
                Icon(Icons.Default.KeyboardArrowRight, "Next PDF page", tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(PdfEditorCanvas),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = pageBitmap
            if (bitmap != null) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .aspectRatio(ratio)
                        .background(Color.White)
                        .then(
                            if (tool == PdfEditorTool.TEXT) {
                                Modifier.pointerInput(currentPage, tool) {
                                    detectTapGestures { tap ->
                                        pendingTextPosition = Offset(
                                            (tap.x / size.width.toFloat()).coerceIn(0f, 1f),
                                            (tap.y / size.height.toFloat()).coerceIn(0f, 1f),
                                        )
                                        textValue = ""
                                        textDialogOpen = true
                                    }
                                }
                            } else if (tool != PdfEditorTool.VIEW) {
                                Modifier.pointerInput(currentPage, tool) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            activeStroke = listOf(
                                                Offset(
                                                    (start.x / size.width.toFloat()).coerceIn(0f, 1f),
                                                    (start.y / size.height.toFloat()).coerceIn(0f, 1f),
                                                )
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            activeStroke = activeStroke + Offset(
                                                (change.position.x / size.width.toFloat()).coerceIn(0f, 1f),
                                                (change.position.y / size.height.toFloat()).coerceIn(0f, 1f),
                                            )
                                        },
                                        onDragEnd = {
                                            if (activeStroke.isNotEmpty()) {
                                                addEdit(
                                                    PdfEditAction.StrokeAction(
                                                        pageIndex = currentPage,
                                                        tool = tool,
                                                        points = activeStroke,
                                                    )
                                                )
                                            }
                                            activeStroke = emptyList()
                                        },
                                        onDragCancel = { activeStroke = emptyList() },
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                        .semantics { contentDescription = "Editable PDF page" },
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF page " + (currentPage + 1),
                        modifier = Modifier.fillMaxSize(),
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pageEdits = edits.filter { it.pageIndex == currentPage }
                        pageEdits.forEach { edit ->
                            when (edit) {
                                is PdfEditAction.StrokeAction -> {
                                    drawPdfStroke(edit.tool, edit.points)
                                }
                                is PdfEditAction.TextAction -> {
                                    drawIntoCanvas { canvas ->
                                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.BLACK
                                            textSize = size.height * 0.025f
                                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        }
                                        canvas.nativeCanvas.drawText(
                                            edit.text,
                                            edit.position.x * size.width,
                                            edit.position.y * size.height,
                                            paint,
                                        )
                                    }
                                }
                            }
                        }

                        if (activeStroke.isNotEmpty()) {
                            drawPdfStroke(tool, activeStroke)
                        }
                    }
                }
            }

            if (pageLoading) {
                Surface(
                    color = Color.Black.copy(alpha = 0.70f),
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PdfEditorAccent, modifier = Modifier.size(32.dp))
                    }
                }
            }

            loadError?.let { message ->
                Surface(
                    color = PdfEditorPanel,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 330.dp),
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        }

        exportError?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFF8CA0),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PdfEditorPanel)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PdfEditorPanel)
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                color = Color(0xFF293134),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.weight(1f),
            ) {
                TextField(
                    value = caption,
                    onValueChange = { onCaptionChange(it.take(1024)) },
                    placeholder = { Text("Add a caption…", color = PdfEditorMuted) },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "PDF caption" },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PdfEditorAccent,
                    ),
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            Surface(
                color = PdfEditorAccent,
                shape = CircleShape,
                modifier = Modifier
                    .size(50.dp)
                    .semantics { contentDescription = "Send PDF" },
                onClick = ::sendPdf,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (exporting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(Icons.Default.Send, "Send PDF", tint = Color.White)
                    }
                }
            }
        }
    }

    if (textDialogOpen) {
        AlertDialog(
            onDismissRequest = { textDialogOpen = false },
            title = { Text("Add text") },
            text = {
                TextField(
                    value = textValue,
                    onValueChange = { textValue = it.take(160) },
                    placeholder = { Text("Text") },
                    maxLines = 4,
                    modifier = Modifier.semantics { contentDescription = "PDF text value" },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = textValue.trim()
                        if (value.isNotBlank()) {
                            addEdit(
                                PdfEditAction.TextAction(
                                    pageIndex = currentPage,
                                    position = pendingTextPosition,
                                    text = value,
                                )
                            )
                        }
                        textDialogOpen = false
                    },
                ) {
                    Text("Add", color = PdfEditorAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { textDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (discardOpen) {
        AlertDialog(
            onDismissRequest = { discardOpen = false },
            title = { Text("Discard PDF edits?") },
            text = { Text("The original PDF will stay unchanged.") },
            confirmButton = {
                TextButton(onClick = onCancel) {
                    Text("Discard", color = PdfEditorAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { discardOpen = false }) {
                    Text("Keep editing")
                }
            },
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfStroke(
    tool: PdfEditorTool,
    points: List<Offset>,
) {
    if (points.isEmpty()) return
    val color = when (tool) {
        PdfEditorTool.HIGHLIGHT -> Color(0x66FFE66D)
        PdfEditorTool.SIGN -> Color.Black
        else -> Color(0xFFE94B72)
    }
    val width = when (tool) {
        PdfEditorTool.HIGHLIGHT -> size.height * 0.025f
        PdfEditorTool.SIGN -> size.height * 0.006f
        else -> size.height * 0.005f
    }

    if (points.size == 1) {
        drawCircle(
            color = color,
            radius = width / 2f,
            center = Offset(points[0].x * size.width, points[0].y * size.height),
        )
        return
    }

    val path = androidx.compose.ui.graphics.Path()
    path.moveTo(points.first().x * size.width, points.first().y * size.height)
    points.drop(1).forEach { point ->
        path.lineTo(point.x * size.width, point.y * size.height)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width),
    )
}

private fun readPdfPageCount(file: File): Int {
    require(file.isFile && file.length() > 0L) { "This PDF is no longer available." }
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            return renderer.pageCount
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            require(renderer.pageCount > 0) { "This PDF has no pages." }
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val largest = max(page.width, page.height).coerceAtLeast(1)
                val scale = (1800f / largest.toFloat()).coerceIn(1f, 2f)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                val matrix = Matrix().apply { postScale(scale, scale) }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

private fun exportPdfEdits(
    context: android.content.Context,
    source: File,
    edits: List<PdfEditAction>,
): Result<Unit> = runCatching {
    require(source.isFile && source.length() > 0L) { "The PDF draft is missing." }
    PDFBoxResourceLoader.init(context)
    val temp = File(source.parentFile, source.nameWithoutExtension + "_writing.pdf")
    if (temp.exists()) temp.delete()

    PDDocument.load(source).use { document ->
        edits.groupBy { it.pageIndex }.forEach pageLoop@ { (pageIndex, pageEdits) ->
            if (pageIndex !in 0 until document.numberOfPages) return@pageLoop
            val page = document.getPage(pageIndex)
            val box = page.cropBox ?: page.mediaBox
            val pageWidth = box.width
            val pageHeight = box.height

            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true,
            ).use { stream ->
                pageEdits.forEach editLoop@ { edit ->
                    when (edit) {
                        is PdfEditAction.StrokeAction -> {
                            if (edit.points.isEmpty()) return@editLoop
                            val (red, green, blue, alpha, width) = when (edit.tool) {
                                PdfEditorTool.HIGHLIGHT -> PdfStrokeStyle(255, 220, 50, 0.28f, pageHeight * 0.025f)
                                PdfEditorTool.SIGN -> PdfStrokeStyle(20, 20, 20, 1f, pageHeight * 0.006f)
                                else -> PdfStrokeStyle(233, 75, 114, 1f, pageHeight * 0.005f)
                            }

                            val state = PDExtendedGraphicsState().apply {
                                setStrokingAlphaConstant(alpha)
                            }
                            stream.setGraphicsStateParameters(state)
                            stream.setStrokingColor(red, green, blue)
                            stream.setLineWidth(width.coerceAtLeast(1.2f))
                            stream.setLineCapStyle(1)
                            val first = edit.points.first()
                            stream.moveTo(
                                box.lowerLeftX + first.x * pageWidth,
                                box.lowerLeftY + (1f - first.y) * pageHeight,
                            )
                            edit.points.drop(1).forEach { point ->
                                stream.lineTo(
                                    box.lowerLeftX + point.x * pageWidth,
                                    box.lowerLeftY + (1f - point.y) * pageHeight,
                                )
                            }
                            if (edit.points.size == 1) {
                                stream.lineTo(
                                    box.lowerLeftX + first.x * pageWidth + 0.5f,
                                    box.lowerLeftY + (1f - first.y) * pageHeight,
                                )
                            }
                            stream.stroke()
                        }

                        is PdfEditAction.TextAction -> {
                            val state = PDExtendedGraphicsState().apply {
                                setNonStrokingAlphaConstant(1f)
                            }
                            stream.setGraphicsStateParameters(state)
                            stream.setNonStrokingColor(20, 20, 20)
                            stream.beginText()
                            stream.setFont(PDType1Font.HELVETICA, (pageHeight * 0.022f).coerceIn(10f, 22f))
                            stream.newLineAtOffset(
                                box.lowerLeftX + edit.position.x * pageWidth,
                                box.lowerLeftY + (1f - edit.position.y) * pageHeight,
                            )
                            stream.showText(pdfSafeText(edit.text))
                            stream.endText()
                        }
                    }
                }
            }
        }
        document.save(temp)
    }

    require(temp.isFile && temp.length() > 0L) { "The edited PDF could not be created." }
    temp.copyTo(source, overwrite = true)
    temp.delete()
}

private data class PdfStrokeStyle(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Float,
    val width: Float,
)

private fun pdfSafeText(value: String): String =
    value.map { char ->
        if (char.code in 32..255) char else '?'
    }.joinToString("")
