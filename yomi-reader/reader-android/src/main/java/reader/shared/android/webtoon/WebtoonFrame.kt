package reader.shared.android.webtoon

import android.content.Context
import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

class WebtoonFrame(context: Context) : FrameLayout(context) {
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val flingDetector = GestureDetector(context, FlingListener())

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

    private val recycler: WebtoonRecyclerView?
        get() = getChildAt(0) as? WebtoonRecyclerView

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        flingDetector.onTouchEvent(ev)

        val recyclerRect = Rect()
        recycler?.getHitRect(recyclerRect) ?: return super.dispatchTouchEvent(ev)
        recyclerRect.inset(1, 1)

        if (recyclerRect.right < recyclerRect.left || recyclerRect.bottom < recyclerRect.top) {
            return super.dispatchTouchEvent(ev)
        }

        ev.setLocation(
            ev.x.coerceIn(recyclerRect.left.toFloat(), recyclerRect.right.toFloat()),
            ev.y.coerceIn(recyclerRect.top.toFloat(), recyclerRect.bottom.toFloat()),
        )
        return super.dispatchTouchEvent(ev)
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            recycler?.onScaleBegin()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            recycler?.onScale(detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            recycler?.onScaleEnd()
        }
    }

    inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            recycler?.onManualScroll()
            return recycler?.zoomFling(velocityX.toInt(), velocityY.toInt()) ?: false
        }
    }
}
