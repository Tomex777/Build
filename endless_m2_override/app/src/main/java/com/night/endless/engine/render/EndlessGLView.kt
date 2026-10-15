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
    private var movePointerId = MotionEvent.INVALID_POINTER_ID
    private var lookPointerId = MotionEvent.INVALID_POINTER_ID
    private var lastLookX = 0f
    private var lastLookY = 0f

    /** Normalized x/y for the native-looking, pass-through Compose stick HUD. */
    var onSurfaceStickChanged: ((Float, Float) -> Unit)? = null

    private fun resetSurfaceMovement() {
        movePointerId = MotionEvent.INVALID_POINTER_ID
        endlessRenderer.setSurfaceMovementInput(0f, 0f)
        onSurfaceStickChanged?.invoke(0f, 0f)
    }

    fun cancelSurfaceControls() {
        resetSurfaceMovement()
        lookPointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun beginSurfacePointer(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        val d = resources.displayMetrics.density
        // Align to the 148dp ring rendered at (18dp, bottom-28dp).
        val dx = x - 92f * d
        val dy = y - (height - 102f * d)
        if (movePointerId == MotionEvent.INVALID_POINTER_ID &&
            dx * dx + dy * dy < 86f * 86f * d * d) {
            movePointerId = id
            updateStick(x, y)
        } else if (lookPointerId == MotionEvent.INVALID_POINTER_ID) {
            lookPointerId = id
            lastLookX = x
            lastLookY = y
        }
    }

    private fun updateStick(x: Float, y: Float) {
        val d = resources.displayMetrics.density
        val dx = x - 92f * d
        val dy = y - (height - 102f * d)
        val axes = SurfaceJoystickMath.fromDisplacement(dx, dy, 51f * d)
        endlessRenderer.setSurfaceMovementInput(axes.first, axes.second)
        onSurfaceStickChanged?.invoke(axes.second, -axes.first)
    }

    private fun handleSurfaceTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginSurfacePointer(event, 0)
            MotionEvent.ACTION_POINTER_DOWN ->
                beginSurfacePointer(event, event.actionIndex)
            MotionEvent.ACTION_MOVE -> {
                val stickIndex = event.findPointerIndex(movePointerId)
                if (stickIndex >= 0) updateStick(
                    event.getX(stickIndex), event.getY(stickIndex)
                )
                val lookIndex = event.findPointerIndex(lookPointerId)
                if (lookIndex >= 0) {
                    val x = event.getX(lookIndex)
                    val y = event.getY(lookIndex)
                    endlessRenderer.orbitBy(x - lastLookX, y - lastLookY, height)
                    lastLookX = x
                    lastLookY = y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val leavingId = event.getPointerId(event.actionIndex)
                if (leavingId == movePointerId) resetSurfaceMovement()
                if (leavingId == lookPointerId) {
                    lookPointerId = MotionEvent.INVALID_POINTER_ID
                    // Another unassigned finger can keep aiming without a jump.
                    for (i in 0 until event.pointerCount) {
                        if (i == event.actionIndex || event.getPointerId(i) == movePointerId) continue
                        lookPointerId = event.getPointerId(i)
                        lastLookX = event.getX(i)
                        lastLookY = event.getY(i)
                        break
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelSurfaceControls()
        }
        return true
    }

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

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Picking on release keeps the hit test tied to this gesture. Waiting
            // for double-tap confirmation can pick a different projected body
            // after a subsequent overview drag or Compose label selection.
            endlessRenderer.pick(e.x, e.y)
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        // GLSurfaceView's default chooser only requires 16 depth bits.
        // Human-scale stones/shadows over kilometre terrain need 24 bits
        // to avoid quantized depth stripes at grazing viewing angles.
        setEGLConfigChooser(8, 8, 8, 0, 24, 0)
        setRenderer(endlessRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (endlessRenderer.isSurfaceMode()) {
            return handleSurfaceTouch(event)
        }
        if (movePointerId != MotionEvent.INVALID_POINTER_ID ||
            lookPointerId != MotionEvent.INVALID_POINTER_ID) cancelSurfaceControls()
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
