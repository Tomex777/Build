package com.example.whatsapp.presentation.reader.mihon

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.DirectionalViewPager
import kotlin.math.abs

/*
 * Transplanted/adapted from Mihon's:
 * - GestureDetectorWithLongTap.kt
 * - viewer/pager/Pager.kt
 * - viewer/webtoon/WebtoonRecyclerView.kt
 * - viewer/webtoon/WebtoonFrame.kt
 *
 * Upstream revision 424bbc53b85c19acd3c3b7c03ec6f73f516f25bc, Apache-2.0.
 */
internal open class MihonGestureDetectorWithLongTap(
    context: Context,
    listener: Listener,
) : GestureDetector(context, listener) {
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longTapTime = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapTime = ViewConfiguration.getDoubleTapTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var lastUp = 0L
    private var lastDownEvent: MotionEvent? = null
    private val longTapFn = Runnable {
        lastDownEvent?.let(listener::onLongTapConfirmed)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDownEvent?.recycle()
                lastDownEvent = MotionEvent.obtain(ev)
                if (ev.downTime - lastUp > doubleTapTime) {
                    downX = ev.x
                    downY = ev.y
                    handler.postDelayed(longTapFn, longTapTime)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) {
                    handler.removeCallbacks(longTapFn)
                }
            }
            MotionEvent.ACTION_UP -> {
                lastUp = ev.eventTime
                handler.removeCallbacks(longTapFn)
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN,
            -> handler.removeCallbacks(longTapFn)
        }
        return super.onTouchEvent(ev)
    }

    internal open class Listener : SimpleOnGestureListener() {
        open fun onLongTapConfirmed(ev: MotionEvent) = Unit
    }
}

internal open class MihonPager(
    context: Context,
    isHorizontal: Boolean = true,
) : DirectionalViewPager(context, isHorizontal) {
    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Boolean)? = null
    private var gestureDetectorEnabled = true

    private val gestureListener = object : MihonGestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            tapListener?.invoke(ev)
            return true
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            if (longTapListener?.invoke(ev) == true) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private val gestureDetector =
        MihonGestureDetectorWithLongTap(context, gestureListener)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (gestureDetectorEnabled) gestureDetector.onTouchEvent(ev)
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        try {
            super.onInterceptTouchEvent(ev)
        } catch (_: IllegalArgumentException) {
            false
        }

    override fun onTouchEvent(ev: MotionEvent): Boolean =
        try {
            super.onTouchEvent(ev)
        } catch (_: NullPointerException) {
            false
        } catch (_: IndexOutOfBoundsException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    override fun executeKeyEvent(event: KeyEvent): Boolean = false

    fun setGestureDetectorEnabled(enabled: Boolean) {
        gestureDetectorEnabled = enabled
    }
}

internal class MihonWebtoonRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {
    private var zooming = false
    private var atLastPosition = false
    private var atFirstPosition = false
    private var halfWidth = 0
    private var halfHeight = 0

    var originalHeight = 0
        private set

    private var heightSet = false
    private var firstVisibleItemPosition = 0
    private var lastVisibleItemPosition = 0
    private var currentScale = DEFAULT_RATE

    var zoomOutDisabled = false
        set(value) {
            field = value
            if (value && currentScale < DEFAULT_RATE) {
                zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
            }
        }

    private val minRate: Float
        get() = if (zoomOutDisabled) DEFAULT_RATE else MIN_RATE

    private val listener = GestureListener()
    private val detector = Detector()

