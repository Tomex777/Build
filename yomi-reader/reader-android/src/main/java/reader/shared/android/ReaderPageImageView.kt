package reader.shared.android

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import reader.shared.android.webtoon.WebtoonSubsamplingImageView
import java.io.InputStream

open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs) {
    private var imageView: SubsamplingScaleImageView? = null
    private var currentStream: InputStream? = null
    private var config: Config? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    fun setImage(stream: InputStream, config: Config) {
        recycle()
        this.config = config
        currentStream = stream

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

    fun onPageSelected(forward: Boolean) {
        val view = imageView ?: return
        if (!view.isReady) return
        val cfg = config ?: return
        if (cfg.landscapeZoom &&
            cfg.minimumScaleType == SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE &&
            view.sWidth > view.sHeight &&
            view.scale == view.minScale
        ) {
            val point = if (forward) PointF(0f, 0f) else PointF(view.sWidth.toFloat(), 0f)
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
        imageView?.recycle()
        imageView?.let(::removeView)
        imageView = null
        runCatching { currentStream?.close() }
        currentStream = null
    }

    data class Config(
        val zoomDurationMillis: Int = 300,
        val minimumScaleType: Int = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val landscapeZoom: Boolean = false,
    )
}

private const val MAX_ZOOM_SCALE = 5f
