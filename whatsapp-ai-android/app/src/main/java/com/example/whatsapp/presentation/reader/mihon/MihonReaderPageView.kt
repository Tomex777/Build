package com.example.whatsapp.presentation.reader.mihon

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/*
 * Rendering behavior is adapted from Mihon's ReaderPageImageView and
 * WebtoonSubsamplingImageView at revision
 * 424bbc53b85c19acd3c3b7c03ec6f73f516f25bc (Apache-2.0).
 *
 * Night-specific code resolves local/remote extension pages before handing the
 * resulting image to Mihon's SubsamplingScaleImageView fork.
 */
internal class MihonReaderPageView(
    context: Context,
    private val isWebtoon: Boolean,
) : FrameLayout(context) {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var loadJob: Job? = null

    private data class PendingWebtoonLayout(
        val width: Int,
        val height: Int,
        val leftMargin: Int,
        val rightMargin: Int,
        val bottomMargin: Int,
    )

    private var pendingWebtoonLayout: PendingWebtoonLayout? = null
    private var retryAction: (() -> Unit)? = null

    private val imageView: SubsamplingScaleImageView =
        if (isWebtoon) {
            MihonWebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }

    private val progressView =
        ProgressBar(context).apply {
            isIndeterminate = true
            visibility = View.GONE
        }

    private val errorView =
        TextView(context).apply {
            text = "Page failed to load\nTap to retry"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 15f
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                retryAction?.invoke()
            }
        }

    var onTap: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    init {
        imageView.apply {
            setDoubleTapZoomStyle(
                SubsamplingScaleImageView.ZOOM_FOCUS_CENTER,
            )
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object :
                    SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(
                        newScale: Float,
                        origin: Int,
                    ) {
                        onScaleChanged?.invoke(newScale)
                    }

                    override fun onCenterChanged(
                        newCenter: PointF?,
                        origin: Int,
                    ) = Unit
                },
            )
            if (!isWebtoon) {
                setOnClickListener { onTap?.invoke() }
            }
        }

        addView(
            imageView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            progressView,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        addView(
            errorView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun bind(
        page: MihonPageSpec,
        cropBorders: Boolean,
        sidePaddingPercent: Int,
        gapPx: Int = 0,
        scaleType: MihonImageScaleType = MihonImageScaleType.FIT_SCREEN,
        zoomStart: MihonZoomStart = MihonZoomStart.AUTOMATIC,
        readingMode: MihonReadingMode = MihonReadingMode.RIGHT_TO_LEFT,
        landscapeZoom: Boolean = true,
    ) {
        retryAction = {
            bind(
                page = page,
                cropBorders = cropBorders,
                sidePaddingPercent = sidePaddingPercent,
                gapPx = gapPx,
                scaleType = scaleType,
                zoomStart = zoomStart,
                readingMode = readingMode,
                landscapeZoom = landscapeZoom,
            )
        }
        errorView.visibility = View.GONE
        progressView.visibility = View.VISIBLE
        loadJob?.cancel()
        imageView.recycle()

        loadJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    MihonPageResolver.resolve(context, page)
                }
            }.onSuccess { resolved ->
                if (isWebtoon) {
                    val screenWidth =
                        resources.displayMetrics.widthPixels
                    val margin =
                        (
                            screenWidth *
                                (
                                    sidePaddingPercent
                                        .coerceIn(0, 25) / 100f
                                )
                            ).toInt()
                    val usableWidth =
                        (screenWidth - margin * 2)
                            .coerceAtLeast(1)
                    val imageHeight =
                        if (
                            resolved.width > 0 &&
                            resolved.height > 0
                        ) {
                            (
                                usableWidth *
                                    (
                                        resolved.height.toFloat() /
                                            resolved.width.toFloat()
                                        )
                                )
                                .toInt()
                                .coerceAtLeast(1)
                        } else {
                            resources.displayMetrics.heightPixels
                        }

                    pendingWebtoonLayout =
                        PendingWebtoonLayout(
                            width = usableWidth,
                            height = imageHeight + gapPx,
                            leftMargin = margin,
                            rightMargin = margin,
                            bottomMargin = gapPx,
                        )
                    applyPendingWebtoonLayout()
                }

                imageView.apply {
                    val effectiveScaleType =
                        if (isWebtoon) {
                            SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
                        } else {
                            scaleType.value
                        }

                    val effectiveZoomStart =
                        when (zoomStart) {
                            MihonZoomStart.AUTOMATIC ->
                                when (readingMode) {
                                    MihonReadingMode.LEFT_TO_RIGHT ->
                                        MihonZoomStart.LEFT
                                    MihonReadingMode.RIGHT_TO_LEFT ->
                                        MihonZoomStart.RIGHT
                                    else ->
                                        MihonZoomStart.CENTER
                                }

                            else -> zoomStart
                        }

                    setMinimumScaleType(effectiveScaleType)
                    setMinimumDpi(1)
                    setCropBorders(cropBorders)
                    setOnImageEventListener(
                        object :
                            SubsamplingScaleImageView
                                .DefaultOnImageEventListener() {
                            override fun onReady() {
                                progressView.visibility = View.GONE
                                errorView.visibility = View.GONE
                                maxScale = scale * 5f
                                setDoubleTapZoomScale(scale * 2f)

                                if (!isWebtoon) {
                                    val startPoint =
                                        when (effectiveZoomStart) {
                                            MihonZoomStart.LEFT ->
                                                PointF(0f, 0f)
                                            MihonZoomStart.RIGHT ->
                                                PointF(
                                                    sWidth.toFloat(),
                                                    0f,
                                                )
                                            MihonZoomStart.CENTER,
                                            MihonZoomStart.AUTOMATIC,
                                            -> center
                                        }

                                    setScaleAndCenter(
                                        scale,
                                        startPoint,
                                    )

                                    if (
                                        landscapeZoom &&
                                        scaleType ==
                                        MihonImageScaleType.FIT_SCREEN &&
                                        sWidth > sHeight &&
                                        scale == minScale
                                    ) {
                                        val targetScale =
                                            height.toFloat() /
                                                sHeight
                                                    .toFloat()
                                                    .coerceAtLeast(1f)
                                        animateScaleAndCenter(
                                            targetScale,
                                            startPoint,
                                        )
                                            ?.withDuration(500)
                                            ?.start()
                                    }
                                }
                            }

                            override fun onImageLoadError(e: Exception) {
                                showPageLoadError()
                            }

                            override fun onTileLoadError(e: Exception) {
                                showPageLoadError()
                            }
                        },
                    )
                    setImage(
                        ImageSource.uri(context, resolved.uri),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    return@onFailure
                }
                showPageLoadError()
            }
        }
    }

    private fun showPageLoadError() {
        progressView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyPendingWebtoonLayout()
    }

    private fun applyPendingWebtoonLayout() {
        if (!isWebtoon) return
        val pending = pendingWebtoonLayout ?: return

        // RecyclerView stores its ViewHolder reference inside its own
        // LayoutParams instance. Replacing that instance after attachment
        // corrupts RecyclerView bookkeeping and crashes on the next layout.
        // Only mutate the LayoutParams that RecyclerView assigned.
        val params =
            layoutParams as? RecyclerView.LayoutParams
                ?: return

        params.width = pending.width
        params.height = pending.height
        params.leftMargin = pending.leftMargin
        params.rightMargin = pending.rightMargin
        params.bottomMargin = pending.bottomMargin
        requestLayout()
    }

    fun canPanLeft(): Boolean =
        RectF().let { remaining ->
            imageView.getPanRemaining(remaining)
            remaining.left > 1f
        }

    fun canPanRight(): Boolean =
        RectF().let { remaining ->
            imageView.getPanRemaining(remaining)
            remaining.right > 1f
        }

    fun panLeft() {
        val center = imageView.center ?: return
        imageView.animateCenter(
            PointF(
                center.x - imageView.width / imageView.scale,
                center.y,
            ),
        )
            ?.withDuration(250)
            ?.start()
    }

    fun panRight() {
        val center = imageView.center ?: return
        imageView.animateCenter(
            PointF(
                center.x + imageView.width / imageView.scale,
                center.y,
            ),
        )
            ?.withDuration(250)
            ?.start()
    }

    fun recycle() {
        loadJob?.cancel()
        progressView.visibility = View.GONE
        errorView.visibility = View.GONE
        imageView.recycle()
    }

    fun destroy() {
        retryAction = null
        scope.cancel()
        imageView.recycle()
    }
}

