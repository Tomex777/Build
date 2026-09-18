package com.night.endless.engine.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class EndlessGLView(
    context: Context,
    onSelectionChanged: (String?) -> Unit
) : GLSurfaceView(context) {
    val endlessRenderer = EndlessRenderer(context, onSelectionChanged)
    private var lastX = 0f
    private var lastY = 0f

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            endlessRenderer.zoomBy(1f / detector.scaleFactor)
            return true
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

            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress && event.pointerCount == 1) {
                val dx = event.x - lastX
                val dy = event.y - lastY
                endlessRenderer.orbitBy(dx, dy, height)
                lastX = event.x
                lastY = event.y
            }
        }
        return true
    }
}