    var doubleTapZoom = true
    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    private var manuallyScrolling = false
    private var tapDuringManualScroll = false

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2
        halfHeight = MeasureSpec.getSize(heightSpec) / 2
        if (!heightSet) {
            originalHeight = MeasureSpec.getSize(heightSpec)
            heightSet = true
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            tapDuringManualScroll = manuallyScrolling
        }
        detector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val manager = layoutManager as? LinearLayoutManager ?: return
        lastVisibleItemPosition = manager.findLastVisibleItemPosition()
        firstVisibleItemPosition = manager.findFirstVisibleItemPosition()
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val manager = layoutManager
        val visibleItemCount = manager?.childCount ?: 0
        val totalItemCount = manager?.itemCount ?: 0
        atLastPosition =
            visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1
        atFirstPosition = firstVisibleItemPosition == 0
        if (state == SCROLL_STATE_IDLE) manuallyScrolling = false
    }

    private fun getPositionX(positionX: Float): Float {
        if (currentScale < 1f) return 0f
        val maxPositionX = halfWidth * (currentScale - 1f)
        return positionX.coerceIn(-maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        if (currentScale < 1f) {
            return (originalHeight / 2 - halfHeight).toFloat()
        }
        val maxPositionY = halfHeight * (currentScale - 1f)
        return positionY.coerceIn(-maxPositionY, maxPositionY)
    }

    private fun zoom(
        fromRate: Float,
        toRate: Float,
        fromX: Float,
        toX: Float,
        fromY: Float,
        toY: Float,
    ) {
        zooming = true
        val animatorSet = AnimatorSet()

        val translationX = ValueAnimator.ofFloat(fromX, toX).apply {
            addUpdateListener { animation ->
                x = animation.animatedValue as Float
            }
        }
        val translationY = ValueAnimator.ofFloat(fromY, toY).apply {
            addUpdateListener { animation ->
                y = animation.animatedValue as Float
            }
        }
        val scale = ValueAnimator.ofFloat(fromRate, toRate).apply {
            addUpdateListener { animation ->
                currentScale = animation.animatedValue as Float
                setScaleRate(currentScale)
            }
        }

        animatorSet.playTogether(translationX, translationY, scale)
        animatorSet.duration = 200L
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
        animatorSet.doOnEnd {
            zooming = false
            currentScale = toRate
        }
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (currentScale <= 1f) return false

        val animatorSet = AnimatorSet()
        if (velocityX != 0) {
            val newX = getPositionX(x + 0.4f * velocityX / 2)
            ValueAnimator.ofFloat(x, newX).also { animation ->
                animation.addUpdateListener {
                    x = getPositionX(it.animatedValue as Float)
                }
                animatorSet.play(animation)
            }
        }
        if (velocityY != 0 && (atFirstPosition || atLastPosition)) {
            val newY = getPositionY(y + 0.4f * velocityY / 2)
            ValueAnimator.ofFloat(y, newY).also { animation ->
                animation.addUpdateListener {
                    y = getPositionY(it.animatedValue as Float)
                }
                animatorSet.play(animation)
            }
        }

        animatorSet.duration = 400L
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
        return true
    }

    private fun zoomScrollBy(dx: Int, dy: Int) {
        if (dx != 0) x = getPositionX(x + dx)
        if (dy != 0) y = getPositionY(y + dy)
    }

    private fun setScaleRate(rate: Float) {
        scaleX = rate
        scaleY = rate
    }

    fun onScale(scaleFactor: Float) {
        currentScale =
            (currentScale * scaleFactor).coerceIn(minRate, MAX_SCALE_RATE)
        setScaleRate(currentScale)

        layoutParams.height =
            if (currentScale < 1f) {
                (originalHeight / currentScale).toInt()
            } else {
                originalHeight
            }

        halfHeight = layoutParams.height / 2

        if (currentScale != DEFAULT_RATE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }
        requestLayout()
    }

    fun onScaleBegin() {
        if (detector.isDoubleTapping) detector.isQuickScaling = true
    }

    fun onScaleEnd() {
        if (scaleX < minRate) {
            zoom(currentScale, minRate, x, 0f, y, 0f)
        }
    }

    fun onManualScroll() {
        manuallyScrolling = true
    }

    inner class GestureListener : MihonGestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            if (!tapDuringManualScroll) tapListener?.invoke(ev)
            return false
        }

        override fun onDoubleTap(ev: MotionEvent): Boolean {
            detector.isDoubleTapping = true
            return false
        }

        fun onDoubleTapConfirmed(ev: MotionEvent) {
            if (!zooming && doubleTapZoom) {
                if (scaleX != DEFAULT_RATE) {
                    zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
                    layoutParams.height = originalHeight
                    halfHeight = layoutParams.height / 2
                    requestLayout()
                } else {
                    val targetScale = 2f
                    val targetX = (halfWidth - ev.x) * (targetScale - 1f)
                    val targetY = (halfHeight - ev.y) * (targetScale - 1f)
                    zoom(
                        DEFAULT_RATE,
                        targetScale,
                        0f,
                        targetX,
                        0f,
                        targetY,
                    )
                }
            }
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            if (longTapListener?.invoke(ev) == true) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    inner class Detector :
        MihonGestureDetectorWithLongTap(context, listener) {
        private var scrollPointerId = 0
        private var downX = 0
        private var downY = 0
        private val touchSlop =
            ViewConfiguration.get(context).scaledTouchSlop
        private var zoomDragging = false

        var isDoubleTapping = false
        var isQuickScaling = false

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val actionIndex = ev.actionIndex
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    scrollPointerId = ev.getPointerId(0)
                    downX = (ev.x + 0.5f).toInt()
                    downY = (ev.y + 0.5f).toInt()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollPointerId = ev.getPointerId(actionIndex)
                    downX = (ev.getX(actionIndex) + 0.5f).toInt()
                    downY = (ev.getY(actionIndex) + 0.5f).toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDoubleTapping && isQuickScaling) return true
                    val index = ev.findPointerIndex(scrollPointerId)
                    if (index < 0) return false

                    val currentX = (ev.getX(index) + 0.5f).toInt()
                    val currentY = (ev.getY(index) + 0.5f).toInt()
                    var dx = currentX - downX
                    var dy =
                        if (atFirstPosition || atLastPosition) {
                            currentY - downY
                        } else {
                            0
                        }

                    if (!zoomDragging && currentScale > 1f) {
                        var startScroll = false
                        if (abs(dx) > touchSlop) {
                            dx += if (dx < 0) touchSlop else -touchSlop
                            startScroll = true
                        }
                        if (abs(dy) > touchSlop) {
                            dy += if (dy < 0) touchSlop else -touchSlop
                            startScroll = true
                        }
                        if (startScroll) zoomDragging = true
                    }

                    if (zoomDragging) zoomScrollBy(dx, dy)
                }
                MotionEvent.ACTION_UP -> {
                    if (isDoubleTapping && !isQuickScaling) {
                        listener.onDoubleTapConfirmed(ev)
                    }
                    zoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    zoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
            }
            return super.onTouchEvent(ev)
        }
    }

    companion object {
        private const val MIN_RATE = 0.5f
        private const val DEFAULT_RATE = 1f
        private const val MAX_SCALE_RATE = 3f
    }
}

