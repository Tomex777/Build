package com.night.sora.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Render a real shareable PNG rather than sharing the text plus an external URL.
 * Supports remote images with a conservative download budget and avoids public storage permissions.
 */
internal suspend fun createCaptionedMeme(context: Context, imageUrl: String, caption: String): Uri =
    withContext(Dispatchers.IO) {
        require(caption.isNotBlank()) { "Write a caption first." }
        val endpoint = URL(imageUrl)
        require(endpoint.protocol in listOf("http", "https")) { "Unsupported image address." }
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 14_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Sora/1.0 meme-image-export")
        }
        val bytes = try {
            require(connection.responseCode in 200..299) { "Couldn't download the meme image." }
            val stated = connection.contentLengthLong
            require(stated <= 12L * 1024 * 1024 || stated < 0L) { "Image is too large." }
            connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 12 * 1024 * 1024) { "Image is too large." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Invalid meme image." }
        options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 1440).coerceAtLeast(1)
        options.inJustDecodeBounds = false
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
            "Unable to decode meme image."
        }
        val target = 1080
        val sourceSide = minOf(decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(decoded,
            (decoded.width - sourceSide) / 2, (decoded.height - sourceSide) / 2,
            sourceSide, sourceSide)
        val square = Bitmap.createScaledBitmap(cropped, target, target, true)
        val result = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(square, 0f, 0f, null)

        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 48f
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 0f, 2f, Color.BLACK)
        }
        val words = caption.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val proposal = if (line.isEmpty()) word else "$line $word"
            if (captionPaint.measureText(proposal) > 990f && line.isNotEmpty()) {
                lines.add(line)
                line = word
            } else line = proposal
        }
        if (line.isNotEmpty()) lines.add(line)
        // Reduce text size before rendering long captions to avoid horizontal clipping.
        val lineCount = lines.size.coerceAtLeast(1)
        captionPaint.textSize = when {
            lineCount > 7 -> 30f
            lineCount > 5 -> 36f
            else -> 48f
        }
        val lineHeight = captionPaint.textSize * 1.3f
        val barTop = (target - (lineCount * lineHeight + 38f)).coerceAtLeast(0f)
        canvas.drawRect(0f, barTop, target.toFloat(), target.toFloat(),
            Paint().apply { color = Color.argb(202, 0, 0, 0) })
        lines.forEachIndexed { index, text ->
            val fitSize = captionPaint.textSize
            while (captionPaint.measureText(text) > 1010f && captionPaint.textSize > 18f) {
                captionPaint.textSize -= 1f
            }
            canvas.drawText(text, target / 2f, barTop + 24f + (index + 1) * lineHeight,
                captionPaint)
            captionPaint.textSize = fitSize
        }
        val directory = File(context.cacheDir, "shared-memes").apply { mkdirs() }
        val file = File(directory, UUID.randomUUID().toString() + ".png")
        file.outputStream().use { require(result.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        // Keep at most 20 recent share files rather than accumulating indefinitely.
        directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
        if (square !== cropped) square.recycle()
        if (cropped !== decoded) cropped.recycle()
        decoded.recycle()
        result.recycle()
        FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }
