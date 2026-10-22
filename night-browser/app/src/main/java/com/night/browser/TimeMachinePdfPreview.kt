package com.night.browser

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Genuine first-page preview from a local PDF, decoded on an IO thread. */
@Composable
internal fun TimeMachinePdfPreview(
    archive: TimeMachineArchive,
    version: TimeMachineVersion,
    width: Dp,
    height: Dp
) {
    val cover by produceState<Bitmap?>(null, version.id, version.sizeBytes) {
        value = withContext(Dispatchers.IO) {
            val file = archive.fileForPreview(version) ?: return@withContext null
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (renderer.pageCount <= 0) return@use null
                        renderer.openPage(0).use { page ->
                            val targetWidth = 240
                            val targetHeight = (page.height.toLong() * targetWidth / page.width)
                                .toInt().coerceIn(1, 420)
                            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                .apply {
                                    eraseColor(AndroidColor.WHITE)
                                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                        }
                    }
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier.size(width, height).background(Color(0xFF293140)),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null) {
            Image(
                bitmap = cover!!.asImageBitmap(),
                contentDescription = "PDF first page preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width, height)
            )
        } else {
            Icon(Icons.Rounded.PictureAsPdf, "PDF preview unavailable",
                tint = Color(0xFFB9BEFF))
        }
    }
}
