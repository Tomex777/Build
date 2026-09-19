package com.example.whatsapp.presentation.chatscreen

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightPdfSheet(
    localPath: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val descriptor = remember(localPath) {
        runCatching {
            ParcelFileDescriptor.open(File(localPath), ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
    }
    val renderer = remember(descriptor) {
        descriptor?.let { runCatching { PdfRenderer(it) }.getOrNull() }
    }

    DisposableEffect(renderer, descriptor) {
        onDispose {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF101416),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 6.dp)
                    .fillMaxWidth(0.12f)
                    .background(Color(0xFF6D7478))
                    .padding(vertical = 2.dp),
            )
        },
        modifier = Modifier.fillMaxHeight(0.97f),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = File(localPath).name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = if (renderer != null) {
                            renderer.pageCount.toString() + if (renderer.pageCount == 1) " page" else " pages"
                        } else {
                            "Could not open PDF"
                        },
                        color = Color(0xFF9EA7AB),
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }

            if (renderer != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF242729)),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items((0 until renderer.pageCount).toList()) { index ->
                        NightPdfPage(renderer = renderer, pageIndex = index)
                    }
                }
            }
        }
    }
}

@Composable
private fun NightPdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widthPx = with(density) {
        (configuration.screenWidthDp.dp - 20.dp).roundToPx().coerceAtLeast(720)
    }

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        renderer,
        pageIndex,
        widthPx,
    ) {
        value = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                runCatching {
                    renderer.openPage(pageIndex).use { page ->
                        val height = (widthPx * (page.height.toFloat() / page.width.toFloat()))
                            .toInt()
                            .coerceAtLeast(1)
                        Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { image ->
                            image.eraseColor(AndroidColor.WHITE)
                            page.render(
                                image,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                        }
                    }
                }.getOrNull()
            }
        }
    }

    bitmap?.let { image ->
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "PDF page " + (pageIndex + 1),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        )
    }
}
