package reader.shared.android

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.view.GestureDetector
import android.view.MotionEvent
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import coil3.asDrawable
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.github.chrisbanes.photoview.PhotoView
import okio.BufferedSource
import okio.buffer
import okio.source
import reader.shared.android.coil.ReaderImageLoader
import reader.shared.android.webtoon.WebtoonSubsamplingImageView
import java.io.InputStream

open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs) {
    private var imageView: SubsamplingScaleImageView? = null
    private var animatedView: AppCompatImageView? = null
    private var currentStream: InputStream? = null
    private var currentSource: BufferedSource? = null
    private var imageDisposable: Disposable? = null
    private var config: Config? = null
    private var selectedDirection: Boolean? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    fun setImage(stream: InputStream, config: Config, isAnimated: Boolean = false) {
        // Pager selection may arrive before this asynchronous image stream.
        // Preserve the pending direction across the old image teardown.
        val pendingDirection = selectedDirection
        recycle()
        selectedDirection = pendingDirection
        this.config = config
        currentStream = stream

        if (isAnimated) {
            setAnimatedImage(stream, config)
            return
        }

        val view = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setMinimumScaleType(config.minimumScaleType)
            setMinimumDpi(1)
            setCropBorders(config.cropBorders)
            setDoubleTapZoomDuration(config.zoomDurationMillis)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged?.invoke(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) = Unit
                },
            )
            setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        maxScale = scale * MAX_ZOOM_SCALE
                        setDoubleTapZoomScale(scale * 2f)
                        // Follow Mihon's original zoom-start preference. This
                        // happens on actual image readiness, not when the pager
                        // first selects an as-yet undecoded page.
                        when (config.zoomStartPosition) {
                            ZoomStartPosition.LEFT ->
                                setScaleAndCenter(scale, PointF(0f, sHeight / 2f))
                            ZoomStartPosition.RIGHT ->
                                setScaleAndCenter(scale, PointF(sWidth.toFloat(), sHeight / 2f))
                            ZoomStartPosition.CENTER -> Unit
                        }
                        selectedDirection?.let { this@ReaderPageImageView.onPageSelected(it) }
                        this@ReaderPageImageView.onImageLoaded?.invoke()
                    }

                    override fun onImageLoadError(e: Exception) {
                        this@ReaderPageImageView.onImageLoadError?.invoke(e)
                    }
                },
            )
        }

        imageView = view
        addView(view, MATCH_PARENT, MATCH_PARENT)
        view.setImage(ImageSource.inputStream(stream))
        view.isVisible = true
    }

    /** Animated pages use Mihon's PhotoView/Coil path; tiled pages retain SSIV. */
    private fun setAnimatedImage(stream: InputStream, config: Config) {
        val view = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context).apply {
                setScaleLevels(1f, 2f, MAX_ZOOM_SCALE)
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(event: MotionEvent): Boolean {
                            if (scale > 1f) {
                                setScale(1f, event.x, event.y, true)
                            } else {
                                setScale(2f, event.x, event.y, true)
                            }
                            return true
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ -> onScaleChanged?.invoke(scale) }
            }
        }.apply {
            adjustViewBounds = true
        }
        (view as? PhotoView)?.setZoomTransitionDuration(config.zoomDurationMillis)
        animatedView = view
        addView(view, MATCH_PARENT, MATCH_PARENT)

        val source = stream.source().buffer()
        currentSource = source
        val request = ImageRequest.Builder(context)
            .data(source)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    view.setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    view.isVisible = true
                    onImageLoaded?.invoke()
                },
            )
            .listener(
                onError = { _, result -> onImageLoadError?.invoke(result.throwable) },
            )
            .crossfade(false)
            .build()
        imageDisposable = ReaderImageLoader.get(context).enqueue(request)
    }

    fun onPageSelected(forward: Boolean) {
        selectedDirection = forward
        val view = imageView ?: return
        if (!view.isReady) return
        val cfg = config ?: return
        if (cfg.landscapeZoom &&
            cfg.minimumScaleType == SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE &&
            view.sWidth > view.sHeight &&
            view.scale == view.minScale
        ) {
            val point = when (cfg.zoomStartPosition) {
                ZoomStartPosition.LEFT ->
                    if (forward) PointF(0f, 0f) else PointF(view.sWidth.toFloat(), 0f)
                ZoomStartPosition.RIGHT ->
                    if (forward) PointF(view.sWidth.toFloat(), 0f) else PointF(0f, 0f)
                ZoomStartPosition.CENTER -> view.center ?: PointF(view.sWidth / 2f, view.sHeight / 2f)
            }
            val targetScale = height.toFloat() / view.sHeight.toFloat()
            view.animateScaleAndCenter(targetScale, point)
                ?.withDuration(500)
                ?.withEasing(EASE_IN_OUT_QUAD)
                ?.withInterruptible(true)
                ?.start()
        }
    }

    fun canPanLeft(): Boolean = canPan { it.left }
    fun canPanRight(): Boolean = canPan { it.right }

    private fun canPan(selector: (RectF) -> Float): Boolean {
        val view = imageView ?: return false
        // A page turn must never be swallowed while the tile source is still becoming ready.
        // getPanRemaining() can report transient room before the image has settled at minScale,
        // which is especially visible with SAF-backed folder pages that decode more slowly.
        if (!view.isReady || view.scale <= view.minScale * 1.01f) return false
        val remaining = RectF()
        view.getPanRemaining(remaining)
        return selector(remaining) > 1f
    }

    fun panLeft() = pan { center, view ->
        center.also { it.x -= view.width / view.scale }
    }

    fun panRight() = pan { center, view ->
        center.also { it.x += view.width / view.scale }
    }

    private fun pan(transform: (PointF, SubsamplingScaleImageView) -> PointF) {
        val view = imageView ?: return
        val center = view.center ?: return
        view.animateCenter(transform(center, view))
            ?.withEasing(EASE_OUT_QUAD)
            ?.withDuration(250)
            ?.withInterruptible(true)
            ?.start()
    }

    fun recycle() {
        imageDisposable?.dispose()
        imageDisposable = null
        imageView?.recycle()
        imageView?.let(::removeView)
        imageView = null
        animatedView?.setImageDrawable(null)
        animatedView?.let(::removeView)
        animatedView = null
        runCatching { currentSource?.close() }
        currentSource = null
        runCatching { currentStream?.close() }
        currentStream = null
        selectedDirection = null
    }

    /** Same zoom-start semantics as Mihon v0.19.9 ReaderPageImageView. */
    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }

    data class Config(
        val zoomDurationMillis: Int = 300,
        val minimumScaleType: Int = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
    )
}

private const val MAX_ZOOM_SCALE = 5f