private class MihonWebtoonSubsamplingImageView(
    context: Context,
) : SubsamplingScaleImageView(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

internal data class ResolvedMihonPage(
    val uri: Uri,
    val width: Int,
    val height: Int,
)

internal object MihonPageResolver {
    private val client = OkHttpClient()

    // A recycled webtoon holder can request the same remote page while an
    // earlier blocking OkHttp read is still finishing. Keep one writer per
    // cache key so no holder can observe a partially written image.
    private val networkCacheLocks =
        java.util.concurrent.ConcurrentHashMap<String, Any>()

    fun resolve(
        context: Context,
        page: MihonPageSpec,
    ): ResolvedMihonPage {
        val uri =
            when {
                page.source.startsWith("http://") ||
                    page.source.startsWith("https://") -> {
                    resolveNetworkPage(context, page)
                }

                page.source.startsWith("content://") ||
                    page.source.startsWith("file://") ||
                    page.source.startsWith(
                        "android.resource://",
                    ) -> Uri.parse(page.source)

                else -> Uri.fromFile(File(page.source))
            }

        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }

        return ResolvedMihonPage(
            uri = uri,
            width = options.outWidth.coerceAtLeast(0),
            height = options.outHeight.coerceAtLeast(0),
        )
    }

    private fun resolveNetworkPage(
        context: Context,
        page: MihonPageSpec,
    ): Uri {
        val directory =
            File(
                context.cacheDir,
                "mihon_reader_pages",
            ).apply {
                mkdirs()
            }

        val key =
            sha256(
                page.source +
                    "\n" +
                    page.headers.entries
                        .sortedBy { it.key }
                        .joinToString("\n") {
                            it.key + ":" + it.value
                        },
            )

        val target = File(directory, key)
        val lock =
            networkCacheLocks.computeIfAbsent(key) { Any() }

        synchronized(lock) {
            if (!isUsableImageFile(target)) {
                // Never stream into the final cache path. RecyclerView may
                // recycle and rebind a holder while this blocking request is
                // still running; publishing only a complete temp file keeps a
                // second holder from decoding half an image.
                target.delete()
                val partial = File(directory, key + ".part")
                partial.delete()

                try {
                    val request =
                        Request.Builder()
                            .url(page.source)
                            .apply {
                                page.headers.forEach { (name, value) ->
                                    header(name, value)
                                }
                            }
                            .build()

                    client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) {
                            "Image request failed: HTTP " +
                                response.code
                        }
                        val body = requireNotNull(response.body)
                        partial.outputStream().use { output ->
                            body.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }

                    check(isUsableImageFile(partial)) {
                        "Downloaded reader page is not a decodable image."
                    }

                    check(
                        partial.renameTo(target) ||
                            runCatching {
                                partial.copyTo(
                                    target,
                                    overwrite = true,
                                )
                                partial.delete()
                                true
                            }.getOrDefault(false),
                    ) {
                        "Could not publish downloaded reader page."
                    }
                } finally {
                    partial.delete()
                }
            }
        }

        return Uri.fromFile(target)
    }

    private fun isUsableImageFile(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false

        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        }.getOrDefault(false)
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