internal class MihonWebtoonFrame(
    context: Context,
) : FrameLayout(context) {
    private val scaleDetector =
        ScaleGestureDetector(context, ScaleListener())
    private val flingDetector =
        GestureDetector(context, FlingListener())

    var doubleTapZoom = true
        set(value) {
            field = value
            recycler?.doubleTapZoom = value
            scaleDetector.isQuickScaleEnabled = value
        }

    var zoomOutDisabled = false
        set(value) {
            field = value
            recycler?.zoomOutDisabled = value
        }

    private val recycler: MihonWebtoonRecyclerView?
        get() = getChildAt(0) as? MihonWebtoonRecyclerView

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        flingDetector.onTouchEvent(ev)

        val recyclerRect = Rect()
        recycler?.getHitRect(recyclerRect)
            ?: return super.dispatchTouchEvent(ev)

        recyclerRect.inset(1, 1)
        if (
            recyclerRect.right < recyclerRect.left ||
            recyclerRect.bottom < recyclerRect.top
        ) {
            return super.dispatchTouchEvent(ev)
        }

        ev.setLocation(
            ev.x.coerceIn(
                recyclerRect.left.toFloat(),
                recyclerRect.right.toFloat(),
            ),
            ev.y.coerceIn(
                recyclerRect.top.toFloat(),
                recyclerRect.bottom.toFloat(),
            ),
        )
        return super.dispatchTouchEvent(ev)
    }

    inner class ScaleListener :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(
            detector: ScaleGestureDetector,
        ): Boolean {
            recycler?.onScaleBegin()
            return true
        }

        override fun onScale(
            detector: ScaleGestureDetector,
        ): Boolean {
            recycler?.onScale(detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            recycler?.onScaleEnd()
        }
    }

    inner class FlingListener :
        GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            recycler?.onManualScroll()
            return recycler?.zoomFling(
                velocityX.toInt(),
                velocityY.toInt(),
            ) ?: false
        }
    }
}
