package com.example.whatsapp.presentation.reader.mihon

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.io.File
import java.security.MessageDigest
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

    private val imageView: SubsamplingScaleImageView =
        if (isWebtoon) {
            MihonWebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
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
    }

    fun bind(
        page: MihonPageSpec,
        cropBorders: Boolean,
        sidePaddingPercent: Int,
        gapPx: Int = 0,
    ) {
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

                    layoutParams =
                        RecyclerView.LayoutParams(
                            usableWidth,
                            imageHeight + gapPx,
                        ).apply {
                            leftMargin = margin
                            rightMargin = margin
                            bottomMargin = gapPx
                        }
                }

                imageView.apply {
                    setMinimumScaleType(
                        SubsamplingScaleImageView
                            .SCALE_TYPE_CENTER_INSIDE,
                    )
                    setMinimumDpi(1)
                    setCropBorders(cropBorders)
                    setOnImageEventListener(
                        object :
                            SubsamplingScaleImageView
                                .DefaultOnImageEventListener() {
                            override fun onReady() {
                                maxScale = scale * 5f
                                setDoubleTapZoomScale(scale * 2f)
                            }
                        },
                    )
                    setImage(
                        ImageSource.uri(context, resolved.uri),
                    )
                }
            }
        }
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
        imageView.recycle()
    }

    fun destroy() {
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
                    "
" +
                    page.headers.entries
                        .sortedBy { it.key }
                        .joinToString("
") {
                            it.key + ":" + it.value
                        },
            )

        val target = File(directory, key)
        if (!target.exists() || target.length() == 0L) {
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
                target.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
        }

        return Uri.fromFile(target)
    }

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
