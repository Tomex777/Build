package com.night.endless.engine.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.pow

class EndlessGLView(
    context: Context,
    onSelectionChanged: (String?) -> Unit
) : GLSurfaceView(context) {
    val endlessRenderer = EndlessRenderer(context, onSelectionChanged)
    private var lastX = 0f
    private var lastY = 0f

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Softer than raw pinch scaling; closer to the HTML OrbitControls feel.
            val adjusted = detector.scaleFactor.toDouble().pow(-0.72).toFloat()
            endlessRenderer.zoomBy(adjusted)
            lastX = detector.focusX
            lastY = detector.focusY
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            lastX = detector.focusX
            lastY = detector.focusY
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            endlessRenderer.pick(e.x, e.y)
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        setRenderer(endlessRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaler.isInProgress || event.pointerCount > 1) {
                    // Keep the reference point current while pinching so the first
                    // single-finger move afterwards cannot produce a jump.
                    lastX = event.x
                    lastY = event.y
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    endlessRenderer.orbitBy(dx, dy, height)
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val remainingIndex =
                    if (event.actionIndex == 0 && event.pointerCount > 1) 1 else 0
                lastX = event.getX(remainingIndex)
                lastY = event.getY(remainingIndex)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                lastX = event.x
                lastY = event.y
            }
        }

        return true
    }
}
